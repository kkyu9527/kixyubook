package com.kixyu9527.kixyubook.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KixyuSystemBarHostTest {
    @Test
    fun staleDestinationCannotClearCurrentPolicy() {
        val host = KixyuSystemBarHost()
        val leavingOwner = Any()
        val currentOwner = Any()
        val currentPolicy = KixyuSystemBarPolicy(
            statusBarVisible = false,
            navigationBarVisible = false,
            useDarkIcons = true,
        )

        host.update(leavingOwner, currentPolicy.copy(statusBarVisible = true))
        host.update(currentOwner, currentPolicy)
        host.clear(leavingOwner)

        assertEquals(currentPolicy, host.policy)
        host.clear(currentOwner)
        assertNull(host.policy)
    }
}
