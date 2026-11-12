package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KixyuUnifiedGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialSurfacesShareTheirRenderedMaterial() = compare(AppUiStyle.MATERIAL)
    @Test fun miuixSurfacesShareTheirRenderedMaterial() = compare(AppUiStyle.MIUIX)
    @Test fun darkMaterialSurfacesShareTheirRenderedMaterial() = compare(AppUiStyle.MATERIAL, ReaderTheme.NIGHT)
    @Test fun darkMiuixSurfacesShareTheirRenderedMaterial() = compare(AppUiStyle.MIUIX, ReaderTheme.NIGHT)
    @Test fun surfacesWithoutABackdropShareTheSameClearFallback() = compare(AppUiStyle.MATERIAL, sampled = false)
    @Test fun materialContentSurfacesShareTheirReadableMaterial() = compare(AppUiStyle.MATERIAL, role = KixyuGlassRole.CONTENT)
    @Test fun miuixContentSurfacesShareTheirReadableMaterial() = compare(AppUiStyle.MIUIX, role = KixyuGlassRole.CONTENT)
    @Test fun darkMaterialContentSurfacesShareTheirReadableMaterial() = compare(AppUiStyle.MATERIAL, ReaderTheme.NIGHT, role = KixyuGlassRole.CONTENT)
    @Test fun darkMiuixContentSurfacesShareTheirReadableMaterial() = compare(AppUiStyle.MIUIX, ReaderTheme.NIGHT, role = KixyuGlassRole.CONTENT)
    @Test fun readableFallbackDoesNotDependOnHavingABackdrop() = compare(AppUiStyle.MATERIAL, sampled = false, role = KixyuGlassRole.CONTENT)

    private fun compare(
        style: AppUiStyle,
        theme: ReaderTheme = ReaderTheme.DAY,
        sampled: Boolean = true,
        role: KixyuGlassRole = KixyuGlassRole.CONTROL,
    ) {
        val tags = listOf("track", "surface", "popup", "interactive") +
            listOfNotNull("island".takeIf { role == KixyuGlassRole.CONTROL })
        compose.setContent {
            KixyuBookTheme(themeMode = theme, uiStyle = style, glassEffectEnabled = true) {
                val background = MaterialTheme.colorScheme.background
                val source = rememberKixyuNavigationBackdrop(background)
                CompositionLocalProvider(LocalKixyuGlassBackdrop provides source.takeIf { sampled }) {
                    Box(Modifier.fillMaxSize().background(background)) {
                        // Horizontally varying, vertically identical background at each surface.
                        Box(
                            Modifier.fillMaxSize()
                                .then(if (sampled) Modifier.kixyuNavigationBackdrop(source) else Modifier)
                                .background(Brush.horizontalGradient(listOf(Color(0xFFAACCEE), Color(0xFFDDCCBB)))),
                        )
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            val shape = KixyuGlassStyle.islandShape
                            fun surfaceModifier(tag: String) = Modifier.size(236.dp, 64.dp).testTag(tag)
                            KixyuGlasmioSurface(source, surfaceModifier("track"), shape, MaterialTheme.colorScheme.onSurface, role = role) {}
                            KixyuGlassSurface(source, surfaceModifier("surface"), shape, role = role) {}
                            if (role == KixyuGlassRole.CONTROL) KixyuGlassIsland(source, surfaceModifier("island"), shape) {}
                            KixyuPopupSurface(surfaceModifier("popup"), shape, role = role) {}
                            KixyuBackdropAwareInteractiveSurface(surfaceModifier("interactive"), shape, role = role) {}
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val expected = compose.onNodeWithTag(tags.first()).captureToImage().toPixelMap()
        tags.drop(1).forEach { tag ->
            val actual = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            assertEquals(tag, expected.width, actual.width)
            assertEquals(tag, expected.height, actual.height)
            // Compare centre, body and curved-edge regions; not only the text-free centre.
            for ((x, y) in listOf(118 to 32, 32 to 32, 204 to 32, 118 to 8, 16 to 16)) {
                val a = expected[x, y]
                val b = actual[x, y]
                assertEquals("$tag red at $x,$y", a.red, b.red, .02f)
                assertEquals("$tag green at $x,$y", a.green, b.green, .02f)
                assertEquals("$tag blue at $x,$y", a.blue, b.blue, .02f)
            }
        }
    }
}
