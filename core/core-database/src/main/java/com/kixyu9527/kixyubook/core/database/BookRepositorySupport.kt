package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.database.entity.*
import com.kixyu9527.kixyubook.core.reader.engine.BookParser
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal data class ChapterCacheKey(val bookUuid: String, val chapterIndex: Int)

internal fun Throwable.diagnosticReason(): String =
    generateSequence(this) { it.cause }
        .mapNotNull { cause -> cause.message?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
        ?: (this::class.qualifiedName ?: "未知错误")

internal data class ImportRegistration(
    val imports: List<RegisteredImport>,
    val duplicateCount: Int,
    val failures: List<String>,
)
internal data class RegisteredImport(
    val bookUuid: String,
    val displayName: String,
    val format: BookFormat,
    val source: File,
    val parser: BookParser,
)

internal const val CHAPTER_CACHE_SIZE = 6
internal const val SLOW_CHAPTER_LOAD_MS = 250L
internal const val IMPORT_CHAPTER_BATCH_SIZE = 32
internal const val IMPORT_INDEX_CONCURRENCY = 2
internal const val DERIVED_DATA_VERSION_PREFERENCES = "derived_data_versions"
internal const val KEY_TXT_PARSER_VERSION = "txt_parser_version"
internal const val TXT_PARSER_VERSION = 1

internal fun String.normalizedEpubIdentityTitle(): String =
    trim().replace(Regex("[\\s　]+"), " ").lowercase(Locale.ROOT)

internal fun String.shortDiagnosticId(): String = take(8)

internal fun BookEntity.toModel() = Book(uuid, title, author, description, coverPath, BookFormat.valueOf(format), originalPath, storagePath, createdTime, contentHash, category)
internal fun ChapterEntity.toModel() = Chapter(
    id,
    bookUuid,
    title.singleLineBookHeading(),
    chapterIndex,
    volumeTitle?.singleLineBookHeading(),
    volumeIndex,
    chapterKey,
)
internal fun ReadingProgressEntity.toModel() = ReadingProgress(
    bookUuid, chapterId, position, offset, updatedTime, fraction,
    chapterKey, paragraphIndex, charOffset, quoteAnchor,
)

internal fun stableChapterKey(bookUuid: String, index: Int, title: String): String {
    val input = "$bookUuid|$index|${title.singleLineBookHeading().lowercase()}"
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        .take(16).joinToString("") { "%02x".format(it) }
}
internal fun BookmarkRow.toModel() = Bookmark(
    uuid,
    bookUuid,
    chapterId,
    chapterTitle.singleLineBookHeading(),
    chapterIndex,
    position,
    preview,
    createdTime,
)
internal fun BookSearchResultRow.toModel() = BookSearchResult(
    chapterId,
    chapterTitle.singleLineBookHeading(),
    chapterIndex,
    paragraphIndex,
    text,
)

/**
 * EPUB image nodes are rehydrated from the immutable source archive when a
 * chapter is opened. Text keeps its persisted indices, so existing progress,
 * bookmarks and search results remain stable without duplicating image bytes.
 */
internal fun DocumentChapter.toReaderParagraphs(
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
                            isFullPageImage = image.isFullPage,
                            cropImageToFill = image.cropToFill,
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

internal fun File.pruneTo(retainedPaths: Set<String>) {
    listFiles().orEmpty().forEach { entry ->
        if (!entry.isFile || entry.absolutePath !in retainedPaths) entry.deleteRecursively()
    }
    delete()
}
