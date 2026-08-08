package com.kixyu9527.kixyubook.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenu
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import kotlinx.coroutines.launch

@Composable
fun DiagnosticLogRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var rawLines by remember { mutableStateOf<List<String>?>(null) }
    val newestLines = rawLines?.asReversed().orEmpty()

    LaunchedEffect(Unit) {
        rawLines = DiagnosticLog.snapshotLines()
    }

    fun exportLog() {
        menuExpanded = false
        scope.launch {
            val latestLines = DiagnosticLog.snapshotLines()
            rawLines = latestLines
            if (latestLines.isEmpty()) {
                snackbar.showSnackbar("暂无可导出的诊断记录")
                return@launch
            }
            runCatching {
                val file = createReadableDiagnosticExport(context, latestLines)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "导出诊断日志",
                    ),
                )
            }.onFailure {
                snackbar.showSnackbar("无法导出诊断日志")
            }
        }
    }

    fun clearLog() {
        menuExpanded = false
        scope.launch {
            if (DiagnosticLog.clearAndAwait()) {
                rawLines = emptyList()
                snackbar.showSnackbar("诊断日志已清空")
            } else {
                snackbar.showSnackbar("无法清空诊断日志")
            }
        }
    }

    KixyuPageScaffold(
        title = "日志详情",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
            }
        },
        actions = {
            Box {
                KixyuIconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, "日志操作")
                }
                KixyuPopupMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    alignEnd = true,
                    items = listOf(
                        KixyuPopupMenuItem(
                            label = "导出",
                            icon = Icons.Outlined.Share,
                            enabled = newestLines.isNotEmpty(),
                            onClick = ::exportLog,
                        ),
                        KixyuPopupMenuItem(
                            label = "清空",
                            icon = Icons.Outlined.DeleteOutline,
                            enabled = newestLines.isNotEmpty(),
                            onClick = ::clearLog,
                        ),
                    ),
                )
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
        when {
            rawLines == null -> Box(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            newestLines.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    null,
                    Modifier.size(KixyuSize.icon * 2),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "暂无诊断日志",
                    modifier = Modifier.padding(top = KixyuSpacing.medium),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.kixyuPageContentWidth()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = KixyuSpacing.screenHorizontal,
                    vertical = KixyuSpacing.screenVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                items(newestLines.size) { index ->
                    val rawLine = newestLines[index]
                    val entry = remember(rawLine) { parseDiagnosticEntry(rawLine) }
                    SelectionContainer {
                        DiagnosticEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticEntryCard(entry: ReadableDiagnosticEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = KixyuSpacing.rowHorizontal, vertical = KixyuSpacing.rowVertical)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.category,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    entry.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                entry.title,
                modifier = Modifier.padding(top = KixyuSpacing.small),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                entry.description,
                modifier = Modifier.padding(top = KixyuSpacing.extraSmall),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.details.isNotEmpty()) {
                Spacer(Modifier.size(KixyuSpacing.small))
                entry.details.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = KixyuSpacing.extraSmall),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            label,
                            modifier = Modifier.widthIn(min = KixyuSize.rowMinHeight),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
