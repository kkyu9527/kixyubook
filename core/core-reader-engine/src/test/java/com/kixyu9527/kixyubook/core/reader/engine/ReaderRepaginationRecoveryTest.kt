package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kixyu9527.kixyubook.core.common.model.Paragraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Exercises the real text paginator: a font-size change produces a different page layout, and the
 * saved character position must still resolve to a page that visibly contains it. This is the
 * engine-level guard for "recover by text position, not by the old page number".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderRepaginationRecoveryTest {
    private fun measurer(): TextMeasurer {
        val context = RuntimeEnvironment.getApplication()
        return TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
            cacheSize = 8,
        )
    }

    @Test
    fun largeFontRepaginatesAndTheSavedCharacterStaysVisible() = runBlocking {
        val longParagraph = "这是一段很长的正文，用来跨越多个页面。".repeat(200)
        val chapter = ReaderChapter(
            id = 1,
            bookUuid = "book",
            title = "第一章",
            index = 0,
            paragraphs = listOf(Paragraph(1, 1, 0, longParagraph)),
        )
        val paginator = MeasuredReaderPaginator(measurer(), Density(1f))
        val smallSpec = ReaderLayoutSpec(400f, 760f, 16f, 1.6f, 0f, 16f)
        val largeSpec = ReaderLayoutSpec(400f, 760f, 30f, 1.6f, 0f, 16f)

        val smallPages = paginator.paginate(chapter, smallSpec, FontFamily.Default, showRegularChapterTitle = false)
        val largePages = paginator.paginate(chapter, largeSpec, FontFamily.Default, showRegularChapterTitle = false)

        assertTrue("small layout should span several pages", smallPages.size > 1)
        assertTrue("a larger font must repaginate into more pages", largePages.size > smallPages.size)

        // A position in the middle of the paragraph, not the start and (almost surely) not a page
        // start, so the assertion checks visibility rather than an exact page boundary.
        val targetOffset = longParagraph.length / 2
        val positions = ReaderPositionManager()

        listOf(smallPages, largePages).forEach { pages ->
            val page = positions.pageFor(pages, paragraphIndex = 0, charOffset = targetOffset)
            val visible = pages[page].blocks.any { block ->
                block.paragraphIndex == 0 &&
                    targetOffset >= block.textStart &&
                    targetOffset < block.textStart + block.visibleText.length.coerceAtLeast(1)
            }
            assertTrue("target character must stay visible after repagination", visible)
        }
    }
}
