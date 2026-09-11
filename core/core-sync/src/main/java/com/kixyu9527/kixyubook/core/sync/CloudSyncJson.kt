package com.kixyu9527.kixyubook.core.sync
import com.kixyu9527.kixyubook.core.common.configuration.*

import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.database.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


internal fun bookMetadataJson(book: BookEntity) = JSONObject()
    .put("schema", 1).put("uuid", book.uuid).put("title", book.title).put("author", book.author)
    .put("description", book.description).put("format", book.format).put("createdTime", book.createdTime)
    .put("contentHash", book.contentHash).put("category", book.category)

internal fun progressJson(progress: ReadingProgressEntity, chapterKey: String) = JSONObject()
    .put("schema", 1).put("bookUuid", progress.bookUuid).put("chapterKey", chapterKey)
    .put("paragraphIndex", progress.paragraphIndex).put("charOffset", progress.charOffset)
    .put("progression", progress.fraction).put("quoteAnchor", progress.quoteAnchor)
    .put("updatedTime", progress.updatedTime)

internal fun bookmarksJson(bookUuid: String, values: List<BookmarkRow>, chapters: Map<Long, ChapterEntity>): JSONObject = JSONObject()
    .put("schema", 1)
    .put("bookUuid", bookUuid)
    .put("updatedAt", System.currentTimeMillis())
    .put("items", JSONArray().apply { values.forEach { value ->
        put(JSONObject().put("uuid", value.uuid).put("chapterKey", chapters[value.chapterId]?.chapterKey.orEmpty())
            .put("chapterIndex", value.chapterIndex).put("paragraphIndex", value.position)
            .put("preview", value.preview).put("createdTime", value.createdTime))
    } })

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
)
