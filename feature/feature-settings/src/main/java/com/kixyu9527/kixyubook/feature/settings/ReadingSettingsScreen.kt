package com.kixyu9527.kixyubook.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOperationHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderColorEditorContent
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderResetAllAction
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderSettingsGroup
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderSettingsGroupContent
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderSettingsGroupEntry
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.ReaderSettingsUpdate
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuReaderCustomColorsTitle
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

/**
 * Global reading settings: one list of the four canonical groups plus the single reset action.
 * Every group opens as its own full page with its own toolbar.
 */
@Composable
fun ReadingSettingsRoute(
    onBack: () -> Unit,
    onOpenGroup: (KixyuReaderSettingsGroup) -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) = KixyuOperationHost(viewModel.operations) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var resetAllVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    KixyuPageScaffold(
        title = stringResource(R.string.settings_reading),
        largeTitle = false,
        showTopBar = !embedded,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) {
                    Icon(KixyuSymbols.ArrowBack, stringResource(R.string.settings_back))
                }
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuReaderSettingsHeader(
                    title = stringResource(R.string.settings_reading),
                    onReset = { resetAllVisible = true },
                )
            }
            item {
                KixyuSection(title = stringResource(R.string.settings_reading)) {
                    KixyuReaderSettingsGroup.entries.forEachIndexed { index, group ->
                        if (index > 0) {
                            KixyuDivider()
                        }
                        KixyuReaderSettingsGroupEntry(
                            group = group,
                            settings = state.settings,
                            fonts = state.fonts,
                            onClick = { onOpenGroup(group) },
                        )
                    }
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
    KixyuActionDialog(
        show = resetAllVisible,
        onDismissRequest = { resetAllVisible = false },
        title = stringResource(R.string.settings_reset_all_reader_settings),
        confirmLabel = stringResource(R.string.settings_reset_defaults),
        onConfirm = {
            resetAllVisible = false
            viewModel.resetAllReaderSettings()
        },
        dismissLabel = stringResource(R.string.settings_cancel),
    ) {
        Text(stringResource(R.string.settings_reset_reader_warning))
    }
}

/** One group as a standalone page; font and colour editors open as further pages. */
@Composable
fun ReadingGroupSettingsRoute(
    groupName: String,
    onBack: () -> Unit,
    onManageFonts: () -> Unit,
    onEditColors: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) = KixyuOperationHost(viewModel.operations) {
    val group = remember(groupName) {
        KixyuReaderSettingsGroup.entries.firstOrNull { it.name == groupName }
    }
    if (group == null) {
        // A stale saved route must not strand the user on a blank page; the pop is deferred until
        // the destination is resumed, because a back call during the push animation is dropped.
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(groupName, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                onBack()
            }
        }
        return@KixyuOperationHost
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    ReadingGroupSettingsPage(
        group = group,
        settings = state.settings,
        fonts = state.fonts,
        onBack = onBack,
        onSettingsChange = viewModel::update,
        onManageFonts = onManageFonts,
        onEditColors = onEditColors,
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    )
}

@Composable
internal fun ReadingGroupSettingsPage(
    group: KixyuReaderSettingsGroup,
    settings: ReaderSettings,
    fonts: List<UserFont>,
    onBack: () -> Unit,
    onSettingsChange: ReaderSettingsUpdate,
    onManageFonts: () -> Unit,
    onEditColors: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    KixyuPageScaffold(
        title = stringResource(group.titleRes),
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(KixyuSymbols.ArrowBack, stringResource(R.string.settings_back))
            }
        },
        snackbarHost = snackbarHost,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection(title = stringResource(group.titleRes)) {
                    Column(Modifier.fillMaxWidth()) {
                        KixyuReaderSettingsGroupContent(
                            group = group,
                            settings = settings,
                            fonts = fonts,
                            onSettingsChange = onSettingsChange,
                            onManageFonts = onManageFonts,
                            onEditColors = onEditColors,
                        )
                    }
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
}

/** The custom colour editor as a standalone page. */
@Composable
fun ReadingColorsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) = KixyuOperationHost(viewModel.operations) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    ReadingColorsPage(
        settings = state.settings,
        onBack = onBack,
        onSettingsChange = viewModel::update,
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    )
}

@Composable
internal fun ReadingColorsPage(
    settings: ReaderSettings,
    onBack: () -> Unit,
    onSettingsChange: ReaderSettingsUpdate,
    snackbarHost: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val title = kixyuReaderCustomColorsTitle()
    KixyuPageScaffold(
        title = title,
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(KixyuSymbols.ArrowBack, stringResource(R.string.settings_back))
            }
        },
        snackbarHost = snackbarHost,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection(title = title) {
                    KixyuReaderColorEditorContent(settings, onSettingsChange)
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
}

@Composable
private fun KixyuReaderSettingsHeader(title: String, onReset: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        onReset?.let { KixyuReaderResetAllAction(onClick = it) }
    }
}
