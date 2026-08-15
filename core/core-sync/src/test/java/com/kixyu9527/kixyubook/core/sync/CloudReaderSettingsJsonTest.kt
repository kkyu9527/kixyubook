package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.model.ReaderBrightnessMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import org.junit.Test
import org.junit.Assert.assertEquals

class CloudReaderSettingsJsonTest {
    @Test
    fun globalReaderSettingsRoundTripIncludesNewPresentationOptions() {
        val expected = ReaderSettings(
            showReadingTime = true,
            showBatteryLevel = true,
            brightnessMode = ReaderBrightnessMode.MANUAL,
            brightness = .72f,
        )

        val actual = jsonToSettings(settingsToJson(expected))

        assertEquals(expected, actual)
    }
}
