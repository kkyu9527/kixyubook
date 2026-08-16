package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.dao.TextCorrectionDao
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal class CloudSyncPayloadFactory(
    private val context: Context,
    private val books: BookDao,
    private val fonts: FontDao,
    private val corrections: TextCorrectionDao,
    private val settingsRepository: ReaderSettingsRepository,
    private val libraryPreferencesRepository: LibraryPreferencesRepository,
    private val readingReminders: ReadingReminderScheduler,
    private val preferences: SyncPreferencesStore,
) {
    suspend fun materialize(
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
        SyncEntityType.CORRECTION -> corrections.get(mutation.entityId)?.let { correction ->
            listOf(jsonObject("corrections/${correction.uuid}", correctionJson(correction)))
        }.orEmpty()
    }

    private suspend fun settingsJson(): JSONObject = settingsPayloadJson(
        reader = settingsRepository.settings.first(),
        readingGoalMinutes = settingsRepository.readingGoalMinutes.first(),
        library = libraryPreferencesRepository.preferences.first(),
        readingReminder = readingReminders.settings.first(),
    )


    fun jsonObject(key: String, json: JSONObject): LocalCloudObject {
        val file = tempFile("payload").apply { writeText(json.toString()) }
        return LocalCloudObject(key, "${key.replace('/', '-')}.json", "application/json", file, true)
    }

    private fun tempFile(prefix: String) = File(
        context.cacheDir,
        "cloud-sync/$prefix-${UUID.randomUUID()}",
    ).also { it.parentFile?.mkdirs() }
}
