package com.kixyu9527.kixyubook.core.common.memory

import java.lang.ref.WeakReference

/**
 * Process-wide memory pressure levels shared by Android entry points and feature-owned caches.
 * Persistent disk caches are deliberately outside this contract: trimming memory must not make
 * reopening a large book expensive or reduce offline reading reliability.
 */
enum class MemoryPressureLevel {
    /** The app UI is no longer visible; discard presentation-only memory when practical. */
    BACKGROUND,

    /** The system is approaching its memory budget; retain only immediately useful state. */
    MODERATE,

    /** The process may be killed imminently; flush user state and release all derived memory. */
    CRITICAL,
}

fun interface MemoryPressureListener {
    fun onMemoryPressure(level: MemoryPressureLevel)
}

data class MemoryPressureDispatchResult(
    val listenerCount: Int,
    val failureCount: Int,
)

/**
 * Weak registry so short-lived reader coordinators and ViewModels never become process leaks.
 * A faulty cache cannot prevent the remaining caches from responding to a system warning.
 */
object MemoryPressureRegistry {
    private val listeners = mutableListOf<WeakReference<MemoryPressureListener>>()

    fun register(listener: MemoryPressureListener) = synchronized(listeners) {
        listeners.removeClearedAnd(listener)
        listeners += WeakReference(listener)
    }

    fun unregister(listener: MemoryPressureListener) = synchronized(listeners) {
        listeners.removeClearedAnd(listener)
    }

    fun dispatch(level: MemoryPressureLevel): MemoryPressureDispatchResult {
        val snapshot = synchronized(listeners) {
            val active = listeners.mapNotNull { it.get() }
            listeners.removeAll { it.get() == null }
            active
        }
        var failures = 0
        snapshot.forEach { listener ->
            runCatching { listener.onMemoryPressure(level) }
                .onFailure { failures += 1 }
        }
        return MemoryPressureDispatchResult(snapshot.size, failures)
    }

    private fun MutableList<WeakReference<MemoryPressureListener>>.removeClearedAnd(
        listener: MemoryPressureListener,
    ) {
        removeAll { reference ->
            val registered = reference.get()
            registered == null || registered === listener
        }
    }
}
