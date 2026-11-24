package com.kixyu9527.kixyubook.feature.reader.browser

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserSecurityTest {
    @Test fun theProductionSettingsDoNotGiveBooksFileContentNetworkStorageOrPopupAccess() {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        try {
            val settings = view.settings
            settings.configureEpubReader()
            assertTrue(settings.javaScriptEnabled) // Only sanitized documents + bundled app script.
            assertFalse(settings.allowFileAccess)
            assertFalse(settings.allowContentAccess)
            assertTrue(settings.blockNetworkLoads)
            assertFalse(settings.domStorageEnabled)
            assertFalse(settings.databaseEnabled)
            assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
            assertFalse(settings.supportMultipleWindows())
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.mixedContentMode)
            assertEquals(WebSettings.LOAD_NO_CACHE, settings.cacheMode)
            assertTrue(settings.mediaPlaybackRequiresUserGesture)
        } finally { view.destroy() }
    }
}
