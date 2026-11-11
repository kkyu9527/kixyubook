package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category

/** One settings-local logging vocabulary shared by settings, backup and habit writes. */
internal object SettingsDiagnostics {
    fun failure(
        event: String,
        stage: String,
        target: String,
        error: Throwable? = null,
    ) {
        DiagnosticLog.record(
            Category.SETTINGS,
            event,
            outcome = "failed",
            details = mapOf(
                "stage" to stage,
                "target" to target,
                "errorType" to (error?.javaClass?.simpleName ?: "unknown"),
                "reason" to (error?.message ?: ""),
            ),
        )
    }
}
