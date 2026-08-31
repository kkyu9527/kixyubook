package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.reader.engine.ReaderTextActionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTextInteractionStateTest {
    @Test
    fun clearingSelectionResetsTargetAndNestedPanelAtomically() {
        val state = ReaderTextInteractionState()
        state.publishTarget(
            ReaderTextActionTarget(
                chapterIndex = 2,
                paragraphIndex = 4,
                text = "selected text",
                selectedStart = 0,
                selectedEnd = 8,
            ),
        )
        state.showPanel(ReaderTextActionPanel.ANNOTATION_STYLE)

        state.clearSelection()

        assertNull(state.target)
        assertEquals(ReaderTextActionPanel.PRIMARY, state.panel)
        assertEquals(1, state.clearRequest)
    }
}
