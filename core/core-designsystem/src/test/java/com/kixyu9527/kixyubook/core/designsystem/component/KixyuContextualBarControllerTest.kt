package com.kixyu9527.kixyubook.core.designsystem.component

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class KixyuContextualBarControllerTest {
    @Test
    fun oldOwnerCannotClearNewerContextualBar() {
        val controller = KixyuContextualBarController()
        val leavingOwner = Any()
        val activeOwner = Any()
        val leavingState = KixyuContextualBarState(emptyList())
        val activeState = KixyuContextualBarState(emptyList())

        controller.show(leavingOwner, leavingState)
        controller.show(activeOwner, activeState)
        controller.clear(leavingOwner)

        assertEquals(activeState, controller.state)
        controller.clear(activeOwner)
        assertNull(controller.state)
    }
}
