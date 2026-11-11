package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp")
class KixyuNavigationGlassScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun material() = capture(AppUiStyle.MATERIAL, "material")

    @Test fun miuix() = capture(AppUiStyle.MIUIX, "miuix")

    @Test fun materialDark() = capture(AppUiStyle.MATERIAL, "material_dark", ReaderTheme.NIGHT)

    @Test fun miuixDark() = capture(AppUiStyle.MIUIX, "miuix_dark", ReaderTheme.NIGHT)

    @Test
    @Config(qualifiers = "en-rUS-w1000dp-h700dp")
    fun tablet() = capture(AppUiStyle.MIUIX, "tablet", tablet = true)

    private fun capture(
        style: AppUiStyle,
        suffix: String,
        theme: ReaderTheme = ReaderTheme.DAY,
        tablet: Boolean = false,
    ) {
        compose.setContent {
            KixyuBookTheme(
                themeMode = theme,
                uiStyle = style,
                glassEffectEnabled = true,
            ) {
                var selected by remember { mutableStateOf("home") }
                val background = MaterialTheme.colorScheme.background
                val backdrop = rememberKixyuNavigationBackdrop(background)
                Box(Modifier.fillMaxSize().background(background)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .kixyuNavigationBackdrop(backdrop),
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = .32f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = .24f),
                                ))),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            // Blank pages cannot reveal over-blur or wrong sample coordinates.
                            repeat(8) { index ->
                                Text(
                                    "Chapter ${index + 1} · A book on the shelf",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(12.dp),
                                )
                            }
                        }
                    }
                    KixyuNavigationBar(
                        items = listOf(
                            KixyuNavigationItem("home", "Home", KixyuSymbols.AutoStories),
                            KixyuNavigationItem("library", "Library", KixyuSymbols.LibraryBooks),
                            KixyuNavigationItem("settings", "Settings", KixyuSymbols.Settings),
                        ),
                        selectedKey = selected,
                        onSelected = { selected = it.route },
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth(if (tablet) .5f else 1f),
                    )
                }
            }
        }

        compose.onNodeWithText("Home").assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/navigation_$suffix.png")
    }
}
