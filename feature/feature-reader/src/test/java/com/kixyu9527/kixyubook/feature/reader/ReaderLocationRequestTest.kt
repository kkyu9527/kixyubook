package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Chapter
import org.junit.Assert.*
import org.junit.Test

class ReaderLocationRequestTest {
    private val chapters = listOf(10, 20, 30).map { Chapter(it.toLong(), "book", "chapter", it) }

    @Test fun sourcesShareResolutionAndHistoryKeepsExactCharacterAnchor() {
        val history = ReaderLocationHistory()
        val start = ReaderLocation(0, 5, 23)
        var current = start
        for (source in listOf(ReaderLocationSource.BOOKMARK, ReaderLocationSource.ANNOTATION, ReaderLocationSource.SEARCH, ReaderLocationSource.DOCUMENT_LINK)) {
            val target = sourceLocation(20, 8, 42, source).resolve(chapters)!!
            assertEquals(ReaderLocation(1, 8, 42), target)
            history.record(current, target)
            current = target
        }
        assertEquals(start, history.goBack(current))
        assertEquals(current, history.goForward(start))
    }

    @Test fun directoryPositionsAreNotConfusedWithSourceIndexesAndUnknownIndexesStayUnresolved() {
        val request = directoryLocation(2, source = ReaderLocationSource.DIRECTORY)
        assertEquals(ReaderLocation(2, 0, 0), request.resolve(chapters))
        assertNull(request.resolve(emptyList()))
        // Unknown source indexes and out-of-range positions are explicitly unresolved, never a
        // silently valid but wrong page.
        assertNull(directoryLocation(9, source = ReaderLocationSource.DIRECTORY).resolve(chapters))
        assertNull(sourceLocation(-1, -8, -4, ReaderLocationSource.RESTORE).resolve(chapters))
        assertNull(sourceLocation(99, 0, 0, ReaderLocationSource.BOOKMARK).resolve(chapters))
        // A known source index still maps to its directory position with its character anchor.
        assertEquals(ReaderLocation(1, 8, 42), sourceLocation(20, 8, 42, ReaderLocationSource.BOOKMARK).resolve(chapters))
    }
}
