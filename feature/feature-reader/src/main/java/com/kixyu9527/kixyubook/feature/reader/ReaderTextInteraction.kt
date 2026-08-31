package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kixyu9527.kixyubook.core.reader.engine.ReaderTextActionTarget

internal enum class ReaderTextActionPanel {
    PRIMARY,
    ANNOTATION_STYLE,
    MORE,
}

/**
 * Single owner for every long-press text interaction in the reader.
 *
 * Renderers only publish a source-anchored target. The host owns menu mode, clearing, modal
 * hand-off and back priority, so paged/scrolling readers and Material/MIUIX never grow separate
 * selection lifecycles.
 */
@Stable
internal class ReaderTextInteractionState {
    var target by mutableStateOf<ReaderTextActionTarget?>(null)
        private set

    var panel by mutableStateOf(ReaderTextActionPanel.PRIMARY)
        private set

    var clearRequest by mutableIntStateOf(0)
        private set

    fun publishTarget(value: ReaderTextActionTarget) {
        target = value
    }

    fun showPanel(value: ReaderTextActionPanel) {
        panel = value
    }

    fun resetPanel() {
        panel = ReaderTextActionPanel.PRIMARY
    }

    fun clearSelection() {
        target = null
        panel = ReaderTextActionPanel.PRIMARY
        clearRequest++
    }
}

@Composable
internal fun rememberReaderTextInteractionState(): ReaderTextInteractionState =
    remember { ReaderTextInteractionState() }
