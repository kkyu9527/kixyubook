package com.kixyu9527.kixyubook.update

import org.junit.Assert.assertFalse
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
}
