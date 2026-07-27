package com.kixyu9527.kixyubook.core.common.model

enum class BookFormat { TXT, EPUB, PDF }

data class Book(
    val id: Long,
    val title: String,
    val author: String,
    val coverPath: String?,
    val filePath: String,
    val format: BookFormat,
    val addedTime: Long,
)

data class Chapter(
    val id: Long,
    val bookId: Long,
    val title: String,
    val index: Int,
)

data class Paragraph(
    val id: Long,
    val chapterId: Long,
    val index: Int,
    val text: String,
)

data class ReadingProgress(
    val bookId: Long,
    val chapterId: Long,
    val position: Int,
    val updatedTime: Long,
    val fraction: Float = 0f,
)

data class LibraryBook(
    val book: Book,
    val progress: ReadingProgress?,
)

enum class ReaderTheme { DAY, NIGHT, EYE_CARE }
enum class PageMode { SCROLL, PAGED }

data class ReaderSettings(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.72f,
    val letterSpacing: Float = 0.01f,
    val margin: Float = 24f,
    val theme: ReaderTheme = ReaderTheme.DAY,
    val pageMode: PageMode = PageMode.SCROLL,
)

data class ChapterContent(
    val chapter: Chapter,
    val paragraphs: List<Paragraph>,
)

data class ImportSummary(
    val importedCount: Int,
    val failures: List<String> = emptyList(),
)
