package com.kixyu9527.kixyubook.core.common.repository

/** Set before any provider initializes; a failed rollback must never expose mixed library files. */
object StartupRecoveryState {
    @Volatile var failure: Throwable? = null
        private set
    fun recover(action: () -> Unit) {
        failure = try { action(); null } catch (error: Exception) { error }
    }
    fun requireReady() { check(failure == null) { "Library recovery requires an app restart" } }
}
