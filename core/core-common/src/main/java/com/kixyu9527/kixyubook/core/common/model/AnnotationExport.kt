package com.kixyu9527.kixyubook.core.common.model

enum class AnnotationExportFormat { MARKDOWN, PLAIN_TEXT }

/** Streams one annotation at a time; preserves quotes and notes without emitting active HTML. */
fun Appendable.writeAnnotationExport(
    title: String,
    author: String,
    chapterTitles: Map<Int, String>,
    annotations: List<ReaderAnnotation>,
    format: AnnotationExportFormat,
) {
    val markdown = format == AnnotationExportFormat.MARKDOWN
    fun literal(value: String): String = if (!markdown) value else buildString {
        value.forEach { char ->
            when (char) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '\\', '`', '*', '_', '[', ']', '#', '!', '|', '~' -> append('\\').append(char)
                else -> append(char)
            }
        }
    }
    appendLine((if (markdown) "# " else "") + literal(title))
    if (author.isNotBlank()) appendLine(literal(author))
    var chapter: Int? = null
    annotations.sortedWith(compareBy(ReaderAnnotation::chapterIndex, ReaderAnnotation::paragraphIndex, ReaderAnnotation::startOffset)).forEach { annotation ->
        if (chapter != annotation.chapterIndex) {
            chapter = annotation.chapterIndex
            appendLine()
            appendLine((if (markdown) "## " else "") + literal(chapterTitles[chapter].orEmpty().ifBlank { "${annotation.chapterIndex + 1}" }))
        }
        appendLine()
        annotation.exactText.lineSequence().forEach { appendLine((if (markdown) "> " else "") + literal(it)) }
        if (annotation.note.isNotBlank()) { appendLine(); appendLine(literal(annotation.note)) }
        appendLine()
        appendLine("---")
    }
}
