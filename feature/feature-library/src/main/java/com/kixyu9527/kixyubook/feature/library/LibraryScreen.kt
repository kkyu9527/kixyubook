package com.kixyu9527.kixyubook.feature.library
import com.kixyu9527.kixyubook.core.common.operation.UserOperationKind
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOperationHost

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
import com.kixyu9527.kixyubook.core.common.operation.UserOperationState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ImportProgress
import com.kixyu9527.kixyubook.core.common.model.ImportItemStatus
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
    val importHistory by viewModel.importHistory.collectAsStateWithLifecycle()
    val repairProgress by viewModel.repairProgress.collectAsStateWithLifecycle()
    val repairOutcome by viewModel.repairOutcome.collectAsStateWithLifecycle()
    val operation by viewModel.operations.state.collectAsStateWithLifecycle()
    var repairTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exportedMessage = stringResource(R.string.library_exported)
    val exportedManyMessage = stringResource(R.string.library_exported_many)
    val exportedPartialMessage = stringResource(R.string.library_exported_partial)
    val viewExportAction = stringResource(R.string.library_open_export_location)
    val openExportFailedMessage = stringResource(R.string.library_open_export_failed)
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        viewModel.import(uris.map { it.toString() })
    }
    var pendingExportBookUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportOriginalUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var exportChoiceUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAnnotationBookUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val exportNotes = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val uuid = pendingAnnotationBookUuid
        pendingAnnotationBookUuid = null
        if (uri != null && uuid != null) viewModel.exportAnnotations(uuid, uri.toString(),
            com.kixyu9527.kixyubook.core.common.model.AnnotationExportFormat.PLAIN_TEXT)
    }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val uuid = pendingAnnotationBookUuid
        pendingAnnotationBookUuid = null
        if (uri != null && uuid != null) viewModel.exportAnnotations(uuid, uri.toString(),
            com.kixyu9527.kixyubook.core.common.model.AnnotationExportFormat.MARKDOWN)
    }
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
    // The provider decides the final MIME; the suggested name keeps the original extension.
    val exportOriginal = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bookUuid = pendingExportOriginalUuid
        pendingExportOriginalUuid = null
        if (uri != null && bookUuid != null) viewModel.exportOriginal(bookUuid, uri.toString())
    }
    val beginExport: (LibraryBook) -> Unit = { item ->
        exportChoiceUuid = item.book.uuid
    }
    state.books.firstOrNull { it.book.uuid == exportChoiceUuid }?.let { item ->
        KixyuActionDialog(
            show = true,
            title = stringResource(R.string.library_export_choose),
            onDismissRequest = { exportChoiceUuid = null },
            confirmLabel = stringResource(com.kixyu9527.kixyubook.core.designsystem.R.string.kixyu_cancel),
            onConfirm = { exportChoiceUuid = null },
            dismissLabel = null,
        ) {
            Column {
                KixyuTextButton(text = stringResource(R.string.library_export_original), onClick = {
                    exportChoiceUuid = null
                    pendingExportOriginalUuid = item.book.uuid
                    exportOriginal.launch(originalExportFileName(item))
                })
                KixyuTextButton(text = stringResource(R.string.library_export_body), onClick = {
                    exportChoiceUuid = null
                    pendingExportBookUuid = item.book.uuid
                    exportTxt.launch(exportFileName(item))
                })
                listOf("MARKDOWN", "PLAIN_TEXT").forEach { format ->
                    KixyuTextButton(text = stringResource(if (format == "MARKDOWN") R.string.library_export_notes_md else R.string.library_export_notes_txt), onClick = {
                        exportChoiceUuid = null
                        pendingAnnotationBookUuid = item.book.uuid
                        val name = item.book.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        if (format == "MARKDOWN") exportMarkdown.launch("$name.md") else exportNotes.launch("$name.txt")
                    })
                }
            }
        }
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
    KixyuOperationHost(viewModel.operations) {
    LibraryScreen(
        state = state,
        importProgress = importProgress,
        importHistory = importHistory,
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
        onOpenBook = { if (repairProgress == null) onOpenBook(it) },
        onDelete = viewModel::delete,
        onDeleteMany = viewModel::deleteBooks,
        onExport = beginExport,
        onExportMany = beginBatchExport,
        onUpdateMetadata = viewModel::updateMetadata,
        onRepair = { viewModel.clearRepairOutcome(); repairTarget = it },
        onSetCategories = viewModel::setCategories,
        onDropDocuments = { uris, releasePermission ->
            // The drag grant is transient; persist what the provider allows so a failed import can
            // be retried from history. Releasing the drag permission does not revoke these.
            uris.forEach { value ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        android.net.Uri.parse(value),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            viewModel.import(uris) { releasePermission?.invoke() }
        },
        onClearImportProgress = viewModel::clearFinishedImportProgress,
        onCancelImport = viewModel::cancelImport,
        onRetryImport = viewModel::retryImport,
        onClearImportHistory = viewModel::clearImportHistory,
    )
        repairTarget?.let { uuid ->
            LibraryBookRepairDialog(
                title = state.books.firstOrNull { it.book.uuid == uuid }?.book?.title.orEmpty(),
                running = operation.let { it is UserOperationState.Running && it.kind == UserOperationKind.REPAIR },
                progress = repairProgress,
                outcome = repairOutcome,
                onDismiss = { repairTarget = null },
                onRepair = { viewModel.repairBook(uuid, it) },
            )
        }
    }
}
