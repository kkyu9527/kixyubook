package com.kixyu9527.kixyubook.feature.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticLogSessionTest {
    @Test fun cancelledParsingDoesNotPublishPartialSnapshotOrBlockNextLoad() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DiagnosticLog.initialize(context)
        DiagnosticLog.clearAndAwait()
        DiagnosticLog.record(DiagnosticLog.Category.READER, "sample")
        val config = Configuration(context.resources.configuration)
        val session = DiagnosticLogSession()
        val reachedCheckpoint = CompletableDeferred<Unit>()
        val job = launch {
            session.load(context, config, null, false) {
                reachedCheckpoint.complete(Unit)
                awaitCancellation()
            }
        }
        reachedCheckpoint.await()
        job.cancelAndJoin()
        assertNull(session.peek(config, null, false))
        assertTrue(session.load(context, config, null, false).hasEntries)
    }

    @Test fun clearingDiagnosticsAlsoDropsInMemoryCacheStatistics() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DiagnosticLog.initialize(context)
        DiagnosticLog.clearAndAwait()
        com.kixyu9527.kixyubook.core.common.cache.CacheDiagnostics.named("clear-test").parsed("entry")
        assertTrue(
            com.kixyu9527.kixyubook.core.common.cache.CacheDiagnostics.snapshot().isNotEmpty(),
        )
        assertTrue(DiagnosticLog.clearAndAwait())
        assertTrue(
            com.kixyu9527.kixyubook.core.common.cache.CacheDiagnostics.snapshot().isEmpty(),
        )
    }

    @Test fun categoryPreviewReusesSnapshotAndClearInvalidatesEveryPage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DiagnosticLog.initialize(context)
        DiagnosticLog.clearAndAwait()
        DiagnosticLog.record(DiagnosticLog.Category.READER, "sample", outcome = "io_error")
        DiagnosticLog.record(DiagnosticLog.Category.IMPORT, "sample", outcome = "success")
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("en")) }
        val session = DiagnosticLogSession()
        val overview = session.load(context, config, null, false)
        assertEquals(2, overview.summaries.sumOf { it.count })
        val reader = session.load(context, config, "READER", false)
        assertEquals(1, reader.entries.size)
        assertSame(overview, session.peek(config, null, false))
        assertSame(reader, session.load(context, config, "READER", false))
        val failures = session.load(context, config, null, true)
        assertEquals(listOf("READER"), failures.summaries.map { it.key })
        DiagnosticLog.clearAndAwait()
        session.invalidate()
        assertNull(session.peek(config, "READER", false))
        assertFalse(session.load(context, config, null, false).hasEntries)
    }

    @Test fun settingsFailureLinePreservesRootCauseFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = DiagnosticLogFormatter(context.resources).parseDiagnosticEntry(
            "2026-11-10T10:00:00Z | SETTINGS | settings_write_failed | outcome=failed | " +
                "stage=persist | target=reader:theme | failure=TEMPORARY | attempt=2",
        )
        assertEquals("SETTINGS", entry.categoryKey)
        assertEquals("Settings", entry.category)
        assertEquals("Setting could not be saved", entry.title)
        assertTrue(entry.isFailure)
        assertTrue(entry.details.any { it.first == "Setting" && it.second == "reader:theme" })
        assertTrue(entry.details.any { it.first == "Failure type" && it.second == "TEMPORARY" })
        assertTrue(entry.details.any { it.first == "Attempt" && it.second == "2" })
        assertTrue(entry.details.any { it.first == "Interrupted or failed stage" && it.second == "Persisting setting" })
    }

    @Test fun changingLocaleDoesNotReuseLabelsFromOldSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DiagnosticLog.initialize(context)
        DiagnosticLog.clearAndAwait()
        DiagnosticLog.record(DiagnosticLog.Category.READER, "sample")
        val session = DiagnosticLogSession()
        fun configuration(tag: String) = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }
        val english = session.load(context, configuration("en"), "READER", false)
        val japanese = session.load(context, configuration("ja"), "READER", false)
        assertNotEquals(english.entries.single().category, japanese.entries.single().category)
        assertEquals(english.entries.single().id, japanese.entries.single().id)
        assertNull(session.peek(configuration("en"), "READER", false))
    }
}
