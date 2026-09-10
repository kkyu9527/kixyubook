package com.kixyu9527.kixyubook.core.common.cache

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderCacheBudgetTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun imageAllocationRetainsLowMemoryPolicyAndHasAnUpperBound() {
        assertEquals(8 * 1024 * 1024, ReaderCacheBudget.imageMemoryBytes(256, true))
        assertEquals(16 * 1024 * 1024, ReaderCacheBudget.imageMemoryBytes(256, false))
        assertEquals(48 * 1024 * 1024, ReaderCacheBudget.imageMemoryBytes(8192, false))
    }

    @Test fun byteLimitEvictsColdEntriesAndOversizeDoesNotEvictNeighbours() {
        val cache = WeightedLruCache<Int, String>(10, 6) { it.length.toLong() }
        cache[1] = "1111"; cache[2] = "2222"
        assertEquals("1111", cache[1])
        cache[3] = "3333"
        assertNull(cache[2])
        cache[4] = "too large to retain"
        assertNull(cache[4])
        assertEquals("1111", cache[1])
        assertEquals(8L, cache.retainedBytes)
        cache.removeMatching { it == 1 }
        assertEquals(4L, cache.retainedBytes)
        cache.trimToSize(0)
        assertEquals(0L, cache.retainedBytes)
    }

    @Test fun diskBudgetKeepsRecentlyReadEntriesAndNeverDeletesOtherFiles() {
        val root = folder.newFolder()
        val original = File(root, "book.epub").apply { writeText("original") }
        val first = File(root, "1.bin").apply { writeText("1111"); setLastModified(1) }
        val second = File(root, "2.bin").apply { writeText("2222"); setLastModified(2) }
        val budget = DiskCacheBudget(root, "bin", 8)
        budget.touch(first)
        val third = File(root, "3.bin").apply { writeText("3333") }
        budget.written(third)
        assertFalse(second.exists())
        assertTrue(first.exists())
        assertTrue(third.exists())
        assertEquals("original", original.readText())
        DiskCacheBudget(root, "bin", 4)
        assertEquals(4L, root.listFiles()!!.filter { it.extension == "bin" }.sumOf(File::length))
    }
}
