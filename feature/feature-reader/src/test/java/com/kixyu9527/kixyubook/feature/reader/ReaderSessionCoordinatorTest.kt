package com.kixyu9527.kixyubook.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionCoordinatorTest {
    @Test
    fun aSupersededRequestCanNeitherClearNorReportTheNewerOne() {
        val session = ReaderSessionCoordinator()
        val first = session.begin(3)
        // The same chapter index is requested again; identity alone would have confused the two.
        val second = session.begin(3)

        assertFalse(session.isCurrent(first))
        assertTrue(session.isCurrent(second))

        session.finish(first)
        assertEquals(3, session.pendingIndex)

        session.finish(second)
        assertNull(session.pendingIndex)
    }

    @Test
    fun clearInvalidatesTheInFlightRequest() {
        val session = ReaderSessionCoordinator()
        val token = session.begin(1)
        session.clear()

        assertNull(session.pendingIndex)
        assertFalse(session.isCurrent(token))
    }
}
