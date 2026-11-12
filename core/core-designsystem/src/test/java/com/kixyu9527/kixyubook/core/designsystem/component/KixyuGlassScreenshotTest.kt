package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
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
class KixyuGlassScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun material() = capture(AppUiStyle.MATERIAL, ReaderTheme.DAY, "material")

    @Test
    fun miuix() = capture(AppUiStyle.MIUIX, ReaderTheme.DAY, "miuix")

    @Test
    fun materialDark() = capture(AppUiStyle.MATERIAL, ReaderTheme.NIGHT, "material_dark")

    @Test
    fun miuixDark() = capture(AppUiStyle.MIUIX, ReaderTheme.NIGHT, "miuix_dark")

    private fun capture(style: AppUiStyle, theme: ReaderTheme, suffix: String) {
        compose.setContent {
            KixyuBookTheme(themeMode = theme, uiStyle = style, glassEffectEnabled = true) {
                val backdrop = rememberKixyuNavigationBackdrop(MaterialTheme.colorScheme.background)
                CompositionLocalProvider(LocalKixyuGlassBackdrop provides backdrop) {
                    Box(Modifier.fillMaxSize()) {
                        BusyBackground(backdrop, theme)
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            KixyuPopupSurface(
                                modifier = Modifier.size(320.dp, 360.dp),
                                backdropEffect = KixyuPopupBackdropEffect.SURFACE_ONLY,
                            ) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("目录 / 简介 / 更新日志", style = MaterialTheme.typography.titleLarge)
                                    Text("内容面板优先保证阅读", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "第十四章：黄金\n\n书籍简介和更新日志不应与背后的正文混在一起。" +
                                            "保留统一的玻璃边缘与高光，文字区域提供稳定的阅读底色。",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    KixyuTextButton(text = "关闭", onClick = {})
                                }
                            }
                            KixyuGlassIsland(backdrop, Modifier.size(236.dp, 64.dp)) {
                                Text("阅读 · 书库 · 设置", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/glass_$suffix.png")
    }

    @Composable
    private fun BusyBackground(backdrop: KixyuNavigationBackdrop, theme: ReaderTheme) {
        Box(Modifier.fillMaxSize().kixyuNavigationBackdrop(backdrop)) {
            Canvas(Modifier.fillMaxSize()) {
                val dark = theme == ReaderTheme.NIGHT
                drawRect(
                    Brush.linearGradient(
                        if (dark) listOf(Color(0xFF0B1730), Color(0xFF392044), Color(0xFF123B36))
                        else listOf(Color(0xFFB8D8FF), Color(0xFFFFC7A8), Color(0xFFC7B8FF)),
                    ),
                )
                drawCircle(
                    Color.White.copy(alpha = if (dark) .20f else .55f),
                    radius = size.minDimension * .35f,
                    center = Offset(size.width * .2f, size.height * .25f),
                )
                drawCircle(
                    Color(0xFF3355AA).copy(alpha = if (dark) .45f else .5f),
                    radius = size.minDimension * .28f,
                    center = Offset(size.width * .8f, size.height * .7f),
                )
            }
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(30) { index ->
                    Text(
                        "背景正文 ${index + 1} · 阅读内容应保持独立，不与弹窗文字重叠。",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
