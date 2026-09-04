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
internal fun LibraryFilters(
    state: LibraryUiState,
    onSearch: (String) -> Unit,
    onCategory: (String) -> Unit,
) {
    var categoriesExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        TextField(
            value = state.query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.library_search_hint), maxLines = 1) },
            leadingIcon = { Icon(KixyuSymbols.Search, null, Modifier.size(KixyuSize.icon)) },
            trailingIcon = {
                if (state.query.isNotEmpty()) KixyuIconButton({ onSearch("") }) {
                    Icon(KixyuSymbols.Close, stringResource(R.string.library_clear_search), Modifier.size(KixyuSize.icon))
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
                    stringResource(R.string.library_book_count, state.books.size),
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
                        Icon(KixyuSymbols.Category, null, Modifier.size(KixyuSize.iconSmall))
                        Spacer(Modifier.size(KixyuSpacing.small))
                        Text(state.category, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(KixyuSymbols.ExpandMore, stringResource(R.string.library_choose_category), Modifier.size(KixyuSize.iconSmall))
                    }
                    KixyuPopupMenu(
                        expanded = categoriesExpanded,
                        onDismissRequest = { categoriesExpanded = false },
                        items = state.categories.map { category ->
                            KixyuPopupMenuItem(
                                label = category,
                                icon = KixyuSymbols.Category,
                                selected = state.category == category,
                            ) {
                                categoriesExpanded = false
                                onCategory(category)
                            }
                        },
                        modifier = Modifier.heightIn(max = KixyuSize.libraryCategoryMenuMaxHeight),
                        width = categorySelectorWidth,
                    )
                }
            }
        }
        if (state.hiddenOnly && state.hiddenCategories.isNotEmpty()) {
            Text(
                stringResource(R.string.library_hidden_only_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun LibraryBookCollection(
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
            onMoveBook = onMoveBook,
            onFinishReorder = onFinishReorder,
        )
        return
    }
    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    var reorderMoved by remember { mutableStateOf(false) }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromUuid = from.key as? String ?: return@rememberReorderableLazyListState
        val toUuid = to.key as? String ?: return@rememberReorderableLazyListState
        reorderMoved = true
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
                    val onDragStarted: (Offset) -> Unit = {
                        reorderMoved = false
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    Modifier.draggableHandle(
                        onDragStarted = onDragStarted,
                        onDragStopped = {
                            if (reorderMoved) {
                                onFinishReorder()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                        },
                    )
                } else Modifier
                LibraryBookRow(
                    item = item,
                    selected = isSelected(item),
                    selectionMode = selectionMode,
                    dragging = dragging,
                    onOpen = { onOpen(item) },
                    onSelectionChange = { onSelectionChange(item) },
                    modifier = Modifier.animateItem(),
                    reorderHandle = if (!reorderEnabled) null else {
                        {
                            Icon(
                                imageVector = KixyuSymbols.DragHandle,
                                contentDescription = stringResource(R.string.library_custom_sort_hint),
                                modifier = dragModifier.size(48.dp).padding(KixyuSpacing.medium),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
        footer?.let { item(key = "library_footer", contentType = "footer") { it() } }
    }
}

@Composable
internal fun LibraryBookGrid(
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
    onMoveBook: (String, String) -> Unit,
    onFinishReorder: () -> Unit,
) {
    val lazyGridState = rememberLazyGridState()
    val hapticFeedback = LocalHapticFeedback.current
    var reorderMoved by remember { mutableStateOf(false) }
    val reorderState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        val fromUuid = from.key as? String ?: return@rememberReorderableLazyGridState
        val toUuid = to.key as? String ?: return@rememberReorderableLazyGridState
        reorderMoved = true
        onMoveBook(fromUuid, toUuid)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyVerticalGrid(
        columns = if (adaptiveGrid) GridCells.Adaptive(112.dp) else GridCells.Fixed(3),
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
                    val onDragStarted: (Offset) -> Unit = {
                        reorderMoved = false
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    Modifier.draggableHandle(
                        onDragStarted = onDragStarted,
                        onDragStopped = {
                            if (reorderMoved) {
                                onFinishReorder()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                        },
                    )
                } else Modifier
                LibraryBookGridCard(
                    item = item,
                    selected = isSelected(item),
                    selectionMode = selectionMode,
                    dragging = dragging,
                    onOpen = { onOpen(item) },
                    onSelectionChange = { onSelectionChange(item) },
                    modifier = Modifier.animateItem(),
                    reorderHandle = if (!reorderEnabled) null else {
                        {
                            Icon(
                                imageVector = KixyuSymbols.DragHandle,
                                contentDescription = stringResource(R.string.library_custom_sort_hint),
                                modifier = dragModifier
                                    .align(Alignment.TopStart)
                                    .size(48.dp)
                                    .padding(KixyuSpacing.medium),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
        footer?.let {
            item(key = "library_grid_footer", span = { GridItemSpan(maxLineSpan) }) { it() }
        }
    }
}
