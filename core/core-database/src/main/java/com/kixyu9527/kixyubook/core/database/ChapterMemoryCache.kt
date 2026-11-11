package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.cache.ReaderCacheBudget
import com.kixyu9527.kixyubook.core.common.cache.WeightedLruCache
import com.kixyu9527.kixyubook.core.common.model.ChapterContent

/**
 * Process-local decoded chapter cache. A single lock owns every mutation so eviction, deletion
 * and reads cannot race while the repository continues to coordinate persistence and indexing.
 */
internal class ChapterMemoryCache {
    private val lock = Any()
    private val cache = WeightedLruCache<ChapterCacheKey, ChapterContent>(
        ReaderCacheBudget.CHAPTER_MEMORY_BYTES,
        ReaderCacheBudget.MAX_MEMORY_CHAPTERS,
        diagnosticsName = "chapter",
    ) { content ->
        256L + content.paragraphs.sumOf { paragraph ->
            128L + paragraph.text.length * 2L + paragraph.spans.size * 96L
        }
    }

    operator fun get(key: ChapterCacheKey): ChapterContent? = synchronized(lock) { cache[key] }

    operator fun set(key: ChapterCacheKey, value: ChapterContent) = synchronized(lock) {
        cache[key] = value
    }

    fun removeBook(bookUuid: String) = synchronized(lock) {
        cache.removeMatching { it.bookUuid == bookUuid }
    }

    fun removeBooks(bookUuids: Set<String>) = synchronized(lock) {
        cache.removeMatching { it.bookUuid in bookUuids }
    }

    fun clear() = synchronized(lock) { cache.clear() }
}
