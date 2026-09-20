package com.kixyu9527.kixyubook.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateUrlTrustTest {
    @Test
    fun onlyExactRepositoryDownloadUrlsAreTrusted() {
        assertTrue(
            AppUpdateDownloader.isTrustedDownloadUrl(
                "https://github.com/kkyu9527/kixyubook/releases/download/v1/app.apk",
            ),
        )
        assertTrue(
            "a query string must not defeat the suffix check",
            AppUpdateDownloader.isTrustedDownloadUrl(
                "https://github.com/kkyu9527/kixyubook/releases/download/v1/app.apk?token=1",
            ),
        )
        assertFalse(
            AppUpdateDownloader.isTrustedDownloadUrl(
                "https://github.com/kkyu9527/kixyubook.evil.com/releases/download/v1/app.apk",
            ),
        )
        assertFalse(
            AppUpdateDownloader.isTrustedDownloadUrl(
                "http://github.com/kkyu9527/kixyubook/releases/download/v1/app.apk",
            ),
        )
        assertFalse(
            AppUpdateDownloader.isTrustedDownloadUrl(
                "https://github.com/kkyu9527/kixyubook/releases/download/v1/app.apk.exe",
            ),
        )

        assertTrue(
            GitHubUpdateRepository.isTrustedApkUrl(
                "https://github.com/kkyu9527/kixyubook/releases/download/v1/app.apk",
            ),
        )
        assertFalse(
            GitHubUpdateRepository.isTrustedApkUrl(
                "https://evil.com/github.com/kkyu9527/kixyubook/releases/download/v1/app.apk",
            ),
        )
    }
}
