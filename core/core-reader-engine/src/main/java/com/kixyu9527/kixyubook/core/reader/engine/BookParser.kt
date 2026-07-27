package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.BookFormat
import java.io.File

interface BookParser {
    val format: BookFormat
    fun readMetadata(file: File, fallbackTitle: String): DocumentMetadata
    suspend fun readChapters(file: File, emit: suspend (DocumentChapter) -> Unit)
}

class BookParserRegistry(
    private val parsers: List<BookParser> = listOf(TxtBookParser(), EpubBookParser()),
) {
    fun parserFor(format: BookFormat): BookParser = parsers.firstOrNull { it.format == format }
        ?: error("暂不支持 ${format.name} 格式")
}
