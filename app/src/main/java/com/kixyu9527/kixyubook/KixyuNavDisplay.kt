package com.kixyu9527.kixyubook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReleaseNotesState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuContextualActionBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuContextualBarController
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPredictiveBackHandler
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuGlassBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuContextualBarController
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPredictivePopupTransform
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuUsesNavigationRail
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuPredictiveBackState
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.theme.kixyuPageBackground
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.core.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal data class TopDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)


@Composable
internal fun KixyuNavDisplay(
    navigator: KixyuNavigator,
    initialTopLevelRoute: String?,
    onTopLevelRouteChanged: (String) -> Unit,
    initialReaderSettings: ReaderSettings,
    updateState: AppUpdateState,
    releaseNotesState: ReleaseNotesState,
    onCheckForUpdates: () -> Unit,
    onUpdateResultConsumed: () -> Unit,
    onLoadReleaseNotes: () -> Unit,
    onAnimationPriorityChanged: (Boolean) -> Unit,
    onPrepareReader: (String) -> Unit,
    onPrioritizeBookSync: (String) -> Unit,
    onBookOpened: (String) -> Unit,
    externalImportRequestId: Long?,
    externalImportUris: List<String>,
    onExternalImportConsumed: (Long) -> Unit,
    onExitApp: () -> Unit,
) {
    val route = navigator.current()
    val homeIcon = KixyuSymbols.AutoStories
    val libraryIcon = KixyuSymbols.LibraryBooks
    val settingsIcon = KixyuSymbols.Settings
    val homeLabel = androidx.compose.ui.res.stringResource(com.kixyu9527.kixyubook.feature.home.R.string.home_title)
    val libraryLabel = androidx.compose.ui.res.stringResource(com.kixyu9527.kixyubook.feature.library.R.string.library_title)
    val settingsLabel = androidx.compose.ui.res.stringResource(com.kixyu9527.kixyubook.feature.settings.R.string.settings_title)
    val top = remember(homeIcon, libraryIcon, settingsIcon, homeLabel, libraryLabel, settingsLabel) {
        listOf(
            TopDestination(Routes.HOME, homeLabel, homeIcon),
            TopDestination(Routes.LIBRARY, libraryLabel, libraryIcon),
            TopDestination(Routes.SETTINGS, settingsLabel, settingsIcon),
        )
    }
    val useNavigationRail = kixyuUsesNavigationRail()
    val initialTopLevelPage = remember(top, initialTopLevelRoute) {
        topLevelPageForRoute(
            savedRoute = initialTopLevelRoute,
            routes = top.map(TopDestination::route),
        )
    }
    val pagerState = rememberPagerState(
        initialPage = initialTopLevelPage,
        pageCount = { top.size },
    )
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val uriHandler = LocalUriHandler.current
    var pageAnimation by remember { mutableStateOf<Job?>(null) }
    var animationPriorityJob by remember { mutableStateOf<Job?>(null) }
    var bookNavigationPending by remember { mutableStateOf(false) }
    var bookReorderAfterReaderExitJob by remember { mutableStateOf<Job?>(null) }
    var releaseNotesVisible by rememberSaveable { mutableStateOf(false) }
    var diagnosticOnlyFailures by rememberSaveable { mutableStateOf(false) }
    val contextualBarController = remember { KixyuContextualBarController() }
    val contextualBarState = contextualBarController.state
    LaunchedEffect(pagerState, top) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> top.getOrNull(page)?.route?.let(onTopLevelRouteChanged) }
    }
    LaunchedEffect(externalImportRequestId) {
        if (externalImportRequestId == null) return@LaunchedEffect
        if (navigator.current() != AppRoute.Home) navigator.popToHome()
        val libraryPage = top.indexOfFirst { it.route == Routes.LIBRARY }
        if (libraryPage >= 0 && pagerState.currentPage != libraryPage) {
            pagerState.scrollToPage(libraryPage)
        }
    }
    val topLevelActive = route == AppRoute.Home
    val topLevelBackState = rememberKixyuPredictiveBackState<Unit>()
    // Home, Library and Settings are sibling pages inside the single HOME destination. At that
    // level Back exits the task; it must never pop an accidentally restored detail/reader entry.
    KixyuPredictiveBackHandler(
        target = Unit.takeIf { topLevelActive && contextualBarState == null },
        state = topLevelBackState,
        animateOnCommit = false, // Android owns the app-to-home surface, not this overlay state.
        onBack = { onExitApp() },
    )
    val prioritizeAnimation: () -> Unit = {
        onAnimationPriorityChanged(true)
        animationPriorityJob?.cancel()
        animationPriorityJob = scope.launch {
            kotlinx.coroutines.delay(KixyuMotion.BackNavigationMillis.toLong())
            withFrameNanos { }
            withFrameNanos { }
            onAnimationPriorityChanged(false)
        }
    }
    var previousRoute by remember { mutableStateOf(route) }
    LaunchedEffect(route) {
        // A posted open request is no longer pending once Navigation publishes either its target
        // or a competing route. This also self-heals a request interrupted by an overlay/state
        // update instead of leaving the library unable to open books until process restart.
        bookNavigationPending = false
        if (previousRoute != route) {
            previousRoute = route
            prioritizeAnimation()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            animationPriorityJob?.cancel()
            bookReorderAfterReaderExitJob?.cancel()
            onAnimationPriorityChanged(false)
        }
    }

    val openBook: (String) -> Unit = { bookUuid ->
        val sourceRoute = navigator.current()
        if (sourceRoute in setOf(AppRoute.Home, AppRoute.HiddenLibrary) && !bookNavigationPending) {
            bookNavigationPending = true
            // Warm only Room and an existing binary chapter cache while the lightweight reader
            // surface animates. An uncached EPUB deliberately does not start parsing here.
            onPrepareReader(bookUuid)
            onPrioritizeBookSync(bookUuid)
            // Leave the current input dispatch, like Readest's setTimeout(0), without resuming
            // from a Compose frame callback. withFrameNanos resumed at the beginning of the next
            // VSYNC and placed destination creation directly inside that frame's 8.3 ms budget.
            val posted = view.post {
                try {
                    if (navigator.current() == sourceRoute) {
                        prioritizeAnimation()
                        navigator.push(AppRoute.Reader(bookUuid))
                    }
                } finally {
                    bookNavigationPending = false
                }
            }
            if (!posted) bookNavigationPending = false
        }
    }

    // The bar is an overlay outside NavDisplay. During predictive back the
    // destination underneath can therefore occupy the full window; the bar is
    // introduced only after the pop has committed to a top-level destination.
    val showBar = topLevelActive || contextualBarState != null
    // Delay its return until the top-level destination has committed. On exit,
    // AnimatedVisibility removes the bar after the short transition so an
    // invisible navigation item cannot intercept touches on secondary pages.
    var bottomBarPresented by remember { mutableStateOf(showBar) }
    LaunchedEffect(showBar) {
        if (showBar) {
            kotlinx.coroutines.delay(120)
            bottomBarPresented = true
        } else {
            bottomBarPresented = false
        }
    }
    val selectTopDestination: (KixyuNavigationItem) -> Unit = { destination ->
        val targetPage = top.indexOfFirst { it.route == destination.route }
        if (targetPage >= 0 && targetPage != pagerState.settledPage) {
            pageAnimation?.cancel()
            prioritizeAnimation()
            pageAnimation = scope.launch {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = tween(
                        durationMillis = KixyuMotion.PageNavigationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }
    val popDestination: () -> Unit = {
        prioritizeAnimation()
        navigator.pop()
    }
    val exitReader: (String) -> Unit = { bookUuid ->
        prioritizeAnimation()
        val returned = if (navigator.previous() == AppRoute.HiddenLibrary) {
            navigator.pop()
        } else {
            navigator.popToHome()
        }
        if (returned) {
            bookReorderAfterReaderExitJob?.cancel()
            bookReorderAfterReaderExitJob = scope.launch {
                kotlinx.coroutines.delay(KixyuMotion.BackNavigationMillis.toLong())
                onBookOpened(bookUuid)
            }
        } else {
            onExitApp()
        }
    }
    val handleNavigationBack: () -> Unit = {
        when (val current = navigator.current()) {
            is AppRoute.Reader -> exitReader(current.bookUuid)
            AppRoute.Home -> onExitApp()
            else -> popDestination()
        }
    }
    val navBackground = kixyuPageBackground()
    val navigationBackdrop = rememberKixyuNavigationBackdrop(navBackground)
    CompositionLocalProvider(
        LocalKixyuGlassBackdrop provides navigationBackdrop,
        LocalKixyuContextualBarController provides contextualBarController,
        LocalKixyuNavigationContentPadding provides KixyuSize.bottomNavigationContentHeight,
    ) {
        // Keep task content opaque while Android owns the app-to-home predictive animation.
        // A popup fade here exposes the Activity window background before finish() completes.
        Box(
            Modifier.fillMaxSize()
                .background(navBackground),
        ) {
            KixyuAnimatedNavDisplay(
                backStack = navigator.backStack,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (initialReaderSettings.glassEffectEnabled) {
                            Modifier.kixyuNavigationBackdrop(navigationBackdrop)
                        } else {
                            Modifier
                        },
                    ),
                onBack = handleNavigationBack,
                entryProvider = kixyuEntryProvider(
                    KixyuNavEntryDependencies(
                        topDestinations = top,
                        pagerState = pagerState,
                        navigator = navigator,
                        initialReaderSettings = initialReaderSettings,
                        updateState = updateState,
                        diagnosticOnlyFailures = diagnosticOnlyFailures,
                        externalImportRequestId = externalImportRequestId,
                        externalImportUris = externalImportUris,
                        uriHandler = uriHandler,
                        openBook = openBook,
                        prioritizeAnimation = prioritizeAnimation,
                        popDestination = popDestination,
                        exitReader = exitReader,
                        onExternalImportConsumed = onExternalImportConsumed,
                        onCheckForUpdates = onCheckForUpdates,
                        onUpdateResultConsumed = onUpdateResultConsumed,
                        onShowReleaseNotes = {
                            onLoadReleaseNotes()
                            releaseNotesVisible = true
                        },
                        onDiagnosticOnlyFailuresChanged = { diagnosticOnlyFailures = it },
                    ),
                ),
            )
            AnimatedVisibility(
                visible = bottomBarPresented,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(if (useNavigationRail) .5f else 1f),
                enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { height -> height / 8 },
                exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { height -> height / 8 },
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = contextualBarState,
                        transitionSpec = {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                        },
                        contentKey = { it != null },
                        label = "bottomBarMode",
                    ) { contextual ->
                        if (contextual == null) {
                            KixyuNavigationBar(
                                items = top.map { KixyuNavigationItem(it.route, it.label, it.icon) },
                                selectedKey = top.getOrNull(pagerState.settledPage)?.route,
                                enabled = bottomBarPresented,
                                onSelected = selectTopDestination,
                                backdrop = navigationBackdrop,
                            )
                        } else {
                            KixyuContextualActionBar(
                                actions = contextual.actions,
                                backdrop = navigationBackdrop,
                                modifier = Modifier.kixyuPredictivePopupTransform(
                                    contextual.backProgress(),
                                ),
                            )
                        }
                    }
                }
            }
            ReleaseNotesModal(
                show = releaseNotesVisible,
                state = releaseNotesState,
                onDismiss = { releaseNotesVisible = false },
                onRetry = onLoadReleaseNotes,
                onOpenReleasePage = {
                    runCatching {
                        uriHandler.openUri(
                            (releaseNotesState as? ReleaseNotesState.Available)
                                ?.release
                                ?.releaseUrl
                                ?: "$PROJECT_SOURCE_URL/releases",
                        )
                    }.isSuccess
                },
            )
        }
    }
}

internal fun topLevelPageForRoute(savedRoute: String?, routes: List<String>): Int =
    routes.indexOf(savedRoute).takeIf { it >= 0 } ?: 0
