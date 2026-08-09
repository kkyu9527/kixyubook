package com.kixyu9527.kixyubook.core.common.diagnostics

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.util.concurrent.Executors
import java.util.zip.ZipException
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

    private fun String.safeField(): String = redactSensitiveValues()
        .replace(Regex("[\\r\\n|]+"), " ")
        .take(MAX_FIELD_LENGTH)

    enum class Category { LIBRARY, SYNC, IMPORT, EPUB_PARSE, READER, PAGINATION }
}

/** Stable, R8-independent description suitable for persisted diagnostic records. */
data class DiagnosticFailure(
    val outcome: String,
    val reason: String,
)

fun Throwable.toDiagnosticFailure(): DiagnosticFailure {
    val classification = when (this) {
        is ZipException -> "invalid_archive" to "压缩文件结构损坏或不完整"
        is EOFException -> "truncated_input" to "文件内容不完整"
        is FileNotFoundException -> "missing_file" to "找不到需要读取的文件"
        is SQLiteConstraintException -> "constraint_error" to "本地数据违反唯一性约束"
        is SQLiteException -> "local_data_error" to "读取或保存本地数据失败"
        is SecurityException -> "permission_error" to "没有完成此操作所需的访问权限"
        is OutOfMemoryError -> "memory_error" to "处理内容时可用内存不足"
        is IOException -> "io_error" to "读取或写入数据失败"
        is IllegalArgumentException -> "invalid_data" to "输入数据不符合预期格式"
        is IllegalStateException -> "invalid_state" to "当前数据状态不符合操作要求"
        else -> "unexpected_error" to "发生未预期错误"
    }
    val message = generateSequence(this) { it.cause }
        .mapNotNull { cause -> cause.message?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
        ?.redactSensitiveValues()
        ?.take(MAX_DIAGNOSTIC_REASON_LENGTH)
    return DiagnosticFailure(
        outcome = classification.first,
        reason = message?.let { "${classification.second}：$it" } ?: classification.second,
    )
}

private const val MAX_DIAGNOSTIC_REASON_LENGTH = 180
private val credentialPattern = Regex(
    "(?i)(authorization|access[_-]?token|refresh[_-]?token|id[_-]?token|bearer)(\\s*[:=]\\s*|\\s+)[^\\s,;]+",
)
private val emailPattern = Regex("(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")

private fun String.redactSensitiveValues(): String =
    replace(credentialPattern) { match -> "${match.groupValues[1]}=<已隐藏>" }
        .replace(emailPattern, "<账号已隐藏>")
