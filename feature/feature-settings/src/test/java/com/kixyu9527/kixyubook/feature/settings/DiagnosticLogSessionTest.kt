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
