package com.kixyu9527.kixyubook.core.common.diagnostics

import java.io.IOException
import java.util.zip.ZipException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticFailureTest {
    @Test
    fun archiveFailureUsesStableOutcomeAndKeepsUsefulReason() {
        val failure = ZipException("invalid LOC header at chapter-77.xhtml").toDiagnosticFailure()

        assertEquals("invalid_archive", failure.outcome)
        assertTrue(failure.reason.contains("压缩文件结构损坏或不完整"))
        assertTrue(failure.reason.contains("chapter-77.xhtml"))
    }

    @Test
    fun diagnosticReasonRedactsCredentialsAndAccountAddress() {
        val failure = IOException(
            "Authorization: Bearer-secret access_token=top-secret user@example.com",
        ).toDiagnosticFailure()

        assertEquals("io_error", failure.outcome)
        assertFalse(failure.reason.contains("Bearer-secret"))
        assertFalse(failure.reason.contains("top-secret"))
        assertFalse(failure.reason.contains("user@example.com"))
        assertTrue(failure.reason.contains("已隐藏"))
    }
}
