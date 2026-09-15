package com.kixyu9527.kixyubook.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuReaderSettingsLevelBackStateTest {
    private val theme = KixyuReaderSettingsGroup.THEME_SCREEN.name
    private val font = KixyuReaderSettingsGroup.FONT_LAYOUT.name

    @Test
    fun aGestureOnTheColourLevelPreviewsTheGroupAndOnlyTheChildMoves() {
        val state = KixyuReaderSettingsLevelBackState()
        state.onFrame(KIXYU_READER_LEVEL_COLORS, 0f)

        val frame = state.onFrame(KIXYU_READER_LEVEL_COLORS, 0.4f)

        assertNull(frame.poppedKey)
        assertFalse(frame.suppressProgress)
        assertTrue(frame.showParentPreview)
        assertEquals(
            0.4f,
            frame.progressFor(KIXYU_READER_LEVEL_COLORS, KIXYU_READER_LEVEL_COLORS, 0.4f),
            0f,
        )
        assertEquals(0f, frame.progressFor(theme, KIXYU_READER_LEVEL_COLORS, 0.4f), 0f)
    }

    @Test
    fun aCommittedPopKeepsTheFinalFrameOnTheLevelThatLeft() {
        val state = KixyuReaderSettingsLevelBackState()
        state.onFrame(KIXYU_READER_LEVEL_COLORS, 0.4f)
        state.onFrame(KIXYU_READER_LEVEL_COLORS, 1f)

        val frame = state.onFrame(theme, 1f)

        assertEquals(KIXYU_READER_LEVEL_COLORS, frame.poppedKey)
        assertFalse(frame.showParentPreview)
        assertEquals(1f, frame.progressFor(KIXYU_READER_LEVEL_COLORS, theme, 1f), 0f)
        assertEquals(0f, frame.progressFor(theme, theme, 1f), 0f)
    }

    @Test
    fun aSecondBackGestureOnTheGroupFollowsTheFingerAgain() {
        val state = KixyuReaderSettingsLevelBackState()
        state.onFrame(KIXYU_READER_LEVEL_COLORS, 1f)
        state.onFrame(theme, 1f)

        val frame = state.onFrame(theme, 0.3f)

        assertNull(frame.poppedKey)
        assertTrue(frame.showParentPreview)
        assertEquals(0.3f, frame.progressFor(theme, theme, 0.3f), 0f)
    }

    @Test
    fun poppingTheGroupMovesTheFrameToTheGroupThatLeft() {
        val state = KixyuReaderSettingsLevelBackState()
        state.onFrame(font, 1f)

        val frame = state.onFrame(KIXYU_READER_LEVEL_ROOT, 1f)

        assertEquals(font, frame.poppedKey)
        assertEquals(1f, frame.progressFor(font, KIXYU_READER_LEVEL_ROOT, 1f), 0f)
        assertEquals(0f, frame.progressFor(KIXYU_READER_LEVEL_ROOT, KIXYU_READER_LEVEL_ROOT, 1f), 0f)
    }

    @Test
    fun reopeningTheSameLevelNeverInheritsTheStaleCommittedProgress() {
        val state = KixyuReaderSettingsLevelBackState()
        state.onFrame(KIXYU_READER_LEVEL_COLORS, 1f)
        state.onFrame(theme, 1f)

        val frame = state.onFrame(KIXYU_READER_LEVEL_COLORS, 1f)

        assertNull(frame.poppedKey)
        assertTrue(frame.suppressProgress)
        assertFalse(frame.showParentPreview)
        assertEquals(0f, frame.progressFor(KIXYU_READER_LEVEL_COLORS, KIXYU_READER_LEVEL_COLORS, 1f), 0f)
    }

    @Test
    fun aFreshGestureAfterReopeningClearsTheSuppression() {
        val state = KixyuReaderSettingsLevelBackState()
        state.onFrame(KIXYU_READER_LEVEL_COLORS, 1f)
        state.onFrame(theme, 1f)
        state.onFrame(KIXYU_READER_LEVEL_COLORS, 1f)

        val frame = state.onFrame(KIXYU_READER_LEVEL_COLORS, 0.5f)

        assertFalse(frame.suppressProgress)
        assertEquals(0.5f, frame.progressFor(KIXYU_READER_LEVEL_COLORS, KIXYU_READER_LEVEL_COLORS, 0.5f), 0f)
    }

    @Test
    fun anEmptyBackPressResetsWithoutAPreview() {
        val state = KixyuReaderSettingsLevelBackState()
        val frame = state.onFrame(KIXYU_READER_LEVEL_ROOT, 0f)

        assertNull(frame.poppedKey)
        assertFalse(frame.showParentPreview)
        assertEquals(0f, frame.progressFor(KIXYU_READER_LEVEL_ROOT, KIXYU_READER_LEVEL_ROOT, 0f), 0f)
    }
}
