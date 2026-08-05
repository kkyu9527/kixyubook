package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.runtime.Immutable
import com.kixyu9527.kixyubook.core.common.model.Paragraph
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import com.kixyu9527.kixyubook.core.common.model.singleLineBookHeading

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
    val isFullPage: Boolean = false,
    val cropToFill: Boolean = false,
)

data class DocumentChapter(
    val title: String,
    val paragraphs: List<String>,
    val images: List<DocumentImage> = emptyList(),
    val paragraphSpans: List<List<ReaderTextSpan>> = emptyList(),
    val volumeTitle: String? = null,
    val volumeIndex: Int? = null,
)

/** Lightweight table-of-contents entry whose index points to the source document spine. */
data class DocumentChapterOutline(
    val sourceIndex: Int,
    val title: String,
    val volumeTitle: String? = null,
    val volumeIndex: Int? = null,
)

@Immutable
data class ReaderChapter(
    val id: Long,
    val bookUuid: String,
    val title: String,
    val index: Int,
    val paragraphs: List<Paragraph>,
)

@Immutable
data class ReaderChapterHeading(val ordinal: String?, val name: String)

fun splitReaderChapterHeading(rawTitle: String): ReaderChapterHeading {
    val title = readerChapterTitle(rawTitle)
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
    val chapterTitle = readerChapterTitle(title).normalizedReaderHeading()
    val firstTextIndex = paragraphs.indexOfFirst { it.kind == ParagraphKind.TEXT }
    if (firstTextIndex < 0) return paragraphs
    val candidate = paragraphs[firstTextIndex].text.normalizedReaderHeading()
    return if (candidate == fullTitle || candidate == chapterTitle) {
        paragraphs.filterIndexed { index, _ -> index != firstTextIndex }
    } else {
        paragraphs
    }
}

private fun readerChapterTitle(rawTitle: String): String {
    val fullTitle = rawTitle.singleLineBookHeading()
    if (CHAPTER_ORDINAL_PATTERN.matches(fullTitle)) return fullTitle

    // Some legacy entries combine a volume and chapter as "卷名 · 第一章 标题". Only strip
    // that prefix when the suffix is independently a chapter heading; a middle dot may also be
    // part of the actual title or a transliterated name, for example "催眠钱宁·卢".
    val suffix = rawTitle.substringAfterLast('·').singleLineBookHeading()
    return suffix.takeIf { it != fullTitle && CHAPTER_ORDINAL_PATTERN.matches(it) } ?: fullTitle
}

private fun String.normalizedReaderHeading(): String = trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')

private val CHAPTER_ORDINAL_PATTERN = Regex(
    "^(?:正文\\s+)?((?:第\\s*[\\p{N}〇零一二三四五六七八九十百千万两]+\\s*[章节回话集幕])|" +
        "(?:(?i:chapter)\\s*[\\p{L}\\p{N}]+))" +
        "\\s*(?:[：:、.．\\-—]\\s*)?(.*)$",
)

@Immutable
data class ReaderLayoutSpec(
    val viewportWidthDp: Float,
    val viewportHeightDp: Float,
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val letterSpacingEm: Float,
    val horizontalMarginDp: Float,
)

enum class ReaderImageSizeClass { COMPACT, ILLUSTRATION, PORTRAIT, WIDE }

@Immutable
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

@Immutable
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
    val isFullPageImage: Boolean = false,
    val cropImageToFill: Boolean = false,
)

@Immutable
data class ReaderPage(
    val index: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val isChapterOpening: Boolean,
    val blocks: List<DocumentBlock>,
) {
    val startParagraph: Int get() = blocks.firstOrNull()?.paragraphIndex ?: 0
    val isFullPageImage: Boolean get() = blocks.singleOrNull()?.isFullPageImage == true
}

fun ReaderChapter.fullPageImageParagraph(): Paragraph? = contentParagraphs().singleOrNull()?.takeIf {
    it.kind == ParagraphKind.IMAGE && it.resourcePath != null && it.isFullPageImage
}
