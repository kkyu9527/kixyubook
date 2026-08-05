package com.kixyu9527.kixyubook.feature.library

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenu
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
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
        onDropDocuments = { uris, releasePermission ->
            viewModel.import(uris) { releasePermission?.invoke() }
        },
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
    onDropDocuments: (List<String>, (() -> Unit)?) -> Unit,
) {
    var managingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var reparsingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val stringSetSaver = remember {
        Saver<Set<String>, List<String>>(save = { it.toList() }, restore = { it.toSet() })
    }
    var selectedBookUuids by rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf(emptySet()) }
    var confirmingBatchDelete by rememberSaveable { mutableStateOf(false) }
    var previewBookUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val managing = state.books.firstOrNull { it.book.uuid == managingUuid }
    val deleting = state.books.firstOrNull { it.book.uuid == deletingUuid }
    val reparsing = state.books.firstOrNull { it.book.uuid == reparsingUuid }
    val visibleBookUuids = state.books.mapTo(linkedSetOf()) { it.book.uuid }
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
    val expanded = kixyuWindowSizeClass().supportsTwoPane
    val activity = LocalContext.current.findActivity()
    val latestDropDocuments by rememberUpdatedState(onDropDocuments)
    val dropTarget = remember(activity) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val androidEvent = event.toAndroidDragEvent()
                val clipData = androidEvent.clipData ?: return false
                val uris = buildList {
                    repeat(clipData.itemCount) {
                        clipData.getItemAt(it).uri?.toString()?.let(::add)
                    }
                }
                if (uris.isEmpty()) return false
                val permission = activity?.requestDragAndDropPermissions(androidEvent)
                latestDropDocuments(uris) { permission?.release() }
                return true
            }
        }
    }
    LaunchedEffect(visibleBookUuids) {
        selectedBookUuids = selectedBookUuids.intersect(visibleBookUuids)
        if (visibleBookUuids.isEmpty()) selectionMode = false
        if (previewBookUuid !in visibleBookUuids) previewBookUuid = state.books.firstOrNull()?.book?.uuid
    }
    KixyuPageScaffold(
        title = if (selectionMode) "已选择 ${selectedBookUuids.size} 本" else "书库",
        modifier = Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.mimeTypes().any { mime ->
                    mime == ClipDescription.MIMETYPE_TEXT_URILIST ||
                        mime == "text/plain" ||
                        mime == "application/epub+zip" ||
                        mime == "application/zip" ||
                        mime == "application/octet-stream"
                }
            },
            target = dropTarget,
        ),
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
                                        label = "导入书籍",
                                        icon = Icons.Outlined.Add,
                                        enabled = true,
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
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = navigationContentPadding + KixyuSpacing.small)
                    .padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    ) { innerPadding ->
        if (expanded) {
            Row(
                modifier = Modifier.kixyuPageContentWidth(KixyuSize.expandedPageContentMaxWidth)
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(
                        start = KixyuSpacing.screenHorizontal,
                        top = KixyuSpacing.screenVertical,
                        end = KixyuSpacing.screenHorizontal,
                    ),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
            ) {
                Column(
                    modifier = Modifier.weight(.58f).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                ) {
                    LibraryFilters(state, onSearch, onCategory)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                        contentPadding = PaddingValues(bottom = navigationContentPadding),
                    ) {
                        if (state.books.isEmpty()) {
                            item { LibraryEmptyState(Modifier.fillParentMaxSize().padding(KixyuSpacing.extraLarge)) }
                        }
                        items(state.books, key = { it.book.uuid }) { item ->
                            LibraryBookRow(
                                item = item,
                                selected = if (selectionMode) item.book.uuid in selectedBookUuids
                                else item.book.uuid == previewBookUuid,
                                selectionMode = selectionMode,
                                onOpen = {
                                    if (selectionMode) selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                                    else previewBookUuid = item.book.uuid
                                },
                                onSelectionChange = { selectedBookUuids = selectedBookUuids.toggle(item.book.uuid) },
                                onManage = { managingUuid = item.book.uuid },
                                onDelete = { deletingUuid = item.book.uuid },
                            )
                        }
                    }
                }
                LibraryBookDetailPane(
                    item = state.books.firstOrNull { it.book.uuid == previewBookUuid },
                    onOpen = onOpenBook,
                    onManage = { managingUuid = it },
                    onDelete = { deletingUuid = it },
                    modifier = Modifier.weight(.42f).fillMaxSize(),
                )
            }
        } else {
            LazyColumn(
                Modifier.kixyuPageContentWidth()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = KixyuSpacing.screenHorizontal,
                    vertical = KixyuSpacing.screenVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                item { LibraryFilters(state, onSearch, onCategory) }
                if (state.books.isEmpty()) {
                    item { LibraryEmptyState(Modifier.fillParentMaxSize().padding(KixyuSpacing.extraLarge)) }
                }
                items(state.books, key = { it.book.uuid }) { item ->
                    LibraryBookRow(
                        item = item,
                        selected = item.book.uuid in selectedBookUuids,
                        selectionMode = selectionMode,
                        onOpen = {
                            if (selectionMode) selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                            else onOpenBook(item.book.uuid)
                        },
                        onSelectionChange = { selectedBookUuids = selectedBookUuids.toggle(item.book.uuid) },
                        onManage = { managingUuid = item.book.uuid },
                        onDelete = { deletingUuid = item.book.uuid },
                    )
                }
                item { Spacer(Modifier.height(navigationContentPadding)) }
                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }

    managing?.let { item ->
        BookManagementDialog(
            item = item,
            dismiss = { managingUuid = null },
            reparse = { managingUuid = null; reparsingUuid = item.book.uuid },
            save = { title, author, description, category ->
                onUpdateMetadata(item.book.uuid, title, author, description)
                onSetCategory(item.book.uuid, category)
                managingUuid = null
            },
        )
    }
    deleting?.let { item ->
        KixyuActionDialog(
            show = true,
            title = "删除《${item.book.title}》？",
            onDismissRequest = { deletingUuid = null },
            confirmLabel = "删除",
            onConfirm = { onDelete(item.book.uuid); deletingUuid = null },
        ) { Text("书籍文件、阅读进度和统计也会一并删除。") }
    }
    reparsing?.let { item ->
        KixyuActionDialog(
            show = true,
            title = "重新解析正文？",
            onDismissRequest = { reparsingUuid = null },
            confirmLabel = "重新解析",
            onConfirm = { onReparse(item.book.uuid); reparsingUuid = null },
        ) { Text("将按新的编码和章节规则重建目录。书籍信息、分类和可恢复的阅读位置会保留。") }
    }
    if (confirmingBatchDelete) {
        KixyuActionDialog(
            show = true,
            title = "删除选中的 ${selectedBookUuids.size} 本书？",
            onDismissRequest = { confirmingBatchDelete = false },
            confirmLabel = "删除",
            onConfirm = {
                onDeleteMany(selectedBookUuids)
                confirmingBatchDelete = false
                selectionMode = false
                selectedBookUuids = emptySet()
            },
        ) { Text("书籍文件、阅读进度和统计也会一并删除。") }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun LibraryFilters(
    state: LibraryUiState,
    onSearch: (String) -> Unit,
    onCategory: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            state.categories.forEach { category ->
                FilterChip(state.category == category, { onCategory(category) }, { Text(category, maxLines = 1) })
            }
        }
    }
}

@Composable
private fun LibraryBookDetailPane(
    item: LibraryBook?,
    onOpen: (String) -> Unit,
    onManage: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("选择一本书查看详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(KixyuSpacing.large),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BookCover(
                    item.book.title,
                    item.book.coverPath,
                    Modifier.size(KixyuSize.libraryDetailCoverWidth, KixyuSize.libraryDetailCoverHeight),
                )
                Text(
                    item.book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.book.author,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.book.description.ifBlank { "暂无简介" },
                    modifier = Modifier.weight(1f, fill = false),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                item.progress?.let {
                    LinearProgressIndicator(
                        progress = { it.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
                    )
                }
                KixyuButton(
                    text = if (item.progress == null) "开始阅读" else "继续阅读",
                    onClick = { onOpen(item.book.uuid) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                    OutlinedButton(onClick = { onManage(item.book.uuid) }, modifier = Modifier.weight(1f)) {
                        Text("管理", maxLines = 1)
                    }
                    OutlinedButton(onClick = { onDelete(item.book.uuid) }, modifier = Modifier.weight(1f)) {
                        Text("删除", maxLines = 1)
                    }
                }
            }
        }
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
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(onManage) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            onManage()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .semantics { contentDescription = "打开书籍：${item.book.title}" },
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
    var title by rememberSaveable(item.book.uuid) { mutableStateOf(item.book.title) }
    var author by rememberSaveable(item.book.uuid) { mutableStateOf(item.book.author) }
    var description by rememberSaveable(item.book.uuid) { mutableStateOf(item.book.description) }
    var category by rememberSaveable(item.book.uuid) { mutableStateOf(item.book.category) }
    KixyuActionDialog(
        show = true,
        title = "编辑书籍",
        onDismissRequest = dismiss,
        confirmLabel = "保存",
        onConfirm = { save(title, author, description, category) },
    ) {
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
    }
}
