package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.entity.*
import com.kixyu9527.kixyubook.core.reader.engine.BookParserRegistry
import com.kixyu9527.kixyubook.core.reader.engine.BookParser
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapterOutline
import com.kixyu9527.kixyubook.core.reader.engine.EpubBookParser
import com.kixyu9527.kixyubook.core.reader.engine.TxtBookParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBookRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: KixyuDatabase,
    private val dao: BookDao,
) : BookRepository {
    private val parsers = BookParserRegistry()
    // Parsed XHTML is derived data, but it must not disappear during ordinary Android cache
    // reclamation. A partially evicted cache made otherwise identical directory jumps vary from
    // instant to a full ZIP/XHTML parse. noBackupFilesDir persists it without bloating backups.
    private val epubChapterCache = EpubChapterCache(
        File(context.noBackupFilesDir, "epub-chapters").apply(File::mkdirs),
    )
    private val chapterCacheLock = Any()
    private val chapterLoadMutex = Mutex()
    private val importRegistrationMutex = Mutex()
    private val importIndexSemaphore = Semaphore(IMPORT_INDEX_CONCURRENCY)
    private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val importIndexJobs = ConcurrentHashMap<String, Job>()
    private val importEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val chapterCache = object : LinkedHashMap<ChapterCacheKey, ChapterContent>(
        CHAPTER_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChapterCacheKey, ChapterContent>) =
            size > CHAPTER_CACHE_SIZE
    }

    override fun observeLibrary(): Flow<List<LibraryBook>> = combine(dao.observeBooks(), dao.observeAllProgress()) { books, progresses ->
        val byBook = progresses.associateBy { it.bookUuid }
        books.map { LibraryBook(it.toModel(), byBook[it.uuid]?.toModel()) }
            .sortedWith(compareByDescending<LibraryBook> { it.progress?.updatedTime ?: 0L }.thenByDescending { it.book.createdTime })
    }

    override fun observeImportEvents(): Flow<String> = importEvents.asSharedFlow()

    override suspend fun importDocuments(uriStrings: List<String>): ImportSummary = withContext(Dispatchers.IO) {
        val registration = importRegistrationMutex.withLock {
            registerDocuments(uriStrings)
        }
        registration.imports.forEach(::enqueueBackgroundIndex)
        ImportSummary(registration.imports.size, registration.duplicateCount, registration.failures)
    }

    /**
     * Registers every selected file before any expensive body parsing starts. Room observers can
     * therefore show the complete selection in the library immediately instead of waiting for the
     * preceding book's full-text index.
     */
    private suspend fun registerDocuments(uriStrings: List<String>): ImportRegistration {
        val imports = mutableListOf<RegisteredImport>()
        var duplicates = 0
        val failures = mutableListOf<String>()
        uriStrings.distinct().forEach { rawUri ->
            val uri = rawUri.toUri()
            val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "未命名小说" }
            val importDir = File(context.cacheDir, "imports").apply { mkdirs() }
            val temp = File(importDir, UUID.randomUUID().toString())
            var insertedUuid: String? = null
            try {
                val hash = context.contentResolver.openInputStream(uri)?.use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    temp.outputStream().use { output -> DigestInputStream(input, digest).use { it.copyTo(output) } }
                    digest.digest().joinToString("") { "%02x".format(it) }
                } ?: error("无法读取文件")
                if (dao.findUuidByHash(hash) != null) {
                    duplicates++
                    return@forEach
                }
                val format = detectFormat(displayName, temp)
                val parser = parsers.parserFor(format)
                val metadata = parser.readMetadata(temp, displayName)
                val identity = metadata.identityHint?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                val bookUuid = identity?.takeUnless { dao.bookExists(it) } ?: UUID.randomUUID().toString()
                if (identity != null && dao.bookExists(identity)) {
                    duplicates++
                    return@forEach
                }
                val extension = if (format == BookFormat.EPUB) "epub" else "txt"
                val stored = File(context.filesDir, "books/$bookUuid.$extension").also { it.parentFile?.mkdirs() }
                temp.copyTo(stored, overwrite = true)
                val coverPath = metadata.coverBytes?.let { bytes ->
                    File(context.filesDir, "covers/$bookUuid.${metadata.coverExtension}").also {
                        it.parentFile?.mkdirs()
                        it.writeBytes(bytes)
                    }.absolutePath
                }
                dao.insertBook(
                    BookEntity(bookUuid, metadata.title, metadata.author, metadata.description, coverPath, format.name, rawUri, stored.absolutePath, System.currentTimeMillis(), hash, "未分类"),
                )
                insertedUuid = bookUuid
                val outlines = if (format == BookFormat.EPUB) {
                    registerEpubDirectory(bookUuid, stored, parser as EpubBookParser)
                        .also { if (it.isEmpty()) error("未找到可阅读章节") }
                } else {
                    emptyList()
                }
                imports += RegisteredImport(bookUuid, displayName, hash, format, stored, parser, outlines)
            } catch (error: CancellationException) {
                insertedUuid?.let { removeIncompleteImport(it) }
                throw error
            } catch (error: Exception) {
                insertedUuid?.let { removeIncompleteImport(it) }
                failures += "$displayName：${error.message ?: "导入失败"}"
            } finally {
                temp.delete()
            }
        }
        return ImportRegistration(imports, duplicates, failures)
    }

    private fun enqueueBackgroundIndex(book: RegisteredImport) {
        val job = importScope.launch(start = CoroutineStart.LAZY) {
            importIndexSemaphore.withPermit {
                try {
                    if (book.format == BookFormat.EPUB) {
                        indexEpubChapters(
                            bookUuid = book.bookUuid,
                            contentHash = book.contentHash,
                            source = book.source,
                            parser = book.parser as EpubBookParser,
                            outlines = book.outlines,
                        )
                    } else if (importStreamingChapters(book.bookUuid, book.source, book.parser) == 0) {
                        error("未找到可阅读章节")
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (book.format == BookFormat.TXT) {
                        removeIncompleteImport(book.bookUuid)
                        importEvents.emit("${book.displayName}：${error.message ?: "导入失败"}")
                    } else {
                        // EPUB navigation and lazy chapter reading remain usable even if the
                        // optional full-text search index fails midway.
                        importEvents.emit("${book.displayName}：全文索引未完成，仍可正常阅读")
                    }
                }
            }
        }
        importIndexJobs.put(book.bookUuid, job)?.cancel()
        job.invokeOnCompletion { importIndexJobs.remove(book.bookUuid, job) }
        job.start()
    }

    private fun detectFormat(name: String, file: File): BookFormat {
        if (name.endsWith(".epub", true) || file.isEpub()) return BookFormat.EPUB
        if (name.endsWith(".txt", true)) return BookFormat.TXT
        val sample = FileInputStream(file).use { input -> ByteArray(1024).let { it.copyOf(input.read(it).coerceAtLeast(0)) } }
        if (sample.any { it == '\n'.code.toByte() } || sample.isNotEmpty()) return BookFormat.TXT
        error("仅支持 TXT 与 EPUB")
    }

    private fun File.isEpub(): Boolean = runCatching {
        ZipFile(this).use { zip -> zip.getEntry("mimetype")?.let { zip.getInputStream(it).bufferedReader().readText().trim() } == "application/epub+zip" }
    }.getOrDefault(false)

    override suspend fun deleteBook(bookUuid: String) = deleteBooks(setOf(bookUuid))

    override suspend fun deleteBooks(bookUuids: Set<String>): Unit = withContext(Dispatchers.IO) {
        if (bookUuids.isEmpty()) return@withContext
        bookUuids.forEach { bookUuid -> importIndexJobs.remove(bookUuid)?.cancel() }
        val books = dao.getBooks(bookUuids)
        database.withTransaction { dao.deleteBooks(bookUuids) }
        synchronized(chapterCacheLock) {
            chapterCache.keys.removeAll { it.bookUuid in bookUuids }
        }
        books.forEach { book ->
            epubChapterCache.clearBook(book.uuid)
            File(book.storagePath).delete()
            book.coverPath?.let(::File)?.delete()
        }
    }

    override suspend fun getBook(bookUuid: String) = withContext(Dispatchers.IO) { dao.getBook(bookUuid)?.toModel() }
    override suspend fun getChapters(bookUuid: String) = withContext(Dispatchers.IO) { dao.getChapters(bookUuid).map { it.toModel() } }
    override fun observeChapters(bookUuid: String): Flow<List<Chapter>> =
        dao.observeChapters(bookUuid).map { rows -> rows.map(ChapterEntity::toModel) }

    override suspend fun getChapter(bookUuid: String, chapterIndex: Int): ChapterContent? = withContext(Dispatchers.IO) {
        val cacheKey = ChapterCacheKey(bookUuid, chapterIndex)
        synchronized(chapterCacheLock) { chapterCache[cacheKey] }?.let { return@withContext it }
        chapterLoadMutex.withLock {
            synchronized(chapterCacheLock) { chapterCache[cacheKey] }?.let { return@withLock it }
            val chapter = dao.getChapter(bookUuid, chapterIndex) ?: return@withLock null
            val storedParagraphs = dao.getParagraphs(chapter.id)
            val book = dao.getBook(bookUuid)
            val paragraphs = if (book?.format == BookFormat.EPUB.name) {
                val parsed = epubChapterCache.read(bookUuid, book.contentHash, chapterIndex)
                    ?: runCatching {
                        (parsers.parserFor(BookFormat.EPUB) as EpubBookParser)
                            .readChapter(File(book.storagePath), chapterIndex, chapter.title)
                    }.getOrNull()?.also { parsedChapter ->
                        epubChapterCache.write(bookUuid, book.contentHash, chapterIndex, parsedChapter)
                    }
                parsed?.toReaderParagraphs(chapter.id, storedParagraphs) ?: storedParagraphs.map { paragraph ->
                    Paragraph(paragraph.id, paragraph.chapterId, paragraph.paragraphIndex, paragraph.text)
                }
            } else {
                storedParagraphs.map { paragraph ->
                    Paragraph(paragraph.id, paragraph.chapterId, paragraph.paragraphIndex, paragraph.text)
                }
            }
            ChapterContent(chapter.toModel(), paragraphs).also { content ->
                synchronized(chapterCacheLock) { chapterCache[cacheKey] = content }
            }
        }
    }

    override suspend fun prepareReader(bookUuid: String) {
        val chapterIndex = withContext(Dispatchers.IO) {
            val progress = dao.getProgress(bookUuid)
            val chapters = dao.getChapters(bookUuid)
            progress?.chapterId?.let { chapterId ->
                chapters.firstOrNull { it.id == chapterId }?.chapterIndex
            } ?: chapters.firstOrNull()?.chapterIndex
        } ?: return
        getChapter(bookUuid, chapterIndex)
    }

    override fun observeProgress(bookUuid: String) = dao.observeProgress(bookUuid).map { it?.toModel() }
    override suspend fun saveProgress(progress: ReadingProgress) = withContext(Dispatchers.IO) {
        dao.saveProgress(ReadingProgressEntity(progress.bookUuid, progress.chapterId, progress.position, progress.offset, progress.updatedTime, progress.fraction))
    }

    override suspend fun updateBookMetadata(bookUuid: String, title: String, author: String, description: String): Unit = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookUuid) ?: error("书籍不存在")
        dao.insertMetadataEdit(MetadataEditEntity(UUID.randomUUID().toString(), bookUuid, book.title, book.author, book.description, title.trim(), author.trim(), description.trim(), System.currentTimeMillis()))
        dao.updateBookMetadata(bookUuid, title.trim().ifBlank { "未命名书籍" }, author.trim().ifBlank { "未知作者" }, description.trim())
    }

    override suspend fun reparseTxt(bookUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val book = dao.getBook(bookUuid) ?: error("书籍不存在")
            require(book.format == BookFormat.TXT.name) { "只有 TXT 可以重新解析" }
            val source = File(book.storagePath)
            require(source.isFile) { "找不到原始 TXT 文件" }
            val parser = parsers.parserFor(BookFormat.TXT)
            val metadata = parser.readMetadata(source, book.title)
            val previousChapters = dao.getChapters(bookUuid)
            val previousChapterIndex = previousChapters.withIndex().associate { it.value.id to it.index }
            val previousProgress = dao.getProgress(bookUuid)
            val previousBookmarks = dao.getBookmarks(bookUuid)
            val previousProgressText = previousProgress?.let { progress ->
                dao.getParagraph(progress.chapterId, progress.position)?.text
            }
            database.withTransaction {
                dao.deleteProgress(bookUuid)
                dao.deleteChapters(bookUuid)
                var chapterIndex = 0
                val chapterIds = mutableListOf<Long>()
                parser.readChapters(source) { chapter ->
                    val chapterId = dao.insertChapter(
                        ChapterEntity(bookUuid = bookUuid, title = chapter.title, chapterIndex = chapterIndex++),
                    )
                    chapterIds += chapterId
                    dao.insertParagraphsChunked(chapterId, chapter.paragraphs)
                }
                require(chapterIndex > 0) { "未找到可阅读章节" }
                val paragraphsByChapter = chapterIds.associateWith { chapterId ->
                    dao.getParagraphs(chapterId)
                }

                previousBookmarks.forEach { bookmark ->
                    val targetChapterId = chapterIds[bookmark.chapterIndex.coerceIn(chapterIds.indices)]
                    val targetPosition = bookmark.position.coerceIn(
                        0,
                        paragraphsByChapter[targetChapterId].orEmpty().lastIndex.coerceAtLeast(0),
                    )
                    dao.insertBookmark(
                        BookmarkEntity(
                            uuid = bookmark.uuid,
                            bookUuid = bookUuid,
                            chapterId = targetChapterId,
                            position = targetPosition,
                            preview = bookmark.preview,
                            createdTime = bookmark.createdTime,
                        ),
                    )
                }

                previousProgress?.let { progress ->
                    val targetChapterId = previousChapterIndex[progress.chapterId]
                        ?.coerceIn(0, chapterIds.lastIndex)
                        ?.let(chapterIds::get)
                        ?: chapterIds.first()
                    val targetParagraphs = paragraphsByChapter[targetChapterId].orEmpty()
                    val lastParagraph = targetParagraphs.lastIndex.coerceAtLeast(0)
                    val targetPosition = previousProgressText
                        ?.let { text -> targetParagraphs.indexOfFirst { it.text == text } }
                        ?.takeIf { it >= 0 }
                        ?: progress.position.coerceIn(0, lastParagraph)
                    dao.saveProgress(
                        progress.copy(
                            chapterId = targetChapterId,
                            position = targetPosition,
                        ),
                    )
                }
                if (!dao.hasMetadataEdits(bookUuid)) {
                    dao.updateBookMetadata(
                        bookUuid,
                        metadata.title.trim().ifBlank { book.title },
                        metadata.author.trim().ifBlank { "未知作者" },
                        metadata.description.trim(),
                    )
                }
            }
            synchronized(chapterCacheLock) {
                chapterCache.keys.removeAll { it.bookUuid == bookUuid }
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun setCategory(bookUuid: String, category: String) = withContext(Dispatchers.IO) { dao.setCategory(bookUuid, category.trim().ifBlank { "未分类" }) }

    override fun observeBookmarks(bookUuid: String): Flow<List<Bookmark>> =
        dao.observeBookmarks(bookUuid).map { rows -> rows.map { it.toModel() } }

    override suspend fun addBookmark(bookmark: Bookmark): Unit = withContext(Dispatchers.IO) {
        dao.insertBookmark(
            BookmarkEntity(
                uuid = bookmark.uuid,
                bookUuid = bookmark.bookUuid,
                chapterId = bookmark.chapterId,
                position = bookmark.position,
                preview = bookmark.preview,
                createdTime = bookmark.createdTime,
            ),
        )
    }

    override suspend fun deleteBookmark(bookmarkUuid: String) = withContext(Dispatchers.IO) {
        dao.deleteBookmark(bookmarkUuid)
    }

    override suspend fun searchBook(bookUuid: String, query: String): List<BookSearchResult> = withContext(Dispatchers.IO) {
        val escaped = query.trim().replace("~", "~~").replace("%", "~%").replace("_", "~_")
        if (escaped.isBlank()) emptyList() else dao.searchBook(bookUuid, escaped).map { it.toModel() }
    }

    private suspend fun importStreamingChapters(
        bookUuid: String,
        source: File,
        parser: BookParser,
    ): Int {
        var chapterIndex = 0
        val pending = ArrayList<DocumentChapter>(IMPORT_CHAPTER_BATCH_SIZE)

        suspend fun flush() {
            if (pending.isEmpty()) return
            val batch = pending.toList()
            pending.clear()
            database.withTransaction {
                val startIndex = chapterIndex
                val chapterIds = dao.insertChapters(batch.mapIndexed { offset, chapter ->
                    ChapterEntity(bookUuid = bookUuid, title = chapter.title, chapterIndex = startIndex + offset)
                })
                batch.zip(chapterIds).forEach { (chapter, chapterId) ->
                    dao.insertParagraphsChunked(chapterId, chapter.paragraphs)
                }
                chapterIndex += batch.size
            }
        }

        parser.readChapters(source) { chapter ->
            pending += chapter
            if (pending.size >= IMPORT_CHAPTER_BATCH_SIZE) flush()
        }
        flush()
        return chapterIndex
    }

    private suspend fun removeIncompleteImport(bookUuid: String) {
        dao.getBook(bookUuid)?.let { book ->
            File(book.storagePath).delete()
            book.coverPath?.let(::File)?.delete()
        }
        epubChapterCache.clearBook(bookUuid)
        dao.deleteBook(bookUuid)
    }

    private suspend fun registerEpubDirectory(
        bookUuid: String,
        source: File,
        parser: EpubBookParser,
    ): List<DocumentChapterOutline> {
        val outlines = parser.readChapterOutlines(source)
        if (outlines.isEmpty()) return emptyList()
        database.withTransaction {
            dao.insertChapters(
                outlines.map { outline ->
                    ChapterEntity(
                        bookUuid = bookUuid,
                        title = outline.title,
                        chapterIndex = outline.sourceIndex,
                    )
                },
            )
        }
        return outlines
    }

    private suspend fun indexEpubChapters(
        bookUuid: String,
        contentHash: String,
        source: File,
        parser: EpubBookParser,
        outlines: List<DocumentChapterOutline>,
    ) {
        val warmSourceIndices = outlines.take(EPUB_IMPORT_WARM_CHAPTERS)
            .mapTo(hashSetOf(), DocumentChapterOutline::sourceIndex)
        val indexedSourceIndices = outlines.mapTo(hashSetOf(), DocumentChapterOutline::sourceIndex)
        val chapterIds = dao.getChapters(bookUuid).associate { it.chapterIndex to it.id }

        val pending = ArrayList<IndexedImportedChapter>(IMPORT_CHAPTER_BATCH_SIZE)
        suspend fun flush() {
            if (pending.isEmpty()) return
            val batch = pending.toList()
            pending.clear()
            database.withTransaction {
                batch.forEach { indexed ->
                    val chapterId = chapterIds[indexed.sourceIndex] ?: return@forEach
                    dao.updateChapterTitle(chapterId, indexed.chapter.title)
                    dao.insertParagraphsChunked(chapterId, indexed.chapter.paragraphs)
                }
            }
            // Thousands of one-file cache writes made import dramatically slower. Only warm the
            // opening chapters; every other rich chapter is cached when the reader actually needs it.
            batch.filter { it.sourceIndex in warmSourceIndices }
                .forEach { indexed ->
                    epubChapterCache.write(bookUuid, contentHash, indexed.sourceIndex, indexed.chapter)
                }
        }

        parser.readIndexedChapters(source, indexedSourceIndices) { sourceIndex, chapter ->
            pending += IndexedImportedChapter(sourceIndex, chapter)
            if (pending.size >= IMPORT_CHAPTER_BATCH_SIZE) flush()
        }
        flush()
    }
}

private data class ChapterCacheKey(val bookUuid: String, val chapterIndex: Int)
private data class IndexedImportedChapter(val sourceIndex: Int, val chapter: DocumentChapter)
private data class ImportRegistration(
    val imports: List<RegisteredImport>,
    val duplicateCount: Int,
    val failures: List<String>,
)
private data class RegisteredImport(
    val bookUuid: String,
    val displayName: String,
    val contentHash: String,
    val format: BookFormat,
    val source: File,
    val parser: BookParser,
    val outlines: List<DocumentChapterOutline>,
)

private const val CHAPTER_CACHE_SIZE = 32
private const val IMPORT_CHAPTER_BATCH_SIZE = 32
private const val EPUB_IMPORT_WARM_CHAPTERS = 3
private const val IMPORT_INDEX_CONCURRENCY = 2

private fun BookEntity.toModel() = Book(uuid, title, author, description, coverPath, BookFormat.valueOf(format), originalPath, storagePath, createdTime, contentHash, category)
private fun ChapterEntity.toModel() = Chapter(id, bookUuid, title, chapterIndex)
private fun ReadingProgressEntity.toModel() = ReadingProgress(bookUuid, chapterId, position, offset, updatedTime, fraction)
private fun BookmarkRow.toModel() = Bookmark(uuid, bookUuid, chapterId, chapterTitle, chapterIndex, position, preview, createdTime)
private fun BookSearchResultRow.toModel() = BookSearchResult(chapterId, chapterTitle, chapterIndex, paragraphIndex, text)

/**
 * EPUB image nodes are rehydrated from the immutable source archive when a
 * chapter is opened. Text keeps its persisted indices, so existing progress,
 * bookmarks and search results remain stable without duplicating image bytes.
 */
private fun DocumentChapter.toReaderParagraphs(
    chapterId: Long,
    persisted: List<ParagraphEntity>,
): List<Paragraph> {
    if (images.isEmpty()) return paragraphs.mapIndexed { index, text ->
        val stored = persisted.getOrNull(index)
        Paragraph(
            stored?.id ?: index.toLong(),
            chapterId,
            stored?.paragraphIndex ?: index,
            text,
            spans = paragraphSpans.getOrNull(index).orEmpty(),
        )
    }
    val imagesByIndex = images.groupBy { it.contentIndex }
    val contentCount = paragraphs.size + images.size
    var textIndex = 0
    return buildList {
        repeat(contentCount) { contentIndex ->
            val contentImages = imagesByIndex[contentIndex]
            if (!contentImages.isNullOrEmpty()) {
                contentImages.forEachIndexed { imageOffset, image ->
                    val position = persisted.getOrNull((textIndex - 1).coerceAtLeast(0))?.paragraphIndex
                        ?: persisted.getOrNull(textIndex)?.paragraphIndex
                        ?: textIndex.coerceAtLeast(0)
                    add(
                        Paragraph(
                            id = Long.MIN_VALUE + contentIndex * 16L + imageOffset,
                            chapterId = chapterId,
                            index = position,
                            text = image.altText,
                            kind = ParagraphKind.IMAGE,
                            resourcePath = image.resourcePath,
                            mediaType = image.mediaType,
                            intrinsicWidth = image.intrinsicWidth,
                            intrinsicHeight = image.intrinsicHeight,
                        ),
                    )
                }
            } else {
                val text = paragraphs.getOrNull(textIndex) ?: return@repeat
                val stored = persisted.getOrNull(textIndex)
                add(
                    Paragraph(
                        stored?.id ?: textIndex.toLong(),
                        chapterId,
                        stored?.paragraphIndex ?: textIndex,
                        text,
                        spans = paragraphSpans.getOrNull(textIndex).orEmpty(),
                    ),
                )
                textIndex++
            }
        }
        while (textIndex < paragraphs.size) {
            val stored = persisted.getOrNull(textIndex)
            add(
                Paragraph(
                    stored?.id ?: textIndex.toLong(),
                    chapterId,
                    stored?.paragraphIndex ?: textIndex,
                    paragraphs[textIndex],
                    spans = paragraphSpans.getOrNull(textIndex).orEmpty(),
                ),
            )
            textIndex++
        }
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input -> DigestInputStream(input, digest).use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (stream.read(buffer) != -1) { /* Consume the stream into the digest. */ }
    } }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
