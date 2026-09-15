package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.PageTurnAnimation
import com.kixyu9527.kixyubook.core.common.model.ReaderBrightnessMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.designsystem.R
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import kotlin.math.roundToInt

/**
 * The single canonical reader-settings information architecture.
 *
 * Level 1 is the quick controls (font size, brightness) plus this ordered list of four groups.
 * Both the settings page and the in-reader panel render them with the same titles, summaries,
 * icons and order. Level 2 is the rows inside one group. Level 3 is a dedicated surface for
 * custom colour editing and font management.
 */
enum class KixyuReaderSettingsGroup(@StringRes val titleRes: Int) {
    FONT_LAYOUT(R.string.kixyu_reader_group_font_layout),
    PAGE_TURN(R.string.kixyu_reader_group_page_turn),
    THEME_SCREEN(R.string.kixyu_reader_group_theme_screen),
    INFORMATION(R.string.kixyu_reader_group_information),
}

val KixyuReaderSettingsGroup.icon: ImageVector
    @Composable get() = when (this) {
        KixyuReaderSettingsGroup.FONT_LAYOUT -> KixyuSymbols.FormatUnderlined
        KixyuReaderSettingsGroup.PAGE_TURN -> KixyuSymbols.ViewCarousel
        KixyuReaderSettingsGroup.THEME_SCREEN -> KixyuSymbols.Palette
        KixyuReaderSettingsGroup.INFORMATION -> KixyuSymbols.Info
    }

const val KIXYU_READER_LEVEL_ROOT = "root"
const val KIXYU_READER_LEVEL_COLORS = "colors"

/** One level identity for the whole app: root, a group name, or the colour editor. */
fun kixyuReaderLevelKey(group: KixyuReaderSettingsGroup?, colorsOpen: Boolean): String = when {
    group == null -> KIXYU_READER_LEVEL_ROOT
    colorsOpen -> KIXYU_READER_LEVEL_COLORS
    else -> group.name
}

/** Level depth: root = 0, a group = 1, the colour editor = 2. */
fun kixyuReaderLevelDepth(levelKey: String): Int = when (levelKey) {
    KIXYU_READER_LEVEL_ROOT -> 0
    KIXYU_READER_LEVEL_COLORS -> 2
    else -> 1
}

/**
 * Shared level transition (Material shared-axis X): the leaving level fades and shifts out while
 * the entering level fades and shifts in from the opposite edge. The cross-fade keeps the two
 * transparent lists from showing through each other, unlike a pure translate.
 */
fun AnimatedContentTransitionScope<*>.kixyuReaderSettingsLevelTransition(forward: Boolean): ContentTransform {
    val enter = slideInHorizontally(
        animationSpec = tween(KixyuMotion.ReaderPopupEnterMillis, delayMillis = LEVEL_ENTER_DELAY_MILLIS),
    ) { if (forward) it / 3 else -it / 3 } +
        fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis, delayMillis = LEVEL_ENTER_DELAY_MILLIS))
    val exit = slideOutHorizontally(animationSpec = tween(KixyuMotion.ReaderPopupExitMillis)) {
        if (forward) -it / 3 else it / 3
    } + fadeOut(tween(KixyuMotion.ReaderPopupExitMillis))
    return enter togetherWith exit
}

private const val LEVEL_ENTER_DELAY_MILLIS = 40

/**
 * Pure back-state machine for the reader settings levels.
 *
 * One committed gesture keeps its final frame on the level that left (see
 * [KixyuPredictiveBackState.commit]); the next level must not inherit that stale progress, and a
 * new gesture must take over immediately. Keeping this logic outside the composable makes the
 * "colors -> group -> root" and "reopen the same level" sequences testable.
 */
class KixyuReaderSettingsLevelBackState {
    private var lastKey: String? = null
    private var poppedKey: String? = null
    private var suppressProgress = false

    data class Frame(
        val poppedKey: String?,
        val suppressProgress: Boolean,
        val showParentPreview: Boolean,
    )

    fun onFrame(levelKey: String, rawProgress: Float): Frame {
        val forward = lastKey?.let { kixyuReaderLevelDepth(levelKey) > kixyuReaderLevelDepth(it) } ?: false
        if (rawProgress < 1f) {
            suppressProgress = false
            poppedKey = null
        }
        if (forward) suppressProgress = true
        if (levelKey != lastKey) {
            poppedKey = if (forward || rawProgress < 1f) null else lastKey
            lastKey = levelKey
        }
        return Frame(
            poppedKey = poppedKey,
            suppressProgress = suppressProgress,
            showParentPreview = rawProgress > 0f && poppedKey == null && !suppressProgress,
        )
    }
}

/** The progress that may be applied to [key]; 0 everywhere that is not the active pop target. */
fun KixyuReaderSettingsLevelBackState.Frame.progressFor(
    key: String,
    levelKey: String,
    rawProgress: Float,
): Float = when {
    suppressProgress -> 0f
    key == (poppedKey ?: levelKey) -> rawProgress
    else -> 0f
}

/** Pure level stack shared by both surfaces: Back always pops exactly one level. */
class KixyuReaderSettingsNavState {
    var openGroup by mutableStateOf<KixyuReaderSettingsGroup?>(null)
        private set
    var colorsOpen by mutableStateOf(false)
        private set

    fun open(group: KixyuReaderSettingsGroup) {
        openGroup = group
        colorsOpen = false
    }

    fun openColors() {
        if (openGroup == KixyuReaderSettingsGroup.THEME_SCREEN) colorsOpen = true
    }

    /** Returns true when a level was popped; false means the caller should dismiss the surface. */
    fun back(): Boolean {
        if (colorsOpen) {
            colorsOpen = false
            return true
        }
        if (openGroup == null) return false
        openGroup = null
        return true
    }

    fun reset() {
        openGroup = null
        colorsOpen = false
    }
}

/** Canonical reader-owned reset; app-shared language/style/accent and sync flags stay untouched. */
fun ReaderSettings.resetReaderOwnedFields(): ReaderSettings =
    KixyuReaderSettingsGroup.entries.fold(this) { settings, group -> group.resetToDefaults(settings) }

/** Canonical per-group field ownership: the single "reset all" action folds these groups. */
fun KixyuReaderSettingsGroup.resetToDefaults(current: ReaderSettings): ReaderSettings {
    val defaults = ReaderSettings()
    return when (this) {
        KixyuReaderSettingsGroup.FONT_LAYOUT -> current.copy(
            fontSize = defaults.fontSize,
            lineHeight = defaults.lineHeight,
            letterSpacing = defaults.letterSpacing,
            margin = defaults.margin,
            fontUuid = defaults.fontUuid,
        )
        KixyuReaderSettingsGroup.PAGE_TURN -> current.copy(
            pageMode = defaults.pageMode,
            pageTurnAnimation = defaults.pageTurnAnimation,
            volumeKeyPageTurn = defaults.volumeKeyPageTurn,
        )
        KixyuReaderSettingsGroup.THEME_SCREEN -> current.copy(
            theme = defaults.theme,
            customThemeEnabled = defaults.customThemeEnabled,
            customDayTheme = defaults.customDayTheme,
            customNightTheme = defaults.customNightTheme,
            brightnessMode = defaults.brightnessMode,
            brightness = defaults.brightness,
            keepScreenOn = defaults.keepScreenOn,
            glassEffectEnabled = defaults.glassEffectEnabled,
            glassFrostLevel = defaults.glassFrostLevel,
        )
        KixyuReaderSettingsGroup.INFORMATION -> current.copy(
            showStatusBar = defaults.showStatusBar,
            hideNavigationBar = defaults.hideNavigationBar,
            showChapterTitle = defaults.showChapterTitle,
            showPageNumber = defaults.showPageNumber,
            showReadingTime = defaults.showReadingTime,
            showBatteryLevel = defaults.showBatteryLevel,
        )
    }
}

/** The single reset entry: level 1 only. Levels 2/3 never show a reset action. */
@Composable
fun KixyuReaderResetAllAction(onClick: () -> Unit) {
    KixyuTextButton(text = stringResource(R.string.kixyu_reader_reset_group), onClick = onClick)
}

/** One level-1 entry with the group's current configuration summary. */
@Composable
fun KixyuReaderSettingsGroupEntry(
    group: KixyuReaderSettingsGroup,
    settings: ReaderSettings,
    fonts: List<UserFont>,
    onClick: () -> Unit,
) {
    KixyuSettingsRow(
        title = stringResource(group.titleRes),
        supportingText = group.summary(settings, fonts),
        icon = group.icon,
        onClick = onClick,
    ) {
        Icon(KixyuSymbols.KeyboardArrowRight, null)
    }
}

/** Current-configuration summary shown on the level-1 entry, e.g. `系统字体 · 20sp · 1.7×`. */
@Composable
fun KixyuReaderSettingsGroup.summary(settings: ReaderSettings, fonts: List<UserFont>): String {
    val separator = stringResource(R.string.kixyu_summary_separator)
    return when (this) {
        KixyuReaderSettingsGroup.FONT_LAYOUT -> listOf(
            fonts.firstOrNull { it.uuid == settings.fontUuid }?.name
                ?: stringResource(R.string.kixyu_system_default),
            "${settings.fontSize.roundToInt()}sp",
            "${settings.lineHeight}×",
        ).joinToString(separator)
        KixyuReaderSettingsGroup.PAGE_TURN -> buildList {
            add(
                stringResource(
                    when {
                        settings.pageMode == PageMode.SCROLL -> R.string.kixyu_mode_scroll
                        settings.pageTurnAnimation == PageTurnAnimation.COVER -> R.string.kixyu_mode_cover
                        else -> R.string.kixyu_mode_slide
                    },
                ),
            )
            if (settings.volumeKeyPageTurn) add(stringResource(R.string.kixyu_volume_page_turn))
        }.joinToString(separator)
        KixyuReaderSettingsGroup.THEME_SCREEN -> buildList {
            add(settings.theme.displayName())
            add(
                if (settings.brightnessMode == ReaderBrightnessMode.SYSTEM) {
                    stringResource(R.string.kixyu_follow_system)
                } else {
                    "${(settings.brightness * 100).roundToInt()}%"
                },
            )
            if (settings.keepScreenOn) add(stringResource(R.string.kixyu_keep_screen_on))
        }.joinToString(separator)
        KixyuReaderSettingsGroup.INFORMATION -> listOfNotNull(
            stringResource(R.string.kixyu_show_chapter_title).takeIf { settings.showChapterTitle },
            stringResource(R.string.kixyu_show_page_number).takeIf { settings.showPageNumber },
            stringResource(R.string.kixyu_show_time).takeIf { settings.showReadingTime },
            stringResource(R.string.kixyu_show_battery).takeIf { settings.showBatteryLevel },
        ).joinToString(separator).ifBlank { stringResource(R.string.kixyu_reader_info_hidden) }
    }
}

/**
 * Renders exactly one level-2 group. Both surfaces call this, so content and internal order are
 * defined in one place. The INFORMATION group keeps its two labelled sub-groups (system bars and
 * in-page information) so the same six toggles never drift apart.
 */
@Composable
fun KixyuReaderSettingsGroupContent(
    group: KixyuReaderSettingsGroup,
    settings: ReaderSettings,
    fonts: List<UserFont>,
    onSettingsChange: ReaderSettingsUpdate,
    onManageFonts: () -> Unit,
    onEditColors: () -> Unit,
    onBrightnessPreview: (Float?) -> Unit = {},
) {
    when (group) {
        KixyuReaderSettingsGroup.FONT_LAYOUT -> {
            KixyuFontControls(
                fonts = fonts,
                selectedFontUuid = settings.fontUuid,
                onManageFonts = onManageFonts,
            )
            KixyuDivider()
            KixyuReaderTypographyControls(settings, onSettingsChange)
        }
        KixyuReaderSettingsGroup.PAGE_TURN -> {
            KixyuReaderModeControls(settings, onSettingsChange)
            KixyuDivider()
            KixyuReaderBehaviorControls(settings, onSettingsChange)
        }
        KixyuReaderSettingsGroup.THEME_SCREEN -> {
            KixyuThemeModeControl(settings, onSettingsChange)
            KixyuDivider()
            KixyuSettingsRow(
                title = stringResource(R.string.kixyu_custom_colors),
                onClick = {
                    onSettingsChange { current -> current.copy(customThemeEnabled = !current.customThemeEnabled) }
                },
            ) {
                KixyuSwitch(
                    checked = settings.customThemeEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange { current -> current.copy(customThemeEnabled = enabled) }
                    },
                )
            }
            if (settings.customThemeEnabled) {
                KixyuDivider()
                KixyuSettingsRow(
                    title = stringResource(R.string.kixyu_edit_reader_colors),
                    supportingText = stringResource(R.string.kixyu_edit_reader_colors_hint),
                    onClick = onEditColors,
                ) {
                    Icon(KixyuSymbols.KeyboardArrowRight, null)
                }
            }
            KixyuDivider()
            KixyuReaderBrightnessSlider(settings, onSettingsChange, onBrightnessPreview)
            KixyuDivider()
            KixyuReaderKeepScreenOnControl(settings, onSettingsChange)
            KixyuDivider()
            KixyuGlassEffectControls(settings, onSettingsChange)
        }
        KixyuReaderSettingsGroup.INFORMATION -> {
            KixyuSectionLabel(stringResource(R.string.kixyu_reader_group_system_bars))
            KixyuReaderSystemBarControls(settings, onSettingsChange)
            KixyuSectionLabel(stringResource(R.string.kixyu_reader_group_page_info))
            KixyuReaderPageInfoControls(settings, onSettingsChange)
        }
    }
}

/** Design-system title so callers never reference internal string ids. */
@Composable
fun kixyuReaderCustomColorsTitle(): String = stringResource(R.string.kixyu_custom_colors)

/** Level-3 colour editor embedded in the same surface: day/night switch plus four colours. */
@Composable
fun KixyuReaderColorEditorContent(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    var editingNight by rememberSaveable(settings.theme) { mutableStateOf(settings.theme == ReaderTheme.NIGHT) }
    Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                listOf(false to R.string.kixyu_theme_day, true to R.string.kixyu_theme_night).forEach { (night, label) ->
                    FilterChip(
                        selected = editingNight == night,
                        onClick = { editingNight = night },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        val editing = if (editingNight) settings.customNightTheme else settings.customDayTheme
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = KixyuSpacing.rowHorizontal)
                .background(androidx.compose.ui.graphics.Color(editing.backgroundHex.toColorInt()))
                .padding(KixyuSpacing.medium),
        ) {
            Text(
                text = stringResource(R.string.kixyu_color_preview_title),
                color = androidx.compose.ui.graphics.Color(editing.titleHex.toColorInt()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.kixyu_color_preview_body),
                color = androidx.compose.ui.graphics.Color(editing.bodyHex.toColorInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.kixyu_color_preview_accent),
                color = androidx.compose.ui.graphics.Color(editing.accentHex.toColorInt()),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        CustomThemeEditor(
            theme = editing,
            onChanged = { custom ->
                onSettingsChange { current ->
                    if (editingNight) {
                        current.copy(customNightTheme = custom)
                    } else {
                        current.copy(customDayTheme = custom)
                    }
                }
            },
        )
    }
}

@Composable
private fun KixyuSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KixyuSpacing.rowHorizontal, vertical = KixyuSpacing.extraSmall),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun KixyuReaderSystemBarControls(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    ReaderSwitch(
        title = stringResource(R.string.kixyu_show_status_bar),
        supportingText = stringResource(R.string.kixyu_show_status_bar_hint),
        checked = settings.showStatusBar,
    ) { onSettingsChange { current -> current.copy(showStatusBar = it) } }
    KixyuDivider()
    // Positive semantics: checked means the bar is shown (includes gesture and three-button bars).
    ReaderSwitch(
        title = stringResource(R.string.kixyu_show_navigation_bar),
        supportingText = stringResource(R.string.kixyu_show_navigation_bar_hint),
        checked = !settings.hideNavigationBar,
    ) { onSettingsChange { current -> current.copy(hideNavigationBar = !it) } }
}

@Composable
private fun KixyuReaderPageInfoControls(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    ReaderSwitch(
        title = stringResource(R.string.kixyu_show_chapter_title),
        supportingText = stringResource(R.string.kixyu_show_chapter_title_hint),
        checked = settings.showChapterTitle,
    ) { onSettingsChange { current -> current.copy(showChapterTitle = it) } }
    KixyuDivider()
    ReaderSwitch(
        title = stringResource(R.string.kixyu_show_page_number),
        supportingText = stringResource(R.string.kixyu_show_page_number_hint),
        checked = settings.showPageNumber,
    ) { onSettingsChange { current -> current.copy(showPageNumber = it) } }
    KixyuDivider()
    ReaderSwitch(
        title = stringResource(R.string.kixyu_show_time),
        supportingText = stringResource(R.string.kixyu_show_time_hint),
        checked = settings.showReadingTime,
    ) { onSettingsChange { current -> current.copy(showReadingTime = it) } }
    KixyuDivider()
    ReaderSwitch(
        title = stringResource(R.string.kixyu_show_battery),
        supportingText = stringResource(R.string.kixyu_show_battery_hint),
        checked = settings.showBatteryLevel,
    ) { onSettingsChange { current -> current.copy(showBatteryLevel = it) } }
}
