package com.kixyu9527.kixyubook.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSheetReplacementStateTest {
    @Test
    fun replacingTheColourPopupNeverInheritsTheCommittedFade() {
        val replacement = ReaderSheetReplacementState()
        // The system gesture committed on the colour popup: the shared animator sits at 1 (faded).
        val committed = { 1f }

        replacement.begin()
        assertTrue(replacement.replacing)
        assertEquals(
            "the theme popup must start settled, not transparent",
            0f,
            replacement.progress(committed),
            0.001f,
        )

        replacement.settle()
        assertEquals(committed(), replacement.progress(committed), 0.001f)
        // A later gesture on the theme popup drives the same animator again.
        assertEquals(0.4f, replacement.progress { 0.4f }, 0.001f)
    }
}
