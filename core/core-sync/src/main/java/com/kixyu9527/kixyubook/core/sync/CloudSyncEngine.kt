package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private data class LocalCloudObject(
    val key: String,
    val name: String,
    val mimeType: String,
    val file: File,
    val temporary: Boolean = false,
)

@Singleton
class CloudSyncEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: KixyuDatabase,
    private val books: BookDao,
    private val fonts: FontDao,
    private val syncDao: SyncDao,
    private val bookRepository: BookRepository,
    private val fontRepository: FontRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val preferences: SyncPreferencesStore,
    private val mutations: RoomSyncMutationRecorder,
    private val drive: DriveAppDataClient,
    private val accountClient: GoogleAccountClient,
) {
    suspend fun inspectInitialSync(): InitialSyncDecision = withContext(Dispatchers.IO) {
        val token = accountClient.accessToken()
            ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
        val remote = drive.listAll(token).associateBy(DriveObject::objectKey)
        val restorableBooks = remote.keys.asSequence()
            .filter { it.startsWith("books/") && it.endsWith("/metadata") }
            .mapNotNull { it.split('/').getOrNull(1) }
            .filter { "books/$it/source" in remote }
            .distinct()
            .count()
        InitialSyncDecision(
            localBookCount = books.getAllBooks().size,
            cloudBookCount = restorableBooks,
        )
    }

    suspend fun replaceCloudWithLocalLibrary() = withContext(Dispatchers.IO) {
        val token = accountClient.accessToken()
            ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
        deleteAllCloudData(token)
        seedInitialOutbox()
    }

    suspend fun prepareCloudRestore() = withContext(Dispatchers.IO) {
        // A disconnected device may still contain queued deletions. Once the user explicitly
        // chooses cloud restore, those local mutations must not hide or delete remote books.
        syncDao.clearOutbox()
        syncDao.clearObjectStates()
    }

    suspend fun synchronize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val persisted = preferences.current()
            if (!persisted.enabled || persisted.account == null || !persisted.initialSyncApproved) {
                return@runCatching
            }
            preferences.markRunning()
            val token = accountClient.accessToken()
                ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
            var remote = drive.listAll(token).associateBy(DriveObject::objectKey).toMutableMap()

            // A durable page token is maintained for incremental wake-ups. A full appData listing
            // remains the recovery path if Google invalidates an old token or local state is lost.
            val newPageToken = runCatching {
                persisted.pageToken?.let { drive.listChanges(token, it).newStartPageToken }
                    ?: drive.startPageToken(token)
            }.getOrNull()

            applyRemoteTombstones(token, remote)
            applyRemoteChanges(token, remote, persisted.initialMergeComplete)

            if (!persisted.initialMergeComplete) seedInitialOutbox()
            val pending = syncDao.pending()
            pending.forEach { mutation ->
                try {
                    if (mutation.operation == SyncMutationOperation.DELETE.name) {
                        remote = pushDeletion(token, mutation, remote)
                    } else {
                        val deferredLargePayload = shouldDeferLargePayload(mutation)
                        materialize(mutation, includeLargePayload = !deferredLargePayload).forEach { local ->
                            try {
                                val hash = local.file.sha256()
                                val known = remote[local.key]
                                val uploaded = drive.upload(
                                    token = token,
                                    name = local.name,
                                    objectKey = local.key,
                                    mimeType = local.mimeType,
                                    source = local.file,
                                    existingFileId = known?.id,
                                )
                                remote[local.key] = uploaded
                                syncDao.upsertObjectState(
                                    SyncObjectStateEntity(
                                        objectKey = local.key,
                                        driveFileId = uploaded.id,
                                        localHash = hash,
                                        localChangedAt = mutation.changedAt,
                                        remoteModifiedAt = uploaded.modifiedAt,
                                        remoteVersion = uploaded.version,
                                    ),
                                )
                            } finally {
                                if (local.temporary) local.file.delete()
                            }
                        }
                        if (!deferredLargePayload) syncDao.removeOutbox(listOf(mutation.uuid))
                    }
                } catch (error: Throwable) {
                    syncDao.markAttempts(listOf(mutation.uuid))
                    throw error
                }
            }
            preferences.markSuccess(newPageToken ?: persisted.pageToken)
        }.onFailure { error ->
            if (error is AuthorizationRequiredException || (error is DriveHttpException && error.statusCode == 401)) {
                preferences.markAuthRequired(error.message ?: "需要重新授权 Google Drive")
            } else {
                preferences.markError(error.message ?: "同步失败")
            }
        }
    }

    suspend fun deleteAllCloudData(token: String) = withContext(Dispatchers.IO) {
        drive.listAll(token).forEach { drive.delete(token, it.id) }
        syncDao.clearObjectStates()
        syncDao.clearOutbox()
        syncDao.clearTombstones()
    }

    suspend fun enqueueAllCurrentState() = withContext(Dispatchers.IO) { seedInitialOutbox() }

    private suspend fun seedInitialOutbox() {
        books.getAllBooks().forEach { mutations.record(SyncEntityType.BOOK, it.uuid) }
        books.getAllProgress().forEach { mutations.record(SyncEntityType.PROGRESS, it.bookUuid) }
        books.getAllBookmarkEntities().map(BookMarkOwner::from).map(BookMarkOwner::bookUuid).distinct()
            .forEach { mutations.record(SyncEntityType.BOOKMARKS, it) }
        mutations.record(SyncEntityType.SETTINGS, "global")
        books.getAllSessions().forEach { session ->
            val syncUuid = session.syncUuid.ifBlank { "legacy-${session.id}" }
            mutations.record(SyncEntityType.SESSION, syncUuid)
        }
        if (preferences.current().syncFonts) {
            fonts.getAllFonts().forEach { mutations.record(SyncEntityType.FONT, it.uuid) }
        }
    }

    private suspend fun materialize(
        mutation: SyncOutboxEntity,
        includeLargePayload: Boolean,
    ): List<LocalCloudObject> = when (
        SyncEntityType.valueOf(mutation.entityType)
    ) {
        SyncEntityType.BOOK -> books.getBook(mutation.entityId)?.let { book ->
            buildList {
                add(jsonObject("books/${book.uuid}/metadata", bookMetadataJson(book)))
                if (preferences.current().syncOriginalFiles && includeLargePayload) {
                    val source = File(book.storagePath)
                    if (source.isFile) add(
                        LocalCloudObject(
                            key = "books/${book.uuid}/source",
                            name = "book-${book.uuid}.${book.format.lowercase()}",
                            mimeType = if (book.format == BookFormat.EPUB.name) "application/epub+zip" else "text/plain",
                            file = source,
                        ),
                    )
                }
            }
        }.orEmpty()
        SyncEntityType.PROGRESS -> books.getProgress(mutation.entityId)?.let { progress ->
            val chapterKey = progress.chapterKey.ifBlank {
                books.getChapters(progress.bookUuid).firstOrNull { it.id == progress.chapterId }?.chapterKey.orEmpty()
            }
            listOf(jsonObject("progress/${progress.bookUuid}", progressJson(progress, chapterKey)))
        }.orEmpty()
        SyncEntityType.BOOKMARKS -> {
            val chapters = books.getChapters(mutation.entityId).associateBy { it.id }
            val values = books.getBookmarks(mutation.entityId)
            listOf(jsonObject("bookmarks/${mutation.entityId}", bookmarksJson(mutation.entityId, values, chapters)))
        }
        SyncEntityType.SETTINGS -> listOf(jsonObject("settings/global", settingsJson()))
        SyncEntityType.SESSION -> books.getSessionBySyncUuid(mutation.entityId)?.let {
            listOf(jsonObject("sessions/${it.syncUuid}", sessionJson(it)))
        }.orEmpty()
        SyncEntityType.FONT -> if (preferences.current().syncFonts && includeLargePayload) {
            fonts.getFont(mutation.entityId)?.let { font ->
                listOf(
                    jsonObject("fonts/${font.uuid}/metadata", fontJson(font)),
                    LocalCloudObject(
                        key = "fonts/${font.uuid}/source",
                        name = "font-${font.uuid}.${File(font.filePath).extension.ifBlank { "ttf" }}",
                        mimeType = "application/octet-stream",
                        file = File(font.filePath),
                    ),
                )
            }.orEmpty()
        } else emptyList()
    }

    private suspend fun pushDeletion(
        token: String,
        mutation: SyncOutboxEntity,
        currentRemote: MutableMap<String, DriveObject>,
    ): MutableMap<String, DriveObject> {
        val prefix = when (SyncEntityType.valueOf(mutation.entityType)) {
            SyncEntityType.BOOK -> "books/${mutation.entityId}/"
            SyncEntityType.FONT -> "fonts/${mutation.entityId}/"
            SyncEntityType.PROGRESS -> "progress/${mutation.entityId}"
            SyncEntityType.BOOKMARKS -> "bookmarks/${mutation.entityId}"
            SyncEntityType.SESSION -> "sessions/${mutation.entityId}"
            SyncEntityType.SETTINGS -> "settings/global"
        }
        currentRemote.filterKeys { it == prefix || it.startsWith(prefix) }.forEach { (key, file) ->
            drive.delete(token, file.id)
            currentRemote.remove(key)
            syncDao.removeObjectState(key)
        }
        val now = System.currentTimeMillis()
        val expiresAt = PERMANENT_TOMBSTONE_EXPIRY
        val tombstoneKey = "tombstones/${mutation.entityType.lowercase()}/${mutation.entityId}"
        val tombstone = jsonObject(
            tombstoneKey,
            JSONObject()
                .put("type", mutation.entityType)
                .put("entityId", mutation.entityId)
                .put("deletedAt", now)
                .put("deviceId", mutation.deviceId)
                .put("expiresAt", expiresAt),
        )
        val uploaded = try {
            drive.upload(token, tombstone.name, tombstone.key, tombstone.mimeType, tombstone.file, currentRemote[tombstoneKey]?.id)
        } finally {
            tombstone.file.delete()
        }
        currentRemote[tombstoneKey] = uploaded
        syncDao.upsertTombstone(SyncTombstoneEntity(tombstoneKey, now, mutation.deviceId, expiresAt))
        syncDao.removeOutbox(listOf(mutation.uuid))
        return currentRemote
    }

    private suspend fun applyRemoteTombstones(token: String, remote: MutableMap<String, DriveObject>) {
        remote.filterKeys { it.startsWith("tombstones/") }.forEach { (key, objectInfo) ->
            val temp = tempFile("tombstone")
            try {
                drive.download(token, objectInfo.id, temp)
                val json = JSONObject(temp.readText())
                // Keep the field for compatibility with already released clients, but use the
                // largest Long value so old versions never garbage-collect a permanent deletion.
                // Legacy 30-day tombstones are upgraded as soon as a current client sees them.
                if (json.optLong("expiresAt") != PERMANENT_TOMBSTONE_EXPIRY) {
                    json.put("expiresAt", PERMANENT_TOMBSTONE_EXPIRY)
                    val normalized = jsonObject(key, json)
                    try {
                        remote[key] = drive.upload(
                            token = token,
                            name = normalized.name,
                            objectKey = normalized.key,
                            mimeType = normalized.mimeType,
                            source = normalized.file,
                            existingFileId = objectInfo.id,
                        )
                    } finally {
                        normalized.file.delete()
                    }
                }
                val type = runCatching { SyncEntityType.valueOf(json.getString("type")) }.getOrNull() ?: return@forEach
                val id = json.getString("entityId")
                mutations.withoutRecording {
                    when (type) {
                        SyncEntityType.BOOK -> if (books.bookExists(id)) bookRepository.deleteBook(id)
                        SyncEntityType.FONT -> fontRepository.deleteFont(id)
                        else -> Unit
                    }
                }
                syncDao.removeOutbox(type.name, id)
                syncDao.upsertTombstone(
                    SyncTombstoneEntity(
                        objectKey = key,
                        deletedAt = json.optLong("deletedAt"),
                        deviceId = json.optString("deviceId"),
                        expiresAt = PERMANENT_TOMBSTONE_EXPIRY,
                    ),
                )
            } finally {
                temp.delete()
            }
        }
    }

    private suspend fun applyRemoteChanges(
        token: String,
        remote: Map<String, DriveObject>,
        initialMergeComplete: Boolean,
    ) {
        val dirty = syncDao.pending().flatMap(::keysForMutation).toSet()
        val localStates = syncDao.allObjectStates().associateBy { it.objectKey }
        val candidates = remote.filter { (key, value) ->
            !key.startsWith("tombstones/") && key !in dirty &&
                (!initialMergeComplete || value.modifiedAt > (localStates[key]?.remoteModifiedAt ?: 0))
        }

        // Restore immutable sources before applying dependent progress and bookmarks.
        candidates.filterKeys { it.endsWith("/metadata") && it.startsWith("books/") }.forEach { (key, metadata) ->
            val uuid = key.split('/').getOrNull(1) ?: return@forEach
            val sourceInfo = remote["books/$uuid/source"] ?: return@forEach
            if (!books.bookExists(uuid)) {
                val metaFile = tempFile("book-meta")
                val sourceFile = tempFile("book-source")
                try {
                    drive.download(token, metadata.id, metaFile)
                    drive.download(token, sourceInfo.id, sourceFile)
                    val book = parseBook(JSONObject(metaFile.readText()))
                    mutations.withoutRecording { bookRepository.restoreSyncedBook(book, sourceFile.absolutePath) }
                } finally {
                    metaFile.delete(); sourceFile.delete()
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
                } finally { temp.delete() }
            }
            rememberRemote(key, metadata)
            rememberRemote("books/$uuid/source", sourceInfo)
        }

        candidates.forEach { (key, info) ->
            when {
                key.startsWith("progress/") -> applyRemoteProgress(token, info)
                key.startsWith("bookmarks/") -> applyRemoteBookmarks(token, info)
                key == "settings/global" -> applyRemoteSettings(token, info)
                key.startsWith("sessions/") -> applyRemoteSession(token, info)
                key.startsWith("fonts/") && key.endsWith("/metadata") -> {
                    val uuid = key.split('/').getOrNull(1) ?: return@forEach
                    remote["fonts/$uuid/source"]?.let { applyRemoteFont(token, info, it) }
                }
            }
            rememberRemote(key, info)
        }
    }

    private suspend fun applyRemoteProgress(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        val bookUuid = json.getString("bookUuid")
        if (!books.bookExists(bookUuid)) return@withJsonDownload
        val chapterKey = json.optString("chapterKey")
        val chapter = books.getChapterByKey(bookUuid, chapterKey)
            ?: books.getChapter(bookUuid, json.optInt("chapterIndex"))
            ?: return@withJsonDownload
        val remoteTime = json.optLong("updatedTime")
        val local = books.getProgress(bookUuid)
        if (local != null && local.updatedTime > remoteTime) return@withJsonDownload
        mutations.withoutRecording {
            bookRepository.saveProgress(
                ReadingProgress(
                    bookUuid = bookUuid,
                    chapterId = chapter.id,
                    position = json.optInt("paragraphIndex"),
                    offset = json.optInt("charOffset"),
                    updatedTime = remoteTime,
                    fraction = json.optDouble("progression").toFloat(),
                    chapterKey = chapter.chapterKey,
                    paragraphIndex = json.optInt("paragraphIndex"),
                    charOffset = json.optInt("charOffset"),
                    quoteAnchor = json.optString("quoteAnchor"),
                ),
            )
        }
    }

    private suspend fun applyRemoteBookmarks(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        val bookUuid = json.getString("bookUuid")
        if (!books.bookExists(bookUuid)) return@withJsonDownload
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

    private suspend fun applyRemoteSettings(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        val remote = jsonToSettings(json.getJSONObject("reader"))
        val goal = json.optInt("readingGoalMinutes", 30)
        mutations.withoutRecording {
            settingsRepository.update { remote }
            settingsRepository.setReadingGoalMinutes(goal)
        }
    }

    private suspend fun applyRemoteSession(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
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

    private suspend fun applyRemoteFont(token: String, metadata: DriveObject, source: DriveObject) {
        val uuid = metadata.objectKey.split('/').getOrNull(1) ?: return
        if (fonts.getFont(uuid) != null || !preferences.current().syncFonts) return
        val metaFile = tempFile("font-meta")
        val sourceFile = File(context.filesDir, "fonts/$uuid.ttf")
        try {
            drive.download(token, metadata.id, metaFile)
            drive.download(token, source.id, sourceFile)
            val json = JSONObject(metaFile.readText())
            fonts.insert(UserFontEntity(uuid, json.optString("name", "云端字体"), sourceFile.absolutePath, json.optLong("createdTime")))
        } catch (error: Throwable) {
            sourceFile.delete()
            throw error
        } finally { metaFile.delete() }
    }

    private suspend fun settingsJson(): JSONObject = JSONObject()
        .put("schema", 1)
        .put("updatedAt", System.currentTimeMillis())
        .put("reader", settingsToJson(settingsRepository.settings.first()))
        .put("readingGoalMinutes", settingsRepository.readingGoalMinutes.first())

    private fun bookMetadataJson(book: BookEntity) = JSONObject()
        .put("schema", 1).put("uuid", book.uuid).put("title", book.title).put("author", book.author)
        .put("description", book.description).put("format", book.format).put("createdTime", book.createdTime)
        .put("contentHash", book.contentHash).put("category", book.category)

    private fun progressJson(progress: ReadingProgressEntity, chapterKey: String) = JSONObject()
        .put("schema", 1).put("bookUuid", progress.bookUuid).put("chapterKey", chapterKey)
        .put("paragraphIndex", progress.paragraphIndex).put("charOffset", progress.charOffset)
        .put("progression", progress.fraction).put("quoteAnchor", progress.quoteAnchor)
        .put("updatedTime", progress.updatedTime)

    private fun bookmarksJson(bookUuid: String, values: List<BookmarkRow>, chapters: Map<Long, ChapterEntity>): JSONObject = JSONObject()
        .put("schema", 1)
        .put("bookUuid", bookUuid)
        .put("updatedAt", System.currentTimeMillis())
        .put("items", JSONArray().apply { values.forEach { value ->
            put(JSONObject().put("uuid", value.uuid).put("chapterKey", chapters[value.chapterId]?.chapterKey.orEmpty())
                .put("chapterIndex", value.chapterIndex).put("paragraphIndex", value.position)
                .put("preview", value.preview).put("createdTime", value.createdTime))
        } })

    private fun sessionJson(value: ReadingSessionEntity) = JSONObject()
        .put("schema", 1).put("uuid", value.syncUuid).put("bookUuid", value.bookUuid)
        .put("startedTime", value.startedTime).put("durationMillis", value.durationMillis).put("epochDay", value.epochDay)

    private fun fontJson(value: UserFontEntity) = JSONObject()
        .put("schema", 1).put("uuid", value.uuid).put("name", value.name).put("createdTime", value.createdTime)

    private fun settingsToJson(value: ReaderSettings) = JSONObject()
        .put("fontSize", value.fontSize).put("lineHeight", value.lineHeight).put("letterSpacing", value.letterSpacing)
        .put("margin", value.margin).put("theme", value.theme.name).put("pageMode", value.pageMode.name)
        .put("customThemeEnabled", value.customThemeEnabled).put("customDayTheme", customThemeJson(value.customDayTheme))
        .put("customNightTheme", customThemeJson(value.customNightTheme)).put("fontUuid", value.fontUuid)
        .put("appColorTheme", value.appColorTheme.name).put("appUiStyle", value.appUiStyle.name)
        .put("showStatusBar", value.showStatusBar).put("showPageNumber", value.showPageNumber)
        .put("volumeKeyPageTurn", value.volumeKeyPageTurn).put("keepScreenOn", value.keepScreenOn)
        .put("showChapterTitle", value.showChapterTitle)

    private fun jsonToSettings(value: JSONObject) = ReaderSettings(
        fontSize = value.optDouble("fontSize", 19.0).toFloat(),
        lineHeight = value.optDouble("lineHeight", 1.72).toFloat(),
        letterSpacing = value.optDouble("letterSpacing", .01).toFloat(),
        margin = value.optDouble("margin", 24.0).toFloat(),
        theme = enumValue(value, "theme", ReaderTheme.SYSTEM),
        pageMode = enumValue(value, "pageMode", PageMode.SCROLL),
        customThemeEnabled = value.optBoolean("customThemeEnabled"),
        customDayTheme = jsonToCustomTheme(value.optJSONObject("customDayTheme"), CustomReaderTheme()),
        customNightTheme = jsonToCustomTheme(value.optJSONObject("customNightTheme"), ReaderSettings().customNightTheme),
        fontUuid = value.optString("fontUuid").takeIf { it.isNotBlank() && it != "null" },
        appColorTheme = enumValue(value, "appColorTheme", AppColorTheme.DEFAULT),
        appUiStyle = enumValue(value, "appUiStyle", AppUiStyle.MATERIAL),
        showStatusBar = value.optBoolean("showStatusBar", true),
        showPageNumber = value.optBoolean("showPageNumber", true),
        volumeKeyPageTurn = value.optBoolean("volumeKeyPageTurn"),
        keepScreenOn = value.optBoolean("keepScreenOn", true),
        showChapterTitle = value.optBoolean("showChapterTitle", true),
    )

    private fun customThemeJson(value: CustomReaderTheme) = JSONObject()
        .put("background", value.backgroundHex).put("body", value.bodyHex).put("title", value.titleHex).put("accent", value.accentHex)

    private fun jsonToCustomTheme(value: JSONObject?, fallback: CustomReaderTheme) = value?.let {
        CustomReaderTheme(it.optString("background", fallback.backgroundHex), it.optString("body", fallback.bodyHex),
            it.optString("title", fallback.titleHex), it.optString("accent", fallback.accentHex))
    } ?: fallback

    private inline fun <reified T : Enum<T>> enumValue(json: JSONObject, key: String, fallback: T): T =
        runCatching { enumValueOf<T>(json.optString(key)) }.getOrDefault(fallback)

    private fun parseBook(json: JSONObject) = SyncedBook(
        uuid = json.getString("uuid"), title = json.optString("title", "未命名书籍"),
        author = json.optString("author", "未知作者"), description = json.optString("description"),
        format = enumValue(json, "format", BookFormat.TXT), createdTime = json.optLong("createdTime"),
        contentHash = json.getString("contentHash"), category = json.optString("category", "未分类"),
    )

    private fun jsonObject(key: String, json: JSONObject): LocalCloudObject {
        val file = tempFile("payload").apply { writeText(json.toString()) }
        return LocalCloudObject(key, "${key.replace('/', '-')}.json", "application/json", file, true)
    }

    private suspend fun withJsonDownload(token: String, info: DriveObject, block: suspend (JSONObject) -> Unit) {
        val file = tempFile("json")
        try { drive.download(token, info.id, file); block(JSONObject(file.readText())) } finally { file.delete() }
    }

    private suspend fun rememberRemote(key: String, value: DriveObject) {
        val previous = syncDao.objectState(key)
        syncDao.upsertObjectState(
            SyncObjectStateEntity(key, value.id, previous?.localHash, previous?.localChangedAt ?: 0, value.modifiedAt, value.version),
        )
    }

    private fun keysForMutation(value: SyncOutboxEntity): List<String> = when (SyncEntityType.valueOf(value.entityType)) {
        SyncEntityType.BOOK -> listOf("books/${value.entityId}/metadata", "books/${value.entityId}/source")
        SyncEntityType.PROGRESS -> listOf("progress/${value.entityId}")
        SyncEntityType.BOOKMARKS -> listOf("bookmarks/${value.entityId}")
        SyncEntityType.SETTINGS -> listOf("settings/global")
        SyncEntityType.SESSION -> listOf("sessions/${value.entityId}")
        SyncEntityType.FONT -> listOf("fonts/${value.entityId}/metadata", "fonts/${value.entityId}/source")
    }

    private fun tempFile(prefix: String) = File(context.cacheDir, "cloud-sync/$prefix-${UUID.randomUUID()}")
        .also { it.parentFile?.mkdirs() }

    private fun File.sha256(): String = inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun shouldDeferLargePayload(mutation: SyncOutboxEntity): Boolean {
        val type = SyncEntityType.valueOf(mutation.entityType)
        val hasLargePayload = when (type) {
            SyncEntityType.BOOK -> preferences.current().syncOriginalFiles && books.getBook(mutation.entityId)?.storagePath?.let(::File)?.isFile == true
            SyncEntityType.FONT -> preferences.current().syncFonts && fonts.getFont(mutation.entityId)?.filePath?.let(::File)?.isFile == true
            else -> false
        }
        if (!hasLargePayload || !preferences.current().wifiOnlyForLargeFiles) return false
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
    }

    private data class BookMarkOwner(val bookUuid: String) {
        companion object { fun from(value: BookmarkEntity) = BookMarkOwner(value.bookUuid) }
    }

    private class AuthorizationRequiredException(message: String) : Exception(message)

    private companion object {
        const val PERMANENT_TOMBSTONE_EXPIRY = Long.MAX_VALUE
    }
}
