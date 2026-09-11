package com.kixyu9527.kixyubook.core.common.cache

/** Allocations add up to a bounded budget; user books, annotations and indexes are not caches. */
object ReaderCacheBudget {
    const val CHAPTER_MEMORY_BYTES = 24L * 1024 * 1024
    const val PAGINATION_MEMORY_BYTES = 16L * 1024 * 1024
    const val EPUB_PACKAGE_MEMORY_BYTES = 8L * 1024 * 1024
    const val EPUB_CSS_MEMORY_BYTES = 6L * 1024 * 1024
    const val EPUB_DISK_BYTES = 192L * 1024 * 1024
    const val PAGINATION_DISK_BYTES = 96L * 1024 * 1024
    const val MAX_MEMORY_CHAPTERS = 6
    const val DEFAULT_IMAGE_MEMORY_BYTES = 32 * 1024 * 1024

    fun imageMemoryBytes(memoryClassMiB: Int, lowRam: Boolean): Int = if (lowRam) {
        8 * 1024 * 1024
    } else {
        (memoryClassMiB.toLong() * 1024 * 1024 / 16).coerceIn(12L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    }
}

/** Caller owns synchronization. Oversized entries remain usable by the caller, but are not retained. */
class WeightedLruCache<K, V>(
    private val maxBytes: Long,
    private val maxEntries: Int,
    diagnosticsName: String? = null,
    private val weigh: (V) -> Long,
) {
    private val diagnostics = diagnosticsName?.let(CacheDiagnostics::named)
    private data class Entry<V>(val value: V, val bytes: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, .75f, true)
    var retainedBytes: Long = 0
        private set
    val size get() = entries.size

    operator fun get(key: K): V? = entries[key]?.value.also {
        if (it == null) diagnostics?.misses?.incrementAndGet() else diagnostics?.hits?.incrementAndGet()
    }
    operator fun set(key: K, value: V) {
        entries.remove(key)?.let { retainedBytes -= it.bytes }
        val bytes = weigh(value).coerceAtLeast(1)
        if (bytes > maxBytes) { diagnostics?.oversized?.incrementAndGet(); return }
        entries[key] = Entry(value, bytes)
        retainedBytes += bytes
        trimToSize(maxEntries)
    }
    fun trimToSize(maximum: Int) {
        val iterator = entries.entries.iterator()
        while ((retainedBytes > maxBytes || entries.size > maximum) && iterator.hasNext()) {
            retainedBytes -= iterator.next().value.bytes
            iterator.remove()
            diagnostics?.evictions?.incrementAndGet()
        }
    }
    fun removeMatching(predicate: (K) -> Boolean) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (predicate(entry.key)) { retainedBytes -= entry.value.bytes; iterator.remove() }
        }
    }
    fun clear() { entries.clear(); retainedBytes = 0 }
}
