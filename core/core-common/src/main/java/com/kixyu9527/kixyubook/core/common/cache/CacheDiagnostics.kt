package com.kixyu9527.kixyubook.core.common.cache

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Process counters only. No logging, formatting, file I/O or book text on the cache hot path. */
object CacheDiagnostics {
    class Counters {
        val hits = AtomicLong()
        val misses = AtomicLong()
        val evictions = AtomicLong()
        val oversized = AtomicLong()
        val parses = AtomicLong()
        val repeatedParses = AtomicLong()
        private val recent = LinkedHashMap<Int, Unit>(16, .75f, true)

        @Synchronized fun parsed(identity: Any) {
            parses.incrementAndGet()
            if (recent.put(identity.hashCode(), Unit) != null) repeatedParses.incrementAndGet()
            if (recent.size > 256) recent.entries.iterator().let { it.next(); it.remove() }
        }

        fun isEmpty(): Boolean = hits.get() == 0L && misses.get() == 0L && evictions.get() == 0L &&
            oversized.get() == 0L && parses.get() == 0L
    }

    private val counters = ConcurrentHashMap<String, Counters>()
    fun named(name: String): Counters = counters.getOrPut(name) { Counters() }

    /** Drops every counter so a cleared log cannot immediately republish stale cache statistics. */
    fun reset() {
        counters.clear()
    }

    /**
     * Emits the journal's pipe-delimited record shape (`time | CATEGORY | event | key=value`) so the
     * diagnostics screen can localize cache statistics instead of flagging them as damaged entries.
     */
    fun snapshot(): List<String> {
        val now = Instant.now()
        return counters.entries
            .filterNot { it.value.isEmpty() }
            .sortedBy { it.key }
            .map { (name, value) ->
                "$now | CACHE | cache_stats | cache=$name | hits=${value.hits.get()} | " +
                    "misses=${value.misses.get()} | evictions=${value.evictions.get()} | " +
                    "oversized=${value.oversized.get()} | parses=${value.parses.get()} | " +
                    "repeatedRecentParses=${value.repeatedParses.get()}"
            }
    }
}
