package com.kixyu9527.kixyubook.core.common.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small, privacy-conscious diagnostic journal for operations that are otherwise hard to inspect.
 *
 * Entries are serialized on a background thread. Once the file reaches [MAX_FILE_BYTES], only the
 * newest [RETAINED_BYTES] are retained, so diagnostics can never grow without bound. Callers must
 * only pass identifiers and timings; book contents, authorization tokens and account addresses do
 * not belong in this log.
 */
object DiagnosticLog {
    private const val TAG = "KixyuDiagnostics"
    private const val DIRECTORY = "diagnostics"
    private const val FILE_NAME = "kixyu-diagnostics.log"
    private const val MAX_FILE_BYTES = 20L * 1024L * 1024L
    private const val RETAINED_BYTES = 10 * 1024 * 1024
    private const val MAX_FIELD_LENGTH = 240

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kixyu-diagnostics").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null

    fun initialize(context: Context) {
        logFile = File(context.filesDir, DIRECTORY).apply { mkdirs() }.resolve(FILE_NAME)
    }

    fun record(
        category: Category,
        event: String,
        elapsedMs: Long? = null,
        outcome: String? = null,
        details: Map<String, Any?> = emptyMap(),
    ) {
        val line = buildString {
            append(Instant.now())
            append(" | ").append(category.name)
            append(" | ").append(event.safeField())
            elapsedMs?.let { append(" | elapsedMs=").append(it.coerceAtLeast(0)) }
            outcome?.let { append(" | outcome=").append(it.safeField()) }
            details.forEach { (key, value) ->
                if (value != null) append(" | ").append(key.safeField()).append('=').append(value.toString().safeField())
            }
            append('\n')
        }
        runCatching { Log.d(TAG, line.trimEnd()) }
        executor.execute {
            runCatching {
                val file = logFile ?: return@runCatching
                trimIfNeeded(file, line.toByteArray().size)
                file.appendText(line)
            }.onFailure { error -> runCatching { Log.w(TAG, "Unable to persist diagnostics", error) } }
        }
    }

    suspend fun snapshotLines(): List<String> = withContext(Dispatchers.IO) {
        executor.submit<List<String>> {
            runCatching { logFile?.takeIf(File::exists)?.readLines().orEmpty() }.getOrDefault(emptyList())
        }.get()
    }

    suspend fun clearAndAwait(): Boolean = withContext(Dispatchers.IO) {
        executor.submit<Boolean> {
            runCatching {
                val file = logFile ?: return@runCatching true
                !file.exists() || file.delete()
            }.getOrDefault(false)
        }.get()
    }

    private fun trimIfNeeded(file: File, incomingBytes: Int) {
        if (!file.exists() || file.length() + incomingBytes <= MAX_FILE_BYTES) return
        val bytes = file.readBytes()
        val start = (bytes.size - RETAINED_BYTES).coerceAtLeast(0)
        var firstLineBreak = start
        while (firstLineBreak < bytes.size && bytes[firstLineBreak] != '\n'.code.toByte()) {
            firstLineBreak++
        }
        file.writeBytes(bytes.copyOfRange((firstLineBreak + 1).coerceAtMost(bytes.size), bytes.size))
    }

    private fun String.safeField(): String =
        replace(Regex("[\\r\\n|]+"), " ").take(MAX_FIELD_LENGTH)

    enum class Category { LIBRARY, SYNC, IMPORT, EPUB_PARSE, READER, PAGINATION }
}
