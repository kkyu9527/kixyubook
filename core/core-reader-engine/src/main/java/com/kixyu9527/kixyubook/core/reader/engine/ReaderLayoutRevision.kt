package com.kixyu9527.kixyubook.core.reader.engine

/** Layout content identity, stable across processes and independent of Room IDs/user annotations. */
internal fun ReaderChapter.layoutRevision(): Int = paragraphs.fold(1) { hash, paragraph ->
    var revision = 31 * hash + paragraph.text.hashCode()
    revision = 31 * revision + paragraph.kind.ordinal
    revision = 31 * revision + paragraph.resourcePath.hashCode()
    revision = 31 * revision + paragraph.intrinsicWidth
    revision = 31 * revision + paragraph.intrinsicHeight
    paragraph.spans.forEach { span ->
        revision = 31 * revision + span.start
        revision = 31 * revision + span.end
        revision = 31 * revision + span.styles.fold(0) { mask, style -> mask or (1 shl style.ordinal) }
        revision = 31 * revision + (span.foreground?.ordinal ?: -1)
        revision = 31 * revision + (span.background?.ordinal ?: -1)
        revision = 31 * revision + span.linkTarget.hashCode()
    }
    revision = 31 * revision + paragraph.isFullPageImage.hashCode()
    31 * revision + paragraph.cropImageToFill.hashCode()
}
