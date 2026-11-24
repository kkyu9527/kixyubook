package com.kixyu9527.kixyubook.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import com.kixyu9527.kixyubook.core.common.cache.CacheDiagnostics
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry
import com.kixyu9527.kixyubook.core.common.model.*
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real ZIPs and bitmap pixels, not just leaf tags or a missing-file placeholder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderImageLifetimeTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    @Test fun materialFullPageSurvivesSelectionScopeMovesAfterCacheEviction() = retain(AppUiStyle.MATERIAL, true)
    @Test fun miuixFullPageSurvivesSelectionScopeMovesAfterCacheEviction() = retain(AppUiStyle.MIUIX, true)
    @Test fun materialInlineArtSurvivesSelectionScopeMovesAfterCacheEviction() = retain(AppUiStyle.MATERIAL, false)
    @Test fun miuixInlineArtSurvivesSelectionScopeMovesAfterCacheEviction() = retain(AppUiStyle.MIUIX, false)
    @Test fun materialPagerKeepsRealPicturesDuringDragAndChapterHandoff() = drag(AppUiStyle.MATERIAL, PageTurnAnimation.HORIZONTAL_SLIDE)
    @Test fun miuixCoverPagerKeepsRealPicturesDuringDragAndChapterHandoff() = drag(AppUiStyle.MIUIX, PageTurnAnimation.COVER)

    private fun drag(style: AppUiStyle, animation: PageTurnAnimation) {
        val file = fixture()
        val chapters = listOf(Chapter(10, "book", "Plate", 0), Chapter(20, "book", "Body", 1))
        fun chapter(index: Int, resource: String) = ReaderChapter(chapters[index].id, "book", chapters[index].title, index,
            listOf(Paragraph(index.toLong() + 1, chapters[index].id, 0, resource, kind = ParagraphKind.IMAGE,
                resourcePath = resource, intrinsicWidth = 400, intrinsicHeight = 300, isFullPageImage = true)))
        val current = chapter(0, "red.png")
        val next = chapter(1, "blue.png")
        val state = mutableStateOf(ReaderUiState(
            book = Book("book", "Art", "", "", null, BookFormat.EPUB, file.path, file.path, 0, "fixture"),
            chapter = current, chapters = chapters, prefetchedChapters = mapOf(0 to current, 1 to next),
            settings = ReaderSettings(showChapterTitle = false, pageTurnAnimation = animation), loading = false,
        ))
        val turns = MutableSharedFlow<Int>()
        compose.setContent {
            val coordinator = rememberReaderPaginationCoordinator()
            val measurer = rememberTextMeasurer()
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                Box(Modifier.size(400.dp, 500.dp)) {
                    PagedReader(state.value.toReaderContentState(), state.value.chapter!!, spec, palette,
                        savePosition = { _, _, _, _, _ -> }, settlePage = { destination ->
                            state.value = state.value.copy(
                                chapter = if (destination.chapterIndex == 0) current else next,
                                chapterIndex = destination.chapterIndex, restorePosition = destination.paragraphIndex,
                                restoreCharOffset = destination.charOffset, settledPageIndex = destination.pageIndex,
                                navigationVersion = state.value.navigationVersion + 1,
                            )
                        }, middleTap = {}, dismissControls = {}, volumeTurns = turns, chapterTurns = turns,
                        paginationCoordinator = coordinator, paginationMeasurer = measurer, chapterRendered = {},
                        setPageInteractionActive = {}, resourcePriorityActive = false, twoPageSpread = false,
                        prioritizeAdjacentChapter = { _, _ -> }, spreadGutter = 0.dp,
                        topInsetDp = 0f, bottomInsetDp = 0f, physicalViewportHeightDp = 500f,
                        onTextActionTarget = {}, onDocumentLink = {})
                }
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("red.png").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithContentDescription("blue.png").fetchSemanticsNodes().isNotEmpty()
        }
        // A semantics node proves visible decoding, not completion of every queued prefetch.
        // Join the same serial loader before taking a counter baseline or evicting its cache.
        runBlocking {
            prefetchReaderEpubImages(file.path, listOf("red.png", "blue.png").map { resource ->
                page(true).copy(blocks = page(true).blocks.map { it.copy(resourcePath = resource) })
            }, density = 1f, fullPageViewportWidthDp = 400f, fullPageViewportHeightDp = 500f)
        }
        compose.waitForIdle()
        val decodes = CacheDiagnostics.named("epub-images").parses.get()
        val held = File(folder.root, "held.epub")
        compose.runOnIdle {
            MemoryPressureRegistry.dispatch(MemoryPressureLevel.CRITICAL)
            check(file.renameTo(held))
        }
        try {
            // Observe multiple live frames while the finger remains down. An end-state-only
            // assertion misses the image being destroyed at drag start and decoded at release.
            compose.mainClock.autoAdvance = false
            compose.onRoot().performTouchInput { down(Offset(350f, 250f)) }
            listOf(320f, 280f, 230f).forEach { x ->
                compose.onRoot().performTouchInput { moveTo(Offset(x, 250f), delayMillis = 20) }
                compose.mainClock.advanceTimeBy(32)
                compose.onNodeWithContentDescription("red.png").assertIsDisplayed()
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                assertTrue("A dragged plate must still paint its red bitmap", pixels[20, 250].red > .95f && pixels[20, 250].blue < .05f)
                compose.onNodeWithText("red.png").assertDoesNotExist()
            }
            compose.onRoot().performTouchInput { moveTo(Offset(40f, 250f)); up() }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithContentDescription("blue.png").assertIsDisplayed()
            val blue = compose.onNodeWithContentDescription("blue.png").captureToImage().toPixelMap()
            assertTrue(blue[blue.width / 2, blue.height / 2].blue > .95f)
            compose.onRoot().performTouchInput { swipeRight() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("red.png").assertIsDisplayed()
            assertEquals("Dragging and chapter acknowledgement reuse both prepared bitmaps", decodes,
                CacheDiagnostics.named("epub-images").parses.get())
        } finally {
            compose.mainClock.autoAdvance = true
            check(held.renameTo(file))
        }
    }

    private fun retain(style: AppUiStyle, full: Boolean) {
        val file = fixture()
        val enabled = mutableStateOf(true)
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                Box(Modifier.size(400.dp, 500.dp).background(Color.White)) {
                    ReaderPageRenderer(page(full), spec, palette, null, {}, epubPath = file.path,
                        showRegularChapterTitle = false, selectionEnabled = enabled.value)
                }
            }
        }
        awaitRedImage()
        val decodes = CacheDiagnostics.named("epub-images").parses.get()
        val held = File(folder.root, "temporarily-unavailable.epub")
        compose.runOnIdle {
            // The screen's already-decoded image must outlive the disposable LRU. Making only
            // our own fixture unavailable guarantees a re-created image cannot silently pass.
            MemoryPressureRegistry.dispatch(MemoryPressureLevel.CRITICAL)
            check(file.renameTo(held))
        }
        try {
            repeat(3) {
                compose.runOnIdle { enabled.value = false }
                assertRedImage()
                compose.onNodeWithText("logo").assertDoesNotExist()
                compose.runOnIdle { enabled.value = true }
                assertRedImage()
            }
            assertEquals("Selection changes must not decode another bitmap", decodes,
                CacheDiagnostics.named("epub-images").parses.get())
        } finally {
            check(held.renameTo(file))
        }
    }

    @Test fun aReusedImageNodeLoadsItsNewResourceInsteadOfRetainingTheOldBitmap() {
        val file = fixture()
        val resource = mutableStateOf("red.png")
        compose.setContent {
            KixyuBookTheme {
                ReaderPageRenderer(page(false).let { it.copy(blocks = it.blocks.map { block ->
                    block.copy(resourcePath = resource.value)
                }) }, spec, palette, null, {}, epubPath = file.path, showRegularChapterTitle = false)
            }
        }
        awaitRedImage()
        compose.runOnIdle { resource.value = "blue.png" }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("logo").fetchSemanticsNodes().isNotEmpty() &&
                imageCenter().blue > .95f
        }
        assertTrue("A changed resource must replace the previous red artwork", imageCenter().red < .05f)
    }

    @Test fun unresolvedInlineImageDoesNotRenderPublisherAltAsLoadingText() {
        compose.setContent {
            KixyuBookTheme {
                ReaderPageRenderer(page(false), spec, palette, null, {}, epubPath = null,
                    showRegularChapterTitle = false)
            }
        }
        compose.onNodeWithText("logo").assertDoesNotExist()
    }

    @Test fun failedInlineImageStillProvidesReadableFallback() {
        compose.setContent {
            KixyuBookTheme {
                ReaderPageRenderer(page(false), spec, palette, null, {},
                    epubPath = File(folder.root, "missing.epub").path, showRegularChapterTitle = false)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("logo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("logo").assertIsDisplayed()
    }

    private fun awaitRedImage() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("logo").fetchSemanticsNodes().isNotEmpty() }
        assertRedImage()
    }

    private fun imageCenter(): Color {
        val pixels = compose.onNodeWithContentDescription("logo").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun assertRedImage() {
        compose.onNodeWithContentDescription("logo").assertIsDisplayed()
        val pixel = imageCenter()
        assertTrue("The actual bitmap, not white background/alt text, stays visible", pixel.red > .95f && pixel.blue < .05f)
    }

    private fun fixture(): File = folder.newFile("images.epub").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            listOf("red.png" to android.graphics.Color.RED, "blue.png" to android.graphics.Color.BLUE).forEach { (name, color) ->
                val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                zip.putNextEntry(ZipEntry(name))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                zip.closeEntry()
                bitmap.recycle()
            }
        }
    }

    private fun page(full: Boolean) = ReaderPage(0, 0, "", false, listOf(
        DocumentBlock(0, "logo", "logo", false, kind = ParagraphKind.IMAGE,
            resourcePath = "red.png", intrinsicWidth = 400, intrinsicHeight = 300,
            imageWidthDp = 400f, imageHeightDp = 300f, isFullPageImage = full),
    ))

    private val spec = ReaderLayoutSpec(400f, 500f, 20f, 1.5f, 0f, 0f)
    private val palette = ReaderRenderPalette(Color.White, Color.Black, Color.Gray, Color.Blue, Color.Gray)
}
