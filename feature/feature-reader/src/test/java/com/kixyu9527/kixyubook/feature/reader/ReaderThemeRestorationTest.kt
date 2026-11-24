package com.kixyu9527.kixyubook.feature.reader

import android.content.res.Configuration
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderThemeRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialSystemThemeAndSubtreeRecreationKeepThePresentedCharacter() = exercise(AppUiStyle.MATERIAL, PageMode.PAGED)
    @Test fun miuixSystemThemeAndSubtreeRecreationKeepThePresentedCharacter() = exercise(AppUiStyle.MIUIX, PageMode.PAGED)
    @Test fun scrollModeSubtreeRecreationKeepsTheVisibleParagraph() = exercise(AppUiStyle.MATERIAL, PageMode.SCROLL)

    private fun exercise(style: AppUiStyle, mode: PageMode) {
        val harness = readerRecoveryHarness(paragraphCount = 24,
            paragraphText = { "Paragraph $it " + "文".repeat(if (mode == PageMode.PAGED) 2000 else 100) },
            initialSettings = ReaderSettings(fontSize = 25f, pageMode = mode, theme = ReaderTheme.SYSTEM, showChapterTitle = false),
            initialProgress = ReadingProgress("book", 1, 3, 0, 1, chapterKey = "first", paragraphIndex = 3))
        val reader = harness.open()
        val dark = mutableStateOf(false)
        val generation = mutableIntStateOf(0)
        val turns = MutableSharedFlow<Int>()
        var callbacks = 0
        var paragraph = -1
        var character = -1
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                KixyuBookTheme(themeMode = ReaderTheme.SYSTEM, uiStyle = style) {
                    key(generation.intValue) {
                        val content by reader.contentState.collectAsState()
                        Box(Modifier.size(412.dp, 915.dp)) {
                            if (content.chapter != null) ReaderContent(
                                state = content,
                                palette = rememberReaderPalette(content.settings),
                                savePosition = { p, c, complete, end, version ->
                                    reader.onPageSettled(p, c, complete, end, version)
                                    paragraph = p; character = c; callbacks++
                                },
                                presentationAnchor = reader::presentationAnchor,
                                moveChapterFromPage = { _, _, _ -> }, settlePage = reader::settlePage,
                                middleTap = {}, dismissControls = {}, volumeTurns = turns, chapterTurns = turns,
                                chapterRendered = reader::chapterRendered, setPageInteractionActive = {},
                                prioritizeAdjacentChapter = { _, _ -> }, resourcePriorityActive = false,
                                onTextActionTarget = {}, onDocumentLink = {},
                            )
                        }
                    }
                }
            }
        }
        try {
            waitFor { callbacks > 0 && paragraph >= 3 }
            compose.onRoot().performTouchInput {
                if (mode == PageMode.PAGED) swipe(centerRight, centerLeft, 500)
                else swipe(bottomCenter, topCenter, 500)
            }
            waitFor { if (mode == PageMode.PAGED) character > 0 else paragraph > 3 }
            val p = paragraph
            val c = character
            val version = reader.uiState.value.layoutVersion
            for (scheme in listOf(ReaderColorScheme.DYNAMIC, ReaderColorScheme.SEPIA, ReaderColorScheme.GREEN)) {
                compose.runOnIdle {
                    harness.globalSettings.value = harness.globalSettings.value.copy(readerColorScheme = scheme)
                }
                waitFor { reader.contentState.value.settings.readerColorScheme == scheme }
                assertEquals("Changing colours must not re-paginate", version, reader.uiState.value.layoutVersion)
                assertEquals(p, paragraph)
                assertEquals(c, character)
            }
            compose.runOnIdle { dark.value = true }
            compose.waitForIdle()
            assertEquals(version, reader.uiState.value.layoutVersion)
            assertEquals(p, paragraph)
            assertEquals(c, character)
            val beforeRecreate = callbacks
            compose.runOnIdle { generation.intValue++ }
            waitFor { callbacks > beforeRecreate }
            assertEquals("subtree recreation must not use the chapter entry", p, paragraph)
            if (mode == PageMode.PAGED) assertEquals(c, character)
        } finally { harness.closeAll() }
    }

    private fun waitFor(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 20_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            condition()
        }
        compose.waitForIdle()
    }
}
