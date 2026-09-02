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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
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
internal fun LibraryBookDetailPane(
    item: LibraryBook?,
    modifier: Modifier = Modifier,
    selectionCount: Int = 0,
    onOpen: (String) -> Unit,
    onManage: (String) -> Unit,
    onExport: (LibraryBook) -> Unit,
    onDelete: (String) -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        if (selectionCount > 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.library_selected_count, selectionCount),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.library_select_book_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (item.book.description.isBlank()) {
                        stringResource(R.string.library_no_description)
                    } else {
                        item.book.description
                    },
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
                    text = stringResource(
                        if (item.progress == null) R.string.library_start_reading else R.string.library_continue_reading,
                    ),
                    onClick = { onOpen(item.book.uuid) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                    OutlinedButton(onClick = { onExport(item) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.library_action_export), maxLines = 1)
                    }
                    OutlinedButton(onClick = { onManage(item.book.uuid) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.library_action_manage), maxLines = 1)
                    }
                    OutlinedButton(onClick = { onDelete(item.book.uuid) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.library_action_delete), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryBookRow(
    item: LibraryBook,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onSelectionChange: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    reorderHandle: (@Composable BoxScope.() -> Unit)? = null,
) {
    val rowShape = MaterialTheme.shapes.large
    val raisedScale by animateFloatAsState(
        targetValue = if (dragging) 1.015f else 1f,
        label = "libraryBookRowScale",
    )
    val raisedElevation by animateDpAsState(
        targetValue = if (dragging) KixyuSpacing.medium else 0.dp,
        label = "libraryBookRowElevation",
    )
    val openBookDescription = stringResource(R.string.library_open_book, item.book.title)
    val selectedDescription = stringResource(R.string.library_book_selected)
    val notSelectedDescription = stringResource(R.string.library_book_not_selected)
    Surface(
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
            .pointerInput(onSelectionChange) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            onSelectionChange()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = if (selectionMode) item.book.title else openBookDescription
                if (selectionMode) {
                    this.selected = selected
                    stateDescription = if (selected) selectedDescription else notSelectedDescription
                }
            },
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = rowShape,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = if (selectionMode) null else onSelectionChange,
                )
                .padding(KixyuSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        ) {
            Box {
                BookCover(
                    item.book.title,
                    item.book.coverPath,
                    Modifier.size(KixyuSize.libraryCoverWidth, KixyuSize.libraryCoverHeight),
                )
                LibrarySelectionBadge(
                    visible = selectionMode,
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(KixyuSpacing.extraSmall),
                )
            }
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
            Column(horizontalAlignment = Alignment.End) {
                reorderHandle?.let { handle -> Box { handle() } }
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
internal fun LibraryBookGridCard(
    item: LibraryBook,
    selected: Boolean,
    selectionMode: Boolean,
    dragging: Boolean,
    onOpen: () -> Unit,
    onSelectionChange: () -> Unit,
    modifier: Modifier = Modifier,
    reorderHandle: (@Composable BoxScope.() -> Unit)? = null,
) {
    val cardShape = MaterialTheme.shapes.large
    val raisedScale by animateFloatAsState(
        targetValue = if (dragging) 1.025f else 1f,
        label = "libraryBookGridCardScale",
    )
    val raisedElevation by animateDpAsState(
        targetValue = if (dragging) KixyuSpacing.medium else 0.dp,
        label = "libraryBookGridCardElevation",
    )
    val openBookDescription = stringResource(R.string.library_open_book, item.book.title)
    val selectedDescription = stringResource(R.string.library_book_selected)
    val notSelectedDescription = stringResource(R.string.library_book_not_selected)
    Surface(
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
            .pointerInput(onSelectionChange) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            onSelectionChange()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = if (selectionMode) item.book.title else openBookDescription
                if (selectionMode) {
                    this.selected = selected
                    stateDescription = if (selected) selectedDescription else notSelectedDescription
                }
            },
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = cardShape,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = if (selectionMode) null else onSelectionChange,
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
                LibrarySelectionBadge(
                    visible = selectionMode,
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(KixyuSpacing.extraSmall),
                )
                reorderHandle?.invoke(this)
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
private fun LibrarySelectionBadge(
    visible: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = .75f),
        exit = fadeOut() + scaleOut(targetScale = .75f),
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = .88f)
            },
            border = BorderStroke(
                width = 2.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        ) {
            if (selected) {
                Icon(
                    imageVector = KixyuSymbols.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
