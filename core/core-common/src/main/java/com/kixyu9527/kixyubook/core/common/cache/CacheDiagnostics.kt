package com.kixyu9527.kixyubook.core.common.cache

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
    }

    private val counters = ConcurrentHashMap<String, Counters>()
    fun named(name: String): Counters = counters.getOrPut(name) { Counters() }
    fun snapshot(): List<String> = counters.entries.sortedBy { it.key }.map { (name, value) ->
        "cache=$name hits=${value.hits.get()} misses=${value.misses.get()} evictions=${value.evictions.get()} " +
            "oversized=${value.oversized.get()} parses=${value.parses.get()} repeatedRecentParses=${value.repeatedParses.get()}"
    }
}
