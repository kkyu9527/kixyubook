package com.kixyu9527.kixyubook

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation 3 memoizes one entry per back-stack key. This guards the regression where a route
 * captured a snapshot value and only reflected it after leaving and re-entering the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en", application = Application::class)
class NavigationEntryStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun seedLog() = runBlocking {
        DiagnosticLog.initialize(ApplicationProvider.getApplicationContext<Context>())
        DiagnosticLog.clearAndAwait()
        DiagnosticLog.record(DiagnosticLog.Category.READER, "sample", outcome = "success")
    }

    @After fun clearLog() {
        runBlocking { DiagnosticLog.clearAndAwait() }
    }

    @Test fun diagnosticFilterFollowsStateWithoutChangingTheBackStack() {
        // State holders live outside composition so lint does not treat them as unremembered.
        val onlyFailures = mutableStateOf(false)
        val topDestinations = mutableStateOf(emptyList<TopDestination>())
        val initialReaderSettings = mutableStateOf(ReaderSettings())
        val updateState = mutableStateOf<AppUpdateState>(AppUpdateState.Idle)
        val externalImportRequestId = mutableStateOf<Long?>(null)
        val externalImportUris = mutableStateOf(emptyList<String>())
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                val pagerState = rememberPagerState { 1 }
                val dependencies = KixyuNavEntryDependencies(
                    topDestinations = topDestinations,
                    pagerState = pagerState,
                    navigator = KixyuNavigator(AppRoute.DiagnosticLog),
                    initialReaderSettings = initialReaderSettings,
                    updateState = updateState,
                    diagnosticOnlyFailures = onlyFailures,
                    externalImportRequestId = externalImportRequestId,
                    externalImportUris = externalImportUris,
                    uriHandler = object : UriHandler {
                        override fun openUri(uri: String) = Unit
                    },
                    openBook = {},
                    prioritizeAnimation = {},
                    popDestination = {},
                    exitReader = {},
                    onExternalImportConsumed = {},
                    onCheckForUpdates = {},
                    onUpdateResultConsumed = {},
                    onShowReleaseNotes = {},
                    onDiagnosticOnlyFailuresChanged = { onlyFailures.value = it },
                )
                KixyuAnimatedNavDisplay(
                    backStack = listOf(AppRoute.DiagnosticLog),
                    modifier = Modifier.fillMaxSize(),
                    onBack = {},
                    entryProvider = kixyuEntryProvider(dependencies),
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Reading").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { onlyFailures.value = true }
        compose.waitForIdle()
        compose.onAllNodesWithText("No error logs").assertCountEquals(1)
        compose.runOnIdle { onlyFailures.value = false }
        compose.waitForIdle()
        compose.onAllNodesWithText("Reading").assertCountEquals(1)
    }
}
