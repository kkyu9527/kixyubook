package com.kixyu9527.kixyubook.feature.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDropdownRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow

/**
 * LocaleManager owns both persistence and the system language picker on Android 13+.
 * Do not put this device preference into ReaderSettings or change the process-wide locale:
 * neither book data nor the language on other synced devices should be rewritten.
 */
@Composable
internal fun AppLanguageControl() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Observe locale configuration; do not cache labels across a language change.
        val configuration = LocalConfiguration.current
        val manager = context.getSystemService(LocaleManager::class.java)
        val selected = manager.applicationLocales.toLanguageTags().substringBefore(',')
        KixyuDropdownRow(
            title = stringResource(R.string.settings_language),
            selected = selected,
            options = listOf("", "zh-Hans", "zh-Hant", "en", "ja"),
            optionLabel = { tag ->
                when (tag) {
                    "" -> stringResource(R.string.settings_language_system)
                    "zh-Hans" -> stringResource(R.string.language_simplified_chinese)
                    "zh-Hant" -> stringResource(R.string.language_traditional_chinese)
                    "en" -> stringResource(R.string.language_english)
                    "ja" -> stringResource(R.string.language_japanese)
                    else -> configuration.locales[0].getDisplayName(configuration.locales[0])
                }
            },
            onSelected = { tag ->
                if (tag != selected) manager.applicationLocales = LocaleList.forLanguageTags(tag)
            },
        )
    } else {
        KixyuSettingsRow(
            title = stringResource(R.string.settings_language),
            supportingText = stringResource(R.string.settings_language_system_legacy),
        )
    }
}
