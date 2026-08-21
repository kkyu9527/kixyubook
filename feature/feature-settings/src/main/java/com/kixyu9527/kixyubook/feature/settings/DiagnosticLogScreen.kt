package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenu
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import kotlinx.coroutines.launch

@Composable
fun DiagnosticLogRoute(
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onlyFailures: Boolean,
    onOnlyFailuresChanged: (Boolean) -> Unit,
) = DiagnosticLogScreen(
    onBack = onBack,
    onOpenCategory = onOpenCategory,
    onlyFailures = onlyFailures,
    onOnlyFailuresChanged = onOnlyFailuresChanged,
)

@Composable
fun DiagnosticLogCategoryRoute(
    categoryKey: String,
    onBack: () -> Unit,
    onlyFailures: Boolean,
    onOnlyFailuresChanged: (Boolean) -> Unit,
) = DiagnosticLogScreen(
    onBack = onBack,
    categoryKey = categoryKey,
    onlyFailures = onlyFailures,
    onOnlyFailuresChanged = onOnlyFailuresChanged,
)

@Composable
private fun DiagnosticLogScreen(
    onBack: () -> Unit,
    categoryKey: String? = null,
    onOpenCategory: (String) -> Unit = {},
    onlyFailures: Boolean,
    onOnlyFailuresChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var rawLines by remember { mutableStateOf<List<String>?>(null) }
    val newestEntries = remember(rawLines) {
        rawLines.orEmpty().asReversed().map(::parseDiagnosticEntry)
    }
    val filteredEntries = remember(newestEntries, onlyFailures) {
        filterDiagnosticEntries(newestEntries, onlyFailures = onlyFailures)
    }
    val visibleEntries = remember(filteredEntries, categoryKey) {
        filterDiagnosticEntries(filteredEntries, categoryKey = categoryKey)
    }
    val categorySummaries = remember(filteredEntries) {
        filteredEntries.groupBy(ReadableDiagnosticEntry::categoryKey).map { (key, entries) ->
            DiagnosticCategorySummary(
                key = key,
                label = entries.first().category,
                count = entries.size,
                latestTime = entries.first().time,
            )
        }
    }

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
        title = categoryKey?.let(::diagnosticCategoryLabel) ?: "日志详情",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(KixyuSymbols.ArrowBack, "返回")
            }
        },
        actions = {
            Box {
                KixyuIconButton(onClick = { menuExpanded = true }) {
                    Icon(KixyuSymbols.MoreVert, "日志操作")
                }
                KixyuPopupMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    alignEnd = true,
                    items = listOf(
                        KixyuPopupMenuItem(
                            label = "仅显示异常",
                            icon = KixyuSymbols.ErrorOutline,
                            enabled = newestEntries.isNotEmpty(),
                            selected = onlyFailures,
                            onClick = {
                                onOnlyFailuresChanged(!onlyFailures)
                                menuExpanded = false
                            },
                        ),
                        KixyuPopupMenuItem(
                            label = "导出",
                            icon = KixyuSymbols.Share,
                            enabled = newestEntries.isNotEmpty(),
                            onClick = ::exportLog,
                        ),
                        KixyuPopupMenuItem(
                            label = "清空",
                            icon = KixyuSymbols.DeleteOutline,
                            enabled = newestEntries.isNotEmpty(),
                            onClick = ::clearLog,
                        ),
                    ),
                )
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
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

            visibleEntries.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    KixyuSymbols.Description,
                    null,
                    Modifier.size(KixyuSize.icon * 2),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (onlyFailures) "暂无异常日志" else "暂无诊断日志",
                    modifier = Modifier.padding(top = KixyuSpacing.medium),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            categoryKey == null -> LazyColumn(
                modifier = Modifier.kixyuPageContentWidth()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = KixyuSpacing.screenHorizontal,
                    vertical = KixyuSpacing.screenVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                item {
                    KixyuSection(title = "日志分类") {
                        categorySummaries.forEachIndexed { index, summary ->
                            KixyuSettingsRow(
                                title = summary.label,
                                supportingText = "${summary.count} 条记录 · 最新 ${summary.latestTime}",
                                onClick = { onOpenCategory(summary.key) },
                                leading = {
                                    Icon(
                                        KixyuSymbols.Description,
                                        null,
                                        Modifier.size(KixyuSize.icon),
                                    )
                                },
                            ) {
                                Icon(
                                    KixyuSymbols.KeyboardArrowRight,
                                    null,
                                    Modifier.size(KixyuSize.icon),
                                )
                            }
                            if (index < categorySummaries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(
                                        start = KixyuSpacing.rowHorizontal + KixyuSize.icon + KixyuSpacing.medium,
                                    ),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
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
                items(visibleEntries) { entry ->
                    SelectionContainer {
                        DiagnosticEntryCard(entry)
                    }
                }
            }
        }
    }
}

private data class DiagnosticCategorySummary(
    val key: String,
    val label: String,
    val count: Int,
    val latestTime: String,
)

@Composable
private fun DiagnosticEntryCard(entry: ReadableDiagnosticEntry) {
    val badgeColor = if (entry.isFailure) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val badgeContentColor = if (entry.isFailure) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = KixyuSpacing.rowHorizontal,
                vertical = KixyuSpacing.medium,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = badgeColor,
                ) {
                    Text(
                        entry.category,
                        modifier = Modifier.padding(
                            horizontal = KixyuSpacing.small,
                            vertical = KixyuSpacing.extraSmall,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeContentColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    entry.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                entry.title,
                modifier = Modifier.padding(top = KixyuSpacing.small),
                style = MaterialTheme.typography.titleMedium,
                color = if (entry.isFailure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                entry.description,
                modifier = Modifier.padding(top = KixyuSpacing.extraSmall),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.details.isNotEmpty()) {
                Spacer(Modifier.size(KixyuSpacing.medium))
                DiagnosticDetailsTable(entry.details, entry.isFailure)
            }
        }
    }
}

@Composable
private fun DiagnosticDetailsTable(
    details: List<Pair<String, String>>,
    isFailure: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        BoxWithConstraints {
            val labelWidth = if (maxWidth >= 480.dp) 144.dp else 112.dp
            Column {
                details.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = KixyuSpacing.medium,
                                vertical = KixyuSpacing.small,
                            ),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.width(labelWidth),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(KixyuSpacing.medium))
                        Text(
                            text = value,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isFailure && label == "结果") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    if (index < details.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = KixyuSpacing.medium + labelWidth + KixyuSpacing.medium,
                                end = KixyuSpacing.medium,
                            ),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}
