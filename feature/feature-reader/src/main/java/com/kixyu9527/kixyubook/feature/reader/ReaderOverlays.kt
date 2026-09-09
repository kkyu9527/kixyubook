package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.graphics.toColorInt
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDropdownRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuFontControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuGlassSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBackdropAwareInteractiveSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuListRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBehaviorControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBrightnessControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderInformationControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderLayoutControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderThemeControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSearchField
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPredictivePopupTransform
import com.kixyu9527.kixyubook.core.reader.engine.*


@Composable
internal fun ReaderFloatingSheet(
    show: Boolean,
    progress: Float,
    onDismissRequest: () -> Unit,
    backdrop: KixyuNavigationBackdrop,
    maxContentWidth: Dp,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
        exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sheetMaxHeight = maxHeight * .82f
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = .28f * (1f - progress)))
                    .clickable(onClick = onDismissRequest),
            )
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        PaddingValues(
                            start = KixyuSpacing.medium,
                            end = KixyuSpacing.medium,
                            bottom = KixyuSize.floatingSurfaceBottomGap,
                        ),
                    )
                    .animateEnterExit(
                        enter = slideInVertically(tween(KixyuMotion.ReaderPopupEnterMillis)) { it / 3 } +
                            fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
                        exit = slideOutVertically(tween(KixyuMotion.ReaderPopupExitMillis)) { it / 3 } +
                            fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
                    )
                    .kixyuPredictivePopupTransform(progress),
            ) {
                KixyuGlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent()
                            }
                        },
                    fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    // The scroll/content owner reaches the clipped surface bounds. Individual
                    // screens provide visual content padding, never a second container inset.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { content() }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderSearchOverlay(
    visible: Boolean,
    progress: Float,
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSearch: (String, ReaderSearchScope) -> Unit,
    onClearHistory: () -> Unit,
    onMove: (Int) -> Unit,
    onReturn: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.searchQuery) }
    var scopeName by rememberSaveable { mutableStateOf(state.searchScope.name) }
    val searchScope = ReaderSearchScope.entries.firstOrNull { it.name == scopeName } ?: ReaderSearchScope.BOOK
    val bookScopeLabel = stringResource(R.string.reader_search_scope_book)
    val chapterScopeLabel = stringResource(R.string.reader_search_scope_chapter)
    var expanded by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            query = state.searchQuery
            expanded = true
            withFrameNanos { }
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }
    fun submit(submittedQuery: String = query) {
        val normalized = submittedQuery.trim()
        query = submittedQuery
        onSearch(normalized, searchScope)
        focusManager.clearFocus()
        expanded = true
    }
    AnimatedVisibility(
        visible = visible && expanded,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderSearchEnterMillis)),
        exit = fadeOut(tween(KixyuMotion.ReaderSearchExitMillis)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .28f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderSearchEnterMillis)) +
            slideInVertically(tween(KixyuMotion.ReaderSearchEnterMillis)) { it / 5 },
        exit = fadeOut(tween(KixyuMotion.ReaderSearchExitMillis)) +
            slideOutVertically(tween(KixyuMotion.ReaderSearchExitMillis)) { it / 5 },
    ) {
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBarsIgnoringVisibility)
                        .only(WindowInsetsSides.Bottom),
                )
                .padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.small),
            contentAlignment = Alignment.BottomCenter,
        ) {
            KixyuBackdropAwareInteractiveSurface(
                modifier = Modifier.fillMaxWidth()
                    .widthIn(max = KixyuSize.readerSearchPanelMaxWidth)
                    .heightIn(max = KixyuSize.readerSearchPanelMaxHeight)
                    .animateContentSize(tween(KixyuMotion.ReaderSearchEnterMillis))
                    .kixyuPredictivePopupTransform(progress),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(KixyuSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                ) {
                    AnimatedVisibility(visible = expanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.reader_search_book),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                            )
                            KixyuIconButton(onClick = onDismiss) {
                                Icon(KixyuSymbols.Close, stringResource(R.string.reader_close_search))
                            }
                        }
                    }
                    KixyuSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = ::submit,
                        expanded = expanded,
                        onExpandedChange = { if (it) expanded = true },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) expanded = true },
                        placeholder = stringResource(R.string.reader_search_hint),
                        leadingIcon = { Icon(KixyuSymbols.Search, null) },
                        trailingIcon = {
                            KixyuIconButton(onClick = { submit(query) }, enabled = query.isNotBlank()) {
                                Icon(KixyuSymbols.ArrowForward, stringResource(R.string.reader_search))
                            }
                        },
                    )
                    AnimatedVisibility(visible = expanded) {
                        KixyuDropdownRow(
                            title = stringResource(R.string.reader_search_scope),
                            selected = searchScope,
                            options = ReaderSearchScope.entries,
                            optionLabel = { selected ->
                                if (selected == ReaderSearchScope.BOOK) bookScopeLabel else chapterScopeLabel
                            },
                            onSelected = { scopeName = it.name },
                        )
                    }
                    if (expanded && query.isBlank() && state.searchHistory.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.reader_search_history),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                KixyuTextButton(
                                    text = stringResource(R.string.reader_clear_search_history),
                                    onClick = onClearHistory,
                                )
                            }
                            state.searchHistory.forEach { previousQuery ->
                                KixyuTextButton(
                                    text = previousQuery,
                                    onClick = {
                                        query = previousQuery
                                        onSearch(previousQuery, searchScope)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (query.trim() == state.searchQuery && state.searchInProgress) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                        ) {
                            Text(
                                stringResource(
                                    if (state.searchStage == BookSearchStage.INDEXING) {
                                        R.string.reader_search_indexing_progress
                                    } else {
                                        R.string.reader_search_scanning_progress
                                    },
                                    state.searchCompleted,
                                    state.searchTotal,
                                    (state.searchProgress * 100f).toInt(),
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { state.searchProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    state.searchError?.takeIf { query.trim() == state.searchQuery }?.let { error ->
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.searchResults.isNotEmpty() && query.trim() == state.searchQuery) {
                if (!expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.selectedSearchIndex + 1}/${state.searchResults.size}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        KixyuIconButton(
                            onClick = { onMove(-1) },
                            enabled = state.selectedSearchIndex > 0,
                        ) { Icon(KixyuSymbols.KeyboardArrowUp, stringResource(R.string.reader_previous_search_result)) }
                        KixyuIconButton(
                            onClick = { onMove(1) },
                            enabled = state.selectedSearchIndex < state.searchResults.lastIndex,
                        ) { Icon(KixyuSymbols.KeyboardArrowDown, stringResource(R.string.reader_next_search_result)) }
                        if (state.searchReturnAvailable) {
                            KixyuIconButton(
                                onClick = {
                                    onReturn()
                                    expanded = true
                                },
                            ) { Icon(KixyuSymbols.ArrowBack, stringResource(R.string.reader_return_before_search)) }
                        }
                        KixyuIconButton(onClick = onDismiss) {
                            Icon(KixyuSymbols.Close, stringResource(R.string.reader_exit_search))
                        }
                    }
                } else {
                Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.reader_search_result_count, state.searchResults.size, state.searchResults.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    // Keep the final row above the popup's rounded bottom edge. The popup itself
                    // remains edge-to-edge; only scrollable content reserves its visual safe area.
                    contentPadding = PaddingValues(bottom = KixyuSpacing.extraLarge),
                ) {
                    items(state.searchResults.size) { index ->
                        val result = state.searchResults[index]
                        KixyuListRow(
                            title = result.chapterTitle,
                            supportingText = result.text,
                            selected = index == state.selectedSearchIndex,
                            leading = { Text("${index + 1}", style = MaterialTheme.typography.labelMedium) },
                            trailing = { Icon(KixyuSymbols.ChevronRight, null) },
                            onClick = {
                                onSelect(index)
                                focusManager.clearFocus()
                                expanded = false
                            },
                        )
                    }
                }
                }
                    } else if (
                        !state.searchInProgress &&
                        query.trim() == state.searchQuery &&
                        state.searchQuery.isNotBlank()
                    ) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.reader_no_search_result), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                    } else if (query.isNotBlank()) {
                Text(
                    stringResource(R.string.reader_search_query_changed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                    }
                }
            }
        }
    }
}

@Composable internal fun ThemeSheet(
    settings: ReaderSettings,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
    previewBrightness: (Float?) -> Unit,
    onBack: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = KixyuSpacing.large,
            vertical = KixyuSpacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { ReaderSettingsSheetHeader(stringResource(R.string.reader_display_and_brightness), onBack) }
        item {
            KixyuSection(title = stringResource(R.string.reader_display_and_colors)) {
                KixyuReaderThemeControls(
                    settings,
                    { updated -> update { updated } },
                    modeTitle = stringResource(R.string.reader_display_mode),
                )
            }
        }
        item {
            KixyuSection(title = stringResource(R.string.reader_screen_brightness)) {
                KixyuReaderBrightnessControls(
                    settings = settings,
                    onSettingsChange = { updated -> update { updated } },
                    onBrightnessPreview = previewBrightness,
                )
            }
        }
    }
}

@Composable
internal fun LayoutSheet(
    state: ReaderUiState,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
    addFont: () -> Unit,
    deleteFont: (UserFont) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = KixyuSpacing.large,
            vertical = KixyuSpacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { ReaderSettingsSheetHeader(stringResource(R.string.reader_layout_and_turning), onBack) }
        item {
            KixyuSection(title = stringResource(R.string.reader_layout_and_turning)) {
                KixyuFontControls(
                    fonts = state.availableFonts,
                    selectedFontUuid = settings.fontUuid,
                    onSelectFont = { uuid -> update { it.copy(fontUuid = uuid) } },
                    onAddFont = addFont,
                    onDeleteFont = deleteFont,
                )
                KixyuDivider()
                KixyuReaderLayoutControls(settings) { updated -> update { updated } }
            }
        }
    }
}

@Composable
internal fun ReaderInformationSheet(
    settings: ReaderSettings,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onBack: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = KixyuSpacing.large,
            vertical = KixyuSpacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { ReaderSettingsSheetHeader(stringResource(R.string.reader_controls), onBack) }
        item {
            KixyuSection(title = stringResource(R.string.reader_page_turn_controls)) {
                KixyuReaderBehaviorControls(settings) { updated -> update { updated } }
            }
        }
        item {
            KixyuSection(title = stringResource(R.string.reader_reading_information)) {
                KixyuReaderInformationControls(settings) { updated -> update { updated } }
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheetHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KixyuIconButton(onClick = onBack) {
            Icon(KixyuSymbols.ArrowBack, stringResource(R.string.reader_back_to_settings))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
    }
}


@Composable internal fun BookInfoDialog(
    show: Boolean,
    book: Book?,
    progress: Float,
    backdrop: KixyuNavigationBackdrop,
    dismiss: () -> Unit,
) {
    val current = book ?: return
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
        exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .28f * (1f - progress)))
                .clickable(onClick = dismiss),
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(KixyuSpacing.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            val dialogHeight = minOf(maxHeight, 480.dp)
            KixyuGlassSurface(
                backdrop = backdrop,
                modifier = Modifier.widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .height(dialogHeight)
                    .kixyuPredictivePopupTransform(progress)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    },
                fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        stringResource(R.string.reader_book_information),
                        modifier = Modifier.padding(
                            start = KixyuSpacing.extraLarge,
                            end = KixyuSpacing.extraLarge,
                            top = KixyuSpacing.extraLarge,
                            bottom = KixyuSpacing.medium,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = KixyuSpacing.extraLarge,
                                end = KixyuSpacing.extraLarge,
                                bottom = KixyuSpacing.medium,
                            ),
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                            ) {
                                Text(current.title, style = MaterialTheme.typography.titleLarge)
                                Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                                    Text(
                                        stringResource(R.string.reader_author),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(current.author, style = MaterialTheme.typography.bodyLarge)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                                    Text(
                                        stringResource(R.string.reader_description),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        if (current.description.isBlank()) {
                                            stringResource(R.string.reader_no_description)
                                        } else {
                                            current.description
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (current.description.isBlank()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(
                            start = KixyuSpacing.large,
                            end = KixyuSpacing.large,
                            bottom = KixyuSpacing.medium,
                        ),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        KixyuTextButton(text = stringResource(R.string.reader_close), onClick = dismiss)
                    }
                }
            }
        }
    }
}

internal fun readerPalette(settings: ReaderSettings, systemDark: Boolean): ReaderRenderPalette {
    // Built-in night reading always uses true black for OLED panels. Custom reading themes
    // intentionally keep the exact background selected by the user below.
    val dark = ReaderRenderPalette(Color.Black, Color(0xFFD9D9D0), Color(0xFFF0F0E7), Color(0xFFB8CCBD), Color(0xFF92948B))
    val day = ReaderRenderPalette(Color(0xFFFAF8F2), Color(0xFF282620), Color(0xFF171713), Color(0xFF52655A), Color(0xFF716F67))
    val useNightColors = when (settings.theme) {
        ReaderTheme.SYSTEM -> systemDark
        ReaderTheme.DAY -> false
        ReaderTheme.NIGHT -> true
    }
    val default = if (useNightColors) dark else day
    if (!settings.customThemeEnabled) return default
    val custom = if (useNightColors) settings.customNightTheme else settings.customDayTheme
    return ReaderRenderPalette(
        custom.backgroundHex.colorOr(default.background),
        custom.bodyHex.colorOr(default.body),
        custom.titleHex.colorOr(default.title),
        custom.accentHex.colorOr(default.accent),
        custom.bodyHex.colorOr(default.secondary).copy(alpha = .62f),
    )
}

internal fun String.colorOr(fallback: Color) = runCatching { Color(toColorInt()) }.getOrDefault(fallback)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Pagination inputs are almost always unique remainders. Caching their TextLayoutResult objects
// retains large native buffers without producing useful hits, especially for malformed EPUB text.
internal const val READER_TEXT_MEASURE_CACHE_SIZE = 0
internal const val PAGER_NAVIGATION_RADIUS = 10
internal const val READER_OVERLAY_SETTLE_MILLIS = 320L
internal const val READER_CONTROL_FALLBACK_ACCENT_MIX = .1f
internal const val MIN_ICON_CONTRAST = 3f
