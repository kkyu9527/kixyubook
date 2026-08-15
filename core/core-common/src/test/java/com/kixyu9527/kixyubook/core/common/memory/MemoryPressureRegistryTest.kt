package com.kixyu9527.kixyubook.core.common.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryPressureRegistryTest {
    @Test
    fun dispatchNotifiesEveryListenerAndContainsFailures() {
        val received = mutableListOf<MemoryPressureLevel>()
        val healthy = MemoryPressureListener(received::add)
        val failing = MemoryPressureListener { error("test failure") }
        MemoryPressureRegistry.register(healthy)
        MemoryPressureRegistry.register(failing)

        try {
            val result = MemoryPressureRegistry.dispatch(MemoryPressureLevel.CRITICAL)

            assertEquals(listOf(MemoryPressureLevel.CRITICAL), received)
            assertEquals(2, result.listenerCount)
            assertEquals(1, result.failureCount)
        } finally {
            MemoryPressureRegistry.unregister(healthy)
            MemoryPressureRegistry.unregister(failing)
        }
    }

    @Test
    fun duplicateRegistrationStillDispatchesOnce() {
        var calls = 0
        val listener = MemoryPressureListener { calls += 1 }
        MemoryPressureRegistry.register(listener)
        MemoryPressureRegistry.register(listener)

        try {
            val result = MemoryPressureRegistry.dispatch(MemoryPressureLevel.MODERATE)

            assertEquals(1, calls)
            assertEquals(1, result.listenerCount)
        } finally {
            MemoryPressureRegistry.unregister(listener)
        }
    }
}
