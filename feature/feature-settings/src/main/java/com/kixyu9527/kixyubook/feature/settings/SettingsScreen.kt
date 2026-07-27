package com.kixyu9527.kixyubook.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppColorControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderThemeControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.displayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onAppearance: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    val backupCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportBackup(it.toString()) }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingRestore = it.toString() }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.restoreCompleted.collect { restored = true } }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            LargeTopAppBar(
                title = { Text("设置", maxLines = 1) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection {
                    KixyuSettingsRow(
                        title = "外观",
                        supportingText = buildString {
                            append(state.settings.theme.displayName())
                            if (state.settings.customThemeEnabled) append(" · 自定义阅读配色")
                            append(" · ")
                            append(state.fonts.firstOrNull { it.uuid == state.settings.fontUuid }?.name ?: "系统字体")
                        },
                        icon = Icons.Outlined.Palette,
                        onClick = onAppearance,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item {
                KixyuSection(title = "阅读") {
                    KixyuSettingsRow(
                        title = "每日目标",
                        supportingText = "用于首页阅读进度统计",
                        icon = Icons.Outlined.Flag,
                    ) {
                        Text("${state.goalMinutes} 分钟", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    Slider(
                        value = state.goalMinutes.toFloat(),
                        onValueChange = { viewModel.setGoal(it.toInt()) },
                        valueRange = 5f..120f,
                        steps = 22,
                        modifier = Modifier.padding(horizontal = KixyuSpacing.rowHorizontal),
                    )
                }
            }
            item {
                KixyuSection(title = "备份") {
                    Row(
                        Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    ) {
                        Button(
                            onClick = { backupCreator.launch("KixyuBook-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.kixyubackup") },
                            enabled = !state.backupBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.SaveAlt, null, Modifier.size(KixyuSize.iconSmall))
                            Spacer(Modifier.width(KixyuSize.compactButtonIconGap))
                            Text("导出", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { backupPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                            enabled = !state.backupBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.backupBusy) {
                                CircularProgressIndicator(Modifier.size(KixyuSize.iconSmall), strokeWidth = KixyuSpacing.hairline)
                            } else {
                                Icon(Icons.Outlined.Restore, null, Modifier.size(KixyuSize.iconSmall))
                            }
                            Spacer(Modifier.width(KixyuSize.compactButtonIconGap))
                            Text("恢复", maxLines = 1)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(KixyuSize.bottomNavigationContentHeight)) }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复完整备份？", maxLines = 1) },
            text = { Text("当前书库和设置将被备份内容替换，完成后需要重新启动应用。") },
            confirmButton = { TextButton({ pendingRestore = null; viewModel.restoreBackup(uri) }) { Text("开始恢复") } },
            dismissButton = { TextButton({ pendingRestore = null }) { Text("取消") } },
        )
    }
    if (restored) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("恢复完成", maxLines = 1) },
            text = { Text("请关闭后重新打开应用，以加载恢复后的书库。") },
            confirmButton = { Button({ (context as? Activity)?.finishAffinity(); exitProcess(0) }) { Text("关闭应用") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            MediumTopAppBar(
                title = { Text("外观", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection(title = "主题") {
                    KixyuReaderThemeControls(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                    KixyuDivider()
                    KixyuAppColorControl(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                }
            }
            item {
                KixyuSection(title = "字体") {
                    KixyuSettingsRow(
                        title = "系统默认",
                        supportingText = "Android 系统字体",
                        icon = Icons.Outlined.FontDownload,
                        onClick = { viewModel.update { it.copy(fontUuid = null) } },
                    ) {
                        RadioButton(selected = state.settings.fontUuid == null, onClick = null)
                    }
                    state.fonts.forEach { font ->
                        KixyuDivider()
                        KixyuSettingsRow(
                            title = font.name,
                            supportingText = "用户字体",
                            onClick = { viewModel.update { it.copy(fontUuid = font.uuid) } },
                        ) {
                            RadioButton(selected = state.settings.fontUuid == font.uuid, onClick = null)
                            IconButton(onClick = { viewModel.deleteFont(font) }) {
                                Icon(Icons.Outlined.DeleteOutline, "删除字体", Modifier.size(KixyuSize.icon))
                            }
                        }
                    }
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "添加字体",
                        supportingText = "支持 TTF 与 OTF",
                        icon = Icons.Outlined.Add,
                        onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) },
                    )
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}
