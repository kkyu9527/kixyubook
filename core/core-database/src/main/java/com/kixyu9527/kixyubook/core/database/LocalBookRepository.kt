package com.kixyu9527.kixyubook.core.database
import com.kixyu9527.kixyubook.core.common.cache.ReaderCacheBudget
import com.kixyu9527.kixyubook.core.common.cache.WeightedLruCache

import android.content.Context
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.CompleteLibraryRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.MetadataRefreshResult
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.common.repository.TextCorrectionRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticFailure
import com.kixyu9527.kixyubook.core.common.diagnostics.toDiagnosticFailure
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureListener
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.ImportDao
import com.kixyu9527.kixyubook.core.database.entity.*
import com.kixyu9527.kixyubook.core.reader.engine.BookParserRegistry
import com.kixyu9527.kixyubook.core.reader.engine.LocalMetadata
import com.kixyu9527.kixyubook.core.reader.engine.BookParser
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import com.kixyu9527.kixyubook.core.reader.engine.EpubBookParser
import com.kixyu9527.kixyubook.core.reader.engine.ReaderPaginationCacheMaintenance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val epubParseCoordinator: EpubParseCoordinator,
    private val syncMutations: SyncMutationRecorder,
    private val textCorrections: TextCorrectionRepository,
    private val annotations: ReaderAnnotationRepository,
    private val importDao: ImportDao,
    private val libraryPreferences: LibraryPreferencesRepository,
) : BookRepository, CompleteLibraryRepository, MemoryPressureListener {
    private val parsers = BookParserRegistry()
    private val bookMutations = BookMutationStore(context, database, dao, syncMutations)
    // Parsed XHTML is derived data, but it must not disappear during ordinary Android cache
    // reclamation. A partially evicted cache made otherwise identical directory jumps vary from
    // instant to a full ZIP/XHTML parse. noBackupFilesDir persists it without bloating backups.
    private val epubChapterCache = EpubChapterCache(
        File(context.noBackupFilesDir, "epub-chapters").apply(File::mkdirs),
    )
    private val chapterCacheLock = Any()
    private val chapterLoadMutex = Mutex()
    private val storageMutationMutex = LibraryStorageGate.mutex
    private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val importEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val canceledImportRuns = ConcurrentHashMap.newKeySet<String>()
    override val importProgress = importDao.observeLatestRun()
        .map { items ->
            items.toImportRuns().firstOrNull()
        }
        .stateIn(importScope, SharingStarted.Eagerly, null)
    private val openedAtOverrides = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val activityClock = LibraryActivityClock()
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
    private val chapterCache = WeightedLruCache<ChapterCacheKey, ChapterContent>(
        ReaderCacheBudget.CHAPTER_MEMORY_BYTES,
        ReaderCacheBudget.MAX_MEMORY_CHAPTERS,
        diagnosticsName = "chapter",
    ) { content ->
        256L + content.paragraphs.sumOf { paragraph ->
            128L + paragraph.text.length * 2L + paragraph.spans.size * 96L
        }
    }

    init {
        MemoryPressureRegistry.register(this)
        importScope.launch {
            upgradeTxtParserDataIfNeeded()
            epubIndex.upgradeDirectoryDataIfNeeded()
        }
    }

    override fun onMemoryPressure(level: MemoryPressureLevel) {
        synchronized(chapterCacheLock) { chapterCache.clear() }
        (parsers.parserFor(BookFormat.EPUB) as EpubBookParser).clearMemoryCaches()
    }

    override fun observeCompleteLibrary(): Flow<List<LibraryBook>> = combine(
        dao.observeBooks(),
        dao.observeAllProgress(),
        openedAtOverrides,
    ) { books, progresses, openedAt ->
        val byBook = progresses.associateBy { it.bookUuid }
        val booksWithActivity = books.map { entity ->
            val progress = byBook[entity.uuid]
            LibraryBook(entity.toModel(), progress?.toModel()) to maxOf(
                entity.createdTime,
                entity.lastOpenedTime,
                progress?.updatedTime ?: 0L,
                openedAt[entity.uuid] ?: 0L,
            )
        }
        // Synced progress and imported metadata can legitimately carry a timestamp newer than
        // this device's wall clock. Seed the local ordering clock from the complete library so a
        // plain open/close is always newer than every existing activity, even without a page turn.
        activityClock.observe(booksWithActivity.maxOfOrNull { (_, activityTime) -> activityTime } ?: 0L)
        booksWithActivity.sortedByDescending { (_, activityTime) -> activityTime }
            .map { (book, _) -> book }
    }

    override fun observeImportEvents(): Flow<String> = importEvents.asSharedFlow()

    override fun observeImportHistory(): Flow<List<ImportProgress>> =
        importDao.observeHistory().map(List<ImportItemEntity>::toImportRuns)

    override fun clearFinishedImportProgress() {
        val current = importProgress.value?.takeIf(ImportProgress::finished) ?: return
        importScope.launch { importDao.deleteRun(current.runId) }
    }

    override suspend fun clearImportHistory() = withContext(Dispatchers.IO) {
        require(importProgress.value?.finished != false) { context.getString(R.string.db_import_running) }
        importDao.deleteAll()
    }

    override suspend fun retryImport(runId: String): ImportSummary = withContext(Dispatchers.IO) {
        val retryUris = importDao.getRun(runId)
            .filter { it.status in setOf(ImportItemStatus.FAILED.name, ImportItemStatus.CANCELED.name) }
            .map(ImportItemEntity::sourceId)
        if (retryUris.isEmpty()) return@withContext ImportSummary(0)
        importDocuments(retryUris)
    }

    override suspend fun cancelImport(runId: String) = withContext(Dispatchers.IO) {
        canceledImportRuns += runId
        storageMutationMutex.withLock {
            // Re-read inside the gate. An item that finished while cancel was queued is already
            // SUCCEEDED and must keep its delivered book instead of being removed as "incomplete".
            val active = importDao.getRun(runId).filterNot { it.status.isTerminalImportStatus() }
            importDao.upsert(
                active.map { item ->
                    item.copy(
                        stage = ImportStage.FINISHED.name,
                        progress = 1f,
                        status = ImportItemStatus.CANCELED.name,
                        message = context.getString(R.string.import_status_canceled),
                        updatedTime = System.currentTimeMillis(),
                    )
                },
            )
            active.mapNotNull(ImportItemEntity::bookUuid).forEach { uuid ->
                workManager.cancelUniqueWork(TxtIndexWorker.uniqueName(uuid))
                removeIncompleteImport(uuid)
            }
        }
        // Drop the flag once nothing is running: an import that never started would otherwise leak
        // its run id for the process lifetime. A run still in flight keeps the flag so its next
        // ensureImportActive() call observes the cancellation.
        if (importDao.getRun(runId).none { it.status == ImportItemStatus.RUNNING.name }) {
            canceledImportRuns -= runId
        }
    }

    override fun markBookOpened(bookUuid: String) {
        val openedAt = activityClock.next()
        // Publish before Room I/O so the shelf order changes in the same input dispatch as the
        // tap. The durable column keeps that order after process recreation without pretending
        // that the user's reading position changed or creating a cloud-sync conflict.
        openedAtOverrides.update { (it + (bookUuid to openedAt)).boundedOpenedAtOverrides() }
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
        val importRun = startedAt.toString(36)
        val sources = uriStrings.distinct().map { rawUri ->
            ImportItemProgress(
                id = rawUri,
                displayName = displayNameFor(rawUri.toUri()),
            )
        }
        val importStartedTime = System.currentTimeMillis()
        importDao.upsert(sources.mapIndexed { index, item ->
            item.toEntity(importRun, index, importStartedTime)
        })
        importDao.pruneHistory()
        DiagnosticLog.record(
            Category.IMPORT,
            "documents_selected",
            details = mapOf("run" to importRun, "count" to uriStrings.size),
        )
        val registration = try {
            storageMutationMutex.withLock {
                cleanupImportArtifacts()
                pruneUnreferencedBookFiles()
                registerDocuments(importRun, sources)
            }
        } finally {
            canceledImportRuns -= importRun
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
                    "run" to importRun,
                    "failureTypes" to registration.failureDiagnostics
                        .map(DiagnosticFailure::outcome)
                        .distinct()
                        .joinToString(",")
                        .takeIf(String::isNotEmpty),
                    "firstFailureReason" to registration.failureDiagnostics.firstOrNull()?.reason,
                ),
            )
        }
    }

    private val exporter by lazy { BookExportService(context, dao, annotations, ::getChapter) }

    override suspend fun exportAnnotations(bookUuid: String, uriString: String, format: AnnotationExportFormat) =
        exporter.exportAnnotations(bookUuid, uriString, format)

    override suspend fun repairBook(bookUuid: String, mode: BookRepairMode, onProgress: suspend (BookRepairProgress) -> Unit): Result<BookRepairOutcome> = withContext(Dispatchers.IO) {
        try {
            val result = storageMutationMutex.withLock {
                BookRepairService(context, database, dao, annotations, textCorrections, chapterLoadMutex) { uuid ->
                    synchronized(chapterCacheLock) { chapterCache.removeMatching { it.bookUuid == uuid } }
                    epubChapterCache.clearBook(uuid)
                    ReaderPaginationCacheMaintenance.clearBook(context.noBackupFilesDir, uuid)
                }.repair(bookUuid, mode, onProgress)
            }
            Result.success(result)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }

    override suspend fun exportBook(bookUuid: String, uriString: String): Result<Unit> =
        exporter.exportBook(bookUuid, uriString)

    override suspend fun exportBooks(bookUuids: Set<String>, directoryUriString: String): BookExportSummary =
        exporter.exportBooks(bookUuids, directoryUriString)

    /**
     * Registers every selected file before any expensive body parsing starts. Room observers can
     * therefore show the complete selection in the library immediately instead of waiting for the
     * preceding book's full-text index.
     */
    private suspend fun registerDocuments(
        importRun: String,
        sources: List<ImportItemProgress>,
    ): ImportRegistration {
        val imports = mutableListOf<RegisteredImport>()
        var duplicates = 0
        val failures = mutableListOf<String>()
        val failureDiagnostics = mutableListOf<DiagnosticFailure>()
        val importDir = File(context.cacheDir, "imports").apply { mkdirs() }
        sources.forEach { sourceProgress ->
            val rawUri = sourceProgress.id
            val uri = rawUri.toUri()
            val displayName = sourceProgress.displayName
            val temp = File(importDir, UUID.randomUUID().toString())
            var insertedUuid: String? = null
            var storedFile: File? = null
            var coverFile: File? = null
            try {
                ensureImportActive(importRun)
                updateImportProgress(importRun, rawUri, ImportStage.COPYING, .02f, ImportItemStatus.RUNNING)
                val hash = copyImportSource(importRun, uri, rawUri, temp)
                if (dao.findUuidByHash(hash) != null) {
                    duplicates++
                    updateImportProgress(
                        importRun, rawUri,
                        ImportStage.FINISHED,
                        1f,
                        ImportItemStatus.DUPLICATE,
                        message = context.getString(R.string.import_status_duplicate_content),
                    )
                    return@forEach
                }
                updateImportProgress(importRun, rawUri, ImportStage.READING_METADATA, .36f, ImportItemStatus.RUNNING)
                val format = detectFormat(displayName, temp)
                val parser = parsers.parserFor(format)
                val titleFallback = displayName.substringBeforeLast('.').ifBlank { displayName }
                // A SAF document id contains the folder path; the folder is a useful lower-confidence
                // source when the file name itself carries no author (e.g. `01.txt`).
                val folderName = folderNameFor(rawUri.toUri())
                val metadata = parser.readMetadata(temp, titleFallback, displayName, filenameRules(), filenameSpecs())
                    .withFolderFallback(folderName, titleFallback)
                val identity = metadata.identityHint?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                val identityMatch = identity?.let { dao.getBook(it) }
                if (
                    format == BookFormat.EPUB &&
                    identityMatch != null &&
                    identityMatch.title.normalizedEpubIdentityTitle() == metadata.title.normalizedEpubIdentityTitle()
                ) {
                    duplicates++
                    updateImportProgress(
                        importRun, rawUri,
                        ImportStage.FINISHED,
                        1f,
                        ImportItemStatus.DUPLICATE,
                        message = context.getString(R.string.import_status_duplicate_epub),
                    )
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
                // Parse outside Room's write transaction, then publish metadata, directory and
                // the sync mutation together. Other readers never see half a registered book.
                val outlines = if (format == BookFormat.EPUB) {
                    (parser as EpubBookParser).readChapterOutlines(stored)
                        .also { if (it.isEmpty()) error(context.getString(R.string.db_no_chapters)) }
                } else emptyList()
                database.withTransaction {
                    dao.insertBook(
                        BookEntity(bookUuid, metadata.title, metadata.author, metadata.description, coverPath, format.name, rawUri, stored.absolutePath, System.currentTimeMillis(), hash, "未分类", originalDisplayName = displayName, originalFolderName = folderName.orEmpty(), titleSort = metadata.titleSort, seriesName = metadata.seriesName, seriesIndex = metadata.seriesIndex),
                    )
                    epubIndex.registerDirectory(bookUuid, outlines)
                    syncMutations.record(SyncEntityType.BOOK, bookUuid)
                    insertedUuid = bookUuid
                }
                updateImportProgress(
                    importRun, rawUri,
                    ImportStage.BUILDING_DIRECTORY,
                    .62f,
                    ImportItemStatus.RUNNING,
                    bookUuid = bookUuid,
                )
                // Record the item only after its final progress write succeeds. Appending first and
                // then failing would report a book as imported while the catch below deletes it.
                updateImportProgress(
                    importRun, rawUri,
                    if (format == BookFormat.EPUB) ImportStage.FINISHED else ImportStage.INDEXING,
                    if (format == BookFormat.EPUB) 1f else .78f,
                    if (format == BookFormat.EPUB) ImportItemStatus.SUCCEEDED else ImportItemStatus.RUNNING,
                    bookUuid = bookUuid,
                    message = if (format == BookFormat.EPUB) context.getString(R.string.db_import_background) else context.getString(R.string.db_indexing),
                )
                imports += RegisteredImport(importRun, rawUri, bookUuid, displayName, format, stored, parser)
            } catch (error: ImportCanceledException) {
                insertedUuid?.let { removeIncompleteImport(it) }
                storedFile?.delete()
                coverFile?.delete()
                updateImportProgress(
                    importRun,
                    rawUri,
                    ImportStage.FINISHED,
                    1f,
                    ImportItemStatus.CANCELED,
                    message = context.getString(R.string.import_status_canceled),
                )
            } catch (error: CancellationException) {
                insertedUuid?.let { removeIncompleteImport(it) }
                storedFile?.delete()
                coverFile?.delete()
                throw error
            } catch (error: Exception) {
                insertedUuid?.let { removeIncompleteImport(it) }
                storedFile?.delete()
                coverFile?.delete()
                failures += "$displayName：${error.message ?: context.getString(R.string.db_import_failed)}"
                failureDiagnostics += error.toDiagnosticFailure()
                updateImportProgress(
                    importRun, rawUri,
                    ImportStage.FINISHED,
                    1f,
                    ImportItemStatus.FAILED,
                    message = error.message ?: context.getString(R.string.db_import_failed),
                )
            } finally {
                temp.delete()
            }
        }
        importDir.delete()
        return ImportRegistration(imports, duplicates, failures, failureDiagnostics)
    }

    private fun enqueueBackgroundIndex(book: RegisteredImport) {
        if (book.format == BookFormat.EPUB) {
            scheduleEpubIndex()
            return
        }
        val request = OneTimeWorkRequestBuilder<TxtIndexWorker>()
            .setInputData(
                workDataOf(
                    TxtIndexWorker.KEY_BOOK_UUID to book.bookUuid,
                    TxtIndexWorker.KEY_RUN_ID to book.runId,
                    TxtIndexWorker.KEY_SOURCE_ID to book.sourceId,
                    TxtIndexWorker.KEY_DISPLAY_NAME to book.displayName,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(
            TxtIndexWorker.uniqueName(book.bookUuid),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun continueTxtIndex(
        bookUuid: String,
        runId: String?,
        sourceId: String?,
        displayName: String,
    ) = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val book = dao.getBook(bookUuid) ?: return@withContext
        val source = File(book.storagePath)
        try {
            // A worker can be stopped after any committed batch. Restart from the immutable TXT
            // source so retries never leave duplicate or half-indexed chapters.
            database.withTransaction {
                dao.deleteBookParagraphFts(setOf(bookUuid))
                dao.deleteChapters(bookUuid)
            }
            val chapterCount = importStreamingChapters(bookUuid, source, parsers.parserFor(BookFormat.TXT))
            if (chapterCount == 0) error(context.getString(R.string.db_no_chapters))
            DiagnosticLog.record(
                Category.IMPORT,
                "background_index_finished",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = "success",
                details = mapOf("format" to BookFormat.TXT.name, "chapters" to chapterCount),
            )
            if (runId != null && sourceId != null) {
                updateImportProgress(
                    runId,
                    sourceId,
                    ImportStage.FINISHED,
                    1f,
                    ImportItemStatus.SUCCEEDED,
                    bookUuid = bookUuid,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = error.toDiagnosticFailure()
            DiagnosticLog.record(
                Category.IMPORT,
                "background_index_finished",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = failure.outcome,
                details = mapOf(
                    "format" to BookFormat.TXT.name,
                    "book" to bookUuid.shortDiagnosticId(),
                    "reason" to failure.reason,
                ),
            )
            removeIncompleteImport(bookUuid)
            importEvents.emit("$displayName：${error.message ?: context.getString(R.string.db_import_failed)}")
            if (runId != null && sourceId != null) {
                updateImportProgress(
                    runId,
                    sourceId,
                    ImportStage.FINISHED,
                    1f,
                    ImportItemStatus.FAILED,
                    bookUuid = bookUuid,
                    message = error.message ?: context.getString(R.string.db_import_failed),
                )
            }
        }
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

    private suspend fun filenameRules(): List<LocalMetadata.FilenameRule> =
        LocalMetadata.parseFilenameRules(libraryPreferences.preferences.first().filenameRules).rules

    private suspend fun filenameSpecs(): List<FilenameRuleSpec> =
        libraryPreferences.preferences.first().filenameRuleSpecs

    private fun folderNameFor(uri: android.net.Uri): String? = runCatching {
        folderNameFromDocumentId(DocumentsContract.getDocumentId(uri))
    }.getOrNull()

    private fun displayNameFor(uri: android.net.Uri): String =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "未命名小说" }

    private suspend fun updateImportProgress(
        runId: String,
        sourceId: String,
        stage: ImportStage,
        progress: Float,
        status: ImportItemStatus,
        bookUuid: String? = null,
        message: String? = null,
    ) {
        val current = importDao.get(runId, sourceId) ?: return
        importDao.upsert(
            current.copy(
                stage = stage.name,
                progress = progress.coerceIn(0f, 1f),
                status = status.name,
                bookUuid = bookUuid ?: current.bookUuid,
                message = message,
                updatedTime = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun copyImportSource(
        runId: String,
        uri: android.net.Uri,
        sourceId: String,
        destination: File,
    ): String {
        val totalBytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0L }
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: error(context.getString(R.string.db_read_failed))
        input.use { source ->
            DigestInputStream(source, digest).use { hashingInput ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var lastPublished = 0L
                    while (true) {
                        ensureImportActive(runId)
                        val count = hashingInput.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (copied - lastPublished >= IMPORT_PROGRESS_PUBLISH_BYTES) {
                            lastPublished = copied
                            val fraction = totalBytes?.let { copied.toFloat() / it } ?: .5f
                            updateImportProgress(
                                runId, sourceId,
                                ImportStage.COPYING,
                                .02f + fraction.coerceIn(0f, 1f) * .30f,
                                ImportItemStatus.RUNNING,
                            )
                        }
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensureImportActive(runId: String) {
        if (runId in canceledImportRuns) throw ImportCanceledException()
    }

    private fun detectFormat(name: String, file: File): BookFormat {
        if (name.endsWith(".epub", true) || file.isEpub()) return BookFormat.EPUB
        if (name.endsWith(".txt", true)) return BookFormat.TXT
        val sample = FileInputStream(file).use { input -> ByteArray(1024).let { it.copyOf(input.read(it).coerceAtLeast(0)) } }
        if (sample.any { it == '\n'.code.toByte() } || sample.isNotEmpty()) return BookFormat.TXT
        error(context.getString(R.string.db_supported_formats))
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
                require(source.isFile) { context.getString(R.string.db_cloud_file_missing) }
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
                require(actualHash == book.contentHash) { context.getString(R.string.db_cloud_hash_failed) }
                val extension = if (book.format == BookFormat.EPUB) "epub" else "txt"
                val stored = File(context.filesDir, "books/${book.uuid}.$extension").also { it.parentFile?.mkdirs() }
                source.copyTo(stored, overwrite = true)
                try {
                    val parser = parsers.parserFor(book.format)
                    // A synced book carries no original file name; its title is not one either.
                    val parsedMetadata = parser.readMetadata(stored, book.title, sourceName = "")
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
                            // Synced sort/series data wins; a local reparse fills whatever the cloud
                            // payload did not carry.
                            originalDisplayName = book.originalDisplayName,
                            titleSort = book.titleSort.ifBlank { parsedMetadata.titleSort },
                            seriesName = book.seriesName.ifBlank { parsedMetadata.seriesName },
                            seriesIndex = book.seriesIndex ?: parsedMetadata.seriesIndex,
                        ),
                    )
                    if (book.format == BookFormat.EPUB) {
                        epubIndex.registerDirectory(book.uuid, stored, parser as EpubBookParser)
                        scheduleEpubIndex()
                    } else {
                        enqueueBackgroundIndex(
                            RegisteredImport(null, "google-drive://${book.uuid}", book.uuid, book.title, book.format, stored, parser),
                        )
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
            bookUuids.forEach { uuid -> workManager.cancelUniqueWork(TxtIndexWorker.uniqueName(uuid)) }
            val books = dao.getBooks(bookUuids)
            val progressCount = bookUuids.count { uuid -> dao.getProgress(uuid) != null }
            database.withTransaction {
                // Read the child ids inside the delete transaction. Reading them first would miss a
                // correction/annotation inserted in between, which the FK then deletes without a
                // tombstone, so it could be resurrected from Drive.
                val correctionUuids = bookUuids.flatMap { uuid ->
                    textCorrections.getBookCorrections(uuid).map(TextCorrection::uuid)
                }
                val annotationUuids = bookUuids.flatMap { uuid ->
                    annotations.getBookAnnotations(uuid).map(ReaderAnnotation::uuid)
                }
                dao.deleteMetadataEdits(bookUuids)
                dao.deleteBookParagraphFts(bookUuids)
                // Explicit cleanup in the same transaction as the cascade, so pending bookmarks
                // (and their excerpt text) never survive a single or batch book deletion.
                dao.deletePendingBookmarks(bookUuids)
                dao.deleteBooks(bookUuids)
                bookUuids.forEach { uuid ->
                    // Progress and bookmarks are independent Drive objects. Deleting only the
                    // book metadata leaves both objects available to restore stale state when an
                    // EPUB with the same dc:identifier is imported again.
                    syncMutations.record(SyncEntityType.BOOKMARKS, uuid, SyncMutationOperation.DELETE)
                    syncMutations.record(SyncEntityType.PROGRESS, uuid, SyncMutationOperation.DELETE)
                    syncMutations.record(SyncEntityType.BOOK, uuid, SyncMutationOperation.DELETE)
                }
                correctionUuids.forEach { uuid ->
                    syncMutations.record(SyncEntityType.CORRECTION, uuid, SyncMutationOperation.DELETE)
                }
                annotationUuids.forEach { uuid ->
                    syncMutations.record(SyncEntityType.ANNOTATION, uuid, SyncMutationOperation.DELETE)
                }
            }
            synchronized(chapterCacheLock) {
                chapterCache.removeMatching { it.bookUuid in bookUuids }
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
        var diagnosticSource = "unknown"
        try {
            // Keying by the book revision closes the window where the same uuid is re-bound to new
            // bytes (restore/repair) before the in-memory cache is cleared.
            val initialBook = dao.getBook(bookUuid)
            val cacheKey = ChapterCacheKey(bookUuid, initialBook?.contentHash.orEmpty(), chapterIndex)
            synchronized(chapterCacheLock) { chapterCache[cacheKey] }?.let {
                return@withContext textCorrections.applyToChapter(it)
            }

            val initialChapter = dao.getChapter(bookUuid, chapterIndex) ?: return@withContext null
            var source = "database"
            diagnosticSource = source
            val parsed = if (initialBook?.format == BookFormat.EPUB.name) {
                when (priority) {
                    ChapterLoadPriority.USER -> {
                        val diskCached = epubChapterCache.read(bookUuid, initialBook.contentHash, chapterIndex)
                        source = if (diskCached != null) "epub_disk_cache" else "epub_parse"
                        diagnosticSource = source
                        diskCached ?: epubParseCoordinator.interactive {
                            (parsers.parserFor(BookFormat.EPUB) as EpubBookParser)
                                .readChapter(
                                    File(initialBook.storagePath),
                                    chapterIndex,
                                    initialChapter.title,
                                    purpose = "reader",
                                )
                        }
                    }
                    ChapterLoadPriority.READ_AHEAD -> epubParseCoordinator.readAhead {
                        val diskCached = epubChapterCache.read(
                            bookUuid,
                            initialBook.contentHash,
                            chapterIndex,
                        )
                        source = if (diskCached != null) "epub_disk_cache" else "epub_parse"
                        diagnosticSource = source
                        diskCached ?: run {
                            (parsers.parserFor(BookFormat.EPUB) as EpubBookParser)
                                .readChapter(
                                    File(initialBook.storagePath),
                                    chapterIndex,
                                    initialChapter.title,
                                    purpose = "read_ahead",
                                )
                        }
                    }
                    ChapterLoadPriority.PREFETCH -> epubParseCoordinator.prefetch {
                        val diskCached = epubChapterCache.read(bookUuid, initialBook.contentHash, chapterIndex)
                        source = if (diskCached != null) "epub_disk_cache" else "epub_parse"
                        diagnosticSource = source
                        diskCached ?: run {
                            (parsers.parserFor(BookFormat.EPUB) as EpubBookParser)
                                .readChapter(
                                    File(initialBook.storagePath),
                                    chapterIndex,
                                    initialChapter.title,
                                    purpose = "prefetch",
                                )
                        }
                    }
                }
            } else {
                null
            }

            // XHTML parsing deliberately happens outside the shared commit lock. A current-page
            // request can therefore overtake a low-priority neighbour that is slow to decode.
            val content = chapterLoadMutex.withLock {
                val book = dao.getBook(bookUuid) ?: return@withLock null
                val lockedKey = ChapterCacheKey(bookUuid, book.contentHash, chapterIndex)
                synchronized(chapterCacheLock) { chapterCache[lockedKey] }?.let {
                    return@withLock it
                }
                val chapter = dao.getChapter(bookUuid, chapterIndex) ?: return@withLock null
                var storedParagraphs = dao.getParagraphs(chapter.id)
                val isSameEpubRevision = book.format == BookFormat.EPUB.name &&
                    initialBook?.contentHash == book.contentHash &&
                    initialBook.storagePath == book.storagePath
                if (parsed != null && isSameEpubRevision) {
                    epubChapterCache.write(bookUuid, book.contentHash, chapterIndex, parsed)
                    if (!chapter.indexed) {
                        dao.replaceChapterIndex(chapter.id, parsed.title, parsed.paragraphs)
                        storedParagraphs = dao.getParagraphs(chapter.id)
                    }
                }
                val paragraphs = if (isSameEpubRevision) {
                    parsed?.toReaderParagraphs(chapter.id, storedParagraphs)
                        ?: storedParagraphs.map { paragraph ->
                            Paragraph(paragraph.id, paragraph.chapterId, paragraph.paragraphIndex, paragraph.text)
                        }
                } else {
                    storedParagraphs.map { paragraph ->
                        Paragraph(paragraph.id, paragraph.chapterId, paragraph.paragraphIndex, paragraph.text)
                    }
                }
                ChapterContent(chapter.toModel(), paragraphs).also { loaded ->
                    synchronized(chapterCacheLock) { chapterCache[lockedKey] = loaded }
                }
            } ?: return@withContext null

            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (initialBook?.format == BookFormat.EPUB.name || elapsedMs >= SLOW_CHAPTER_LOAD_MS) {
                DiagnosticLog.record(
                    Category.READER,
                    "chapter_loaded",
                    elapsedMs = elapsedMs,
                    outcome = "success",
                    details = mapOf(
                        "format" to (initialBook?.format ?: "unknown"),
                        "book" to bookUuid.shortDiagnosticId(),
                        "chapter" to chapterIndex,
                        "priority" to priority.name,
                        "source" to source,
                        "paragraphs" to content.paragraphs.size,
                    ),
                )
            }
            textCorrections.applyToChapter(content)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val failure = error.toDiagnosticFailure()
            DiagnosticLog.record(
                Category.READER,
                "chapter_loaded",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = failure.outcome,
                details = mapOf(
                    "book" to bookUuid.shortDiagnosticId(),
                    "chapter" to chapterIndex,
                    "priority" to priority.name,
                    "source" to diagnosticSource,
                    "reason" to failure.reason,
                ),
            )
            throw error
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
            chapterCache.removeMatching { it.bookUuid == bookUuid }
        }
        (parsers.parserFor(BookFormat.EPUB) as EpubBookParser).clearMemoryCaches()
    }

    override fun setAppAnimationActive(active: Boolean) {
        epubParseCoordinator.setAppAnimationActive(active)
    }

    override fun observeProgress(bookUuid: String) = dao.observeProgress(bookUuid).map { it?.toModel() }
    override suspend fun saveProgress(progress: ReadingProgress) = bookMutations.saveProgress(progress)

    override suspend fun updateBookMetadata(bookUuid: String, title: String, author: String, description: String): Unit = bookMutations.updateBookMetadata(bookUuid, title, author, description)

    override suspend fun applySyncedBookMetadata(
        bookUuid: String,
        title: String,
        author: String,
        description: String,
    ) {
        database.withTransaction {
            dao.updateBookMetadata(bookUuid, title, author, description)
        }
    }

    override suspend fun updateBookDetails(bookUuid: String, title: String, author: String, description: String, category: String): Unit =
        bookMutations.updateBookDetails(bookUuid, title, author, description, category)

    override suspend fun updateBookImportedMetadata(
        bookUuid: String,
        originalDisplayName: String,
        titleSort: String,
        seriesName: String,
        seriesIndex: Double?,
    ) {
        database.withTransaction {
            dao.updateBookImportedMetadata(bookUuid, originalDisplayName, titleSort, seriesName, seriesIndex)
        }
    }

    override suspend fun reparseTxt(bookUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val book = dao.getBook(bookUuid) ?: error(context.getString(R.string.db_book_missing))
            require(book.format == BookFormat.TXT.name) { context.getString(R.string.db_txt_only) }
            val source = File(book.storagePath)
            require(source.isFile) { context.getString(R.string.db_txt_missing) }
            val parser = parsers.parserFor(BookFormat.TXT)
            val folderTitleFallback = book.originalDisplayName.substringBeforeLast('.')
                .ifBlank { book.originalDisplayName }
            val metadata = parser.readMetadata(source, book.title, book.originalDisplayName, filenameRules(), filenameSpecs())
                .withFolderFallback(book.originalFolderName.takeIf(String::isNotBlank), folderTitleFallback)
            val previousChapters = dao.getChapters(bookUuid)
            val previousChapterIndex = previousChapters.withIndex().associate { it.value.id to it.index }
            val previousProgress = dao.getProgress(bookUuid)
            val previousBookmarks = dao.getBookmarks(bookUuid)
            val previousProgressText = previousProgress?.let { progress ->
                dao.getParagraph(progress.chapterId, progress.position)?.text
            }
            // Capture each bookmark's anchored paragraph text before the chapters are replaced so it
            // can be re-matched against the reparsed text.
            val previousBookmarkTexts = previousBookmarks.associateWith { bookmark ->
                dao.getParagraph(bookmark.chapterId, bookmark.position)?.text
            }
            database.withTransaction {
                dao.deleteProgress(bookUuid)
                dao.deleteBookParagraphFts(setOf(bookUuid))
                dao.deleteChapters(bookUuid)
                var chapterIndex = 0
                val chapterIds = mutableListOf<Long>()
                val chapterKeys = mutableListOf<String>()
                parser.readChapters(source) { chapter ->
                    val index = chapterIndex
                    val chapterKey = stableChapterKey(bookUuid, index, chapter.title)
                    val chapterId = dao.insertChapter(
                        ChapterEntity(
                            bookUuid = bookUuid,
                            title = chapter.title,
                            chapterIndex = index,
                            volumeTitle = chapter.volumeTitle,
                            volumeIndex = chapter.volumeIndex,
                            chapterKey = chapterKey,
                        ),
                    )
                    chapterIndex = index + 1
                    chapterIds += chapterId
                    chapterKeys += chapterKey
                    dao.insertParagraphsChunked(chapterId, chapter.paragraphs)
                }
                require(chapterIndex > 0) { context.getString(R.string.db_no_chapters) }
                val paragraphsByChapter = chapterIds.associateWith { chapterId ->
                    dao.getParagraphs(chapterId)
                }

                // Relocate the bookmarks that still point at a paragraph. A target already used by
                // an earlier bookmark is not free, so the later one becomes pending instead of being
                // silently dropped by the unique (book, chapter, position) index.
                val occupied = mutableSetOf<Pair<Long, Int>>()
                previousBookmarks.forEach { bookmark ->
                    val migrated = migrateReparsedBookmark(
                        bookmark = bookmark,
                        previousChapterIndex = previousChapterIndex,
                        previousParagraphText = previousBookmarkTexts[bookmark],
                        chapterIds = chapterIds,
                        chapterKeys = chapterKeys,
                        paragraphsByChapter = paragraphsByChapter,
                    )
                    var inserted = false
                    if (migrated != null) {
                        val id = dao.insertBookmark(
                            BookmarkEntity(
                                uuid = migrated.uuid,
                                bookUuid = bookUuid,
                                chapterId = migrated.chapterId,
                                position = migrated.position,
                                preview = migrated.preview,
                                createdTime = migrated.createdTime,
                                chapterKey = migrated.chapterKey,
                            ),
                        )
                        if (id != -1L) {
                            occupied += migrated.chapterId to migrated.position
                            inserted = true
                        }
                    }
                    if (!inserted) {
                        // Keep the uuid, anchor text and preview so a later reparse can place it
                        // instead of discarding the user's bookmark.
                        dao.insertPendingBookmark(
                            PendingBookmarkEntity(
                                uuid = bookmark.uuid,
                                bookUuid = bookUuid,
                                anchorText = previousBookmarkTexts[bookmark].orEmpty(),
                                preview = bookmark.preview,
                                createdTime = bookmark.createdTime,
                            ),
                        )
                    }
                }
                // Last, try to place bookmarks an earlier reparse could not relocate: only a unique,
                // still-free anchor is committed; anything ambiguous or already taken stays pending.
                reconcilePendingBookmarks(
                    dao = dao,
                    bookUuid = bookUuid,
                    chapterIds = chapterIds,
                    chapterKeys = chapterKeys,
                    paragraphsByChapter = paragraphsByChapter,
                    occupied = occupied,
                )

                previousProgress?.let { progress ->
                    dao.saveProgress(
                        migrateReparsedProgress(
                            progress = progress,
                            previousChapterIndex = previousChapterIndex,
                            chapterIds = chapterIds,
                            chapterKeys = chapterKeys,
                            paragraphsByChapter = paragraphsByChapter,
                            previousProgressText = previousProgressText,
                        ),
                    )
                }
                // Field ownership is persisted on the book; the pruned edit journal must never
                // decide whether recognition refreshes a field the user did not touch.
                val currentMetadata = dao.getBook(bookUuid) ?: book
                val refreshedTitle = if (currentMetadata.userEditedTitle) {
                    currentMetadata.title
                } else {
                    metadata.title.trim().ifBlank { currentMetadata.title }
                }
                val refreshedAuthor = if (currentMetadata.userEditedAuthor) {
                    currentMetadata.author
                } else {
                    recognizedAuthor(metadata.author, currentMetadata.author)
                }
                val refreshedDescription = if (currentMetadata.userEditedDescription) {
                    currentMetadata.description
                } else {
                    metadata.description.trim()
                }
                if (refreshedTitle != currentMetadata.title ||
                    refreshedAuthor != currentMetadata.author ||
                    refreshedDescription != currentMetadata.description
                ) {
                    dao.updateBookMetadata(bookUuid, refreshedTitle, refreshedAuthor, refreshedDescription)
                    syncMutations.record(SyncEntityType.BOOK, bookUuid)
                }
            }
            synchronized(chapterCacheLock) {
                chapterCache.removeMatching { it.bookUuid == bookUuid }
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

    override suspend fun setCategory(bookUuid: String, category: String) = bookMutations.setCategory(bookUuid, category)

    override suspend fun setCategories(bookUuids: Set<String>, category: String) = bookMutations.setCategories(bookUuids, category)

    override fun observeBookmarks(bookUuid: String): Flow<List<Bookmark>> =
        dao.observeBookmarks(bookUuid).map { rows -> rows.map { it.toModel() } }

    override suspend fun addBookmark(bookmark: Bookmark): Unit = bookMutations.addBookmark(bookmark)

    override suspend fun deleteBookmark(bookmarkUuid: String) = bookMutations.deleteBookmark(bookmarkUuid)

    override suspend fun refreshMetadata(bookUuid: String): Result<MetadataRefreshResult> =
        withContext(Dispatchers.IO) {
            try {
                val book = dao.getBook(bookUuid) ?: error(context.getString(R.string.db_book_missing))
                val parser = parsers.parserFor(BookFormat.valueOf(book.format))
                Result.success(
                    refreshBookMetadata(
                        context, database, dao, parser, syncMutations, bookUuid, filenameRules(), filenameSpecs(),
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    override suspend fun searchBook(
        bookUuid: String,
        query: String,
        onProgress: suspend (BookSearchProgress) -> Unit,
        onResults: suspend (List<BookSearchResult>) -> Unit,
        retainResults: Boolean,
    ): List<BookSearchResult> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return@withContext emptyList()
        val chapters = dao.getChapters(bookUuid)
        onProgress(BookSearchProgress(BookSearchStage.SEARCHING, 0, chapters.size))
        BookSearchScanner(dao).search(
            retainResults = retainResults,
            chapters = chapters,
            query = normalizedQuery,
            corrections = textCorrections.getBookCorrections(bookUuid),
            ensureIndexed = { stored ->
                epubIndex.ensureChapterIndexedForSearch(bookUuid, stored.chapterIndex)
                    ?: error(context.getString(R.string.db_read_failed))
            },
            onProgress = onProgress,
            onResults = onResults,
        )
    }

    override suspend fun resolveEpubLink(bookUuid: String, target: String): EpubLinkResult? =
        withContext(Dispatchers.IO) {
            val book = dao.getBook(bookUuid)?.toModel() ?: return@withContext null
            if (book.format != BookFormat.EPUB) return@withContext null
            (parsers.parserFor(BookFormat.EPUB) as EpubBookParser)
                .resolveLink(File(book.storagePath), target)
        }

    override suspend fun readEpubNavigation(bookUuid: String): List<EpubNavigationEntry> = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookUuid)?.takeIf { it.format == BookFormat.EPUB.name }
            ?: return@withContext emptyList()
        (parsers.parserFor(BookFormat.EPUB) as EpubBookParser).readNavigation(File(book.storagePath))
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
        val book = dao.getBook(bookUuid) ?: return
        database.withTransaction {
            dao.deleteMetadataEdits(setOf(bookUuid))
            dao.deleteBookParagraphFts(setOf(bookUuid))
            dao.deleteBook(bookUuid)
            syncMutations.record(SyncEntityType.BOOK, bookUuid, SyncMutationOperation.DELETE)
        }
        File(book.storagePath).delete()
        book.coverPath?.let(::File)?.delete()
        epubChapterCache.clearBook(bookUuid)
        ReaderPaginationCacheMaintenance.clearBook(context.noBackupFilesDir, bookUuid)
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

    suspend fun continueAllEpubIndexes() = epubIndex.continueAll()
}

private class ImportCanceledException : Exception()

/** Keeps the in-memory optimistic shelf order from growing with every book ever opened. */
private fun Map<String, Long>.boundedOpenedAtOverrides(): Map<String, Long> =
    if (size <= MAX_OPENED_AT_OVERRIDES) this
    else entries.sortedByDescending { it.value }.take(MAX_OPENED_AT_OVERRIDES).associate { it.key to it.value }

private const val MAX_OPENED_AT_OVERRIDES = 512

/**
 * Re-anchors saved reading progress after a TXT reparse. The reader reads `paragraphIndex` and
 * `charOffset` (not the legacy `position`/`offset` columns), so every field must move together or
 * the restored page is wrong. The anchor is relocated by matching the previously read paragraph
 * text inside the reparsed chapter.
 */
internal fun migrateReparsedProgress(
    progress: ReadingProgressEntity,
    previousChapterIndex: Map<Long, Int>,
    chapterIds: List<Long>,
    chapterKeys: List<String>,
    paragraphsByChapter: Map<Long, List<ParagraphEntity>>,
    previousProgressText: String?,
): ReadingProgressEntity {
    val targetIndex = previousChapterIndex[progress.chapterId]?.coerceIn(0, chapterIds.lastIndex) ?: 0
    val targetChapterId = chapterIds[targetIndex]
    val targetParagraphs = paragraphsByChapter[targetChapterId].orEmpty()
    val lastParagraph = targetParagraphs.lastIndex.coerceAtLeast(0)
    val matchedIndex = previousProgressText
        ?.let { text -> targetParagraphs.indexOfFirst { it.text == text } }
        ?.takeIf { it >= 0 }
    val targetPosition = matchedIndex ?: progress.position.coerceIn(0, lastParagraph)
    // The matched paragraph text is unchanged, so the saved intra-paragraph offset is still valid;
    // only reset it when the anchor could not be matched and we fell back to the legacy position.
    val charOffset = if (matchedIndex != null) {
        progress.charOffset.coerceIn(0, targetParagraphs[matchedIndex].text.length)
    } else {
        0
    }
    return progress.copy(
        chapterId = targetChapterId,
        position = targetPosition,
        offset = charOffset,
        paragraphIndex = targetPosition,
        charOffset = charOffset,
        chapterKey = chapterKeys[targetIndex],
    )
}

/** A bookmark re-anchored to the reparsed chapters, carrying the new stable chapter key. */
internal data class ReparsedBookmark(
    val uuid: String,
    val chapterId: Long,
    val position: Int,
    val preview: String,
    val createdTime: Long,
    val chapterKey: String,
)

/**
 * Re-anchors a bookmark after a TXT reparse.
 *
 * Chapter boundaries can shift (for example a new front-matter chapter is recognised), so the old
 * chapter index is not reliable. Resolution order:
 * 1. the reparsed chapter with the same stable key, when it still exists;
 * 2. a chapter that uniquely contains the bookmarked paragraph text;
 * 3. an ambiguous text match, preferring the index-mapped chapter, then reading order;
 * 4. when no anchor text was captured, the index-mapped chapter (legacy best effort).
 *
 * Returns null when the anchor text existed but can no longer be found, so the caller does not bind
 * the bookmark to a different chapter that does not contain it.
 */
internal fun migrateReparsedBookmark(
    bookmark: BookmarkRow,
    previousChapterIndex: Map<Long, Int>,
    previousParagraphText: String?,
    chapterIds: List<Long>,
    chapterKeys: List<String>,
    paragraphsByChapter: Map<Long, List<ParagraphEntity>>,
): ReparsedBookmark? {
    if (chapterIds.isEmpty()) return null
    val keyedIndex = bookmark.chapterKey
        .takeIf { it.isNotBlank() }
        ?.let { key -> chapterKeys.indexOf(key).takeIf { it >= 0 } }
    val textMatches = previousParagraphText?.let { text ->
        chapterIds.indices.flatMap { index ->
            paragraphsByChapter[chapterIds[index]].orEmpty()
                .withIndex()
                .filter { it.value.text == text }
                .map { index to it.index }
        }
    }.orEmpty()
    val indexMapped = previousChapterIndex[bookmark.chapterId]?.coerceIn(0, chapterIds.lastIndex)

    val targetIndex = when {
        keyedIndex != null -> keyedIndex
        textMatches.size == 1 -> textMatches.single().first
        textMatches.isNotEmpty() -> {
            // Ambiguous: prefer the index-mapped chapter when it is one of the matches, otherwise
            // the earliest match in reading order (deterministic, never arbitrary).
            textMatches.firstOrNull { it.first == indexMapped }?.first ?: textMatches.first().first
        }
        previousParagraphText == null -> indexMapped
        else -> null
    } ?: return null

    val targetChapterId = chapterIds[targetIndex]
    val targetParagraphs = paragraphsByChapter[targetChapterId].orEmpty()
    val position = textMatches.firstOrNull { it.first == targetIndex }?.second
        ?: bookmark.position.coerceIn(0, targetParagraphs.lastIndex.coerceAtLeast(0))
    return ReparsedBookmark(
        uuid = bookmark.uuid,
        chapterId = targetChapterId,
        position = position,
        preview = bookmark.preview,
        createdTime = bookmark.createdTime,
        chapterKey = chapterKeys[targetIndex],
    )
}

internal sealed interface PendingBookmarkResolution {
    data class Resolved(val bookmark: ReparsedBookmark) : PendingBookmarkResolution

    /** The anchor is absent, ambiguous, or its only position is taken; the record must stay. */
    data object Unresolved : PendingBookmarkResolution
}

/**
 * Tries to place a previously unlocated bookmark into the reparsed chapters by matching its saved
 * anchor text. Only a unique, still-free paragraph is accepted; an anchor that repeats in the new
 * text is ambiguous and must not be guessed, so the bookmark stays pending instead of being
 * committed to the wrong paragraph or dropped by the unique bookmark index.
 */
internal fun resolvePendingBookmark(
    pending: PendingBookmarkEntity,
    chapterIds: List<Long>,
    chapterKeys: List<String>,
    paragraphsByChapter: Map<Long, List<ParagraphEntity>>,
    occupied: Set<Pair<Long, Int>>,
): PendingBookmarkResolution {
    if (chapterIds.isEmpty()) return PendingBookmarkResolution.Unresolved
    val anchor = pending.anchorText.takeIf { it.isNotBlank() } ?: return PendingBookmarkResolution.Unresolved
    var match: Pair<Long, Int>? = null
    chapterIds.forEachIndexed { index, chapterId ->
        paragraphsByChapter[chapterId].orEmpty().forEachIndexed { position, paragraph ->
            if (paragraph.text != anchor) return@forEachIndexed
            if (match != null) return PendingBookmarkResolution.Unresolved
            match = chapterId to position
        }
    }
    val (chapterId, position) = match ?: return PendingBookmarkResolution.Unresolved
    if (chapterId to position in occupied) return PendingBookmarkResolution.Unresolved
    val chapterIndex = chapterIds.indexOf(chapterId)
    return PendingBookmarkResolution.Resolved(
        ReparsedBookmark(
            uuid = pending.uuid,
            chapterId = chapterId,
            position = position,
            preview = pending.preview,
            createdTime = pending.createdTime,
            chapterKey = chapterKeys[chapterIndex],
        ),
    )
}

/**
 * Commits every pending bookmark that now has a unique, free anchor. The insert result is checked
 * before the pending row is deleted: a conflict must keep the record so the user's bookmark is
 * never silently lost.
 */
internal suspend fun reconcilePendingBookmarks(
    dao: BookDao,
    bookUuid: String,
    chapterIds: List<Long>,
    chapterKeys: List<String>,
    paragraphsByChapter: Map<Long, List<ParagraphEntity>>,
    occupied: MutableSet<Pair<Long, Int>>,
) {
    dao.getPendingBookmarks(bookUuid).forEach { pending ->
        val resolved = resolvePendingBookmark(pending, chapterIds, chapterKeys, paragraphsByChapter, occupied)
        if (resolved !is PendingBookmarkResolution.Resolved) return@forEach
        val id = dao.insertBookmark(
            BookmarkEntity(
                uuid = resolved.bookmark.uuid,
                bookUuid = bookUuid,
                chapterId = resolved.bookmark.chapterId,
                position = resolved.bookmark.position,
                preview = resolved.bookmark.preview,
                createdTime = resolved.bookmark.createdTime,
                chapterKey = resolved.bookmark.chapterKey,
            ),
        )
        if (id != -1L) {
            occupied += resolved.bookmark.chapterId to resolved.bookmark.position
            dao.deletePendingBookmark(pending.uuid)
        }
    }
}
