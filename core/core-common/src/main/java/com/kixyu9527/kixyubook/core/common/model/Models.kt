package com.kixyu9527.kixyubook.core.common.model

enum class BookFormat { TXT, EPUB, MARKDOWN, PDF, COMIC }

data class Book(
    val uuid: String,
    val title: String,
    val author: String,
    val description: String,
    val coverPath: String?,
    val format: BookFormat,
    val originalPath: String,
    val storagePath: String,
    val createdTime: Long,
    val contentHash: String,
    val category: String = "未分类",
)

data class Chapter(val id: Long, val bookUuid: String, val title: String, val index: Int)

enum class ParagraphKind { TEXT, IMAGE }

enum class ReaderInlineStyle {
    BOLD,
    ITALIC,
    ACCENT,
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    MONOSPACE,
    SMALL_CAPS,
    SUPERSCRIPT,
    SUBSCRIPT,
}

/** Stable reader color roles. Publisher shades are classified into these roles before rendering. */
enum class ReaderSemanticColor {
    ACCENT,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    CYAN,
    BLUE,
    PURPLE,
    MAGENTA,
    NEUTRAL,
}

data class ReaderTextSpan(
    val start: Int,
    val end: Int,
    val styles: Set<ReaderInlineStyle>,
    val foreground: ReaderSemanticColor? = null,
    val background: ReaderSemanticColor? = null,
)

data class Paragraph(
    val id: Long,
    val chapterId: Long,
    val index: Int,
    val text: String,
    val kind: ParagraphKind = ParagraphKind.TEXT,
    /** Normalized entry path inside the source EPUB archive. */
    val resourcePath: String? = null,
    val mediaType: String? = null,
    val intrinsicWidth: Int = 0,
    val intrinsicHeight: Int = 0,
    /** EPUB inline semantics normalized independently from publisher colors and fonts. */
    val spans: List<ReaderTextSpan> = emptyList(),
)

data class ReadingProgress(
    val bookUuid: String,
    val chapterId: Long,
    val position: Int,
    val offset: Int = 0,
    val updatedTime: Long,
    val fraction: Float = 0f,
)

data class Bookmark(
    val uuid: String,
    val bookUuid: String,
    val chapterId: Long,
    val chapterTitle: String,
    val chapterIndex: Int,
    val position: Int,
    val preview: String,
    val createdTime: Long,
)

data class BookSearchResult(
    val chapterId: Long,
    val chapterTitle: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
)

data class LibraryBook(val book: Book, val progress: ReadingProgress?)

enum class ReaderTheme { SYSTEM, DAY, NIGHT }
enum class PageMode { SCROLL, PAGED }
enum class AppColorTheme { DEFAULT, DYNAMIC, SAGE, OCEAN, VIOLET, AMBER }
enum class AppUiStyle { MATERIAL, MIUIX }

data class CustomReaderTheme(
    val backgroundHex: String = "#F7F4EC",
    val bodyHex: String = "#292722",
    val titleHex: String = "#171713",
    val accentHex: String = "#52655A",
)

data class ReaderSettings(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.72f,
    val letterSpacing: Float = 0.01f,
    val margin: Float = 24f,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val pageMode: PageMode = PageMode.SCROLL,
    val customThemeEnabled: Boolean = false,
    val customDayTheme: CustomReaderTheme = CustomReaderTheme(),
    val customNightTheme: CustomReaderTheme = CustomReaderTheme(
        backgroundHex = "#11120F",
        bodyHex = "#D9D9D0",
        titleHex = "#F0F0E7",
        accentHex = "#B8CCBD",
    ),
    val fontUuid: String? = null,
    val appColorTheme: AppColorTheme = AppColorTheme.DEFAULT,
    val appUiStyle: AppUiStyle = AppUiStyle.MATERIAL,
    val showStatusBar: Boolean = true,
    val showPageNumber: Boolean = true,
    val volumeKeyPageTurn: Boolean = false,
    val keepScreenOn: Boolean = true,
    val showChapterTitle: Boolean = true,
)

data class ChapterContent(val chapter: Chapter, val paragraphs: List<Paragraph>)

data class ImportSummary(
    val importedCount: Int,
    val duplicateCount: Int = 0,
    val failures: List<String> = emptyList(),
)

data class ReadingStats(
    val todayMillis: Long = 0,
    val totalMillis: Long = 0,
    val todayCharacters: Long = 0,
    val totalCharacters: Long = 0,
    val streakDays: Int = 0,
    val goalMinutes: Int = 30,
)

data class UserFont(val uuid: String, val name: String, val filePath: String, val createdTime: Long)
