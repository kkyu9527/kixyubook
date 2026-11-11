package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuNavigationBackdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp")
class ReaderControlsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun material() = capture(AppUiStyle.MATERIAL, "material")

    @Test
    fun miuix() = capture(AppUiStyle.MIUIX, "miuix")

    @Test fun materialGlass() = capture(AppUiStyle.MATERIAL, "material_glass", glassEnabled = true)
    @Test fun miuixGlass() = capture(AppUiStyle.MIUIX, "miuix_glass", glassEnabled = true)

    private fun capture(style: AppUiStyle, suffix: String, glassEnabled: Boolean = false) {
        compose.setContent {
            KixyuBookTheme(
                themeMode = ReaderTheme.DAY,
                uiStyle = style,
                glassEffectEnabled = glassEnabled,
            ) {
                val background = MaterialTheme.colorScheme.background
                val accent = MaterialTheme.colorScheme.primary
                val backdrop = rememberKixyuNavigationBackdrop(background)
                Box(Modifier.fillMaxSize().background(background)) {
                    if (glassEnabled) {
                        Box(Modifier.fillMaxSize().kixyuNavigationBackdrop(backdrop).background(background)) {
                            Text(
                                "阅读正文与底栏保持独立，玻璃材质与导航栏一致。",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                            )
                        }
                    }
                    ReaderControls(
                        visible = true,
                        toolsMenuVisible = false,
                        settingsMenuVisible = false,
                        controlsBackProgress = { 0f },
                        popupBackProgress = { 0f },
                        bookTitle = "三体",
                        accentColor = accent,
                        backgroundColor = background,
                        backdrop = backdrop,
                        currentPageBookmarked = false,
                        hasPreviousChapter = true,
                        hasNextChapter = true,
                        onPreviousChapter = {},
                        onNextChapter = {},
                        onExit = {},
                        onDirectory = {},
                        onBookInfo = {},
                        onSettings = {},
                        onTools = {},
                        onSheet = {},
                        onResetReadingConfiguration = {},
                        onToggleBookmark = {},
                        onSearch = {},
                        canNavigateBack = true,
                        canNavigateForward = true,
                        onNavigateBack = {},
                        onNavigateForward = {},
                    )
                }
            }
        }

        compose.onNodeWithText("三体").assertExists()
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/reader_controls_$suffix.png")
    }
}
