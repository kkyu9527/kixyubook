package com.kixyu9527.kixyubook.core.designsystem.component

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KixyuPredictiveBackStateTest {
    @Test
    fun resetProgressSnapsAReplacedSurfaceBackToRest() = runBlocking {
        val state = KixyuPredictiveBackState<Unit>()
        state.commit(Unit, animate = false)
        assertEquals(1f, state.progressFor(Unit), 0.001f)

        state.resetProgress(Unit)

        assertEquals(0f, state.progressFor(Unit), 0.001f)
    }
}
