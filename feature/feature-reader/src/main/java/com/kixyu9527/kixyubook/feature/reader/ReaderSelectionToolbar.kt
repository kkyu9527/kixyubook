package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuGlassSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuGlassBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPredictiveBackHandler
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPredictivePopupTransform
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuPredictiveBackState
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.reader.engine.LocalReaderSelectionClearRequest
import kotlinx.coroutines.CompletableDeferred

/** The only UI and lifecycle owner for long-press text interactions in the reader. */
@Composable
internal fun ReaderTextInteractionHost(
    state: ReaderTextInteractionState,
    onCorrectText: () -> Unit,
    onHighlightText: () -> Unit,
    onUnderlineText: () -> Unit,
    onNoteText: () -> Unit,
    dismissKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val currentCorrectionAction by rememberUpdatedState(onCorrectText)
    val currentHighlightAction by rememberUpdatedState(onHighlightText)
    val currentUnderlineAction by rememberUpdatedState(onUnderlineText)
    val currentNoteAction by rememberUpdatedState(onNoteText)
    val highlightLabel = stringResource(R.string.reader_annotation_highlight)
    val noteLabel = stringResource(R.string.reader_annotation_note)
    val underlineLabel = stringResource(R.string.reader_annotation_underline)
    val correctionLabel = stringResource(R.string.reader_correction_action)
    val provider = remember { ReaderTextContextMenuProvider() }
    val predictiveBackState = rememberKixyuPredictiveBackState<Unit>()
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val menuBounds = remember { mutableStateOf<Rect?>(null) }

    val dismissSelection = {
        val hadInteraction = provider.isVisible || state.target != null
        menuBounds.value = null
        provider.dismiss()
        if (hadInteraction) state.clearSelection()
    }

    DisposableEffect(provider) { onDispose(provider::dismiss) }
    LaunchedEffect(dismissKey) { dismissSelection() }
    CompositionLocalProvider(
        LocalTextContextMenuToolbarProvider provides provider,
        LocalTextContextMenuDropdownProvider provides provider,
        LocalReaderSelectionClearRequest provides state.clearRequest,
    ) {
        Box(
            propagateMinConstraints = true,
            modifier = Modifier
                .onGloballyPositioned { layoutCoordinates = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        if (!provider.isVisible) return@awaitEachGesture
                        if (menuBounds.value?.contains(down.position) == true) {
                            return@awaitEachGesture
                        }
                        // Own the complete first outside gesture. Reader tap observers deliberately
                        // listen at Initial pass, so dismissing on DOWN would cancel this handler
                        // before UP and let that same gesture turn a page.
                        down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        dismissSelection()
                    }
                }
                .appendTextContextMenuComponents {
                    item(key = HighlightTextKey, label = highlightLabel) {
                        currentHighlightAction()
                        close()
                    }
                    item(key = UnderlineTextKey, label = underlineLabel) {
                        currentUnderlineAction()
                        close()
                    }
                    item(key = NoteTextKey, label = noteLabel) {
                        currentNoteAction()
                        close()
                    }
                    item(key = CorrectTextKey, label = correctionLabel) {
                        currentCorrectionAction()
                        close()
                    }
                }
                .filterTextContextMenuComponents { component ->
                    component.key === TextContextMenuKeys.CopyKey ||
                        component.key === HighlightTextKey ||
                        component.key === UnderlineTextKey ||
                        component.key === NoteTextKey ||
                        component.key === CorrectTextKey
                },
        ) {
            content()
            layoutCoordinates?.let { coordinates ->
                provider.Menu(
                    coordinates = coordinates,
                    panel = state.panel,
                    sourceActionsEnabled = state.target?.isAnnotatable == true,
                    onPanelChange = state::showPanel,
                    onDismiss = dismissSelection,
                    backProgress = predictiveBackState.progress,
                    onMenuBoundsChange = { menuBounds.value = it },
                )
            }
        }
    }
    KixyuPredictiveBackHandler(
        target = Unit.takeIf { provider.isVisible },
        state = predictiveBackState,
        onBack = {
            if (state.panel == ReaderTextActionPanel.PRIMARY) dismissSelection()
            else state.resetPanel()
        },
    )
}

private object HighlightTextKey
private object UnderlineTextKey
private object NoteTextKey
private object CorrectTextKey

private class ReaderTextContextMenuProvider : TextContextMenuProvider {
    private var request by mutableStateOf<MenuRequest?>(null)
    val isVisible: Boolean get() = request != null

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        request?.close()
        val next = MenuRequest(dataProvider)
        request = next
        try {
            next.awaitClose()
        } finally {
            if (request === next) request = null
        }
    }

    @Composable
    fun Menu(
        coordinates: LayoutCoordinates,
        panel: ReaderTextActionPanel,
        sourceActionsEnabled: Boolean,
        onPanelChange: (ReaderTextActionPanel) -> Unit,
        onDismiss: () -> Unit,
        backProgress: Float,
        onMenuBoundsChange: (Rect?) -> Unit,
    ) {
        val currentRequest = request ?: return
        if (!coordinates.isAttached) return
        LaunchedEffect(currentRequest) { onPanelChange(ReaderTextActionPanel.PRIMARY) }
        DisposableEffect(currentRequest) {
            onDispose { onMenuBoundsChange(null) }
        }
        val data by remember(currentRequest.dataProvider) {
            derivedStateOf(currentRequest.dataProvider::data)
        }
        val bounds = currentRequest.dataProvider.contentBounds(coordinates)
        val positionProvider = rememberSelectionMenuPositionProvider(bounds)

        Layout(
            modifier = Modifier.fillMaxSize().zIndex(20f),
            content = {
                ReaderTextSelectionMenu(
                    data = data,
                    session = currentRequest,
                    panel = panel,
                    sourceActionsEnabled = sourceActionsEnabled,
                    onPanelChange = onPanelChange,
                    onDismiss = onDismiss,
                    backProgress = backProgress,
                    modifier = Modifier.onGloballyPositioned { child ->
                        onMenuBoundsChange(child.boundsInParent())
                    },
                )
            },
        ) { measurables, constraints ->
            val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
            val placeable = measurables.firstOrNull()?.measure(
                constraints.copy(minWidth = 0, minHeight = 0),
            ) ?: return@Layout layout(containerSize.width, containerSize.height) {}
            val menuSize = IntSize(placeable.width, placeable.height)
            val position = positionProvider.calculatePosition(containerSize, menuSize)
            layout(containerSize.width, containerSize.height) {
                placeable.place(position.x, position.y)
            }
        }
    }

    fun dismiss() {
        request?.close()
        request = null
    }
}

private class MenuRequest(
    val dataProvider: TextContextMenuDataProvider,
) : TextContextMenuSession {
    private val closed = CompletableDeferred<Unit>()
    override fun close() { closed.complete(Unit) }
    suspend fun awaitClose() { closed.await() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderTextSelectionMenu(
    data: TextContextMenuData,
    session: TextContextMenuSession,
    panel: ReaderTextActionPanel,
    sourceActionsEnabled: Boolean,
    onPanelChange: (ReaderTextActionPanel) -> Unit,
    onDismiss: () -> Unit,
    backProgress: Float,
    modifier: Modifier = Modifier,
) {
    val actions = data.components.filterIsInstance<TextContextMenuItem>()
        .associateBy(TextContextMenuItem::key)
    if (actions.isEmpty()) return

    fun run(key: Any) {
        actions[key]?.onClick?.invoke(session)
        onDismiss()
    }

    ReaderTextSelectionSurface(
        modifier = modifier.kixyuPredictivePopupTransform(backProgress),
    ) {
        AnimatedContent(
            targetState = panel,
            transitionSpec = {
                fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)) togetherWith
                    fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)) using
                    SizeTransform(clip = false)
            },
            label = "readerTextActions",
        ) { activePanel ->
            FlowRow(Modifier.padding(KixyuSpacing.extraSmall)) {
                when (activePanel) {
                    ReaderTextActionPanel.PRIMARY -> {
                        if (sourceActionsEnabled) ReaderTextActionButton(
                                label = stringResource(R.string.reader_annotation_action),
                                icon = KixyuSymbols.Palette,
                                emphasized = true,
                                onClick = { onPanelChange(ReaderTextActionPanel.ANNOTATION_STYLE) },
                            )
                        actions[NoteTextKey]?.takeIf { sourceActionsEnabled }?.let { action ->
                            ReaderTextActionButton(action.label, KixyuSymbols.EditNoteRounded) {
                                run(NoteTextKey)
                            }
                        }
                        actions[TextContextMenuKeys.CopyKey]?.let { action ->
                            ReaderTextActionButton(action.label, KixyuSymbols.ContentCopyRounded) {
                                run(TextContextMenuKeys.CopyKey)
                            }
                        }
                        if (sourceActionsEnabled) {
                            ReaderTextActionButton(
                                label = stringResource(R.string.reader_more_actions),
                                icon = KixyuSymbols.MoreHoriz,
                                onClick = { onPanelChange(ReaderTextActionPanel.MORE) },
                            )
                        }
                    }
                    ReaderTextActionPanel.ANNOTATION_STYLE -> {
                        ReaderTextActionButton(
                            label = stringResource(R.string.reader_back_to_actions),
                            icon = KixyuSymbols.ArrowBack,
                            onClick = { onPanelChange(ReaderTextActionPanel.PRIMARY) },
                        )
                        actions[HighlightTextKey]?.takeIf { sourceActionsEnabled }?.let { action ->
                            ReaderTextActionButton(
                                label = action.label,
                                icon = KixyuSymbols.Palette,
                                emphasized = true,
                            ) { run(HighlightTextKey) }
                        }
                        actions[UnderlineTextKey]?.takeIf { sourceActionsEnabled }?.let { action ->
                            ReaderTextActionButton(action.label, KixyuSymbols.FormatUnderlined) {
                                run(UnderlineTextKey)
                            }
                        }
                    }
                    ReaderTextActionPanel.MORE -> {
                        ReaderTextActionButton(
                            label = stringResource(R.string.reader_back_to_actions),
                            icon = KixyuSymbols.ArrowBack,
                            onClick = { onPanelChange(ReaderTextActionPanel.PRIMARY) },
                        )
                        actions[CorrectTextKey]?.takeIf { sourceActionsEnabled }?.let { action ->
                            ReaderTextActionButton(action.label, KixyuSymbols.Edit) {
                                run(CorrectTextKey)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The selection toolbar remains in the reader render tree so it can sample the exact same
 * backdrop, frost percentage and fallback recipe as the bottom navigation capsule. Moving it to
 * a separate Popup window forces a denser simulated veil or a whole-window blur, both of which
 * make the configured opacity look different from the rest of the app.
 */
@Composable
private fun ReaderTextSelectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalKixyuGlassBackdrop.current
    if (backdrop != null) {
        KixyuGlassSurface(
            backdrop = backdrop,
            modifier = modifier,
            fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    } else {
        KixyuPopupSurface(modifier = modifier) { content() }
    }
}

@Composable
internal fun ReaderTextActionButton(
    label: String,
    icon: ImageVector,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (emphasized) MaterialTheme.colorScheme.primary else {
        MaterialTheme.colorScheme.onSurface
    }
    val density = LocalDensity.current
    val labelLayout = rememberTextMeasurer().measure(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        constraints = Constraints(maxWidth = with(density) { 64.dp.roundToPx() }),
    )
    // IconButton has a fixed default size; merely removing maxLines would still clip large text.
    val buttonHeight = maxOf(56.dp, with(density) { labelLayout.size.height.toDp() } + 28.dp)
    KixyuIconButton(
        onClick = onClick,
        modifier = Modifier.width(64.dp).height(buttonHeight),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color,
            )
            Text(text = label, color = color, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun rememberSelectionMenuPositionProvider(
    selectionBounds: Rect,
): SelectionMenuPositionProvider {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(selectionBounds, density) { SelectionMenuPositionProvider(selectionBounds, density) }
}

private class SelectionMenuPositionProvider(
    private val selectionBounds: Rect,
    density: Density,
) {
    private val gap = with(density) { 10.dp.roundToPx() }
    private val edgeMargin = with(density) { 12.dp.roundToPx() }

    fun calculatePosition(
        containerSize: IntSize,
        menuSize: IntSize,
    ): IntOffset {
        val fallback = Rect(
            containerSize.width / 2f,
            containerSize.height / 2f,
            containerSize.width / 2f,
            containerSize.height / 2f,
        )
        val bounds = selectionBounds.takeIf {
            it.left.isFinite() && it.top.isFinite() && it.right.isFinite() && it.bottom.isFinite()
        } ?: fallback
        val centerX = bounds.center.x.toInt()
        val selectionTop = bounds.top.toInt().coerceIn(0, containerSize.height)
        val selectionBottom = bounds.bottom.toInt().coerceIn(0, containerSize.height)
        val maxX = (containerSize.width - menuSize.width - edgeMargin).coerceAtLeast(edgeMargin)
        val x = (centerX - menuSize.width / 2).coerceIn(edgeMargin, maxX)
        val above = selectionTop - menuSize.height - gap
        val below = selectionBottom + gap
        val maxY = (containerSize.height - menuSize.height - edgeMargin).coerceAtLeast(edgeMargin)
        val y = when {
            above >= edgeMargin -> above
            below <= maxY -> below
            else -> above.coerceIn(edgeMargin, maxY)
        }
        return IntOffset(x, y)
    }
}
