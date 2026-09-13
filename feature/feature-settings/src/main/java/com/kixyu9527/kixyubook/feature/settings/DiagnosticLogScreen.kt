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
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.flow.first
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    val resources = androidx.compose.ui.platform.LocalResources.current
    val localeConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val formatter = remember(resources, localeConfiguration) { DiagnosticLogFormatter(resources) }
    val sharedSession = LocalDiagnosticLogSession.current
    val session = remember(sharedSession) { sharedSession ?: DiagnosticLogSession() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var page by remember(session, categoryKey) {
        mutableStateOf(session.peek(localeConfiguration, categoryKey, onlyFailures))
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val revision by session.revision.collectAsStateWithLifecycle()
    val visibleEntries = page?.entries.orEmpty()
    val categorySummaries = page?.summaries.orEmpty()
    LaunchedEffect(session, localeConfiguration, onlyFailures, categoryKey, revision, lifecycle) {
        // Navigation 3 caps moving scenes at STARTED. Cached previews remain visible, while
        // uncached parsing and publishing a new list wait for the scene to settle.
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        try {
            val loaded = session.load(context, android.content.res.Configuration(localeConfiguration), categoryKey, onlyFailures) {
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            }
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            page = loaded
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            // Never leave the screen on an infinite spinner when the log cannot be read.
            page = DiagnosticLogPage(false, emptyList(), emptyList())
            snackbar.showSnackbar(resources.getString(R.string.diag_load_error))
        }
    }

    fun exportLog() {
        menuExpanded = false
        scope.launch {
            val latestLines = DiagnosticLog.snapshotLines()
            if (latestLines.isEmpty()) {
                snackbar.showSnackbar(resources.getString(R.string.diag_export_empty))
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
                        resources.getString(R.string.diag_export_title),
                    ),
                )
            }.onFailure {
                snackbar.showSnackbar(resources.getString(R.string.diag_export_error))
            }
        }
    }

    fun clearLog() {
        menuExpanded = false
        scope.launch {
            if (DiagnosticLog.clearAndAwait()) {
                session.invalidate()
                page = DiagnosticLogPage(false, emptyList(), emptyList())
                snackbar.showSnackbar(resources.getString(R.string.diag_cleared))
            } else {
                snackbar.showSnackbar(resources.getString(R.string.diag_clear_error))
            }
        }
    }

    KixyuPageScaffold(
        title = categoryKey?.let(formatter::diagnosticCategoryLabel) ?: stringResource(R.string.settings_log_details),
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(KixyuSymbols.ArrowBack, stringResource(R.string.settings_back))
            }
        },
        actions = {
            Box {
                KixyuIconButton(onClick = { menuExpanded = true }) {
                    Icon(KixyuSymbols.MoreVert, stringResource(R.string.settings_log_actions))
                }
                KixyuPopupMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    alignEnd = true,
                    items = listOf(
                        KixyuPopupMenuItem(
                            label = stringResource(R.string.diag_errors_only),
                            icon = KixyuSymbols.ErrorOutline,
                            enabled = page?.hasEntries == true,
                            selected = onlyFailures,
                            onClick = {
                                onOnlyFailuresChanged(!onlyFailures)
                                menuExpanded = false
                            },
                        ),
                        KixyuPopupMenuItem(
                            label = stringResource(R.string.diag_export),
                            icon = KixyuSymbols.Share,
                            enabled = page?.hasEntries == true,
                            onClick = ::exportLog,
                        ),
                        KixyuPopupMenuItem(
                            label = stringResource(R.string.diag_clear),
                            icon = KixyuSymbols.DeleteOutline,
                            enabled = page?.hasEntries == true,
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
            page == null -> Box(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            (if (categoryKey == null) categorySummaries.isEmpty() else visibleEntries.isEmpty()) -> Column(
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
                    if (onlyFailures) stringResource(R.string.diag_no_errors) else stringResource(R.string.diag_empty),
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
                    KixyuSection(title = stringResource(R.string.settings_log_categories_section)) {
                        categorySummaries.forEachIndexed { index, summary ->
                            KixyuSettingsRow(
                                title = summary.label,
                            supportingText = stringResource(
                                R.string.settings_log_category_summary,
                                summary.count,
                                summary.latestTime,
                            ),
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
                items(visibleEntries, key = { it.id }, contentType = { "diagnostic_entry" }) { entry ->
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
                            color = if (isFailure && index == 0) {
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
