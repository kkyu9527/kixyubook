package com.kixyu9527.kixyubook

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuDetailPageEnterTransition
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuPredictiveBackEnabled
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.feature.settings.DiagnosticLogSession
import com.kixyu9527.kixyubook.feature.settings.LocalDiagnosticLogSession
import kotlinx.coroutines.delay

internal val LocalKixyuNavigationGestureActive = compositionLocalOf { false }

/**
 * App-wide Navigation 3 animation adapter, not a replacement navigation engine. Uses the same
 * entry/scene decorators, event handler and seekable NavDisplay as its backStack overload. Hoisting
 * the official event state lets decoration distinguish a real gesture from a button-driven pop.
 */
@Composable
internal fun KixyuAnimatedNavDisplay(
    backStack: List<AppRoute>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    entryProvider: (AppRoute) -> NavEntry<AppRoute>,
) {
    val diagnosticSession = remember { DiagnosticLogSession() }
    val containsLogs = backStack.any { it is AppRoute.DiagnosticLog || it is AppRoute.DiagnosticLogCategory }
    LaunchedEffect(containsLogs) {
        if (!containsLogs) {
            // Retain the snapshot while the outgoing scene is still animating; release it once
            // the log navigation session is over. Reopening logs then reads a fresh snapshot.
            delay(KixyuMotion.BackNavigationMillis.toLong())
            diagnosticSession.invalidate()
        }
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider,
    )
    val decorator = rememberKixyuPredictiveBackSceneDecorator(entries.map { it.contentKey })
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = remember { listOf(SinglePaneSceneStrategy<AppRoute>()) },
        sceneDecoratorStrategies = remember(decorator) { listOf(decorator) },
        onBack = onBack,
    )
    val gestureState = rememberNavigationEventState(
        currentInfo = SceneInfo(sceneState.currentScene),
        backInfo = sceneState.previousScenes.map { SceneInfo(it) },
    )
    val predictiveEnabled = LocalKixyuPredictiveBackEnabled.current
    NavigationBackHandler(
        state = gestureState,
        isBackEnabled = predictiveEnabled && sceneState.currentScene.previousEntries.isNotEmpty(),
        onBackCompleted = {
            repeat(entries.size - sceneState.currentScene.previousEntries.size) { onBack() }
        },
    )
    // Only the start/end boundary invalidates decoration; each progress event stays inside
    // Navigation 3's seekable transition rather than recomposing all application destinations.
    val gestureActive by remember(gestureState) {
        derivedStateOf { gestureState.transitionState is NavigationEventTransitionState.InProgress }
    }
    CompositionLocalProvider(
        LocalKixyuNavigationGestureActive provides (predictiveEnabled && gestureActive),
        LocalDiagnosticLogSession provides diagnosticSession,
    ) {
        NavDisplay(
            sceneState = sceneState,
            navigationEventState = gestureState,
            modifier = modifier,
            transitionSpec = {
                ContentTransform(kixyuDetailPageEnterTransition(), ExitTransition.None)
            },
            popTransitionSpec = {
                // The decorator translates the outgoing graphics layer on NavDisplay's own
                // transition clock. No second layout/offset animation competes with its scrim.
                ContentTransform(EnterTransition.None, ExitTransition.None)
            },
            predictivePopTransitionSpec = { _ ->
                ContentTransform(EnterTransition.None, ExitTransition.None)
            },
        )
    }
}
