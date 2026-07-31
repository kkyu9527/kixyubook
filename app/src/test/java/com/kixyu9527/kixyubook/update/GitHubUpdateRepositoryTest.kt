package com.kixyu9527.kixyubook.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateRepositoryTest {
    @Test
    fun newerSemanticVersionIsDetected() {
        assertTrue(isNewerVersion("1.6.1", "1.6.0"))
        assertTrue(isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(isNewerVersion("1.10", "1.9.9"))
    }

    @Test
    fun equalOlderAndInvalidVersionsAreRejected() {
        assertFalse(isNewerVersion("1.6", "1.6.0"))
        assertFalse(isNewerVersion("1.5.9", "1.6.0"))
        assertFalse(isNewerVersion("latest", "1.6.0"))
    }

    @Test
    fun atomFallbackPreservesReleaseNotesAsMarkdown() {
        val release = parseLatestReleaseFeed(
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <link rel="alternate" type="text/html" href="https://github.com/kkyu9527/kixyubook/releases/tag/v1.7.0"/>
                <title>v1.7.0</title>
                <content type="html">&lt;h1&gt;Kixyu Book 1.7.0&lt;/h1&gt;
                &lt;p&gt;稳定性 &amp;amp; 更新体验。&lt;/p&gt;
                &lt;h2&gt;新增功能&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;应用内更新&lt;/li&gt;&lt;/ul&gt;</content>
              </entry>
            </feed>
            """.trimIndent(),
        )

        assertEquals("1.7.0", release?.versionName)
        assertEquals("v1.7.0", release?.releaseName)
        assertEquals("https://github.com/kkyu9527/kixyubook/releases/tag/v1.7.0", release?.releaseUrl)
        assertTrue(release?.releaseNotes.orEmpty().contains("# Kixyu Book 1.7.0"))
        assertTrue(release?.releaseNotes.orEmpty().contains("稳定性 & 更新体验。"))
        assertTrue(release?.releaseNotes.orEmpty().contains("## 新增功能"))
        assertTrue(release?.releaseNotes.orEmpty().contains("- 应用内更新"))
        assertNull(release?.downloadUrl)
    }
}
