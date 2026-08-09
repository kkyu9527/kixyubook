package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticFailure
import com.kixyu9527.kixyubook.core.common.diagnostics.toDiagnosticFailure
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.entity.*
import com.kixyu9527.kixyubook.core.reader.engine.BookParserRegistry
import com.kixyu9527.kixyubook.core.reader.engine.BookParser
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapterOutline
import com.kixyu9527.kixyubook.core.reader.engine.EpubBookParser
import com.kixyu9527.kixyubook.core.reader.engine.ReaderPaginationCacheMaintenance
import com.kixyu9527.kixyubook.core.reader.engine.TxtBookParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBookRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: KixyuDatabase,
    private val dao: BookDao,
    private val epubParseCoordinator: EpubParseCoordinator,
    private val syncMutations: SyncMutationRecorder,
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
    private val storageMutationMutex = Mutex()
    private val importIndexSemaphore = Semaphore(IMPORT_INDEX_CONCURRENCY)
    private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val importIndexJobs = ConcurrentHashMap<String, Job>()
    private val importEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val openedAtOverrides = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val openedAtClock = AtomicLong()
    private val workManager by lazy(LazyThreadSafetyMode.NONE) { WorkManager.getInstance(context) }
    private val derivedDataVersions by lazy(LazyThreadSafetyMode.NONE) {
        context.getSharedPreferences(DERIVED_DATA_VERSION_PREFERENCES, Context.MODE_PRIVATE)
    }
    private val epubIndex by lazy(LazyThreadSafetyMode.NONE) {
        EpubIndexCoordinator(
            database = database,
            dao = dao,
            parseCoordinator = epubParseCoordinator,
            chapterCache = epubChapterCache,
            chapterLoadMutex = chapterLoadMutex,
            storageMutationMutex = storageMutationMutex,
            derivedDataVersions = derivedDataVersions,
            scheduleIndex = ::scheduleEpubIndex,
        )
    }
    // Keep only the decoded chapters needed by the active pager. EPUB chapters outside this
    // window remain in the binary disk cache and are cheap to hydrate without retaining a whole
    // reading session in the process heap.
    private val chapterCache = object : LinkedHashMap<ChapterCacheKey, ChapterContent>(
        CHAPTER_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChapterCacheKey, ChapterContent>) =
            size > CHAPTER_CACHE_SIZE
    }

    init {
        importScope.launch {
            upgradeTxtParserDataIfNeeded()
            epubIndex.upgradeDirectoryDataIfNeeded()
        }
    }

    override fun observeLibrary(): Flow<List<LibraryBook>> = combine(
        dao.observeBooks(),
        dao.observeAllProgress(),
        openedAtOverrides,
    ) { books, progresses, openedAt ->
        val byBook = progresses.associateBy { it.bookUuid }
        books.map { entity ->
            val progress = byBook[entity.uuid]
            LibraryBook(entity.toModel(), progress?.toModel()) to maxOf(
                entity.createdTime,
                entity.lastOpenedTime,
                progress?.updatedTime ?: 0L,
                openedAt[entity.uuid] ?: 0L,
            )
        }.sortedByDescending { (_, activityTime) -> activityTime }
            .map { (book, _) -> book }
    }

    override fun observeImportEvents(): Flow<String> = importEvents.asSharedFlow()

    override fun markBookOpened(bookUuid: String) {
        val openedAt = openedAtClock.updateAndGet { previous ->
            maxOf(previous + 1, System.currentTimeMillis())
        }
        // Publish before Room I/O so the shelf order changes in the same input dispatch as the
        // tap. The durable column keeps that order after process recreation without pretending
        // that the user's reading position changed or creating a cloud-sync conflict.
        openedAtOverrides.update { it + (bookUuid to openedAt) }
        importScope.launch {
            runCatching { dao.markBookOpened(bookUuid, openedAt) }
                .onSuccess { updated ->
                    if (updated == 0) {
                        openedAtOverrides.update { current ->
                            if (current[bookUuid] == openedAt) current - bookUuid else current
                        }
                    }
                }
                .onFailure { error ->
                    openedAtOverrides.update { current ->
                        if (current[bookUuid] == openedAt) current - bookUuid else current
                    }
                    val failure = error.toDiagnosticFailure()
                    DiagnosticLog.record(
                        Category.LIBRARY,
                        "book_open_activity_failed",
                        outcome = failure.outcome,
                        details = mapOf(
                            "book" to bookUuid.shortDiagnosticId(),
                            "reason" to failure.reason,
                        ),
                    )
                }
        }
    }

    override suspend fun importDocuments(uriStrings: List<String>): ImportSummary = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        DiagnosticLog.record(Category.IMPORT, "documents_selected", details = mapOf("count" to uriStrings.size))
        val registration = storageMutationMutex.withLock {
            cleanupImportArtifacts()
            pruneUnreferencedBookFiles()
            registerDocuments(uriStrings)
        }
        registration.imports.forEach(::enqueueBackgroundIndex)
        ImportSummary(registration.imports.size, registration.duplicateCount, registration.failures).also { summary ->
            DiagnosticLog.record(
                Category.IMPORT,
                "documents_registered",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = if (summary.failures.isEmpty()) "success" else "partial",
                details = mapOf(
                    "imported" to summary.importedCount,
                    "duplicates" to summary.duplicateCount,
                    "failures" to summary.failures.size,
                ),
            )
        }
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
        val importDir = File(context.cacheDir, "imports").apply { mkdirs() }
        uriStrings.distinct().forEach { rawUri ->
            val uri = rawUri.toUri()
            val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "未命名小说" }
            val temp = File(importDir, UUID.randomUUID().toString())
            var insertedUuid: String? = null
            var storedFile: File? = null
            var coverFile: File? = null
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
                val identityMatch = identity?.let { dao.getBook(it) }
                if (
                    format == BookFormat.EPUB &&
                    identityMatch != null &&
                    identityMatch.title.normalizedEpubIdentityTitle() == metadata.title.normalizedEpubIdentityTitle()
                ) {
                    duplicates++
                    return@forEach
                }
                // EPUB authoring tools occasionally reuse dc:identifier for another title. Only
                // keep that UUID when it is unused; an existing UUID is a duplicate only when the
                // normalized title agrees too.
                val bookUuid = identity?.takeIf { identityMatch == null } ?: UUID.randomUUID().toString()
                val extension = if (format == BookFormat.EPUB) "epub" else "txt"
                val stored = File(context.filesDir, "books/$bookUuid.$extension").also { it.parentFile?.mkdirs() }
                storedFile = stored
                temp.copyTo(stored, overwrite = true)
                val coverPath = metadata.coverBytes?.let { bytes ->
                    File(context.filesDir, "covers/$bookUuid.${metadata.coverExtension}").also {
                        coverFile = it
                        it.parentFile?.mkdirs()
                        it.writeBytes(bytes)
                    }.absolutePath
                }
                dao.insertBook(
                    BookEntity(bookUuid, metadata.title, metadata.author, metadata.description, coverPath, format.name, rawUri, stored.absolutePath, System.currentTimeMillis(), hash, "未分类"),
                )
                insertedUuid = bookUuid
                val outlines = if (format == BookFormat.EPUB) {
                    epubIndex.registerDirectory(bookUuid, stored, parser as EpubBookParser)
                        .also { if (it.isEmpty()) error("未找到可阅读章节") }
                } else {
                    emptyList()
                }
                imports += RegisteredImport(bookUuid, displayName, format, stored, parser)
                syncMutations.record(SyncEntityType.BOOK, bookUuid)
            } catch (error: CancellationException) {
                insertedUuid?.let { removeIncompleteImport(it) }
                storedFile?.delete()
                coverFile?.delete()
                throw error
            } catch (error: Exception) {
                insertedUuid?.let { removeIncompleteImport(it) }
                storedFile?.delete()
                coverFile?.delete()
                failures += "$displayName：${error.message ?: "导入失败"}"
            } finally {
                temp.delete()
            }
        }
        importDir.delete()
        return ImportRegistration(imports, duplicates, failures)
    }

    private fun enqueueBackgroundIndex(book: RegisteredImport) {
        if (book.format == BookFormat.EPUB) {
            scheduleEpubIndex()
            return
        }
        val job = importScope.launch(start = CoroutineStart.LAZY) {
            importIndexSemaphore.withPermit {
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    val chapterCount = importStreamingChapters(book.bookUuid, book.source, book.parser)
                    if (chapterCount == 0) {
                        error("未找到可阅读章节")
                    }
                    DiagnosticLog.record(
                        Category.IMPORT,
                        "background_index_finished",
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        outcome = "success",
                        details = mapOf("format" to book.format.name, "chapters" to chapterCount),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    DiagnosticLog.record(
                        Category.IMPORT,
                        "background_index_finished",
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        outcome = error::class.simpleName ?: "error",
                        details = mapOf("format" to book.format.name),
                    )
                    removeIncompleteImport(book.bookUuid)
                    importEvents.emit("${book.displayName}：${error.message ?: "导入失败"}")
                }
            }
        }
        importIndexJobs.put(book.bookUuid, job)?.cancel()
        job.invokeOnCompletion { importIndexJobs.remove(book.bookUuid, job) }
        job.start()
    }

    private fun scheduleEpubIndex() {
        val request = OneTimeWorkRequestBuilder<EpubIndexWorker>()
            .build()
        workManager.enqueueUniqueWork(
            EpubIndexWorker.UNIQUE_NAME,
            // Appending closes the race where a newly imported book arrives while the previous
            // worker is returning success after its final pending-chapter query.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
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

    override suspend fun restoreSyncedBook(book: SyncedBook, sourceFilePath: String): Boolean =
        withContext(Dispatchers.IO) {
            storageMutationMutex.withLock {
                if (dao.bookExists(book.uuid)) return@withLock true
                if (dao.findUuidByHash(book.contentHash) != null) return@withLock false
                val source = File(sourceFilePath)
                require(source.isFile) { "云端书籍文件不存在" }
                val actualHash = source.inputStream().use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    DigestInputStream(input, digest).use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (stream.read(buffer) >= 0) {
                            // DigestInputStream updates the digest as bytes are consumed.
                        }
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                require(actualHash == book.contentHash) { "云端书籍校验失败" }
                val extension = if (book.format == BookFormat.EPUB) "epub" else "txt"
                val stored = File(context.filesDir, "books/${book.uuid}.$extension").also { it.parentFile?.mkdirs() }
                source.copyTo(stored, overwrite = true)
                try {
                    val parser = parsers.parserFor(book.format)
                    val parsedMetadata = parser.readMetadata(stored, book.title)
                    val coverPath = parsedMetadata.coverBytes?.let { bytes ->
                        File(context.filesDir, "covers/${book.uuid}.${parsedMetadata.coverExtension}").also {
                            it.parentFile?.mkdirs()
                            it.writeBytes(bytes)
                        }.absolutePath
                    }
                    dao.insertBook(
                        BookEntity(
                            uuid = book.uuid,
                            title = book.title,
                            author = book.author,
                            description = book.description,
                            coverPath = coverPath,
                            format = book.format.name,
                            originalPath = "google-drive://${book.uuid}",
                            storagePath = stored.absolutePath,
                            createdTime = book.createdTime,
                            contentHash = book.contentHash,
                            category = book.category,
                        ),
                    )
                    if (book.format == BookFormat.EPUB) {
                        epubIndex.registerDirectory(book.uuid, stored, parser as EpubBookParser)
                        scheduleEpubIndex()
                    } else {
                        enqueueBackgroundIndex(RegisteredImport(book.uuid, book.title, book.format, stored, parser))
                    }
                    true
                } catch (error: Throwable) {
                    removeIncompleteImport(book.uuid)
                    stored.delete()
                    throw error
                }
            }
        }

    override suspend fun deleteBook(bookUuid: String) = deleteBooks(setOf(bookUuid))

    override suspend fun deleteBooks(bookUuids: Set<String>): Unit = withContext(Dispatchers.IO) {
        if (bookUuids.isEmpty()) return@withContext
        openedAtOverrides.update { current -> current - bookUuids }
        val startedAt = SystemClock.elapsedRealtime()
        storageMutationMutex.withLock {
            bookUuids.mapNotNull(importIndexJobs::remove).forEach { job ->
                job.cancelAndJoin()
            }
            val books = dao.getBooks(bookUuids)
            val progressCount = bookUuids.count { uuid -> dao.getProgress(uuid) != null }
            database.withTransaction {
                dao.deleteMetadataEdits(bookUuids)
                dao.deleteBooks(bookUuids)
                bookUuids.forEach { uuid ->
                    // Progress and bookmarks are independent Drive objects. Deleting only the
                    // book metadata leaves both objects available to restore stale state when an
                    // EPUB with the same dc:identifier is imported again.
                    syncMutations.record(SyncEntityType.BOOKMARKS, uuid, SyncMutationOperation.DELETE)
                    syncMutations.record(SyncEntityType.PROGRESS, uuid, SyncMutationOperation.DELETE)
                    syncMutations.record(SyncEntityType.BOOK, uuid, SyncMutationOperation.DELETE)
                }
            }
            synchronized(chapterCacheLock) {
                chapterCache.keys.removeAll { it.bookUuid in bookUuids }
            }
            books.forEach { book ->
                epubChapterCache.clearBook(book.uuid)
                ReaderPaginationCacheMaintenance.clearBook(context.noBackupFilesDir, book.uuid)
                File(book.storagePath).delete()
                book.coverPath?.let(::File)?.delete()
            }
            pruneUnreferencedBookFiles()
            DiagnosticLog.record(
                Category.LIBRARY,
                "books_deleted",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = "success",
                details = buildMap {
                    put("count", books.size)
                    put("progressRecords", progressCount)
                    books.singleOrNull()?.let { put("book", it.uuid.shortDiagnosticId()) }
                },
            )
        }
    }

    override suspend fun getBook(bookUuid: String) = withContext(Dispatchers.IO) { dao.getBook(bookUuid)?.toModel() }
    override suspend fun getChapters(bookUuid: String) = withContext(Dispatchers.IO) { dao.getChapters(bookUuid).map { it.toModel() } }
    override fun observeChapters(bookUuid: String): Flow<List<Chapter>> =
        dao.observeChapters(bookUuid).map { rows -> rows.map(ChapterEntity::toModel) }

    override suspend fun getChapter(
        bookUuid: String,
        chapterIndex: Int,
        priority: ChapterLoadPriority,
    ): ChapterContent? = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val cacheKey = ChapterCacheKey(bookUuid, chapterIndex)
        synchronized(chapterCacheLock) { chapterCache[cacheKey] }?.let { return@withContext it }
        chapterLoadMutex.withLock {
            synchronized(chapterCacheLock) { chapterCache[cacheKey] }?.let { return@withLock it }
            val chapter = dao.getChapter(bookUuid, chapterIndex) ?: return@withLock null
            val book = dao.getBook(bookUuid)
            var storedParagraphs = dao.getParagraphs(chapter.id)
            var source = "database"
            val paragraphs = if (book?.format == BookFormat.EPUB.name) {
                val diskCached = epubChapterCache.read(bookUuid, book.contentHash, chapterIndex)
                source = if (diskCached != null) "epub_disk_cache" else "epub_parse"
                val parsed = diskCached ?: epubParseCoordinator.interactive {
                        (parsers.parserFor(BookFormat.EPUB) as EpubBookParser)
                            .readChapter(File(book.storagePath), chapterIndex, chapter.title, purpose = "reader")
                    }?.also { parsedChapter ->
                        epubChapterCache.write(bookUuid, book.contentHash, chapterIndex, parsedChapter)
                    }
                if (parsed != null && !chapter.indexed) {
                    dao.replaceChapterIndex(chapter.id, parsed.title, parsed.paragraphs)
                    storedParagraphs = dao.getParagraphs(chapter.id)
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
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                if (book?.format == BookFormat.EPUB.name || elapsedMs >= SLOW_CHAPTER_LOAD_MS) {
                    DiagnosticLog.record(
                        Category.READER,
                        "chapter_loaded",
                        elapsedMs = elapsedMs,
                        outcome = "success",
                        details = mapOf(
                            "format" to (book?.format ?: "unknown"),
                            "book" to bookUuid.shortDiagnosticId(),
                            "chapter" to chapterIndex,
                            "priority" to priority.name,
                            "source" to source,
                            "paragraphs" to paragraphs.size,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun prepareReader(bookUuid: String) {
        val prepared = withContext(Dispatchers.IO) {
            val progress = dao.getProgress(bookUuid)
            val chapters = dao.getChapters(bookUuid)
            val chapterIndex = progress?.chapterId?.let { chapterId ->
                chapters.firstOrNull { it.id == chapterId }?.chapterIndex
            } ?: chapters.firstOrNull()?.chapterIndex
            val book = dao.getBook(bookUuid) ?: return@withContext null
            chapterIndex?.let { it to book }
        } ?: return
        val (chapterIndex, book) = prepared
        // Cold-start prewarming must never start an XHTML/CSS parse. It may hydrate an existing
        // binary chapter cache into memory, while an uncached EPUB stays reserved for an explicit
        // user navigation request where the interactive parser has priority.
        if (book.format == BookFormat.EPUB.name &&
            !epubChapterCache.contains(bookUuid, book.contentHash, chapterIndex)
        ) return
        getChapter(bookUuid, chapterIndex, ChapterLoadPriority.USER)
    }

    override fun setReaderInteractionActive(active: Boolean) {
        epubParseCoordinator.setReaderInteractionActive(active)
    }

    override fun setReaderSessionActive(active: Boolean) {
        epubParseCoordinator.setReaderSessionActive(active)
    }

    override fun releaseReaderMemory(bookUuid: String) {
        synchronized(chapterCacheLock) {
            chapterCache.keys.removeAll { it.bookUuid == bookUuid }
        }
        (parsers.parserFor(BookFormat.EPUB) as EpubBookParser).clearMemoryCaches()
    }

    override fun setAppAnimationActive(active: Boolean) {
        epubParseCoordinator.setAppAnimationActive(active)
    }

    override fun observeProgress(bookUuid: String) = dao.observeProgress(bookUuid).map { it?.toModel() }
    override suspend fun saveProgress(progress: ReadingProgress) = withContext(Dispatchers.IO) {
        val chapter = dao.getChapters(progress.bookUuid).firstOrNull { it.id == progress.chapterId }
        val saved = dao.saveProgressIfNewer(
            ReadingProgressEntity(
                bookUuid = progress.bookUuid,
                chapterId = progress.chapterId,
                position = progress.position,
                offset = progress.offset,
                updatedTime = progress.updatedTime,
                fraction = progress.fraction,
                chapterKey = progress.chapterKey.ifBlank { chapter?.chapterKey.orEmpty() },
                paragraphIndex = progress.paragraphIndex,
                charOffset = progress.charOffset,
                quoteAnchor = progress.quoteAnchor,
            ),
        )
        if (saved) syncMutations.record(SyncEntityType.PROGRESS, progress.bookUuid)
    }

    override suspend fun updateBookMetadata(bookUuid: String, title: String, author: String, description: String): Unit = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookUuid) ?: error("书籍不存在")
        dao.insertMetadataEdit(MetadataEditEntity(UUID.randomUUID().toString(), bookUuid, book.title, book.author, book.description, title.trim(), author.trim(), description.trim(), System.currentTimeMillis()))
        dao.updateBookMetadata(bookUuid, title.trim().ifBlank { "未命名书籍" }, author.trim().ifBlank { "未知作者" }, description.trim())
        syncMutations.record(SyncEntityType.BOOK, bookUuid)
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
                        ChapterEntity(
                            bookUuid = bookUuid,
                            title = chapter.title,
                            chapterIndex = chapterIndex++,
                            volumeTitle = chapter.volumeTitle,
                            volumeIndex = chapter.volumeIndex,
                            chapterKey = stableChapterKey(bookUuid, chapterIndex - 1, chapter.title),
                        ),
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

    /** TXT chapters are derived data. Rebuild them once when parsing rules change. */
    private suspend fun upgradeTxtParserDataIfNeeded() {
        if (derivedDataVersions.getInt(KEY_TXT_PARSER_VERSION, 0) >= TXT_PARSER_VERSION) return
        storageMutationMutex.withLock {
            if (derivedDataVersions.getInt(KEY_TXT_PARSER_VERSION, 0) >= TXT_PARSER_VERSION) return@withLock
            dao.getAllBooks()
                .asSequence()
                .filter { it.format == BookFormat.TXT.name && File(it.storagePath).isFile }
                .forEach { reparseTxt(it.uuid) }
            derivedDataVersions.edit { putInt(KEY_TXT_PARSER_VERSION, TXT_PARSER_VERSION) }
        }
    }

    override suspend fun setCategory(bookUuid: String, category: String) = withContext(Dispatchers.IO) {
        dao.setCategory(bookUuid, category.trim().ifBlank { "未分类" })
        syncMutations.record(SyncEntityType.BOOK, bookUuid)
    }

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
        syncMutations.record(SyncEntityType.BOOKMARKS, bookmark.bookUuid)
    }

    override suspend fun deleteBookmark(bookmarkUuid: String) = withContext(Dispatchers.IO) {
        val owner = dao.getAllBookmarkEntities().firstOrNull { it.uuid == bookmarkUuid }?.bookUuid
        dao.deleteBookmark(bookmarkUuid)
        owner?.let { syncMutations.record(SyncEntityType.BOOKMARKS, it) }
        Unit
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
                    ChapterEntity(
                        bookUuid = bookUuid,
                        title = chapter.title,
                        chapterIndex = startIndex + offset,
                        volumeTitle = chapter.volumeTitle,
                        volumeIndex = chapter.volumeIndex,
                        chapterKey = stableChapterKey(bookUuid, startIndex + offset, chapter.title),
                    )
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
        ReaderPaginationCacheMaintenance.clearBook(context.noBackupFilesDir, bookUuid)
        database.withTransaction {
            dao.deleteMetadataEdits(setOf(bookUuid))
            dao.deleteBook(bookUuid)
        }
    }

    private fun cleanupImportArtifacts() {
        File(context.cacheDir, "imports").deleteRecursively()
    }

    private suspend fun pruneUnreferencedBookFiles() {
        val books = dao.getAllBooks()
        File(context.filesDir, "books").pruneTo(books.mapTo(hashSetOf()) { File(it.storagePath).absolutePath })
        File(context.filesDir, "covers").pruneTo(books.mapNotNullTo(hashSetOf()) { it.coverPath?.let(::File)?.absolutePath })
        epubChapterCache.retainBooks(books.mapTo(hashSetOf(), BookEntity::uuid))
    }

    suspend fun continueEpubIndex(bookUuid: String) = epubIndex.continueIndex(bookUuid)

    suspend fun continueAllEpubIndexes() = epubIndex.continueAll()
}
