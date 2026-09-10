package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReaderSearchControllerTest {
    @Test fun incrementalBatchesKeepMoreThanOneThousandResultsAndLastChapterSelectable() = kotlinx.coroutines.runBlocking {
        ReaderSearchFixture().use { fixture ->
            fixture.controller.search("needle", ReaderSearchScope.BOOK)
            val request = fixture.repository.requests.single()
            val all = List(1_205) { index ->
                com.kixyu9527.kixyubook.core.common.model.BookSearchResult(100L + index, "chapter", index, 0, "needle")
            }
            all.chunked(128).forEach { request.partial(it) }
            org.junit.Assert.assertEquals(1_205, fixture.state.value.searchResults.size)
            request.result.complete(all)
            fixture.controller.select(1_204)
            org.junit.Assert.assertEquals(1_204 to 0, fixture.jumps.last())
        }
    }

    private val earlier = BookSearchResult(10, "序章", 2, 7, "黄金序章")

    @Test fun chapterOnlySearchUsesSourceIndexWithoutStartingFullBookWork() {
        ReaderSearchFixture().use { f ->
            f.controller.search(" 黄金 ", ReaderSearchScope.CURRENT_CHAPTER)
            assertEquals(listOf("黄金"), f.history)
            assertTrue(f.repository.requests.isEmpty())
            assertFalse(f.state.value.searchInProgress)
            assertEquals(1f, f.state.value.searchProgress)
            f.controller.select(0)
            assertEquals(listOf(5 to 3), f.jumps)
        }
    }

    @Test fun partialResultsUpdateImmediatelyAndSelectionSurvivesReordering() = runBlocking {
        ReaderSearchFixture().use { f ->
            f.controller.search("黄金", ReaderSearchScope.BOOK)
            val request = f.repository.requests.single()
            val immediate = f.state.value.searchResults.single()
            f.controller.select(0)
            request.progress(BookSearchProgress(BookSearchStage.SEARCHING, 1, 4))
            request.partial(listOf(earlier, immediate, earlier))
            assertTrue(f.state.value.searchInProgress)
            assertEquals(1, f.state.value.searchCompleted)
            assertEquals(4, f.state.value.searchTotal)
            assertEquals(listOf(earlier, immediate), f.state.value.searchResults)
            assertEquals(1, f.state.value.selectedSearchIndex)
            request.result.complete(listOf(earlier, immediate))
            assertFalse(f.state.value.searchInProgress)
            assertEquals(1f, f.state.value.searchProgress)
            f.controller.move(-1)
            assertEquals(listOf(5 to 3, 2 to 7), f.jumps)
            assertEquals(1, f.origins.size)
            f.controller.returnToReadingPosition()
            f.controller.returnToReadingPosition()
            assertEquals(1, f.returns)
            assertFalse(f.state.value.searchReturnAvailable)
        }
    }

    @Test fun lateCallbacksFromReplacedQueryCannotPolluteNewSearch() = runBlocking {
        ReaderSearchFixture().use { f ->
            f.controller.search("黄金", ReaderSearchScope.BOOK)
            val stale = f.repository.requests.single()
            f.controller.search("另一词", ReaderSearchScope.BOOK)
            stale.progress(BookSearchProgress(BookSearchStage.SEARCHING, 4, 4))
            stale.partial(listOf(earlier))
            stale.result.complete(listOf(earlier))
            assertEquals("另一词", f.state.value.searchQuery)
            assertTrue(f.state.value.searchResults.isEmpty())
            assertTrue(f.state.value.searchInProgress)
            assertEquals(0f, f.state.value.searchProgress)
        }
    }

    @Test fun failureKeepsUsableHitsAndRetryClearsError() {
        ReaderSearchFixture().use { f ->
            f.controller.search("黄金", ReaderSearchScope.BOOK)
            f.repository.requests.single().result.completeExceptionally(IllegalStateException("无法读取章节"))
            assertFalse(f.state.value.searchInProgress)
            assertEquals("无法读取章节", f.state.value.searchError)
            assertEquals(1, f.state.value.searchResults.size)
            f.controller.select(0)
            assertEquals(listOf(5 to 3), f.jumps)
            f.controller.search("黄金", ReaderSearchScope.BOOK)
            assertNull(f.state.value.searchError)
            f.repository.requests.last().result.complete(emptyList())
            assertFalse(f.state.value.searchInProgress)
        }
    }

    @Test fun clearCancelsWorkAndLeavesNoSelectionOrReturnAction() = runBlocking {
        ReaderSearchFixture().use { f ->
            f.controller.search("黄金", ReaderSearchScope.BOOK)
            val request = f.repository.requests.single()
            f.controller.select(0)
            f.controller.clear()
            request.partial(listOf(earlier))
            request.result.complete(listOf(earlier))
            f.controller.select(0)
            f.controller.returnToReadingPosition()
            assertEquals("", f.state.value.searchQuery)
            assertTrue(f.state.value.searchResults.isEmpty())
            assertFalse(f.state.value.searchInProgress)
            assertEquals(-1, f.state.value.selectedSearchIndex)
            assertFalse(f.state.value.searchReturnAvailable)
            assertEquals(1, f.jumps.size)
            assertEquals(0, f.returns)
        }
    }
}
