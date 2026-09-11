package com.kixyu9527.kixyubook

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.AppUpdateInfo
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w800dp-h360dp-land", application = Application::class)
class UpdateModalLandscapeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun downloadActionStaysVisibleInAShortLandscapeWindow() {
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                AvailableUpdateModal(
                    update = AppUpdateInfo(
                        versionName = "9.9.9",
                        releaseName = "Kixyu Book 9.9.9",
                        releaseNotes = buildString {
                            repeat(80) { append("Line $it of the release notes.\n") }
                        },
                        releaseUrl = "https://example.com/releases/9.9.9",
                        downloadUrl = "https://example.com/app.apk",
                    ),
                    onDismiss = {},
                    onDownload = { true },
                )
            }
        }
        compose.onNodeWithText("Download").assertIsDisplayed()
    }
}
