package com.kixyu9527.kixyubook.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCloudProgressTest {
    @Test
    fun `new remote progress is applied after reader opened`() {
        assertTrue(
            shouldApplySyncedProgress(
                incomingUpdatedAt = 300,
                acceptedUpdatedAt = 100,
                latestLocalWriteAt = 200,
            ),
        )
    }

    @Test
    fun `own latest page write is not mistaken for remote progress`() {
        assertFalse(
            shouldApplySyncedProgress(
                incomingUpdatedAt = 300,
                acceptedUpdatedAt = 100,
                latestLocalWriteAt = 300,
            ),
        )
    }

    @Test
    fun `already accepted progress is not applied twice`() {
        assertFalse(
            shouldApplySyncedProgress(
                incomingUpdatedAt = 300,
                acceptedUpdatedAt = 300,
                latestLocalWriteAt = 100,
            ),
        )
    }

    @Test
    fun `older remote progress never moves reader backwards`() {
        assertFalse(
            shouldApplySyncedProgress(
                incomingUpdatedAt = 100,
                acceptedUpdatedAt = 300,
                latestLocalWriteAt = 300,
            ),
        )
    }

    @Test
    fun `initial pager settlement is not treated as reading`() {
        assertFalse(hasReaderMovedFromOpening(12, 40, 20, 12, 40, 20))
    }

    @Test
    fun `chapter or position change opens the local progress gate`() {
        assertTrue(hasReaderMovedFromOpening(12, 40, 20, 12, 41, 0))
        assertTrue(hasReaderMovedFromOpening(12, 40, 20, 13, 0, 0))
        assertTrue(hasReaderMovedFromOpening(12, 40, 20, 12, 40, 21))
    }
}
