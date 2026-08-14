package com.kixyu9527.kixyubook.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.reader.engine.rememberReaderFont

private val FONT_MIME_TYPES = arrayOf(
    "font/ttf",
    "font/otf",
    "application/x-font-ttf",
    "application/octet-stream",
)

@Composable
fun FontManagementRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDeletion by remember { mutableStateOf<UserFont?>(null) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    KixyuPageScaffold(
        title = "阅读字体",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(KixyuSymbols.ArrowBack, "返回")
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection(title = "字体") {
                    FontChoiceRow(
                        name = "系统默认",
                        description = "跟随设备字体，体积最小",
                        font = null,
                        selected = state.settings.fontUuid == null,
                        onSelect = { viewModel.update { it.copy(fontUuid = null) } },
                    )
                }
            }
            item {
                KixyuSection(title = "我的字体") {
                    KixyuSettingsRow(
                        title = "导入字体",
                        supportingText = "支持 TTF / OTF，导入后自动应用",
                        icon = KixyuSymbols.Add,
                        onClick = { fontPicker.launch(FONT_MIME_TYPES) },
                    ) {
                        Icon(KixyuSymbols.ChevronRight, null)
                    }
                    state.fonts.forEach { font ->
                        KixyuDivider()
                        FontChoiceRow(
                            name = font.name,
                            description = "用户导入 · TTF / OTF",
                            font = font,
                            selected = state.settings.fontUuid == font.uuid,
                            onSelect = { viewModel.update { it.copy(fontUuid = font.uuid) } },
                            onDelete = { pendingDeletion = font },
                        )
                    }
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }

    val fontToDelete = pendingDeletion
    KixyuActionDialog(
        show = fontToDelete != null,
        title = "删除字体",
        onDismissRequest = { pendingDeletion = null },
        confirmLabel = "删除",
        onConfirm = {
            fontToDelete?.let(viewModel::deleteFont)
            pendingDeletion = null
        },
    ) {
        Text("将从设备和同步数据中删除“${fontToDelete?.name.orEmpty()}”。")
    }
}

@Composable
private fun FontChoiceRow(
    name: String,
    description: String,
    font: UserFont?,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val previewFamily = font?.let { rememberReaderFont(it.filePath) } ?: FontFamily.Default
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val previewBackground = if (selected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = .14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    KixyuSettingsRow(
        title = name,
        supportingText = description,
        selected = selected,
        onClick = onSelect,
        leading = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(previewBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "阅",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = previewFamily,
                    color = foreground,
                )
            }
        },
    ) {
        if (onDelete == null) {
            if (selected) Icon(KixyuSymbols.Check, "当前字体", tint = foreground)
        } else {
            if (selected) Icon(KixyuSymbols.Check, "当前字体", tint = foreground)
            KixyuIconButton(onClick = onDelete) {
                Icon(
                    KixyuSymbols.DeleteOutline,
                    "删除 $name",
                    tint = if (selected) foreground else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
