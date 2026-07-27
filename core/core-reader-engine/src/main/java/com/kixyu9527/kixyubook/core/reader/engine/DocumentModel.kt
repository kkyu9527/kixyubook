package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.Paragraph

data class DocumentMetadata(
    val identityHint: String? = null,
    val title: String,
    val author: String,
    val description: String = "",
    val coverBytes: ByteArray? = null,
    val coverExtension: String = "jpg",
)

data class DocumentChapter(val title: String, val paragraphs: List<String>)

data class ReaderDocument(
    val bookUuid: String,
    val format: BookFormat,
    val chapters: List<DocumentChapterRef>,
)

data class DocumentChapterRef(val id: Long, val title: String, val index: Int)

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
    return paragraphs.dropWhile { paragraph ->
        val candidate = paragraph.text.normalizedReaderHeading()
        candidate == fullTitle || candidate == chapterTitle
    }
}

private fun String.normalizedReaderHeading(): String = trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')

private val CHAPTER_ORDINAL_PATTERN = Regex(
    "^(?:正文\\s+)?((?:第\\s*[\\p{N}〇零一二三四五六七八九十百千万两]+\\s*[章节回话集幕])|" +
        "(?:(?i:chapter)\\s*[\\p{L}\\p{N}]+))" +
        "\\s*(?:[：:、.．\\-—]\\s*)?(.*)$",
)

data class ReaderPosition(val chapterIndex: Int, val paragraphIndex: Int, val characterOffset: Int = 0)

data class ReaderLayoutSpec(
    val viewportWidthDp: Float,
    val viewportHeightDp: Float,
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val letterSpacingEm: Float,
    val horizontalMarginDp: Float,
)

data class DocumentBlock(
    val paragraphIndex: Int,
    val fullText: String,
    val visibleText: String,
    val continuation: Boolean,
    /** Paragraph spacing is omitted when the block already ends at a page boundary. */
    val bottomSpacing: Boolean = true,
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
