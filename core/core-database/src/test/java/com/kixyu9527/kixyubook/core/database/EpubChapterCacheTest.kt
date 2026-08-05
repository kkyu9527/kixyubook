package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import com.kixyu9527.kixyubook.core.reader.engine.DocumentImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubChapterCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun roundTripKeepsFullPageImagePresentation() {
        val cache = EpubChapterCache(folder.newFolder("epub-cache"))
        val chapter = DocumentChapter(
            title = "人物彩图",
            paragraphs = emptyList(),
            images = listOf(
                DocumentImage(
                    contentIndex = 0,
                    resourcePath = "OPS/Images/plate.png",
                    mediaType = "image/png",
                    altText = "人物彩图",
                    intrinsicWidth = 1200,
                    intrinsicHeight = 1800,
                    isFullPage = true,
                    cropToFill = true,
                ),
            ),
        )

        cache.write("book", "content", 2, chapter)

        assertEquals(chapter, cache.read("book", "content", 2))
    }
}
