package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSecondaryButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutRoute(
    onBack: () -> Unit,
    updateState: AppUpdateState,
    currentVersion: String,
    onCheckForUpdates: () -> Unit,
    onUpdateResultConsumed: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onOpenDiagnosticLog: () -> Unit,
    onOpenProjectSource: () -> Boolean,
    onContactTelegram: () -> Boolean,
    appLogo: @Composable () -> Unit,
    embedded: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openExternal: ((() -> Boolean), String) -> Unit = { open, errorMessage ->
        if (!open()) scope.launch { snackbar.showSnackbar(errorMessage) }
    }
    LaunchedEffect(updateState) {
        when (val result = updateState) {
            is AppUpdateState.UpToDate -> {
                snackbar.showSnackbar("当前已是最新版本 ${result.currentVersion}")
                onUpdateResultConsumed()
            }
            is AppUpdateState.Failed -> {
                snackbar.showSnackbar(result.message)
                onUpdateResultConsumed()
            }
            else -> Unit
        }
    }
    KixyuPageScaffold(
        title = "关于",
        largeTitle = false,
        showTopBar = !embedded,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) {
                    Icon(KixyuSymbols.ArrowBack, "返回")
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
            Modifier.kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection(title = "应用") {
                    KixyuSettingsRow(
                        title = "Kixyu Book",
                        supportingText = "本地离线小说阅读器",
                        leading = appLogo,
                    ) {
                        Text("v$currentVersion", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    KixyuDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    ) {
                        KixyuButton(
                            text = if (updateState == AppUpdateState.Checking) "正在检查…" else "检查更新",
                            onClick = onCheckForUpdates,
                            modifier = Modifier.weight(1f),
                            enabled = updateState != AppUpdateState.Checking,
                        )
                        KixyuSecondaryButton(
                            text = "更新日志",
                            onClick = onShowReleaseNotes,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                KixyuSection(title = "诊断") {
                    KixyuSettingsRow(
                        title = "日志详情",
                        supportingText = "查看同步、导入、解析与阅读性能记录",
                        icon = KixyuSymbols.Storage,
                        onClick = onOpenDiagnosticLog,
                    ) {
                        Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item {
                KixyuSection(title = "项目与联系") {
                    KixyuSettingsRow(
                        title = "项目源码",
                        supportingText = "github.com/kkyu9527/kixyubook",
                        onClick = {
                            openExternal(onOpenProjectSource, "无法打开 GitHub，请检查可用的浏览器")
                        },
                        leading = {
                            Icon(
                                painterResource(R.drawable.ic_brand_github),
                                null,
                                Modifier.size(KixyuSize.icon),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        Icon(KixyuSymbols.OpenInNew, null, Modifier.size(KixyuSize.icon))
                    }
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "Telegram 联系",
                        supportingText = "@kkyu9527s_bot",
                        onClick = {
                            openExternal(onContactTelegram, "无法打开 Telegram 联系链接")
                        },
                        leading = {
                            Icon(
                                painterResource(R.drawable.ic_brand_telegram),
                                null,
                                Modifier.size(KixyuSize.icon),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        Icon(KixyuSymbols.OpenInNew, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
}
