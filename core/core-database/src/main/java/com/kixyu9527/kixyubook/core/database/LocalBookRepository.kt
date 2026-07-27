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
import com.kixyu9527.kixyubook.core.reader.engine.TxtBookParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
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

    override fun observeLibrary(): Flow<List<LibraryBook>> = combine(dao.observeBooks(), dao.observeAllProgress()) { books, progresses ->
        val byBook = progresses.associateBy { it.bookUuid }
        books.map { LibraryBook(it.toModel(), byBook[it.uuid]?.toModel()) }
            .sortedWith(compareByDescending<LibraryBook> { it.progress?.updatedTime ?: 0L }.thenByDescending { it.book.createdTime })
    }

    override suspend fun importDocuments(uriStrings: List<String>): ImportSummary = withContext(Dispatchers.IO) {
        var imported = 0
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
                    duplicates++; temp.delete(); return@forEach
                }
                val format = detectFormat(displayName, temp)
                val parser = parsers.parserFor(format)
                val metadata = parser.readMetadata(temp, displayName)
                val identity = metadata.identityHint?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                val bookUuid = identity?.takeUnless { dao.bookExists(it) } ?: UUID.randomUUID().toString()
                if (identity != null && dao.bookExists(identity)) {
                    duplicates++; temp.delete(); return@forEach
                }
                val extension = if (format == BookFormat.EPUB) "epub" else "txt"
                val stored = File(context.filesDir, "books/$bookUuid.$extension").also { it.parentFile?.mkdirs() }
                temp.copyTo(stored, overwrite = true)
                val coverPath = metadata.coverBytes?.let { bytes ->
                    File(context.filesDir, "covers/$bookUuid.${metadata.coverExtension}").also { it.parentFile?.mkdirs(); it.writeBytes(bytes) }.absolutePath
                }
                dao.insertBook(
                    BookEntity(bookUuid, metadata.title, metadata.author, metadata.description, coverPath, format.name, rawUri, stored.absolutePath, System.currentTimeMillis(), hash, "未分类"),
                )
                insertedUuid = bookUuid
                var chapterIndex = 0
                parser.readChapters(stored) { chapter ->
                    val chapterId = dao.insertChapter(ChapterEntity(bookUuid = bookUuid, title = chapter.title, chapterIndex = chapterIndex++))
                    dao.insertParagraphsChunked(chapterId, chapter.paragraphs)
                }
                if (chapterIndex == 0) error("未找到可阅读章节")
                imported++
            } catch (error: Exception) {
                insertedUuid?.let { uuid -> dao.getBook(uuid)?.storagePath?.let(::File)?.delete(); dao.deleteBook(uuid) }
                failures += "$displayName：${error.message ?: "导入失败"}"
            } finally { temp.delete() }
        }
        ImportSummary(imported, duplicates, failures)
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
        val books = dao.getBooks(bookUuids)
        database.withTransaction { dao.deleteBooks(bookUuids) }
        books.forEach { book ->
            File(book.storagePath).delete()
            book.coverPath?.let(::File)?.delete()
        }
    }

    override suspend fun getBook(bookUuid: String) = withContext(Dispatchers.IO) { dao.getBook(bookUuid)?.toModel() }
    override suspend fun getChapters(bookUuid: String) = withContext(Dispatchers.IO) { dao.getChapters(bookUuid).map { it.toModel() } }

    override suspend fun getChapter(bookUuid: String, chapterIndex: Int): ChapterContent? = withContext(Dispatchers.IO) {
        val chapter = dao.getChapter(bookUuid, chapterIndex) ?: return@withContext null
        ChapterContent(chapter.toModel(), dao.getParagraphs(chapter.id).map { paragraph ->
            Paragraph(paragraph.id, paragraph.chapterId, paragraph.paragraphIndex, paragraph.text)
        })
    }

    override fun observeProgress(bookUuid: String) = dao.observeProgress(bookUuid).map { it?.toModel() }
    override suspend fun saveProgress(progress: ReadingProgress) = withContext(Dispatchers.IO) {
        dao.saveProgress(ReadingProgressEntity(progress.bookUuid, progress.chapterId, progress.position, progress.offset, progress.updatedTime, progress.fraction))
    }

    override suspend fun updateTxtMetadata(bookUuid: String, title: String, author: String, description: String): Unit = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookUuid) ?: error("书籍不存在")
        require(book.format == BookFormat.TXT.name) { "EPUB metadata 为只读" }
        dao.insertMetadataEdit(MetadataEditEntity(UUID.randomUUID().toString(), bookUuid, book.title, book.author, book.description, title.trim(), author.trim(), description.trim(), System.currentTimeMillis()))
        dao.updateTxtMetadata(bookUuid, title.trim().ifBlank { "未命名书籍" }, author.trim().ifBlank { "未知作者" }, description.trim())
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
                    dao.updateTxtMetadata(
                        bookUuid,
                        metadata.title.trim().ifBlank { book.title },
                        metadata.author.trim().ifBlank { "未知作者" },
                        metadata.description.trim(),
                    )
                }
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun updateTxtParagraph(
        bookUuid: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        replacementText: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val backup = File(context.cacheDir, "txt-edit-$bookUuid-${UUID.randomUUID()}.bak")
        var source: File? = null
        var sourceChanged = false
        try {
            val book = dao.getBook(bookUuid) ?: error("书籍不存在")
            require(book.format == BookFormat.TXT.name) { "EPUB 正文为只读" }
            val chapter = dao.getChapter(bookUuid, chapterIndex) ?: error("章节不存在")
            val paragraph = dao.getParagraph(chapter.id, paragraphIndex) ?: error("段落不存在")
            val sourceFile = File(book.storagePath)
            source = sourceFile
            require(sourceFile.isFile) { "找不到原始 TXT 文件" }
            sourceFile.copyTo(backup, overwrite = true)

            val parser = parsers.parserFor(BookFormat.TXT) as TxtBookParser
            parser.replaceParagraph(sourceFile, chapterIndex, paragraphIndex, paragraph.text, replacementText).getOrThrow()
            sourceChanged = true
            reparseTxt(bookUuid).getOrThrow()

            val hash = sourceFile.sha256()
            val contentHash = dao.findUuidByHash(hash)?.takeUnless { it == bookUuid }
                ?.let { "edited-$bookUuid-$hash" }
                ?: hash
            dao.updateContentHash(bookUuid, contentHash)
            Result.success(Unit)
        } catch (error: CancellationException) {
            if (sourceChanged && backup.isFile) source?.let { backup.copyTo(it, overwrite = true) }
            throw error
        } catch (error: Exception) {
            if (sourceChanged && backup.isFile) {
                source?.let { backup.copyTo(it, overwrite = true) }
                reparseTxt(bookUuid)
            }
            Result.failure(error)
        } finally {
            backup.delete()
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
}

private fun BookEntity.toModel() = Book(uuid, title, author, description, coverPath, BookFormat.valueOf(format), originalPath, storagePath, createdTime, contentHash, category)
private fun ChapterEntity.toModel() = Chapter(id, bookUuid, title, chapterIndex)
private fun ReadingProgressEntity.toModel() = ReadingProgress(bookUuid, chapterId, position, offset, updatedTime, fraction)
private fun BookmarkRow.toModel() = Bookmark(uuid, bookUuid, chapterId, chapterTitle, chapterIndex, position, preview, createdTime)
private fun BookSearchResultRow.toModel() = BookSearchResult(chapterId, chapterTitle, chapterIndex, paragraphIndex, text)

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input -> DigestInputStream(input, digest).use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (stream.read(buffer) != -1) { /* Consume the stream into the digest. */ }
    } }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
