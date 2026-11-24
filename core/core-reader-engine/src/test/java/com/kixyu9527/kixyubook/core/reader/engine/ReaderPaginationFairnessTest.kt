package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kixyu9527.kixyubook.core.common.model.Paragraph
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPaginationFairnessTest {
    @Test fun aLongLayoutYieldsAtFinalizedPagesInsteadOfBlockingTheOppositeNeighbour() = runBlocking {
        val paginator = MeasuredReaderPaginator(TextMeasurer(
            createFontFamilyResolver(RuntimeEnvironment.getApplication()), Density(1f), LayoutDirection.Ltr, cacheSize = 8,
        ), Density(1f))
        val spec = ReaderLayoutSpec(400f, 500f, 20f, 1.5f, 0f, 10f)
        val long = ReaderChapter(1, "fairness", "Long", 0, List(50) {
            Paragraph(it.toLong(), 1, it, "不能让长章节独占分页线程。".repeat(25))
        })
        var finishedLong = false
        val longResult = async {
            paginator.paginate(long, spec, FontFamily.Default, false).also { finishedLong = true }
        }
        val shortResult = async {
            assertFalse("Another ready layout must get a turn before the long one finishes", finishedLong)
            paginator.paginate(ReaderChapter(2, "fairness", "Short", 1,
                listOf(Paragraph(1, 2, 0, "短章正文"))), spec, FontFamily.Default, false)
        }
        assertEquals(1, shortResult.await().size)
        assertFalse("The opposite neighbour should finish before the long chapter", finishedLong)
        assertTrue(longResult.await().size > 4)
    }
}
