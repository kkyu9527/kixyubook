package com.kixyu9527.kixyubook.core.common.operation

import java.io.FileNotFoundException
import com.kixyu9527.kixyubook.core.common.repository.BackupRecoveryException

enum class OperationFailure(val retryable: Boolean) {
    STORAGE_FULL(false), PERMISSION(false), SOURCE_MISSING(false), RESTART_REQUIRED(false),
    INVALID_INPUT(false), TEMPORARY(true);

    companion object {
        fun from(error: Throwable): OperationFailure {
            val causes = generateSequence(error) { it.cause }.take(16).toList()
            return when {
                causes.any { it.javaClass.simpleName == "SQLiteFullException" || it.message.orEmpty().contains("ENOSPC") ||
                    it.message.orEmpty().contains("No space left on device", ignoreCase = true) } -> STORAGE_FULL
                causes.any { it is SecurityException } -> PERMISSION
                causes.any { it is FileNotFoundException } -> SOURCE_MISSING
                causes.any { it is BackupRecoveryException ||
                    it is IllegalStateException && it.message.orEmpty().contains("closed", ignoreCase = true) } -> RESTART_REQUIRED
                causes.any { it is IllegalArgumentException } -> INVALID_INPUT
                else -> TEMPORARY
            }
        }
    }
}
