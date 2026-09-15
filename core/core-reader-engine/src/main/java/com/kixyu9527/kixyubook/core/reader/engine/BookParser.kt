package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.BookFormat
import java.io.File

interface BookParser {
    val format: BookFormat

    /**
     * @param fallbackTitle used verbatim when the file itself contains no title.
     * @param sourceName the original display/file name, used only as a metadata source. Callers
     * that no longer have it must pass an empty string: a book title or an import URI is not a
     * file name and would otherwise be parsed into a bogus author or a truncated title.
     */
    fun readMetadata(
        file: File,
        fallbackTitle: String,
        sourceName: String = fallbackTitle,
        rules: List<LocalMetadata.FilenameRule> = emptyList(),
        specs: List<com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec> = emptyList(),
    ): DocumentMetadata

    suspend fun readChapters(file: File, emit: suspend (DocumentChapter) -> Unit)
}

class BookParserRegistry(
    private val parsers: List<BookParser> = listOf(TxtBookParser(), EpubBookParser()),
) {
    fun parserFor(format: BookFormat): BookParser = parsers.firstOrNull { it.format == format }
        ?: error("暂不支持 ${format.name} 格式")
}
