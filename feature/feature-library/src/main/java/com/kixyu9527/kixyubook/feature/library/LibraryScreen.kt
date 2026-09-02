package com.kixyu9527.kixyubook.feature.library

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
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
import com.kixyu9527.kixyubook.core.common.model.ImportProgress
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuContextualAction
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuContextualBarState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenu
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupBackdropEffect
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPredictiveBackHandler
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuContextualBarController
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPredictivePopupTransform
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuPredictiveBackState
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
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportedMessage = stringResource(R.string.library_exported)
    val exportedManyMessage = stringResource(R.string.library_exported_many)
    val exportedPartialMessage = stringResource(R.string.library_exported_partial)
    val viewExportAction = stringResource(R.string.library_open_export_location)
    val openExportFailedMessage = stringResource(R.string.library_open_export_failed)
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris.map { it.toString() })
    }
    var pendingExportBookUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val stringSetSaver = remember {
        Saver<Set<String>, List<String>>(save = { it.toList() }, restore = { it.toSet() })
    }
    var pendingBatchExportBookUuids by rememberSaveable(stateSaver = stringSetSaver) {
        mutableStateOf(emptySet())
    }
    val exportTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val bookUuid = pendingExportBookUuid
        pendingExportBookUuid = null
        if (uri != null && bookUuid != null) viewModel.export(bookUuid, uri.toString())
    }
    val beginExport: (LibraryBook) -> Unit = { item ->
        pendingExportBookUuid = item.book.uuid
        val fileName = exportFileName(item)
        exportTxt.launch(fileName)
    }
    val exportDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val bookUuids = pendingBatchExportBookUuids
        pendingBatchExportBookUuids = emptySet()
        if (uri != null && bookUuids.isNotEmpty()) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.exportBooks(bookUuids, uri.toString())
        }
    }
    val beginBatchExport: (Set<String>) -> Unit = { bookUuids ->
        pendingBatchExportBookUuids = bookUuids
        exportDirectory.launch(null)
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
            val message = when {
                event.failedCount > 0 -> exportedPartialMessage.format(event.exportedCount, event.failedCount)
                event.exportedCount > 1 -> exportedManyMessage.format(event.exportedCount)
                else -> exportedMessage
            }
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = viewExportAction,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed && !openExportLocation(context, event.uriString)) {
                snackbar.showSnackbar(openExportFailedMessage)
            }
        }
    }
    LibraryScreen(
        state = state,
        importProgress = importProgress,
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
        onExportMany = beginBatchExport,
        onUpdateMetadata = viewModel::updateMetadata,
        onSetCategory = viewModel::setCategory,
        onSetCategories = viewModel::setCategories,
        onDropDocuments = { uris, releasePermission ->
            viewModel.import(uris) { releasePermission?.invoke() }
        },
        onClearImportProgress = viewModel::clearFinishedImportProgress,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    state: LibraryUiState,
    importProgress: ImportProgress?,
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
    onExportMany: (Set<String>) -> Unit,
    onUpdateMetadata: (String, String, String, String) -> Unit,
    onSetCategory: (String, String) -> Unit,
    onSetCategories: (Set<String>, String) -> Unit,
    onDropDocuments: (List<String>, (() -> Unit)?) -> Unit,
    onClearImportProgress: () -> Unit,
) {
    var managingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var displayDialogVisible by rememberSaveable { mutableStateOf(false) }
    var categoryDialogVisible by rememberSaveable { mutableStateOf(false) }
    val stringSetSaver = remember {
        Saver<Set<String>, List<String>>(save = { it.toList() }, restore = { it.toSet() })
    }
    var selectedBookUuids by rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf(emptySet()) }
    var confirmingBatchDelete by rememberSaveable { mutableStateOf(false) }
    var batchCategoryDialogVisible by rememberSaveable { mutableStateOf(false) }
    var importDialogVisible by rememberSaveable { mutableStateOf(false) }
    var previewBookUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val managing = state.books.firstOrNull { it.book.uuid == managingUuid }
    val deleting = state.books.firstOrNull { it.book.uuid == deletingUuid }
    val visibleBookUuids = state.books.mapTo(linkedSetOf()) { it.book.uuid }
    val selectionMode = selectedBookUuids.isNotEmpty()
    val selectedBooks = state.books.filter { it.book.uuid in selectedBookUuids }
    val clearSelection: () -> Unit = { selectedBookUuids = emptySet() }
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
    val contextualBarController = LocalKixyuContextualBarController.current
    val contextualBarOwner = remember { Any() }
    val selectionBackState = rememberKixyuPredictiveBackState<Unit>()
    val expanded = kixyuWindowSizeClass().supportsTwoPane
    val activity = LocalContext.current.findActivity()
    val latestDropDocuments by rememberUpdatedState(onDropDocuments)
    LaunchedEffect(importProgress?.runId) {
        if (importProgress != null) importDialogVisible = true
    }
    val pageTitle = when {
        selectionMode -> stringResource(R.string.library_selected_count, selectedBookUuids.size)
        state.hiddenOnly -> stringResource(R.string.library_hidden_title)
        else -> stringResource(R.string.library_title)
    }
    val categoryActionLabel = stringResource(R.string.library_action_category)
    val exportActionLabel = stringResource(R.string.library_action_export)
    val editActionLabel = stringResource(R.string.library_action_edit)
    val deleteActionLabel = stringResource(R.string.library_action_delete)
    val categoryActionIcon = KixyuSymbols.Category
    val exportActionIcon = KixyuSymbols.FileUpload
    val editActionIcon = KixyuSymbols.Edit
    val deleteActionIcon = KixyuSymbols.DeleteOutline
    val contextualBarState = remember(
        selectedBookUuids,
        selectedBooks,
        categoryActionLabel,
        exportActionLabel,
        editActionLabel,
        deleteActionLabel,
        categoryActionIcon,
        exportActionIcon,
        editActionIcon,
        deleteActionIcon,
    ) {
        if (!selectionMode) return@remember null
        KixyuContextualBarState(
            actions = buildList {
                if (selectedBookUuids.size == 1) {
                    add(
                        KixyuContextualAction(
                            key = "edit",
                            label = editActionLabel,
                            icon = editActionIcon,
                        ) {
                            managingUuid = selectedBookUuids.single()
                            clearSelection()
                        },
                    )
                }
                add(
                    KixyuContextualAction(
                        key = "category",
                        label = categoryActionLabel,
                        icon = categoryActionIcon,
                    ) { batchCategoryDialogVisible = true },
                )
                add(
                    KixyuContextualAction(
                        key = "export",
                        label = exportActionLabel,
                        icon = exportActionIcon,
                    ) {
                        if (selectedBooks.size == 1) onExport(selectedBooks.single())
                        else onExportMany(selectedBookUuids)
                        clearSelection()
                    },
                )
                add(
                    KixyuContextualAction(
                        key = "delete",
                        label = deleteActionLabel,
                        icon = deleteActionIcon,
                        destructive = true,
                    ) { confirmingBatchDelete = true },
                )
            },
            backProgress = { selectionBackState.progress },
        )
    }
    if (contextualBarController != null && contextualBarState != null) {
        DisposableEffect(contextualBarController, contextualBarOwner, contextualBarState) {
            contextualBarController.show(contextualBarOwner, contextualBarState)
            onDispose { contextualBarController.clear(contextualBarOwner) }
        }
    }
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
        if (previewBookUuid !in visibleBookUuids) previewBookUuid = state.books.firstOrNull()?.book?.uuid
    }
    KixyuPageScaffold(
        title = pageTitle,
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
                    onClick = clearSelection,
                ) {
                    Icon(KixyuSymbols.Close, stringResource(R.string.library_exit_selection))
                }
                state.hiddenOnly -> KixyuIconButton(onClick = onBack) {
                    Icon(KixyuSymbols.ArrowBack, stringResource(R.string.library_back))
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
                ) { Icon(KixyuSymbols.SelectAll, stringResource(R.string.library_select_all)) }
            } else {
                if (importProgress != null) {
                    KixyuIconButton(onClick = { importDialogVisible = true }) {
                        Icon(KixyuSymbols.Schedule, stringResource(R.string.library_import_progress))
                    }
                }
                if (!state.hiddenOnly) {
                    KixyuIconButton(onClick = onImport) {
                        Icon(KixyuSymbols.Add, stringResource(R.string.library_import))
                    }
                    if (state.hiddenCategories.isNotEmpty()) {
                        KixyuIconButton(onClick = onOpenHiddenLibrary) {
                            Icon(KixyuSymbols.VisibilityOff, stringResource(R.string.library_hidden_title))
                        }
                    }
                }
                Box {
                    KixyuIconButton(onClick = { optionsExpanded = true }) {
                        Icon(KixyuSymbols.MoreVert, stringResource(R.string.library_actions))
                    }
                    KixyuPopupMenu(
                        expanded = optionsExpanded,
                        onDismissRequest = { optionsExpanded = false },
                        alignEnd = true,
                        items = listOf(
                            KixyuPopupMenuItem(
                                label = stringResource(R.string.library_display_title),
                                icon = KixyuSymbols.Sort,
                                enabled = true,
                            ) {
                                optionsExpanded = false
                                displayDialogVisible = true
                            },
                            KixyuPopupMenuItem(
                                label = stringResource(R.string.library_manage_categories),
                                icon = KixyuSymbols.Category,
                                enabled = state.allCategories.isNotEmpty(),
                            ) {
                                optionsExpanded = false
                                categoryDialogVisible = true
                            },
                        ),
                    )
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
                            selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                        },
                        onMoveBook = onMoveBook,
                        onFinishReorder = onFinishReorder,
                    )
                }
                LibraryBookDetailPane(
                    item = state.books.firstOrNull { it.book.uuid == previewBookUuid },
                    selectionCount = selectedBookUuids.size,
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
                    selectedBookUuids = selectedBookUuids.toggle(item.book.uuid)
                },
                onMoveBook = onMoveBook,
                onFinishReorder = onFinishReorder,
            )
        }
    }

    KixyuPredictiveBackHandler(
        target = Unit.takeIf { selectionMode },
        state = selectionBackState,
        onBack = { clearSelection() },
    )
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
            title = stringResource(R.string.library_delete_book_title, item.book.title),
            onDismissRequest = { deletingUuid = null },
            confirmLabel = stringResource(R.string.library_action_delete),
            onConfirm = { onDelete(item.book.uuid); deletingUuid = null },
        ) { Text(stringResource(R.string.library_delete_explanation)) }
    }
    if (confirmingBatchDelete) {
        KixyuActionDialog(
            show = true,
            title = stringResource(R.string.library_delete_selected_title, selectedBookUuids.size),
            onDismissRequest = { confirmingBatchDelete = false },
            confirmLabel = stringResource(R.string.library_action_delete),
            onConfirm = {
                onDeleteMany(selectedBookUuids)
                confirmingBatchDelete = false
                clearSelection()
            },
        ) { Text(stringResource(R.string.library_delete_explanation)) }
    }
    if (batchCategoryDialogVisible) {
        BatchCategoryDialog(
            selectedCount = selectedBookUuids.size,
            categories = state.allCategories,
            onDismiss = { batchCategoryDialogVisible = false },
            onConfirm = { category ->
                onSetCategories(selectedBookUuids, category)
                batchCategoryDialogVisible = false
                clearSelection()
            },
        )
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
    if (importDialogVisible && importProgress != null) {
        ImportProgressDialog(
            progress = importProgress,
            onDismiss = { importDialogVisible = false },
            onDone = {
                importDialogVisible = false
                onClearImportProgress()
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
