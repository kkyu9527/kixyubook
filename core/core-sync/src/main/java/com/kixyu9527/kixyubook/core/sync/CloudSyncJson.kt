package com.kixyu9527.kixyubook.core.sync
import com.kixyu9527.kixyubook.core.common.configuration.*

import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.database.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


internal fun bookMetadataJson(book: BookEntity) = JSONObject()
    .put("schema", BOOK_METADATA_SCHEMA).put("uuid", book.uuid)
    .put("title", book.title).put("author", book.author)
    .put("description", book.description).put("format", book.format).put("createdTime", book.createdTime)
    .put("contentHash", book.contentHash).put("category", book.category)
    // Imported-name and publisher sorting/series data must survive a cross-device restore.
    .put("originalDisplayName", book.originalDisplayName)
    .put("titleSort", book.titleSort)
    .put("seriesName", book.seriesName)
    .put("seriesIndex", book.seriesIndex ?: JSONObject.NULL)
    // Field values travel with their "edited by hand" ownership; a new device must be able to keep
    // protecting a manual edit after it restores the library.
    .put("userEditedTitle", book.userEditedTitle)
    .put("userEditedAuthor", book.userEditedAuthor)
    .put("userEditedDescription", book.userEditedDescription)

internal fun progressJson(progress: ReadingProgressEntity, chapterKey: String) = JSONObject()
    .put("schema", 1).put("bookUuid", progress.bookUuid).put("chapterKey", chapterKey)
    .put("paragraphIndex", progress.paragraphIndex).put("charOffset", progress.charOffset)
    .put("progression", progress.fraction).put("quoteAnchor", progress.quoteAnchor)
    .put("updatedTime", progress.updatedTime)

internal const val BOOKMARK_STATUS_LOCATED = "LOCATED"
internal const val BOOKMARK_STATUS_PENDING = "PENDING"

/**
 * A bookmark whose paragraph could not be relocated after a TXT reparse travels with status
 * PENDING and its anchor text, so other devices keep the data instead of losing it. Located
 * bookmarks keep the chapter anchor and are inserted directly.
 */
internal fun bookmarksJson(
    bookUuid: String,
    values: List<BookmarkRow>,
    pending: List<PendingBookmarkEntity>,
    chapters: Map<Long, ChapterEntity>,
): JSONObject = JSONObject()
    .put("schema", 2)
    .put("bookUuid", bookUuid)
    .put("updatedAt", System.currentTimeMillis())
    .put("items", JSONArray().apply {
        values.forEach { value ->
            put(JSONObject().put("uuid", value.uuid).put("status", BOOKMARK_STATUS_LOCATED)
                .put("chapterKey", chapters[value.chapterId]?.chapterKey.orEmpty())
                .put("chapterIndex", value.chapterIndex).put("paragraphIndex", value.position)
                .put("preview", value.preview).put("createdTime", value.createdTime))
        }
        pending.forEach { value ->
            put(JSONObject().put("uuid", value.uuid).put("status", BOOKMARK_STATUS_PENDING)
                .put("anchorText", value.anchorText)
                .put("preview", value.preview).put("createdTime", value.createdTime))
        }
    })

internal fun sessionJson(value: ReadingSessionEntity) = JSONObject()
    .put("schema", 1).put("uuid", value.syncUuid).put("bookUuid", value.bookUuid)
    .put("startedTime", value.startedTime).put("durationMillis", value.durationMillis).put("epochDay", value.epochDay)

internal fun fontJson(value: UserFontEntity) = JSONObject()
    .put("schema", 1).put("uuid", value.uuid).put("name", value.name).put("createdTime", value.createdTime)

internal fun correctionJson(value: TextCorrectionEntity) = JSONObject()
    .put("schema", 1).put("uuid", value.uuid).put("bookUuid", value.bookUuid)
    .put("sourceContentHash", value.sourceContentHash).put("chapterKey", value.chapterKey)
    .put("chapterIndex", value.chapterIndex).put("paragraphIndex", value.paragraphIndex)
    .put("startOffset", value.startOffset).put("endOffset", value.endOffset)
    .put("exactText", value.exactText).put("prefixText", value.prefixText).put("suffixText", value.suffixText)
    .put("replacementText", value.replacementText).put("status", value.status)
    .put("createdTime", value.createdTime).put("updatedTime", value.updatedTime).put("deviceId", value.deviceId)

internal fun parseCorrection(json: JSONObject) = TextCorrection(
    uuid = json.getString("uuid"), bookUuid = json.getString("bookUuid"),
    sourceContentHash = json.optString("sourceContentHash"), chapterKey = json.optString("chapterKey"),
    chapterIndex = json.optInt("chapterIndex"), paragraphIndex = json.optInt("paragraphIndex"),
    startOffset = json.optInt("startOffset"), endOffset = json.optInt("endOffset"),
    exactText = json.optString("exactText"), prefixText = json.optString("prefixText"),
    suffixText = json.optString("suffixText"), replacementText = json.optString("replacementText"),
    status = enumValue(json, "status", TextCorrectionStatus.UNRESOLVED),
    createdTime = json.optLong("createdTime"), updatedTime = json.optLong("updatedTime"),
    deviceId = json.optString("deviceId"),
)

internal fun annotationJson(value: ReaderAnnotationEntity) = JSONObject()
    .put("schema", 1).put("uuid", value.uuid).put("bookUuid", value.bookUuid)
    .put("sourceContentHash", value.sourceContentHash).put("chapterKey", value.chapterKey)
    .put("chapterIndex", value.chapterIndex).put("paragraphIndex", value.paragraphIndex)
    .put("startOffset", value.startOffset).put("endOffset", value.endOffset)
    .put("exactText", value.exactText).put("style", value.style).put("note", value.note)
    .put("createdTime", value.createdTime).put("updatedTime", value.updatedTime).put("deviceId", value.deviceId)

internal fun parseAnnotation(json: JSONObject) = ReaderAnnotation(
    uuid = json.getString("uuid"), bookUuid = json.getString("bookUuid"),
    sourceContentHash = json.optString("sourceContentHash"), chapterKey = json.optString("chapterKey"),
    chapterIndex = json.optInt("chapterIndex"), paragraphIndex = json.optInt("paragraphIndex"),
    startOffset = json.optInt("startOffset"), endOffset = json.optInt("endOffset"),
    exactText = json.optString("exactText"),
    style = enumValue(json, "style", ReaderAnnotationStyle.HIGHLIGHT),
    note = json.optString("note"), createdTime = json.optLong("createdTime"),
    updatedTime = json.optLong("updatedTime"), deviceId = json.optString("deviceId"),
)

internal fun parseBook(json: JSONObject) = SyncedBook(
    uuid = json.getString("uuid"), title = json.optString("title", "未命名书籍"),
    author = json.optString("author", "未知作者"), description = json.optString("description"),
    format = enumValue(json, "format", BookFormat.TXT), createdTime = json.optLong("createdTime"),
    contentHash = json.getString("contentHash"), category = json.optString("category", "未分类"),
    originalDisplayName = json.optString("originalDisplayName"),
    titleSort = json.optString("titleSort"),
    seriesName = json.optString("seriesName"),
    seriesIndex = if (json.isNull("seriesIndex")) null else json.optDouble("seriesIndex"),
    metadataOwnership = if (json.optInt("schema", 1) >= BOOK_METADATA_SCHEMA) {
        BookMetadataOwnership(
            title = json.optBoolean("userEditedTitle", false),
            author = json.optBoolean("userEditedAuthor", false),
            description = json.optBoolean("userEditedDescription", false),
        )
    } else {
        null
    },
)

internal const val BOOK_METADATA_SCHEMA = 3
