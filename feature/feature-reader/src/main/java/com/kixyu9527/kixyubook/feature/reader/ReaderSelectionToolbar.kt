package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPredictiveBackHandler
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPredictivePopupTransform
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuPredictiveBackState
import com.kixyu9527.kixyubook.core.reader.engine.LocalReaderSelectionResetVersion
import kotlinx.coroutines.CompletableDeferred

/** Adds reader-owned actions and a style-aware floating menu to text selection. */
@Composable
internal fun ReaderSelectionToolbar(
    onCorrectParagraph: () -> Unit,
    dismissKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val currentCorrectionAction by rememberUpdatedState(onCorrectParagraph)
    val provider = remember { ReaderTextContextMenuProvider() }
    val predictiveBackState = rememberKixyuPredictiveBackState<Unit>()
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var selectionResetVersion by remember { mutableIntStateOf(0) }
    val dismissSelection = {
        val hadSelectionMenu = provider.isVisible
        provider.dismiss()
        if (hadSelectionMenu) selectionResetVersion++
    }

    DisposableEffect(provider) {
        onDispose(provider::dismiss)
    }
    LaunchedEffect(dismissKey) {
        dismissSelection()
    }
    CompositionLocalProvider(
        LocalTextContextMenuToolbarProvider provides provider,
        LocalTextContextMenuDropdownProvider provides provider,
        LocalReaderSelectionResetVersion provides selectionResetVersion,
    ) {
        Box(
            propagateMinConstraints = true,
            modifier = Modifier
                .onGloballyPositioned { layoutCoordinates = it }
                .pointerInput(provider.isVisible) {
                    if (provider.isVisible) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            // Observe without consuming: the same tap or swipe may still hide
                            // controls, turn the page, or clear the selection naturally.
                            dismissSelection()
                        }
                    }
                }
                .appendTextContextMenuComponents {
                    item(
                        key = CorrectParagraphKey,
                        label = "纠错",
                    ) {
                        currentCorrectionAction()
                        close()
                    }
                }
                .filterTextContextMenuComponents { component ->
                    component.key === TextContextMenuKeys.CopyKey ||
                        component.key === TextContextMenuKeys.SelectAllKey ||
                        component.key === CorrectParagraphKey
                },
        ) {
            content()
            layoutCoordinates?.let { coordinates ->
                provider.Menu(
                    coordinates = coordinates,
                    onDismiss = dismissSelection,
                    backProgress = predictiveBackState.progress,
                )
            }
        }
    }
    KixyuPredictiveBackHandler(
        target = Unit.takeIf { provider.isVisible },
        state = predictiveBackState,
        onBack = { dismissSelection() },
    )
}

private class ReaderTextContextMenuProvider : TextContextMenuProvider {
    private var request by mutableStateOf<MenuRequest?>(null)
    val isVisible: Boolean
        get() = request != null

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        request?.close()
        val newRequest = MenuRequest(dataProvider)
        request = newRequest
        try {
            newRequest.awaitClose()
        } finally {
            if (request === newRequest) request = null
        }
    }

    @Composable
    fun Menu(
        coordinates: LayoutCoordinates,
        onDismiss: () -> Unit,
        backProgress: Float,
    ) {
        val currentRequest = request ?: return
        if (!coordinates.isAttached) return

        val data by remember(currentRequest.dataProvider) {
            derivedStateOf(currentRequest.dataProvider::data)
        }
        val bounds = currentRequest.dataProvider.contentBounds(coordinates)
        val positionProvider = rememberSelectionMenuPositionProvider(bounds)

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                // The reader remains the gesture owner. This lets an outside tap or swipe dismiss
                // the menu and continue into the normal page-turn/control gesture.
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false,
            ),
        ) {
            ReaderTextSelectionMenu(
                data = data,
                session = currentRequest,
                onDismiss = onDismiss,
                backProgress = backProgress,
            )
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

    override fun close() {
        closed.complete(Unit)
    }

    suspend fun awaitClose() {
        closed.await()
    }
}

@Composable
private fun ReaderTextSelectionMenu(
    data: TextContextMenuData,
    session: TextContextMenuSession,
    onDismiss: () -> Unit,
    backProgress: Float,
) {
    val actions = data.components.filterIsInstance<TextContextMenuItem>()
    if (actions.isEmpty()) return

    KixyuPopupSurface(
        modifier = Modifier.kixyuPredictivePopupTransform(backProgress),
        shadowElevation = KixyuSpacing.extraSmall,
        windowBlurred = true,
    ) {
        Row(Modifier.padding(KixyuSpacing.extraSmall)) {
            actions.forEach { action ->
                val visual = actionVisual(action)
                KixyuIconButton(
                    onClick = {
                        action.onClick(session)
                        // Select all mutates the active selection and asks the context-menu data
                        // provider to publish its new range. Resetting SelectionContainer here
                        // immediately discards that range, making the action appear to do
                        // nothing. Copy/correction are terminal actions and may still clear it.
                        if (action.key !== TextContextMenuKeys.SelectAllKey) onDismiss()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = action.label,
                        // These vectors use different internal path bounds. A shared 48dp button
                        // plus per-glyph optical sizing makes their visible weight consistent.
                        modifier = Modifier.size(visual.opticalSize),
                        tint = if (visual.emphasized) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

private data class SelectionActionVisual(
    val icon: ImageVector,
    val opticalSize: androidx.compose.ui.unit.Dp,
    val emphasized: Boolean = false,
)

@Composable
private fun actionVisual(action: TextContextMenuItem): SelectionActionVisual =
    when (action.key) {
        TextContextMenuKeys.CopyKey -> SelectionActionVisual(
            icon = KixyuSymbols.ContentCopyRounded,
            opticalSize = 20.dp,
        )
        TextContextMenuKeys.SelectAllKey -> SelectionActionVisual(
            icon = KixyuSymbols.SelectAllRounded,
            opticalSize = 21.dp,
        )
        CorrectParagraphKey -> SelectionActionVisual(
            icon = KixyuSymbols.EditNoteRounded,
            opticalSize = 21.dp,
            emphasized = true,
        )
        else -> SelectionActionVisual(
            icon = KixyuSymbols.EditNoteRounded,
            opticalSize = 21.dp,
        )
    }

@Composable
private fun rememberSelectionMenuPositionProvider(
    selectionBounds: Rect,
): PopupPositionProvider {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(selectionBounds, density) {
        SelectionMenuPositionProvider(selectionBounds, density)
    }
}

private class SelectionMenuPositionProvider(
    private val selectionBounds: Rect,
    density: Density,
) : PopupPositionProvider {
    private val gap = with(density) { 10.dp.roundToPx() }
    private val edgeMargin = with(density) { 8.dp.roundToPx() }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val selectionCenterX = anchorBounds.left + selectionBounds.center.x.toInt()
        val selectionTop = anchorBounds.top + selectionBounds.top.toInt()
        val selectionBottom = anchorBounds.top + selectionBounds.bottom.toInt()

        val maxX = (windowSize.width - popupContentSize.width - edgeMargin).coerceAtLeast(edgeMargin)
        val x = (selectionCenterX - popupContentSize.width / 2).coerceIn(edgeMargin, maxX)

        val above = selectionTop - popupContentSize.height - gap
        val below = selectionBottom + gap
        val maxY = (windowSize.height - popupContentSize.height - edgeMargin)
            .coerceAtLeast(edgeMargin)
        val y = when {
            above >= edgeMargin -> above
            below <= maxY -> below
            else -> above.coerceIn(edgeMargin, maxY)
        }
        return IntOffset(x, y)
    }
}

private data object CorrectParagraphKey
