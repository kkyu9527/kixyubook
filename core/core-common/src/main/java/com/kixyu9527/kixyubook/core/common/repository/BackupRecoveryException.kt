package com.kixyu9527.kixyubook.core.common.repository

/** The database handle was closed; restart is required even after a successful rollback. */
class BackupRecoveryException(message: String, cause: Throwable) : Exception(message, cause)
