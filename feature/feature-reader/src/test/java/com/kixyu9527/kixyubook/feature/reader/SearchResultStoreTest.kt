package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.BookSearchResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchResultStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun largeSearchSpillsPreviewsAndKeepsLastResultAccessible() {
        val root = folder.newFolder()
        SearchResultStore(root).use { store ->
            val matches = List(20_005) { BookSearchResult(1, "章节", 0, it, "needle 😀 $it") }
            matches.chunked(128).forEach(store::add)
            store.add(matches.take(10))
            assertEquals(20_005, store.count)
            assertEquals(200, store.page(0).rows.size)
            assertEquals(matches.last(), store.page(20_000).rows.last())
            assertEquals(20_004, store.indexOf(matches.last()))
            assertTrue(root.listFiles().orEmpty().isNotEmpty())
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }
}
