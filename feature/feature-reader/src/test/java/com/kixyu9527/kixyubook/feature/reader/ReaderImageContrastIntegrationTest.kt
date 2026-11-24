package com.kixyu9527.kixyubook.feature.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.cache.CacheDiagnostics
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.reader.engine.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderImageContrastIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    @Test fun materialNativeArtUsesTheActualReaderPaletteNotTheAppTheme() = exercise(AppUiStyle.MATERIAL)
    @Test fun miuixNativeArtUsesTheActualReaderPaletteNotTheAppTheme() = exercise(AppUiStyle.MIUIX)
    @Test fun materialFullPagePrefetchDoesNotDecodeAgainWhenTheImageBecomesVisible() = exercise(AppUiStyle.MATERIAL, true)
    @Test fun miuixFullPagePrefetchDoesNotDecodeAgainWhenTheImageBecomesVisible() = exercise(AppUiStyle.MIUIX, true)
    @Test fun materialColoredArtDoesNotPaintTheWholePhysicalViewportWithItsBacking() = exercise(AppUiStyle.MATERIAL, colored = true)
    @Test fun miuixColoredArtDoesNotPaintTheWholePhysicalViewportWithItsBacking() = exercise(AppUiStyle.MIUIX, colored = true)

    private fun exercise(style: AppUiStyle, prefetch: Boolean = false, colored: Boolean = false) {
        val file = folder.newFile("art.epub")
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(100f, 40f, 300f, 160f, Paint().apply { color = android.graphics.Color.BLACK })
        if (colored) Canvas(bitmap).drawRect(200f, 40f, 300f, 160f, Paint().apply { color = android.graphics.Color.rgb(255,112,32) })
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("art.png")); bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry()
        }
        bitmap.recycle()
        val page = ReaderPage(0, 0, "", false, listOf(DocumentBlock(0, "Test art", "Test art", false,
            kind = ParagraphKind.IMAGE, resourcePath = "art.png", intrinsicWidth = 400,
            intrinsicHeight = 200, imageWidthDp = 400f, imageHeightDp = 350f, isFullPageImage = true)))
        if (prefetch) runBlocking {
            prefetchReaderEpubImages(file.path, listOf(page), density = 1f,
                fullPageViewportWidthDp = 400f, fullPageViewportHeightDp = 400f)
        }
        val decodes = CacheDiagnostics.named("epub-images").parses.get()
        val background = mutableStateOf(Color.Black)
        compose.setContent {
            // A custom dark reading palette in a light app catches accidentally reading Material's
            // background instead of the real page color. The PNG must not be inverted or tinted.
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                Box(Modifier.size(400.dp, 400.dp).background(background.value)) {
                    ReaderPageRenderer(
                        page,
                        ReaderLayoutSpec(400f, 350f, 20f, 1.5f, 0f, 0f),
                        ReaderRenderPalette(background.value, Color.White, Color.Gray, Color.Blue, Color.Gray),
                        null, {}, epubPath = file.path, showRegularChapterTitle = false,
                        fullPageViewportWidthDp = 400f, fullPageViewportHeightDp = 400f,
                    )
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Test art").fetchSemanticsNodes().isNotEmpty() }
        fun pixels() = compose.onNodeWithContentDescription("Test art").captureToImage().toPixelMap()
        val dark = pixels()
        assertTrue("Transparent border must retain the dark page, not become a bright plate", dark[10, 10].red < .05f)
        if (colored) {
            assertTrue("Colored pixels remain original", dark[250,200].red > .99f)
            assertTrue(dark[250,200].green in .43f..45f)
            assertTrue("Backing follows the actual 400x200 image, not the 400x400 viewport", dark[10,150].red in .3f..65f)
        } else assertTrue("Neutral ink is legible but not pure white", dark[dark.width / 2, dark.height / 2].red in .65f..85f)
        compose.runOnIdle { background.value = Color.White }
        val light = pixels()
        assertTrue("Light mode restores the transparent border", light[10, 10].red > .999f)
        if (colored) assertTrue(light[250,200].green in .43f..45f)
        else assertTrue(light[light.width / 2, light.height / 2].red < .05f)
        assertEquals("Theme change must not decode another bitmap", decodes + if (prefetch) 0 else 1,
            CacheDiagnostics.named("epub-images").parses.get())
    }
}
