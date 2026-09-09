package com.kixyu9527.kixyubook.feature.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.compositionLocalOf
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A single snapshot shared by the log index and its category destinations, never app data. */
class DiagnosticLogSession {
    private val mutex = Mutex()
    private var cachedConfiguration: Configuration? = null
    private var cachedEntries: List<ReadableDiagnosticEntry>? = null
    @Volatile private var pages = emptyMap<DiagnosticLogPageKey, DiagnosticLogPage>()
    private val invalidation = MutableStateFlow(0)
    internal val revision = invalidation.asStateFlow()

    internal fun peek(configuration: Configuration, category: String?, onlyFailures: Boolean) =
        pages[DiagnosticLogPageKey(configuration, category, onlyFailures)]

    internal suspend fun load(
        context: Context,
        configuration: Configuration,
        category: String?,
        onlyFailures: Boolean,
        awaitIdle: suspend () -> Unit = {},
    ): DiagnosticLogPage = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = DiagnosticLogPageKey(Configuration(configuration), category, onlyFailures)
            pages[key]?.let { return@withLock it }
            if (cachedConfiguration != configuration || cachedEntries == null) {
                // Freeze resources for this request: a language switch cancels its consumer,
                // rather than allowing half of a snapshot to be formatted in another language.
                val resources = context.createConfigurationContext(configuration).resources
                val formatter = DiagnosticLogFormatter(resources)
                val lines = DiagnosticLog.snapshotLines()
                val parsed = lines.asReversed().mapIndexed { index, line ->
                    currentCoroutineContext().ensureActive()
                    if (index % 128 == 0) awaitIdle()
                    formatter.parseDiagnosticEntry(line).copy(id = index)
                }
                cachedConfiguration = Configuration(configuration)
                cachedEntries = parsed
                pages = emptyMap()
            }
            buildDiagnosticLogPage(checkNotNull(cachedEntries), category, onlyFailures).also {
                pages = pages + (key to it)
            }
        }
    }

    suspend fun invalidate() = mutex.withLock {
        cachedEntries = null
        cachedConfiguration = null
        pages = emptyMap()
        invalidation.value += 1
    }
}

private data class DiagnosticLogPageKey(val configuration: Configuration, val category: String?, val onlyFailures: Boolean)

val LocalDiagnosticLogSession = compositionLocalOf<DiagnosticLogSession?> { null }

internal data class DiagnosticCategorySummary(
    val key: String,
    val label: String,
    val count: Int,
    val latestTime: String,
)

internal data class DiagnosticLogPage(
    val hasEntries: Boolean,
    val entries: List<ReadableDiagnosticEntry>,
    val summaries: List<DiagnosticCategorySummary>,
)

internal fun buildDiagnosticLogPage(
    entries: List<ReadableDiagnosticEntry>,
    category: String?,
    onlyFailures: Boolean,
): DiagnosticLogPage {
    val filtered = filterDiagnosticEntries(entries, onlyFailures = onlyFailures)
    return DiagnosticLogPage(
        hasEntries = entries.isNotEmpty(),
        // The category index renders summaries only; no need to retain a second detail list.
        entries = if (category == null) emptyList() else filterDiagnosticEntries(filtered, category),
        summaries = filtered.groupingBy(ReadableDiagnosticEntry::categoryKey).eachCount().map { (key, count) ->
            val latest = filtered.first { it.categoryKey == key }
            DiagnosticCategorySummary(key, latest.category, count, latest.time)
        },
    )
}
