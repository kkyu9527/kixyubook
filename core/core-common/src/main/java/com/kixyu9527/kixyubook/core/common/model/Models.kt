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
    val originalDisplayName: String = "",
    val titleSort: String = "",
    val seriesName: String = "",
    val seriesIndex: Double? = null,
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

enum class ChapterLoadPriority { USER, READ_AHEAD, PREFETCH }

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
    /** User note marker layered on top of the underlying highlight decoration. */
    NOTE,
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
    /** Absolute EPUB archive target such as `OPS/notes.xhtml#note-1`. */
    val linkTarget: String? = null,
)

sealed interface EpubLinkResult {
    data class Footnote(val title: String, val text: String) : EpubLinkResult
    data class Location(val chapterIndex: Int, val paragraphIndex: Int = 0) : EpubLinkResult
}

/** Display-only EPUB navigation; it never replaces a persisted chapter or text anchor. */
data class EpubNavigationEntry(
    val sourceIndex: Int,
    val title: String,
    val target: String,
    val depth: Int = 0,
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
    val originalDisplayName: String = "",
    val titleSort: String = "",
    val seriesName: String = "",
    val seriesIndex: Double? = null,
    /** null means an old payload without ownership; local flags must be preserved. */
    val metadataOwnership: BookMetadataOwnership? = null,
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
    /** Stable chapter identity so the bookmark survives a reparse; '' for legacy rows. */
    val chapterKey: String = "",
)

/** One in-paragraph match: UTF-16 offset and length inside the paragraph text. */
data class SearchMatch(
    val start: Int,
    val length: Int,
)

/** All non-overlapping, case-insensitive occurrences of [query], in reading order. */
fun String.searchMatches(query: String): List<SearchMatch> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<SearchMatch>()
    var index = indexOf(query, 0, ignoreCase = true)
    while (index >= 0) {
        matches += SearchMatch(index, query.length)
        index = indexOf(query, index + query.length, ignoreCase = true)
    }
    return matches
}

data class BookSearchResult(
    val chapterId: Long,
    val chapterTitle: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
    /** Every occurrence in this paragraph, in reading order. A paragraph can match several times. */
    val matches: List<SearchMatch> = emptyList(),
)

enum class BookSearchStage { INDEXING, SEARCHING }

/** Measured work for one search stage; unlike a weighted percentage this never stalls at 72%. */
data class BookSearchProgress(
    val stage: BookSearchStage,
    val completed: Int,
    val total: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 1f else completed.toFloat().div(total).coerceIn(0f, 1f)
}

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

enum class ReaderAnnotationStyle { HIGHLIGHT, UNDERLINE }

/** A reader-owned annotation anchored to source paragraph offsets, independent of pagination. */
data class ReaderAnnotation(
    val uuid: String,
    val bookUuid: String,
    val sourceContentHash: String,
    val chapterKey: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val exactText: String,
    val style: ReaderAnnotationStyle,
    val note: String = "",
    val createdTime: Long,
    val updatedTime: Long,
    val deviceId: String = "",
)

data class LibraryBook(val book: Book, val progress: ReadingProgress?)

enum class ReaderTheme { SYSTEM, DAY, NIGHT }
enum class PageMode { SCROLL, PAGED }
enum class PageTurnAnimation { HORIZONTAL_SLIDE, COVER }
enum class ReaderBrightnessMode { SYSTEM, MANUAL }
enum class AppColorTheme { DEFAULT, WHITE, DYNAMIC, SAGE, OCEAN, VIOLET, AMBER }
enum class AppUiStyle { MATERIAL, MIUIX }

const val DEFAULT_GLASS_FROST_LEVEL = 55f
const val MIN_GLASS_FROST_LEVEL = 0f
const val MAX_GLASS_FROST_LEVEL = 100f

/** Converts the pre-2.8 blur-radius preference into the user-facing frosted percentage. */
fun legacyGlassBlurRadiusToFrostLevel(radius: Float): Float =
    (radius / 40f * 100f).coerceIn(MIN_GLASS_FROST_LEVEL, MAX_GLASS_FROST_LEVEL)

data class CustomReaderTheme(
    val backgroundHex: String = "#F7F4EC",
    val bodyHex: String = "#292722",
    val titleHex: String = "#171713",
    val accentHex: String = "#52655A",
)

/** Per-field "edited by a person" ownership synced with a book. */
data class BookMetadataOwnership(
    val title: Boolean = false,
    val author: Boolean = false,
    val description: Boolean = false,
)

data class ReaderSettings(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.72f,
    val letterSpacing: Float = 0.01f,
    val margin: Float = 24f,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val pageMode: PageMode = PageMode.SCROLL,
    val pageTurnAnimation: PageTurnAnimation = PageTurnAnimation.HORIZONTAL_SLIDE,
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
    val glassFrostLevel: Float = DEFAULT_GLASS_FROST_LEVEL,
    val predictiveBackEnabled: Boolean = false,
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

data class BookExportSummary(
    val exportedCount: Int,
    val failedTitles: List<String> = emptyList(),
    val directoryUri: String,
)

enum class ImportStage { QUEUED, COPYING, READING_METADATA, BUILDING_DIRECTORY, INDEXING, FINISHED }
enum class ImportItemStatus { PENDING, RUNNING, SUCCEEDED, DUPLICATE, FAILED, CANCELED }

data class ImportItemProgress(
    val id: String,
    val displayName: String,
    val stage: ImportStage = ImportStage.QUEUED,
    val progress: Float = 0f,
    val status: ImportItemStatus = ImportItemStatus.PENDING,
    val bookUuid: String? = null,
    val message: String? = null,
)

data class ImportProgress(
    val runId: String,
    val items: List<ImportItemProgress>,
    val startedTime: Long,
    val finished: Boolean = false,
) {
    val completedCount: Int
        get() = items.count {
            it.status in setOf(
                ImportItemStatus.SUCCEEDED,
                ImportItemStatus.DUPLICATE,
                ImportItemStatus.FAILED,
                ImportItemStatus.CANCELED,
            )
        }
    val overallProgress: Float
        get() = if (items.isEmpty()) 0f else items.sumOf { it.progress.toDouble() }.toFloat() / items.size
}

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

enum class FilenameSegmentRole { TITLE, AUTHOR, STATUS, IGNORE }

/**
 * A user-confirmed filename rule. The sample and per-segment roles are the source of truth; the
 * matching pattern is generated from them, so the UI never needs to reverse-engineer regex.
 */
data class FilenameRuleSpec(
    val id: String,
    val sample: String,
    val separator: String?,
    val roles: List<FilenameSegmentRole>,
    /** Bumped when the structural interpretation changes so old rules stay readable. */
    val version: Int = 1,
    /** Disabled rules stay listed but no longer participate in recognition. */
    val enabled: Boolean = true,
)

data class LibraryPreferences(
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.LIST,
    val customOrder: List<String> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
    /** User regexes with named `title`/`author` groups, applied before the built-in rules. */
    val filenameRules: List<String> = emptyList(),
    /** Structured rules created from a real file name and confirmed by the user. */
    val filenameRuleSpecs: List<FilenameRuleSpec> = emptyList(),
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
