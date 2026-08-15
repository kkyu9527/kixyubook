package com.kixyu9527.kixyubook.core.common.model

/** Converts publisher-provided headings into a stable, single-line display title. */
fun String.singleLineBookHeading(): String =
    replace(Regex("[\\s\\p{Z}\u200B\u2060\uFEFF]+"), " ").trim()

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

data class Chapter(
    val id: Long,
    val bookUuid: String,
    val title: String,
    val index: Int,
    val volumeTitle: String? = null,
    val volumeIndex: Int? = null,
    /** Stable across devices; unlike Room's auto-generated chapter id. */
    val chapterKey: String = "",
)

enum class ChapterLoadPriority { USER, PREFETCH }

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
    /** The source XHTML dedicates its complete page to this image. */
    val isFullPageImage: Boolean = false,
    /** The publisher requested a cover-style crop (for example a CSS body background). */
    val cropImageToFill: Boolean = false,
)

data class ReadingProgress(
    val bookUuid: String,
    val chapterId: Long,
    val position: Int,
    val offset: Int = 0,
    val updatedTime: Long,
    val fraction: Float = 0f,
    val chapterKey: String = "",
    val paragraphIndex: Int = position,
    val charOffset: Int = offset,
    val quoteAnchor: String = "",
)

data class SyncedBook(
    val uuid: String,
    val title: String,
    val author: String,
    val description: String,
    val format: BookFormat,
    val createdTime: Long,
    val contentHash: String,
    val category: String,
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

enum class TextCorrectionStatus { ACTIVE, UNRESOLVED, CONFLICT }

/**
 * A user-owned correction anchored to immutable, normalized source text.
 *
 * Offsets are UTF-16 offsets in the original paragraph. They never depend on font, margins,
 * screen size, pagination, or a previously corrected display string.
 */
data class TextCorrection(
    val uuid: String,
    val bookUuid: String,
    val sourceContentHash: String,
    val chapterKey: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val exactText: String,
    val prefixText: String,
    val suffixText: String,
    val replacementText: String,
    val status: TextCorrectionStatus = TextCorrectionStatus.ACTIVE,
    val createdTime: Long,
    val updatedTime: Long,
    val deviceId: String = "",
)

data class LibraryBook(val book: Book, val progress: ReadingProgress?)

enum class ReaderTheme { SYSTEM, DAY, NIGHT }
enum class PageMode { SCROLL, PAGED }
enum class ReaderBrightnessMode { SYSTEM, MANUAL }
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
    val glassEffectEnabled: Boolean = true,
    val showStatusBar: Boolean = true,
    val hideNavigationBar: Boolean = true,
    val showPageNumber: Boolean = true,
    val volumeKeyPageTurn: Boolean = false,
    val keepScreenOn: Boolean = true,
    val showChapterTitle: Boolean = true,
    val showReadingTime: Boolean = false,
    val showBatteryLevel: Boolean = false,
    val brightnessMode: ReaderBrightnessMode = ReaderBrightnessMode.SYSTEM,
    val brightness: Float = 0.5f,
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
    val streakDays: Int = 0,
    val goalMinutes: Int = 30,
    val recentDays: List<DailyReading> = emptyList(),
)

data class DailyReading(
    val epochDay: Long,
    val durationMillis: Long,
)

enum class LibrarySortMode {
    RECENT,
    IMPORTED,
    TITLE,
    AUTHOR,
    PROGRESS,
    CUSTOM,
}

enum class LibraryLayoutMode { LIST, GRID }

data class LibraryPreferences(
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.LIST,
    val customOrder: List<String> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
)

data class UserFont(val uuid: String, val name: String, val filePath: String, val createdTime: Long)

data class AppUpdateInfo(
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val downloadUrl: String?,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(val update: AppUpdateInfo) : AppUpdateState
    data class UpToDate(val currentVersion: String) : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}

sealed interface ReleaseNotesState {
    data object Idle : ReleaseNotesState
    data object Loading : ReleaseNotesState
    data class Available(val release: AppUpdateInfo) : ReleaseNotesState
    data class Unavailable(val message: String) : ReleaseNotesState
}
