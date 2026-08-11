package com.kixyu9527.kixyubook.feature.library

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenu
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.ui.BookCover
import com.kixyu9527.kixyubook.core.ui.LibraryEmptyState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LibraryRoute(
    onOpenBook: (String) -> Unit,
    hiddenOnly: Boolean = false,
    onBack: () -> Unit = {},
    onOpenHiddenLibrary: () -> Unit = {},
    externalImportRequestId: Long? = null,
    externalImportUris: List<String> = emptyList(),
    onExternalImportConsumed: (Long) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val stateFlow = if (hiddenOnly) viewModel.hiddenUiState else viewModel.uiState
    val state by stateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris.map { it.toString() })
    }
    var pendingExportBookUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val exportTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val bookUuid = pendingExportBookUuid
        pendingExportBookUuid = null
        if (uri != null && bookUuid != null) viewModel.export(bookUuid, uri.toString())
    }
    val exportEpub = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip"),
    ) { uri ->
        val bookUuid = pendingExportBookUuid
        pendingExportBookUuid = null
        if (uri != null && bookUuid != null) viewModel.export(bookUuid, uri.toString())
    }
    val beginExport: (LibraryBook) -> Unit = { item ->
        pendingExportBookUuid = item.book.uuid
        val fileName = exportFileName(item)
        when (item.book.format) {
            BookFormat.EPUB -> exportEpub.launch(fileName)
            else -> exportTxt.launch(fileName)
        }
    }
    LaunchedEffect(externalImportRequestId) {
        val requestId = externalImportRequestId ?: return@LaunchedEffect
        if (externalImportUris.isEmpty()) {
            onExternalImportConsumed(requestId)
        } else {
            viewModel.import(externalImportUris) { onExternalImportConsumed(requestId) }
        }
    }
    LaunchedEffect(Unit) { viewModel.messageEvents.collect { if (it.isNotBlank()) snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { event ->
            val result = snackbar.showSnackbar(
                message = "书籍已导出",
                actionLabel = "查看",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed && !openExportLocation(context, event.uriString)) {
                snackbar.showSnackbar("系统无法打开导出位置")
            }
        }
    }
    LibraryScreen(
        state = state,
        snackbar = snackbar,
        onSearch = viewModel::search,
        onCategory = viewModel::selectCategory,
        onSortMode = viewModel::setSortMode,
        onLayoutMode = viewModel::setLayoutMode,
        onMoveBook = viewModel::moveBook,
        onFinishReorder = viewModel::finishCustomReorder,
        onSetCategoryHidden = viewModel::setCategoryHidden,
        onOpenHiddenLibrary = onOpenHiddenLibrary,
        onBack = onBack,
        onImport = { picker.launch(arrayOf("text/plain", "application/epub+zip", "application/zip", "application/octet-stream")) },
        onOpenBook = onOpenBook,
        onDelete = viewModel::delete,
        onDeleteMany = viewModel::deleteBooks,
        onExport = beginExport,
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
    onSortMode: (LibrarySortMode) -> Unit,
    onLayoutMode: (LibraryLayoutMode) -> Unit,
    onMoveBook: (String, String) -> Unit,
    onFinishReorder: () -> Unit,
    onSetCategoryHidden: (String, Boolean) -> Unit,
    onOpenHiddenLibrary: () -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteMany: (Set<String>) -> Unit,
    onExport: (LibraryBook) -> Unit,
    onUpdateMetadata: (String, String, String, String) -> Unit,
    onSetCategory: (String, String) -> Unit,
    onDropDocuments: (List<String>, (() -> Unit)?) -> Unit,
) {
    var managingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var displayDialogVisible by rememberSaveable { mutableStateOf(false) }
    var categoryDialogVisible by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val stringSetSaver = remember {
        Saver<Set<String>, List<String>>(save = { it.toList() }, restore = { it.toSet() })
    }
    var selectedBookUuids by rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf(emptySet()) }
    var confirmingBatchDelete by rememberSaveable { mutableStateOf(false) }
    var previewBookUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val managing = state.books.firstOrNull { it.book.uuid == managingUuid }
    val deleting = state.books.firstOrNull { it.book.uuid == deletingUuid }
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
        title = if (selectionMode) "已选择 ${selectedBookUuids.size} 本" else if (state.hiddenOnly) "隐藏书架" else "书库",
        modifier = Modifier
            .fillMaxSize()
            .revealHiddenCategoriesGesture(
                enabled = !state.hiddenOnly && state.hiddenCategories.isNotEmpty(),
                onReveal = onOpenHiddenLibrary,
            )
            .dragAndDropTarget(
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
        navigationIcon = {
            when {
                selectionMode -> KixyuIconButton(
                    onClick = {
                        selectionMode = false
                        selectedBookUuids = emptySet()
                    },
                ) {
                    Icon(Icons.Outlined.Close, "退出批量选择")
                }
                state.hiddenOnly -> KixyuIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
            }
        },
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
            } else {
                if (!state.hiddenOnly) {
                    KixyuIconButton(onClick = onImport) {
                        Icon(Icons.Outlined.Add, "导入书籍")
                    }
                    if (state.hiddenCategories.isNotEmpty()) {
                        KixyuIconButton(onClick = onOpenHiddenLibrary) {
                            Icon(Icons.Outlined.VisibilityOff, "隐藏书架")
                        }
                    }
                }
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
                                label = "书架显示",
                                icon = Icons.AutoMirrored.Outlined.Sort,
                                enabled = true,
                            ) {
                                optionsExpanded = false
                                displayDialogVisible = true
                            },
                            KixyuPopupMenuItem(
                                label = "管理分类",
                                icon = Icons.Outlined.Category,
                                enabled = state.allCategories.isNotEmpty(),
                            ) {
                                optionsExpanded = false
                                categoryDialogVisible = true
                            },
                            KixyuPopupMenuItem(
                                label = "批量选择",
                                icon = Icons.Outlined.SelectAll,
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
                    LibraryBookCollection(
                        books = state.books,
                        layoutMode = state.layoutMode,
                        adaptiveGrid = true,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = navigationContentPadding),
                        reorderEnabled = state.sortMode == LibrarySortMode.CUSTOM && !selectionMode,
                        selectionMode = selectionMode,
                        isSelected = { item -> if (selectionMode) item.book.uuid in selectedBookUuids else item.book.uuid == previewBookUuid },
                        onOpen = { item ->
                            if (selectionMode) selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                            else previewBookUuid = item.book.uuid
                        },
                        onSelectionChange = { item ->
                            if (selectionMode) {
                                selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                            } else {
                                selectionMode = true
                                selectedBookUuids = setOf(item.book.uuid)
                            }
                        },
                        onManage = { managingUuid = it.book.uuid },
                        onExport = onExport,
                        onDelete = { deletingUuid = it.book.uuid },
                        onMoveBook = onMoveBook,
                        onFinishReorder = onFinishReorder,
                    )
                }
                LibraryBookDetailPane(
                    item = state.books.firstOrNull { it.book.uuid == previewBookUuid },
                    onOpen = onOpenBook,
                    onManage = { managingUuid = it },
                    onExport = { book -> onExport(book) },
                    onDelete = { deletingUuid = it },
                    modifier = Modifier.weight(.42f).fillMaxSize(),
                )
            }
        } else {
            LibraryBookCollection(
                books = state.books,
                layoutMode = state.layoutMode,
                adaptiveGrid = false,
                modifier = Modifier.kixyuPageContentWidth()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = KixyuSpacing.screenHorizontal,
                    vertical = KixyuSpacing.screenVertical,
                ),
                header = { LibraryFilters(state, onSearch, onCategory) },
                footer = { KixyuBottomContentSpacer() },
                reorderEnabled = state.sortMode == LibrarySortMode.CUSTOM && !selectionMode,
                selectionMode = selectionMode,
                isSelected = { it.book.uuid in selectedBookUuids },
                onOpen = { item ->
                    if (selectionMode) selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                    else onOpenBook(item.book.uuid)
                },
                onSelectionChange = { item ->
                    if (selectionMode) {
                        selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                    } else {
                        selectionMode = true
                        selectedBookUuids = setOf(item.book.uuid)
                    }
                },
                onManage = { managingUuid = it.book.uuid },
                onExport = onExport,
                onDelete = { deletingUuid = it.book.uuid },
                onMoveBook = onMoveBook,
                onFinishReorder = onFinishReorder,
            )
        }
    }

    managing?.let { item ->
        BookManagementDialog(
            item = item,
            dismiss = { managingUuid = null },
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
    if (displayDialogVisible) {
        LibraryDisplayDialog(
            selectedSortMode = state.sortMode,
            selectedLayoutMode = state.layoutMode,
            onSortMode = onSortMode,
            onLayoutMode = onLayoutMode,
            onDismiss = { displayDialogVisible = false },
        )
    }
    if (categoryDialogVisible) {
        CategoryVisibilityDialog(
            categories = state.allCategories,
            hiddenCategories = state.hiddenCategories,
            onHiddenChange = onSetCategoryHidden,
            onOpenHiddenLibrary = if (state.hiddenOnly || state.hiddenCategories.isEmpty()) null else {
                {
                    categoryDialogVisible = false
                    onOpenHiddenLibrary()
                }
            },
            onDismiss = { categoryDialogVisible = false },
        )
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
    var categoriesExpanded by remember { mutableStateOf(false) }
    val categoriesScrollState = rememberScrollState()
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
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val categorySelectorWidth = maxWidth * KixyuSize.libraryCategorySelectorWidthFraction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.books.size} 本",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.width(categorySelectorWidth),
                ) {
                    OutlinedButton(
                        onClick = { categoriesExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Outlined.Category, null, Modifier.size(KixyuSize.iconSmall))
                        Spacer(Modifier.size(KixyuSpacing.small))
                        Text(state.category, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ExpandMore, "选择分类", Modifier.size(KixyuSize.iconSmall))
                    }
                    DropdownMenu(
                        expanded = categoriesExpanded,
                        onDismissRequest = { categoriesExpanded = false },
                        modifier = Modifier
                            .width(categorySelectorWidth)
                            .heightIn(max = KixyuSize.libraryCategoryMenuMaxHeight),
                        scrollState = categoriesScrollState,
                    ) {
                        state.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    RadioButton(selected = state.category == category, onClick = null)
                                },
                                onClick = {
                                    categoriesExpanded = false
                                    onCategory(category)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (state.hiddenOnly && state.hiddenCategories.isNotEmpty()) {
            Text(
                "仅显示隐藏分类中的书籍",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LibraryBookCollection(
    books: List<LibraryBook>,
    layoutMode: LibraryLayoutMode,
    adaptiveGrid: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    reorderEnabled: Boolean,
    selectionMode: Boolean,
    isSelected: (LibraryBook) -> Boolean,
    onOpen: (LibraryBook) -> Unit,
    onSelectionChange: (LibraryBook) -> Unit,
    onManage: (LibraryBook) -> Unit,
    onExport: (LibraryBook) -> Unit,
    onDelete: (LibraryBook) -> Unit,
    onMoveBook: (String, String) -> Unit,
    onFinishReorder: () -> Unit,
) {
    if (layoutMode == LibraryLayoutMode.GRID) {
        LibraryBookGrid(
            books = books,
            adaptiveGrid = adaptiveGrid,
            modifier = modifier,
            contentPadding = contentPadding,
            header = header,
            footer = footer,
            reorderEnabled = reorderEnabled,
            selectionMode = selectionMode,
            isSelected = isSelected,
            onOpen = onOpen,
            onSelectionChange = onSelectionChange,
            onManage = onManage,
            onExport = onExport,
            onDelete = onDelete,
            onMoveBook = onMoveBook,
            onFinishReorder = onFinishReorder,
        )
        return
    }
    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    var menuBookUuid by remember { mutableStateOf<String?>(null) }
    var reorderMoved by remember { mutableStateOf(false) }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromUuid = from.key as? String ?: return@rememberReorderableLazyListState
        val toUuid = to.key as? String ?: return@rememberReorderableLazyListState
        reorderMoved = true
        menuBookUuid = null
        onMoveBook(fromUuid, toUuid)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
    ) {
        header?.let { item(key = "library_header", contentType = "header") { it() } }
        if (books.isEmpty()) item(key = "library_empty", contentType = "empty") {
            LibraryEmptyState(Modifier.fillParentMaxSize().padding(KixyuSpacing.extraLarge))
        }
        items(books, key = { it.book.uuid }, contentType = { "book" }) { item ->
            ReorderableItem(reorderState, key = item.book.uuid, enabled = reorderEnabled) { dragging ->
                val dragModifier = if (reorderEnabled) {
                    val menuWasExpanded = menuBookUuid == item.book.uuid
                    val onDragStarted: (Offset) -> Unit = {
                        reorderMoved = false
                        menuBookUuid = item.book.uuid
                        hapticFeedback.performHapticFeedback(
                            if (menuWasExpanded) {
                                HapticFeedbackType.GestureThresholdActivate
                            } else {
                                HapticFeedbackType.LongPress
                            },
                        )
                    }
                    if (menuWasExpanded) {
                        Modifier.draggableHandle(
                            onDragStarted = onDragStarted,
                            onDragStopped = {
                                if (reorderMoved) {
                                    onFinishReorder()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                } else {
                                    menuBookUuid = null
                                    onOpen(item)
                                }
                            },
                        )
                    } else {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = onDragStarted,
                            onDragStopped = {
                                if (reorderMoved) {
                                    onFinishReorder()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                }
                            },
                        )
                    }
                } else Modifier
                LibraryBookRow(
                    item = item,
                    selected = isSelected(item),
                    selectionMode = selectionMode,
                    reorderEnabled = reorderEnabled,
                    menuExpanded = menuBookUuid == item.book.uuid,
                    dragging = dragging,
                    onOpen = { onOpen(item) },
                    onSelectionChange = { onSelectionChange(item) },
                    onManage = { onManage(item) },
                    onExport = { onExport(item) },
                    onDelete = { onDelete(item) },
                    onMenuExpandedChange = { expanded ->
                        menuBookUuid = item.book.uuid.takeIf { expanded }
                    },
                    modifier = Modifier.animateItem().then(dragModifier),
                )
            }
        }
        footer?.let { item(key = "library_footer", contentType = "footer") { it() } }
    }
}

@Composable
private fun LibraryBookGrid(
    books: List<LibraryBook>,
    adaptiveGrid: Boolean,
    modifier: Modifier,
    contentPadding: PaddingValues,
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    reorderEnabled: Boolean,
    selectionMode: Boolean,
    isSelected: (LibraryBook) -> Boolean,
    onOpen: (LibraryBook) -> Unit,
    onSelectionChange: (LibraryBook) -> Unit,
    onManage: (LibraryBook) -> Unit,
    onExport: (LibraryBook) -> Unit,
    onDelete: (LibraryBook) -> Unit,
    onMoveBook: (String, String) -> Unit,
    onFinishReorder: () -> Unit,
) {
    val lazyGridState = rememberLazyGridState()
    val hapticFeedback = LocalHapticFeedback.current
    var menuBookUuid by remember { mutableStateOf<String?>(null) }
    var reorderMoved by remember { mutableStateOf(false) }
    val reorderState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        val fromUuid = from.key as? String ?: return@rememberReorderableLazyGridState
        val toUuid = to.key as? String ?: return@rememberReorderableLazyGridState
        reorderMoved = true
        menuBookUuid = null
        onMoveBook(fromUuid, toUuid)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyVerticalGrid(
        columns = if (adaptiveGrid) GridCells.Adaptive(112.dp) else GridCells.Fixed(2),
        modifier = modifier,
        state = lazyGridState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
    ) {
        header?.let {
            item(key = "library_grid_header", span = { GridItemSpan(maxLineSpan) }) { it() }
        }
        if (books.isEmpty()) item(key = "library_grid_empty", span = { GridItemSpan(maxLineSpan) }) {
            LibraryEmptyState(Modifier.fillMaxWidth().padding(KixyuSpacing.extraLarge))
        }
        gridItems(books, key = { it.book.uuid }, contentType = { "book_grid" }) { item ->
            ReorderableItem(reorderState, key = item.book.uuid, enabled = reorderEnabled) { dragging ->
                val dragModifier = if (reorderEnabled) {
                    val menuWasExpanded = menuBookUuid == item.book.uuid
                    val onDragStarted: (Offset) -> Unit = {
                        reorderMoved = false
                        menuBookUuid = item.book.uuid
                        hapticFeedback.performHapticFeedback(
                            if (menuWasExpanded) {
                                HapticFeedbackType.GestureThresholdActivate
                            } else {
                                HapticFeedbackType.LongPress
                            },
                        )
                    }
                    if (menuWasExpanded) {
                        Modifier.draggableHandle(
                            onDragStarted = onDragStarted,
                            onDragStopped = {
                                if (reorderMoved) {
                                    onFinishReorder()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                } else {
                                    menuBookUuid = null
                                    onOpen(item)
                                }
                            },
                        )
                    } else {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = onDragStarted,
                            onDragStopped = {
                                if (reorderMoved) {
                                    onFinishReorder()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                }
                            },
                        )
                    }
                } else Modifier
                LibraryBookGridCard(
                    item = item,
                    selected = isSelected(item),
                    selectionMode = selectionMode,
                    reorderEnabled = reorderEnabled,
                    menuExpanded = menuBookUuid == item.book.uuid,
                    dragging = dragging,
                    onOpen = { onOpen(item) },
                    onSelectionChange = { onSelectionChange(item) },
                    onManage = { onManage(item) },
                    onExport = { onExport(item) },
                    onDelete = { onDelete(item) },
                    onMenuExpandedChange = { expanded ->
                        menuBookUuid = item.book.uuid.takeIf { expanded }
                    },
                    modifier = Modifier.animateItem().then(dragModifier),
                )
            }
        }
        footer?.let {
            item(key = "library_grid_footer", span = { GridItemSpan(maxLineSpan) }) { it() }
        }
    }
}

@Composable
private fun LibraryBookDetailPane(
    item: LibraryBook?,
    onOpen: (String) -> Unit,
    onManage: (String) -> Unit,
    onExport: (LibraryBook) -> Unit,
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
                    OutlinedButton(onClick = { onExport(item) }, modifier = Modifier.weight(1f)) {
                        Text("导出", maxLines = 1)
                    }
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
    reorderEnabled: Boolean,
    menuExpanded: Boolean,
    onOpen: () -> Unit,
    onSelectionChange: () -> Unit,
    onManage: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
) {
    val rowShape = MaterialTheme.shapes.large
    val raised = dragging || menuExpanded
    val raisedScale by animateFloatAsState(
        targetValue = if (raised) 1.015f else 1f,
        label = "libraryBookRowScale",
    )
    val raisedElevation by animateDpAsState(
        targetValue = if (raised) KixyuSpacing.medium else 0.dp,
        label = "libraryBookRowElevation",
    )
    Surface(
        onClick = {
            if (!reorderEnabled || !menuExpanded) onOpen()
        },
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = raisedScale
                scaleY = raisedScale
                shape = rowShape
                clip = true
                shadowElevation = raisedElevation.toPx()
            }
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
        shape = rowShape,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (reorderEnabled) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = onOpen,
                            onLongClick = if (selectionMode) null else ({ onMenuExpandedChange(true) }),
                        )
                    },
                )
                .padding(KixyuSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        ) {
            BookCover(
                item.book.title,
                item.book.coverPath,
                Modifier.size(KixyuSize.libraryCoverWidth, KixyuSize.libraryCoverHeight),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
            ) {
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
                Box {
                    IconButton(onClick = { onMenuExpandedChange(true) }) {
                        Icon(Icons.Outlined.MoreVert, "更多操作", Modifier.size(KixyuSize.icon))
                    }
                    BookActionPopupMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) },
                        onManage = {
                            onMenuExpandedChange(false)
                            onManage()
                        },
                        onExport = {
                            onMenuExpandedChange(false)
                            onExport()
                        },
                        onDelete = {
                            onMenuExpandedChange(false)
                            onDelete()
                        },
                    )
                }
                Text(
                    "${((item.progress?.fraction ?: 0f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LibraryBookGridCard(
    item: LibraryBook,
    selected: Boolean,
    selectionMode: Boolean,
    reorderEnabled: Boolean,
    menuExpanded: Boolean,
    dragging: Boolean,
    onOpen: () -> Unit,
    onSelectionChange: () -> Unit,
    onManage: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = MaterialTheme.shapes.large
    val raised = dragging || menuExpanded
    val raisedScale by animateFloatAsState(
        targetValue = if (raised) 1.025f else 1f,
        label = "libraryBookGridCardScale",
    )
    val raisedElevation by animateDpAsState(
        targetValue = if (raised) KixyuSpacing.medium else 0.dp,
        label = "libraryBookGridCardElevation",
    )
    Surface(
        onClick = {
            if (!reorderEnabled || !menuExpanded) onOpen()
        },
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = raisedScale
                scaleY = raisedScale
                shape = cardShape
                clip = true
                shadowElevation = raisedElevation.toPx()
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = "打开书籍：${item.book.title}" },
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = cardShape,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (reorderEnabled) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = onOpen,
                            onLongClick = if (selectionMode) null else ({ onMenuExpandedChange(true) }),
                        )
                    },
                )
                .padding(KixyuSpacing.small),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
        ) {
            Box(Modifier.fillMaxWidth()) {
                BookCover(
                    item.book.title,
                    item.book.coverPath,
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                )
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onSelectionChange() },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                BookActionPopupMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                    onManage = {
                        onMenuExpandedChange(false)
                        onManage()
                    },
                    onExport = {
                        onMenuExpandedChange(false)
                        onExport()
                    },
                    onDelete = {
                        onMenuExpandedChange(false)
                        onDelete()
                    },
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
            ) {
                Text(
                    text = item.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { (item.progress?.fraction ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
                )
            }
        }
    }
}

@Composable
private fun BookActionPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onManage: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    if (!expanded) return
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
        ),
    ) {
        KixyuPopupSurface(
            modifier = Modifier.width(IntrinsicSize.Max),
            shape = MaterialTheme.shapes.large,
            shadowElevation = KixyuSpacing.medium,
        ) {
            Column(Modifier.padding(vertical = KixyuSpacing.extraSmall)) {
                BookActionPopupMenuItem("管理", Icons.Outlined.Edit, onManage)
                BookActionPopupMenuItem("导出", Icons.Outlined.FileUpload, onExport)
                BookActionPopupMenuItem("删除", Icons.Outlined.DeleteOutline, onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun BookActionPopupMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = KixyuSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(KixyuSize.icon), tint = contentColor)
        Spacer(Modifier.width(KixyuSpacing.medium))
        Text(label, maxLines = 1, color = contentColor)
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private val LibrarySortMode.label: String
    get() = when (this) {
        LibrarySortMode.RECENT -> "最近活动"
        LibrarySortMode.IMPORTED -> "新导入"
        LibrarySortMode.TITLE -> "书名"
        LibrarySortMode.AUTHOR -> "作者"
        LibrarySortMode.PROGRESS -> "阅读进度"
        LibrarySortMode.CUSTOM -> "自定义"
    }

@Composable
private fun LibraryDisplayDialog(
    selectedSortMode: LibrarySortMode,
    selectedLayoutMode: LibraryLayoutMode,
    onSortMode: (LibrarySortMode) -> Unit,
    onLayoutMode: (LibraryLayoutMode) -> Unit,
    onDismiss: () -> Unit,
) {
    KixyuActionDialog(
        show = true,
        title = "书架显示",
        onDismissRequest = onDismiss,
        confirmLabel = "完成",
        onConfirm = onDismiss,
        dismissLabel = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            Text(
                "布局",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                LibraryLayoutMode.entries.forEach { mode ->
                    val selected = mode == selectedLayoutMode
                    Surface(
                        onClick = { onLayoutMode(mode) },
                        modifier = Modifier.weight(1f),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = KixyuSpacing.medium,
                                vertical = KixyuSpacing.small,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                        ) {
                            Icon(
                                imageVector = if (mode == LibraryLayoutMode.GRID) {
                                    Icons.Outlined.GridView
                                } else {
                                    Icons.AutoMirrored.Outlined.ViewList
                                },
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(if (mode == LibraryLayoutMode.GRID) "宫格" else "列表")
                        }
                    }
                }
            }
            Text(
                "排序",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            librarySortDisplayOrder.forEach { mode ->
                Surface(
                    onClick = { onSortMode(mode) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (mode == selectedSortMode) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selectedSortMode, onClick = null)
                        Column(Modifier.padding(start = KixyuSpacing.small)) {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            if (mode == LibrarySortMode.CUSTOM) {
                                Text(
                                    "长按书籍显示操作，继续拖动可排序",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val librarySortDisplayOrder = listOf(
    LibrarySortMode.RECENT,
    LibrarySortMode.CUSTOM,
    LibrarySortMode.IMPORTED,
    LibrarySortMode.TITLE,
    LibrarySortMode.AUTHOR,
    LibrarySortMode.PROGRESS,
)

@Composable
private fun CategoryVisibilityDialog(
    categories: List<String>,
    hiddenCategories: Set<String>,
    onHiddenChange: (String, Boolean) -> Unit,
    onOpenHiddenLibrary: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    KixyuActionDialog(
        show = true,
        title = "管理分类",
        onDismissRequest = onDismiss,
        confirmLabel = "完成",
        onConfirm = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            Text(
                "隐藏分类后，该分类中的书不会出现在普通书架和继续阅读中。可双指下滑进入隐藏书架。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (onOpenHiddenLibrary != null) {
                Surface(
                    onClick = onOpenHiddenLibrary,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    ) {
                        Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(KixyuSize.icon))
                        Column(Modifier.weight(1f)) {
                            Text("隐藏书架", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${hiddenCategories.size} 个分类已隐藏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("查看", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
            ) {
                categories.forEach { category ->
                    val hidden = category in hiddenCategories
                    Surface(
                        onClick = { onHiddenChange(category, !hidden) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (hidden) "已隐藏" else "显示在书架",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = !hidden, onCheckedChange = { onHiddenChange(category, !it) })
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.revealHiddenCategoriesGesture(
    enabled: Boolean,
    onReveal: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(onReveal) {
    awaitPointerEventScope {
        var distance = 0f
        var intercepting = false
        var revealed = false
        while (true) {
            // Observe before child click handlers. As soon as a second pointer joins the
            // gesture, consume the entire pointer stream so releasing either finger cannot
            // complete a pending book click.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (!intercepting && pressed.size >= 2) {
                intercepting = true
                distance = 0f
            }
            if (intercepting) {
                event.changes.forEach { it.consume() }
            }
            if (pressed.size >= 2 && !revealed) {
                distance += pressed.sumOf { (it.position.y - it.previousPosition.y).toDouble() }.toFloat() / pressed.size
                if (distance > viewConfiguration.touchSlop * 4f) {
                    revealed = true
                    onReveal()
                }
            } else if (pressed.isEmpty()) {
                distance = 0f
                intercepting = false
                revealed = false
            }
        }
    }
}

internal fun exportFileName(item: LibraryBook): String {
    val extension = item.book.format.name.lowercase()
    val withoutExistingExtension = item.book.title.trim().replace(
        Regex("\\.${Regex.escape(extension)}$", RegexOption.IGNORE_CASE),
        "",
    )
    val safeTitle = withoutExistingExtension
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim(' ', '.')
        .take(120)
        .ifBlank { "未命名书籍" }
    return "$safeTitle.$extension"
}

internal fun openExportLocation(context: Context, uriString: String): Boolean {
    val uri = uriString.toUri()
    val parentUri = exportedDocumentParent(context, uri)
    if (parentUri != null) {
        val directoryIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(parentUri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.tryStartActivity(directoryIntent)) return true
    }
    val fileIntent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/octet-stream")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return context.tryStartActivity(fileIntent)
}

private fun exportedDocumentParent(context: Context, uri: Uri): Uri? {
    if (!DocumentsContract.isDocumentUri(context, uri)) return null
    val authority = uri.authority ?: return null
    val parentId = runCatching {
        DocumentsContract.findDocumentPath(context.contentResolver, uri)
            ?.path
            ?.dropLast(1)
            ?.lastOrNull()
    }.getOrNull() ?: if (authority == "com.android.externalstorage.documents") {
        // ExternalStorageProvider uses volume:path document IDs. Other providers are opaque and
        // must not be guessed by splitting their IDs.
        runCatching {
            DocumentsContract.getDocumentId(uri).substringBeforeLast('/', missingDelimiterValue = "")
                .takeIf(String::isNotBlank)
        }.getOrNull()
    } else {
        null
    }
    return parentId?.let { DocumentsContract.buildDocumentUri(authority, it) }
}

private fun Context.tryStartActivity(intent: Intent): Boolean = runCatching {
    startActivity(intent)
}.isSuccess

@Composable
private fun BookManagementDialog(
    item: LibraryBook,
    dismiss: () -> Unit,
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("书名") }, singleLine = true)
            OutlinedTextField(author, { author = it }, modifier = Modifier.fillMaxWidth(), label = { Text("作者") }, singleLine = true)
            OutlinedTextField(
                description,
                { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("简介") },
                minLines = 3,
                maxLines = 6,
            )
            OutlinedTextField(category, { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text("分类") }, singleLine = true)
        }
    }
}
