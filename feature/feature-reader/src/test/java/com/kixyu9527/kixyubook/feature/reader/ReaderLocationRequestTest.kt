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
            val target = ReaderLocationRequest(20, 8, 42, source).resolve(chapters)!!
            assertEquals(ReaderLocation(1, 8, 42), target)
            history.record(current, target)
            current = target
        }
        assertEquals(start, history.goBack(current))
        assertEquals(current, history.goForward(start))
    }

    @Test fun directoryPositionsAreNotConfusedWithSourceIndexesAndEmptyLibraryDoesNotCrash() {
        val request = ReaderLocationRequest(2, source = ReaderLocationSource.DIRECTORY, isChapterPosition = true)
        assertEquals(ReaderLocation(2, 0, 0), request.resolve(chapters))
        assertNull(request.resolve(emptyList()))
        assertEquals(ReaderLocation(0, 0, 0), ReaderLocationRequest(-1, -8, -4, ReaderLocationSource.RESTORE).resolve(chapters))
    }
}
