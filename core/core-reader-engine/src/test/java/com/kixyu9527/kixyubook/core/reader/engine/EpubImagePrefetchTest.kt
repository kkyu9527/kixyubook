package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubImagePrefetchTest {
    @Test
    fun keepsReadingOrderDeduplicatesAndBoundsWork() {
        val first = imageBlock("images/first.jpg", widthDp = 100f, heightDp = 200f)
        val duplicate = imageBlock("images/first.jpg", widthDp = 100f, heightDp = 200f)
        val second = imageBlock("images/second.jpg", widthDp = 80f, heightDp = 120f)
        val pages = listOf(page(0, first), page(1, duplicate, second))

        val requests = readerEpubImagePrefetchRequests(pages, density = 2f, maxImages = 2)

        assertEquals(
            listOf(
                EpubImagePrefetchRequest("images/first.jpg", 200, 400),
                EpubImagePrefetchRequest("images/second.jpg", 160, 240),
            ),
            requests,
        )
    }

    @Test fun fullPagePrefetchUsesThePhysicalViewportAndTheSamePixelRoundingAsRendering() {
        val full = imageBlock("cover.png", 400f, 850f).copy(isFullPageImage = true)
        val inline = imageBlock("drawing.png", 100.5f, 120.5f)
        val requests = readerEpubImagePrefetchRequests(listOf(page(0, full), page(1, inline)), 2.75f, 3,
            fullPageViewportWidthDp = 412.5f, fullPageViewportHeightDp = 915.5f)
        assertEquals(EpubImagePrefetchRequest("cover.png", 1134, 2518), requests[0])
        assertEquals(EpubImagePrefetchRequest("drawing.png", 276, 331), requests[1])
    }

    private fun page(index: Int, vararg blocks: DocumentBlock) = ReaderPage(
        index = index,
        chapterIndex = 0,
        chapterTitle = "chapter",
        isChapterOpening = index == 0,
        blocks = blocks.toList(),
    )

    private fun imageBlock(path: String, widthDp: Float, heightDp: Float) = DocumentBlock(
        paragraphIndex = 0,
        fullText = "",
        visibleText = "",
        continuation = false,
        kind = ParagraphKind.IMAGE,
        resourcePath = path,
        imageWidthDp = widthDp,
        imageHeightDp = heightDp,
    )
}
