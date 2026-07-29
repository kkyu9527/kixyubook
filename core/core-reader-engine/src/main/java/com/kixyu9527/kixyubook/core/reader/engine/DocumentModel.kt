package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.Paragraph
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan

data class DocumentMetadata(
    val identityHint: String? = null,
    val title: String,
    val author: String,
    val description: String = "",
    val coverBytes: ByteArray? = null,
    val coverExtension: String = "jpg",
)

data class DocumentImage(
    /** Position among text and image blocks in the XHTML reading order. */
    val contentIndex: Int,
    val resourcePath: String,
    val mediaType: String,
    val altText: String = "",
    val intrinsicWidth: Int = 0,
    val intrinsicHeight: Int = 0,
)

data class DocumentChapter(
    val title: String,
    val paragraphs: List<String>,
    val images: List<DocumentImage> = emptyList(),
    val paragraphSpans: List<List<ReaderTextSpan>> = emptyList(),
)

/** Lightweight table-of-contents entry whose index points to the source document spine. */
data class DocumentChapterOutline(
    val sourceIndex: Int,
    val title: String,
)

data class ReaderChapter(
    val id: Long,
    val bookUuid: String,
    val title: String,
    val index: Int,
    val paragraphs: List<Paragraph>,
)

data class ReaderChapterHeading(val ordinal: String?, val name: String)

fun splitReaderChapterHeading(rawTitle: String): ReaderChapterHeading {
    val title = rawTitle.substringAfterLast('·').trim()
    val match = CHAPTER_ORDINAL_PATTERN.matchEntire(title)
        ?: return ReaderChapterHeading(ordinal = null, name = title)
    val rawOrdinal = match.groupValues[1].trim()
    val ordinal = if (rawOrdinal.startsWith('第')) {
        rawOrdinal.replace(Regex("\\s+"), "")
    } else {
        rawOrdinal.replace(Regex("^(?i:chapter)\\s*"), "Chapter ")
    }
    return ReaderChapterHeading(
        ordinal = ordinal,
        name = match.groupValues[2].trim(),
    )
}

fun ReaderChapter.contentParagraphs(): List<Paragraph> {
    val fullTitle = title.normalizedReaderHeading()
    val chapterTitle = title.substringAfterLast('·').normalizedReaderHeading()
    val firstTextIndex = paragraphs.indexOfFirst { it.kind == ParagraphKind.TEXT }
    if (firstTextIndex < 0) return paragraphs
    val candidate = paragraphs[firstTextIndex].text.normalizedReaderHeading()
    return if (candidate == fullTitle || candidate == chapterTitle) {
        paragraphs.filterIndexed { index, _ -> index != firstTextIndex }
    } else {
        paragraphs
    }
}

private fun String.normalizedReaderHeading(): String = trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')

private val CHAPTER_ORDINAL_PATTERN = Regex(
    "^(?:正文\\s+)?((?:第\\s*[\\p{N}〇零一二三四五六七八九十百千万两]+\\s*[章节回话集幕])|" +
        "(?:(?i:chapter)\\s*[\\p{L}\\p{N}]+))" +
        "\\s*(?:[：:、.．\\-—]\\s*)?(.*)$",
)

data class ReaderLayoutSpec(
    val viewportWidthDp: Float,
    val viewportHeightDp: Float,
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val letterSpacingEm: Float,
    val horizontalMarginDp: Float,
)

enum class ReaderImageSizeClass { COMPACT, ILLUSTRATION, PORTRAIT, WIDE }

data class ReaderImageLayout(
    val widthDp: Float,
    val heightDp: Float,
    val sizeClass: ReaderImageSizeClass,
)

/**
 * Normalizes publisher-defined image dimensions into four stable reading
 * sizes while always retaining the original aspect ratio.
 */
fun standardizedReaderImageLayout(
    availableWidthDp: Float,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
): ReaderImageLayout {
    val safeWidth = availableWidthDp.coerceAtLeast(120f)
    val aspect = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
        intrinsicWidth.toFloat() / intrinsicHeight
    } else {
        4f / 3f
    }.coerceIn(0.35f, 3.2f)
    val sizeClass = when {
        intrinsicWidth in 1..360 && intrinsicHeight in 1..360 -> ReaderImageSizeClass.COMPACT
        aspect >= 1.45f -> ReaderImageSizeClass.WIDE
        aspect <= 0.82f -> ReaderImageSizeClass.PORTRAIT
        else -> ReaderImageSizeClass.ILLUSTRATION
    }
    val widthFraction: Float
    val maxHeight: Float
    when (sizeClass) {
        ReaderImageSizeClass.COMPACT -> { widthFraction = .48f; maxHeight = 180f }
        ReaderImageSizeClass.WIDE -> { widthFraction = 1f; maxHeight = 260f }
        ReaderImageSizeClass.PORTRAIT -> { widthFraction = .72f; maxHeight = 420f }
        ReaderImageSizeClass.ILLUSTRATION -> { widthFraction = .84f; maxHeight = 340f }
    }
    var width = safeWidth * widthFraction
    var height = width / aspect
    if (height > maxHeight) {
        height = maxHeight
        width = height * aspect
    }
    return ReaderImageLayout(width.coerceAtMost(safeWidth), height, sizeClass)
}

data class DocumentBlock(
    val paragraphIndex: Int,
    val fullText: String,
    val visibleText: String,
    val continuation: Boolean,
    /** Paragraph spacing is omitted when the block already ends at a page boundary. */
    val bottomSpacing: Boolean = true,
    val kind: ParagraphKind = ParagraphKind.TEXT,
    val resourcePath: String? = null,
    val mediaType: String? = null,
    val intrinsicWidth: Int = 0,
    val intrinsicHeight: Int = 0,
    val imageWidthDp: Float = 0f,
    val imageHeightDp: Float = 0f,
    val spans: List<ReaderTextSpan> = emptyList(),
    /** UTF-16 character offset of [visibleText] inside [fullText]. */
    val textStart: Int = 0,
)

data class ReaderPage(
    val index: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val isChapterOpening: Boolean,
    val blocks: List<DocumentBlock>,
) {
    val startParagraph: Int get() = blocks.firstOrNull()?.paragraphIndex ?: 0
}
