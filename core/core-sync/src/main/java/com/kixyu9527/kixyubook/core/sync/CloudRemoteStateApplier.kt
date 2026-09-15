package com.kixyu9527.kixyubook.core.sync
import com.kixyu9527.kixyubook.core.common.configuration.*

import android.content.Context
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.LibraryPreferences
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.common.model.ReadingReminderSettings
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.SettingsWriteGate
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import com.kixyu9527.kixyubook.core.common.repository.TextCorrectionRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.BookmarkEntity
import com.kixyu9527.kixyubook.core.database.entity.PendingBookmarkEntity
import com.kixyu9527.kixyubook.core.database.entity.ReadingSessionEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import com.kixyu9527.kixyubook.core.database.entity.UserFontEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Applies downloaded cloud objects without coupling restore rules to sync orchestration. */
internal class CloudRemoteStateApplier(
    private val context: Context,
    private val database: KixyuDatabase,
    private val books: BookDao,
    private val fonts: FontDao,
    private val syncDao: SyncDao,
    private val bookRepository: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val libraryPreferencesRepository: LibraryPreferencesRepository,
    private val textCorrectionRepository: TextCorrectionRepository,
    private val readerAnnotationRepository: ReaderAnnotationRepository,
    private val readingReminders: ReadingReminderScheduler,
    private val preferences: SyncPreferencesStore,
    private val mutations: RoomSyncMutationRecorder,
    private val drive: DriveAppDataClient,
) {
    suspend fun restoreBook(token: String, uuid: String, knownRemote: Map<String, DriveObject>) {
        val key = "books/$uuid/metadata"
        val metadata = knownRemote[key] ?: return
        val sourceInfo = knownRemote["books/$uuid/source"] ?: return
        if (!books.bookExists(uuid)) {
            val metaFile = tempFile("book-meta")
            val sourceFile = tempFile("book-source")
            try {
                drive.download(token, metadata.id, metaFile)
                drive.download(token, sourceInfo.id, sourceFile)
                val book = parseBook(JSONObject(metaFile.readText()))
                mutations.withoutRecording { bookRepository.restoreSyncedBook(book, sourceFile.absolutePath) }
            } finally {
                metaFile.delete()
                sourceFile.delete()
            }
        } else {
            val temp = tempFile("book-meta")
            try {
                drive.download(token, metadata.id, temp)
                val payload = JSONObject(temp.readText())
                val book = parseBook(payload)
                mutations.withoutRecording {
                    applyBookMetadataFromRemote(database, syncDao, book.uuid) {
                        bookRepository.applySyncedBookMetadata(book.uuid, book.title, book.author, book.description)
                        // Schema 1 has no sort/series/original-name fields: absent means "unknown",
                        // never "clear what this device already has".
                        if (payload.optInt("schema", 1) >= 2) {
                            bookRepository.updateBookImportedMetadata(
                                bookUuid = book.uuid,
                                originalDisplayName = book.originalDisplayName,
                                titleSort = book.titleSort,
                                seriesName = book.seriesName,
                                seriesIndex = book.seriesIndex,
                            )
                        }
                        bookRepository.setCategory(book.uuid, book.category)
                    }
                }
            } finally {
                temp.delete()
            }
        }
        rememberRemote(key, metadata)
        rememberRemote("books/$uuid/source", sourceInfo)
    }

    suspend fun applyProgress(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        applyProgressJson(json)
    }

    suspend fun applyProgressJson(json: JSONObject): Boolean {
        val bookUuid = json.getString("bookUuid")
        if (!books.bookExists(bookUuid)) return false
        val chapterKey = json.optString("chapterKey")
        val chapter = books.getChapterByKey(bookUuid, chapterKey)
            ?: books.getChapter(bookUuid, json.optInt("chapterIndex"))
            ?: return false
        val remoteTime = json.optLong("updatedTime")
        val local = books.getProgress(bookUuid)
        if (local != null && local.updatedTime > remoteTime) return false
        val remoteFraction = json.optDouble("progression").toFloat()
        // A newer timestamp can represent a reread on another device. Never move the visible
        // device backwards automatically; its explicit movement will become the new latest state.
        if (local != null && remoteFraction + PROGRESS_EPSILON < local.fraction) return false
        mutations.withoutRecording {
            bookRepository.saveProgress(
                ReadingProgress(
                    bookUuid = bookUuid,
                    chapterId = chapter.id,
                    position = json.optInt("paragraphIndex"),
                    offset = json.optInt("charOffset"),
                    updatedTime = remoteTime,
                    fraction = remoteFraction,
                    chapterKey = chapter.chapterKey,
                    paragraphIndex = json.optInt("paragraphIndex"),
                    charOffset = json.optInt("charOffset"),
                    quoteAnchor = json.optString("quoteAnchor"),
                ),
            )
        }
        return true
    }

    suspend fun applyBookmarks(token: String, info: DriveObject): Boolean = withJsonDownload(token, info) { json ->
        applyBookmarksJson(json)
    }

    /** Returns whether the remote snapshot replaced the local list; false means a local edit won. */
    suspend fun applyBookmarksJson(json: JSONObject): Boolean =
        mutations.withoutRecording { replaceBookmarksFromRemote(database, books, syncDao, json) }

    suspend fun applySettings(token: String, info: DriveObject) {
        // Capture before the network read so a local edit made while the file downloads wins.
        val startGeneration = SettingsWriteGate.currentGeneration()
        withJsonDownload(token, info) { json ->
            withSettingsWriteGuard(
                startGeneration,
                isLocallyPending = { syncDao.pendingCount(SyncEntityType.SETTINGS.name, "global") > 0 },
            ) { applySettingsJson(json) }
        }
    }

    /** Returns whether the remote settings were applied; false means a local edit won the guard. */
    suspend fun applySettingsJson(json: JSONObject): Boolean {
        val remote = jsonToSettings(json.getJSONObject("reader"))
        val goal = json.optInt("readingGoalMinutes", 30)
        val library = json.optJSONObject("library")?.let(::jsonToLibraryPreferences)
        val reminder = json.optJSONObject("readingReminder")?.let(::jsonToReadingReminder)
        mutations.withoutRecording {
            applySettingsAcrossStores(remote, goal, library, reminder)
        }
        return true
    }

    /**
     * Applies a settings snapshot across the reader, library and reminder stores. DataStore has no
     * cross-store transaction, so capture the previous values first and roll back the stores that
     * were already written if a later write fails: a failure then leaves the device untouched
     * instead of half-updated. The caller holds [SettingsWriteGate], so no local write can
     * interleave with the snapshot or the rollback.
     */
    private suspend fun applySettingsAcrossStores(
        reader: ReaderSettings,
        goal: Int,
        library: LibraryPreferences?,
        reminder: ReadingReminderSettings?,
    ) {
        val previousReader = settingsRepository.settings.first()
        val previousGoal = settingsRepository.readingGoalMinutes.first()
        val previousLibrary = library?.let { libraryPreferencesRepository.preferences.first() }
        val previousReminder = reminder?.let { readingReminders.settings.first() }

        applySettingsWithRollback(
            applyReader = {
                settingsRepository.update { reader }
                settingsRepository.setReadingGoalMinutes(goal)
            },
            applyLibrary = { libraryPreferencesRepository.replace(requireNotNull(library)) },
            applyReminder = { readingReminders.replace(requireNotNull(reminder)) },
            hasLibrary = library != null,
            hasReminder = reminder != null,
            rollbackReader = {
                settingsRepository.update { previousReader }
                settingsRepository.setReadingGoalMinutes(previousGoal)
            },
            rollbackLibrary = { libraryPreferencesRepository.replace(requireNotNull(previousLibrary)) },
            rollbackReminder = { readingReminders.replace(requireNotNull(previousReminder)) },
        )
    }

    suspend fun applySession(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        val uuid = json.getString("uuid")
        if (books.getSessionBySyncUuid(uuid) == null) {
            books.insertSession(
                ReadingSessionEntity(
                    bookUuid = json.getString("bookUuid"),
                    startedTime = json.optLong("startedTime"),
                    durationMillis = json.optLong("durationMillis"),
                    epochDay = json.optLong("epochDay"),
                    syncUuid = uuid,
                ),
            )
        }
    }

    suspend fun applyCorrection(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        textCorrectionRepository.applyRemote(parseCorrection(json))
    }

    suspend fun applyAnnotation(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        readerAnnotationRepository.applyRemote(parseAnnotation(json))
    }

    suspend fun applyFont(token: String, metadata: DriveObject, source: DriveObject) {
        val uuid = metadata.objectKey.split('/').getOrNull(1) ?: return
        if (fonts.getFont(uuid) != null || !preferences.current().syncFonts) return
        val metaFile = tempFile("font-meta")
        val sourceFile = File(context.filesDir, "fonts/$uuid.ttf")
        try {
            drive.download(token, metadata.id, metaFile)
            drive.download(token, source.id, sourceFile)
            val json = JSONObject(metaFile.readText())
            fonts.insert(
                UserFontEntity(
                    uuid,
                    json.optString("name", "云端字体"),
                    sourceFile.absolutePath,
                    json.optLong("createdTime"),
                ),
            )
        } catch (error: Throwable) {
            sourceFile.delete()
            throw error
        } finally {
            metaFile.delete()
        }
    }

    private suspend fun <T> withJsonDownload(
        token: String,
        info: DriveObject,
        block: suspend (JSONObject) -> T,
    ): T {
        val file = tempFile("json")
        return try {
            drive.download(token, info.id, file)
            block(JSONObject(file.readText()))
        } finally {
            file.delete()
        }
    }

    private suspend fun rememberRemote(key: String, value: DriveObject) {
        val previous = syncDao.objectState(key)
        syncDao.upsertObjectState(
            SyncObjectStateEntity(
                key,
                value.id,
                previous?.localHash,
                previous?.localChangedAt ?: 0,
                value.modifiedAt,
                value.version,
            ),
        )
    }

    private fun tempFile(prefix: String) = File(context.cacheDir, "cloud-sync/$prefix-${UUID.randomUUID()}")
        .also { it.parentFile?.mkdirs() }

    private companion object {
        const val PROGRESS_EPSILON = 0.000_001f
    }
}

/**
 * Applies a downloaded settings snapshot only when no local settings write happened since
 * [startGeneration]. The check and the apply run under the same lock that local settings writes
 * take, so there is no window between the check and the write.
 */
internal suspend fun withSettingsWriteGuard(
    startGeneration: Long,
    isLocallyPending: suspend () -> Boolean,
    apply: suspend () -> Unit,
): Boolean = SettingsWriteGate.mutex.withLock {
    if (SettingsWriteGate.currentGeneration() != startGeneration) return@withLock false
    // A write persisted to DataStore but not yet durable in the outbox must also win.
    if (SettingsWriteGate.hasPendingLocalWrite()) return@withLock false
    if (isLocallyPending()) return@withLock false
    apply()
    true
}

/**
 * Applies downloaded book metadata only when no local edit is pending. The check and the write run
 * in one Room transaction, so a rename/description/category edit made while the metadata file was
 * downloading is never overwritten by the older remote snapshot.
 */
internal suspend fun applyBookMetadataFromRemote(
    database: KixyuDatabase,
    syncDao: SyncDao,
    bookUuid: String,
    update: suspend () -> Unit,
): Boolean = database.withTransaction {
    if (syncDao.pendingCount(SyncEntityType.BOOK.name, bookUuid) > 0) return@withTransaction false
    update()
    true
}

/**
 * Replaces one book's bookmark list from a remote snapshot.
 *
 * A bookmark added while the pull is running is still queued in the outbox. Wiping the list here
 * would erase it before the push uploads it. Room serializes transactions, so checking the outbox
 * inside the same transaction is race-free: either the local add committed first (skip the remote
 * snapshot and let local win), or it commits after the replace (the add survives). The skipped
 * remote snapshot is superseded by the pending push and reconciled on the next run.
 *
 * @return true when the remote list replaced the local one; false when it was skipped because a
 * local edit still owns the book (the caller must keep that edit queued for upload).
 */
internal suspend fun replaceBookmarksFromRemote(
    database: KixyuDatabase,
    books: BookDao,
    syncDao: SyncDao,
    json: JSONObject,
): Boolean {
    val bookUuid = json.getString("bookUuid")
    if (!books.bookExists(bookUuid)) return false
    return database.withTransaction {
        if (syncDao.pendingCount(SyncEntityType.BOOKMARKS.name, bookUuid) > 0) return@withTransaction false
        // A snapshot is authoritative for the whole bookmark list, so a pending record the remote
        // no longer carries is a remote deletion and must be cleared locally too.
        books.deleteBookmarksForBook(bookUuid)
        books.deletePendingBookmarks(setOf(bookUuid))
        val items = json.optJSONArray("items") ?: JSONArray()
        for (index in 0 until items.length()) {
            val value = items.getJSONObject(index)
            if (value.optString("status", BOOKMARK_STATUS_LOCATED) == BOOKMARK_STATUS_PENDING) {
                books.insertPendingBookmark(
                    PendingBookmarkEntity(
                        uuid = value.getString("uuid"),
                        bookUuid = bookUuid,
                        anchorText = value.optString("anchorText"),
                        preview = value.optString("preview"),
                        createdTime = value.optLong("createdTime"),
                    ),
                )
                continue
            }
            val chapter = books.getChapterByKey(bookUuid, value.optString("chapterKey"))
                ?: books.getChapter(bookUuid, value.optInt("chapterIndex"))
                ?: continue
            books.insertBookmark(
                BookmarkEntity(
                    uuid = value.getString("uuid"),
                    bookUuid = bookUuid,
                    chapterId = chapter.id,
                    position = value.optInt("paragraphIndex"),
                    preview = value.optString("preview"),
                    createdTime = value.optLong("createdTime"),
                    chapterKey = value.optString("chapterKey").ifBlank { chapter.chapterKey },
                ),
            )
        }
        true
    }
}
