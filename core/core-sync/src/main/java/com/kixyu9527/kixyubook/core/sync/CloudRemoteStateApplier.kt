package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.BookmarkEntity
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
                val book = parseBook(JSONObject(temp.readText()))
                mutations.withoutRecording {
                    bookRepository.updateBookMetadata(book.uuid, book.title, book.author, book.description)
                    bookRepository.setCategory(book.uuid, book.category)
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

    suspend fun applyBookmarks(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        applyBookmarksJson(json)
    }

    suspend fun applyBookmarksJson(json: JSONObject) {
        val bookUuid = json.getString("bookUuid")
        if (!books.bookExists(bookUuid)) return
        mutations.withoutRecording {
            database.withTransaction {
                books.deleteBookmarksForBook(bookUuid)
                val items = json.optJSONArray("items") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val value = items.getJSONObject(index)
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
                        ),
                    )
                }
            }
        }
    }

    suspend fun applySettings(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        applySettingsJson(json)
    }

    suspend fun applySettingsJson(json: JSONObject) {
        val remote = jsonToSettings(json.getJSONObject("reader"))
        val goal = json.optInt("readingGoalMinutes", 30)
        mutations.withoutRecording {
            settingsRepository.update { remote }
            settingsRepository.setReadingGoalMinutes(goal)
        }
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

    private suspend fun withJsonDownload(
        token: String,
        info: DriveObject,
        block: suspend (JSONObject) -> Unit,
    ) {
        val file = tempFile("json")
        try {
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
