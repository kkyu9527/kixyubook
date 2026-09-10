package com.kixyu9527.kixyubook.core.database

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties

/** A recoverable file-set transaction. Paths come from the app, never from the archive. */
internal class RestoreJournal(
    private val root: File,
    private val targets: Map<String, File>,
    private val preferencesKey: String = "preferences",
    private val checkpoint: (String) -> Unit = {},
) {
    private val manifest get() = File(root, "active.properties")
    private val committed get() = File(root, "committed")
    private fun old(key: String) = File(root, "old/$key")
    private fun staged(key: String) = File(root, "new/$key")
    private fun installing(key: String) = File(root, "installing/$key")

    fun requireRecovered() {
        check(!manifest.exists() && !committed.exists()) { "A previous restore needs an app restart" }
    }

    fun prepare(replacements: Map<String, File>) {
        requireRecovered()
        check(root.deleteRecursively() || !root.exists())
        check(root.mkdirs())
        replacements.forEach { (key, source) ->
            require(key in targets && key != preferencesKey)
            copyDurably(source, staged(key))
        }
        // DataStore remains live until restart, so capture (do not rename) its old atomic file.
        targets[preferencesKey]?.takeIf(File::exists)?.let { copyDurably(it, old(preferencesKey)) }
        val values = Properties().apply {
            targets.forEach { (key, target) -> setProperty(key, target.exists().toString()) }
        }
        val temporary = File(root, "active.tmp")
        FileOutputStream(temporary).use { output -> values.store(output, "restore journal"); output.fd.sync() }
        move(temporary, manifest)
        checkpoint("prepared")
    }

    /** Call only after all database users have released the live SQLite handle. */
    fun install() {
        check(manifest.isFile)
        targets.filterKeys { it != preferencesKey }.forEach { (key, target) ->
            // Absence at prepare time does not mean any later file belongs to the restore:
            // SQLite may create a WAL while the archive is staging. Mark only targets whose
            // installation has actually begun, after the live database has been closed.
            val intent = installing(key)
            intent.parentFile?.mkdirs()
            FileOutputStream(intent).use { it.write(1); it.fd.sync() }
            if (target.exists()) move(target, old(key))
            checkpoint("old:$key")
            if (staged(key).exists()) move(staged(key), target)
            checkpoint("new:$key")
        }
    }

    fun commit() {
        FileOutputStream(committed).use { it.write(1); it.fd.sync() }
        // Cleanup failure must not turn a committed restore into a rollback.
        runCatching { cleanup() }
    }

    fun rollback(includePreferences: Boolean) {
        if (!manifest.isFile) return
        val values = Properties().apply { manifest.inputStream().use(::load) }
        targets.forEach { (key, target) ->
            if (key == preferencesKey && !includePreferences) return@forEach
            val previous = old(key)
            if (previous.exists()) {
                check(target.deleteRecursively() || !target.exists()) { "Cannot remove incomplete restore: $key" }
                move(previous, target)
            } else if (values.getProperty(key) == "false" && (key == preferencesKey || installing(key).exists())) {
                check(target.deleteRecursively() || !target.exists()) { "Cannot remove new restore file: $key" }
            }
            checkpoint("rollback:$key")
        }
    }

    fun cleanup() {
        // Keep the commit decision until every rollback artifact is gone. Deleting the marker
        // first would let a cleanup failure turn a successful installation into a later rollback.
        root.listFiles().orEmpty().filter { it != committed }.forEach {
            check(it.deleteRecursively() || !it.exists()) { "Cannot clean restore journal" }
        }
        check(committed.delete() || !committed.exists())
        check(root.delete() || !root.exists())
    }

    fun recoverBeforeOpen() {
        if (committed.exists()) { cleanup(); return }
        if (manifest.exists()) rollback(includePreferences = true)
        cleanup()
    }

    private fun move(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) throw IOException("Cannot atomically move restore file: ${source.name}")
    }

    private fun copyDurably(source: File, target: File) {
        if (source.isDirectory) {
            check(target.mkdirs() || target.isDirectory)
            source.listFiles()?.forEach { copyDurably(it, File(target, it.name)) }
                ?: error("Cannot enumerate restore source")
        } else {
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output); output.fd.sync() }
            }
        }
    }
}

/** Runs before Hilt, DataStore, Room, WorkManager or any screen can observe partial data. */
fun recoverInterruptedBackupRestore(context: Context) = backupRestoreJournal(context).recoverBeforeOpen()

internal fun backupRestoreJournal(context: Context): RestoreJournal {
    val database = context.getDatabasePath(LocalBackupRepository.DATABASE_NAME)
    return RestoreJournal(File(context.noBackupFilesDir, "backup-install"), linkedMapOf(
        "database" to database,
        "wal" to File("${database.path}-wal"),
        "shm" to File("${database.path}-shm"),
        "books" to File(context.filesDir, "books"),
        "covers" to File(context.filesDir, "covers"),
        "fonts" to File(context.filesDir, "fonts"),
        "preferences" to File(context.filesDir, "datastore/reader_settings.preferences_pb"),
    ))
}
