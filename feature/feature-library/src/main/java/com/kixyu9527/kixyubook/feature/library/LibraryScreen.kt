package com.kixyu9527.kixyubook.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuEdgeToEdgeDialogProperties
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenu
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.ui.BookCover
import com.kixyu9527.kixyubook.core.ui.LibraryEmptyState

@Composable
fun LibraryRoute(
    onOpenBook: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris.map { it.toString() })
    }
    LaunchedEffect(Unit) { viewModel.messageEvents.collect { if (it.isNotBlank()) snackbar.showSnackbar(it) } }
    LibraryScreen(
        state = state,
        snackbar = snackbar,
        onSearch = viewModel::search,
        onCategory = viewModel::selectCategory,
        onImport = { picker.launch(arrayOf("text/plain", "application/epub+zip", "application/zip", "application/octet-stream")) },
        onOpenBook = onOpenBook,
        onDelete = viewModel::delete,
        onDeleteMany = viewModel::deleteBooks,
        onReparse = viewModel::reparseTxt,
        onUpdateMetadata = viewModel::updateMetadata,
        onSetCategory = viewModel::setCategory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    onSearch: (String) -> Unit,
    onCategory: (String) -> Unit,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteMany: (Set<String>) -> Unit,
    onReparse: (String) -> Unit,
    onUpdateMetadata: (String, String, String, String) -> Unit,
    onSetCategory: (String, String) -> Unit,
) {
    var managing by remember { mutableStateOf<LibraryBook?>(null) }
    var deleting by remember { mutableStateOf<LibraryBook?>(null) }
    var reparsing by remember { mutableStateOf<LibraryBook?>(null) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedBookUuids by remember { mutableStateOf(emptySet<String>()) }
    var confirmingBatchDelete by remember { mutableStateOf(false) }
    val visibleBookUuids = state.books.mapTo(linkedSetOf()) { it.book.uuid }
    LaunchedEffect(visibleBookUuids) {
        selectedBookUuids = selectedBookUuids.intersect(visibleBookUuids)
        if (visibleBookUuids.isEmpty()) selectionMode = false
    }
    KixyuPageScaffold(
        title = if (selectionMode) "已选择 ${selectedBookUuids.size} 本" else "书库",
        modifier = Modifier.fillMaxSize(),
        actions = {
                    if (selectionMode) {
                        KixyuIconButton(
                            onClick = {
                                selectedBookUuids = if (selectedBookUuids.size == visibleBookUuids.size) {
                                    emptySet()
                                } else visibleBookUuids
                            },
                        ) { Icon(Icons.Outlined.SelectAll, "全选") }
                        KixyuIconButton(
                            onClick = { confirmingBatchDelete = true },
                            enabled = selectedBookUuids.isNotEmpty(),
                        ) { Icon(Icons.Outlined.DeleteSweep, "删除所选书籍") }
                        KixyuIconButton(
                            onClick = {
                                selectionMode = false
                                selectedBookUuids = emptySet()
                            },
                        ) { Icon(Icons.Outlined.Close, "退出批量选择") }
                    } else {
                        Box {
                            KixyuIconButton(onClick = { optionsExpanded = true }) {
                                Icon(Icons.Outlined.MoreVert, "书库操作")
                            }
                            KixyuPopupMenu(
                                expanded = optionsExpanded,
                                onDismissRequest = { optionsExpanded = false },
                                alignEnd = true,
                                items = listOf(
                                    KixyuPopupMenuItem(
                                        label = if (state.importing) "正在导入" else "导入书籍",
                                        icon = Icons.Outlined.Add,
                                        enabled = !state.importing,
                                    ) {
                                        optionsExpanded = false
                                        onImport()
                                    },
                                    KixyuPopupMenuItem(
                                        label = "批量删除",
                                        icon = Icons.Outlined.DeleteSweep,
                                        enabled = state.books.isNotEmpty(),
                                    ) {
                                        optionsExpanded = false
                                        selectedBookUuids = emptySet()
                                        selectionMode = true
                                    },
                                ),
                            )
                        }
                    }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            item {
                TextField(
                    value = state.query,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索书名或作者", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(KixyuSize.icon)) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton({ onSearch("") }) {
                            Icon(Icons.Outlined.Close, "清除", Modifier.size(KixyuSize.icon))
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                ) {
                    state.categories.forEach { category ->
                        FilterChip(state.category == category, { onCategory(category) }, { Text(category, maxLines = 1) })
                    }
                }
            }
            if (state.books.isEmpty()) {
                item { LibraryEmptyState(Modifier.fillParentMaxSize().padding(KixyuSpacing.extraLarge)) }
            }
            items(state.books, key = { it.book.uuid }) { item ->
                LibraryBookRow(
                    item = item,
                    selected = item.book.uuid in selectedBookUuids,
                    selectionMode = selectionMode,
                    onOpen = {
                        if (selectionMode) {
                            selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                        } else onOpenBook(item.book.uuid)
                    },
                    onSelectionChange = {
                        selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                    },
                    onManage = { managing = item },
                    onDelete = { deleting = item },
                )
            }
            item { Spacer(Modifier.height(KixyuSize.bottomNavigationContentHeight)) }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    managing?.let { item ->
        BookManagementDialog(
            item = item,
            dismiss = { managing = null },
            reparse = { managing = null; reparsing = item },
            save = { title, author, description, category ->
                onUpdateMetadata(item.book.uuid, title, author, description)
                onSetCategory(item.book.uuid, category)
                managing = null
            },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            properties = KixyuEdgeToEdgeDialogProperties,
            title = { Text("删除《${item.book.title}》？", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text("书籍文件、阅读进度和统计也会一并删除。") },
            confirmButton = { TextButton({ onDelete(item.book.uuid); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton({ deleting = null }) { Text("取消") } },
        )
    }
    reparsing?.let { item ->
        AlertDialog(
            onDismissRequest = { reparsing = null },
            properties = KixyuEdgeToEdgeDialogProperties,
            title = { Text("重新解析正文？", maxLines = 1) },
            text = { Text("将按新的编码和章节规则重建目录。书籍信息、分类和可恢复的阅读位置会保留。") },
            confirmButton = {
                TextButton({ onReparse(item.book.uuid); reparsing = null }) { Text("重新解析") }
            },
            dismissButton = { TextButton({ reparsing = null }) { Text("取消") } },
        )
    }
    if (confirmingBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmingBatchDelete = false },
            properties = KixyuEdgeToEdgeDialogProperties,
            title = { Text("删除选中的 ${selectedBookUuids.size} 本书？", maxLines = 1) },
            text = { Text("书籍文件、阅读进度和统计也会一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMany(selectedBookUuids)
                        confirmingBatchDelete = false
                        selectionMode = false
                        selectedBookUuids = emptySet()
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton({ confirmingBatchDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LibraryBookRow(
    item: LibraryBook,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onSelectionChange: () -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(KixyuSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        ) {
            BookCover(item.book.title, item.book.coverPath, Modifier.size(KixyuSize.libraryCoverWidth, KixyuSize.libraryCoverHeight))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.book.format.name} · ${item.book.category}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.progress?.let {
                    LinearProgressIndicator(
                        progress = { it.fraction },
                        modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
                    )
                }
            }
            if (selectionMode) {
                Checkbox(selected, onCheckedChange = { onSelectionChange() })
            } else Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, "更多操作", Modifier.size(KixyuSize.icon))
                }
                Text(
                    "${((item.progress?.fraction ?: 0f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("管理", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(KixyuSize.icon)) },
                        onClick = { menuExpanded = false; onManage() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(KixyuSize.icon)) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun BookManagementDialog(
    item: LibraryBook,
    dismiss: () -> Unit,
    reparse: () -> Unit,
    save: (String, String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf(item.book.title) }
    var author by remember { mutableStateOf(item.book.author) }
    var description by remember { mutableStateOf(item.book.description) }
    var category by remember { mutableStateOf(item.book.category) }
    AlertDialog(
        onDismissRequest = dismiss,
        properties = KixyuEdgeToEdgeDialogProperties,
        title = { Text("编辑书籍", maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                OutlinedTextField(title, { title = it }, label = { Text("书名") }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text("作者") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("简介") }, minLines = 2, maxLines = 4)
                OutlinedTextField(category, { category = it }, label = { Text("分类") }, singleLine = true)
                if (item.book.format == com.kixyu9527.kixyubook.core.common.model.BookFormat.TXT) {
                    OutlinedButton(onClick = reparse, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(KixyuSize.iconSmall))
                        Spacer(Modifier.size(KixyuSize.compactButtonIconGap))
                        Text("重新解析正文", maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton({ save(title, author, description, category) }) { Text("保存") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}
