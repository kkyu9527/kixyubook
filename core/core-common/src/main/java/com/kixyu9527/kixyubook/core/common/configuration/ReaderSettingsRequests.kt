package com.kixyu9527.kixyubook.core.common.configuration

import com.kixyu9527.kixyubook.core.common.model.ReaderSettings

/** Keeps not-yet-persisted intentions, including dragging a slider back to its original value. */
class ReaderSettingsRequests {
    private val pending = linkedMapOf<String, String>()

    fun changes(observed: ReaderSettings, transform: (ReaderSettings) -> ReaderSettings): Map<String, String> {
        pending.entries.removeAll { (_, patch) -> applySettingsPatch(observed, patch) == observed }
        val requested = pending.values.fold(observed, ::applySettingsPatch)
        return settingsChanges(requested, transform(requested)).also { pending.putAll(it) }
    }

    fun clear() = pending.clear()
}
