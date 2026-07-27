package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.repository.BackupRepository
import com.kixyu9527.kixyubook.core.common.repository.BackupResult
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: KixyuDatabase,
    private val settingsRepository: ReaderSettingsRepository,
) : BackupRepository {

    override suspend fun exportTo(uriString: String): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val work = File(context.cacheDir, "backup-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val snapshot = File(work, DATABASE_NAME)
                val escapedSnapshotPath = snapshot.absolutePath.replace("'", "''")
                database.openHelper.writableDatabase.execSQL("VACUUM INTO '$escapedSnapshotPath'")
                val settings = settingsRepository.settings.first()
                val goal = settingsRepository.readingGoalMinutes.first()
                val properties = Properties().apply {
                    setProperty("formatVersion", BACKUP_VERSION.toString())
                    setProperty("createdTime", System.currentTimeMillis().toString())
                    setProperty("fontSize", settings.fontSize.toString())
                    setProperty("lineHeight", settings.lineHeight.toString())
                    setProperty("letterSpacing", settings.letterSpacing.toString())
                    setProperty("margin", settings.margin.toString())
                    setProperty("theme", settings.theme.name)
                    setProperty("pageMode", settings.pageMode.name)
                    setProperty("customThemeEnabled", settings.customThemeEnabled.toString())
                    setProperty("customDayBackground", settings.customDayTheme.backgroundHex)
                    setProperty("customDayBody", settings.customDayTheme.bodyHex)
                    setProperty("customDayTitle", settings.customDayTheme.titleHex)
                    setProperty("customDayAccent", settings.customDayTheme.accentHex)
                    setProperty("customNightBackground", settings.customNightTheme.backgroundHex)
                    setProperty("customNightBody", settings.customNightTheme.bodyHex)
                    setProperty("customNightTitle", settings.customNightTheme.titleHex)
                    setProperty("customNightAccent", settings.customNightTheme.accentHex)
                    setProperty("showStatusBar", settings.showStatusBar.toString())
                    setProperty("showPageNumber", settings.showPageNumber.toString())
                    setProperty("volumeKeyPageTurn", settings.volumeKeyPageTurn.toString())
                    setProperty("keepScreenOn", settings.keepScreenOn.toString())
                    setProperty("appColorTheme", settings.appColorTheme.name)
                    setProperty("showChapterTitle", settings.showChapterTitle.toString())
                    settings.fontUuid?.let { setProperty("fontUuid", it) }
                    setProperty("readingGoalMinutes", goal.toString())
                }
                val output = context.contentResolver.openOutputStream(uriString.toUri(), "w") ?: error("无法创建备份文件")
                output.use { raw -> ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST_ENTRY)); properties.store(zip, "KixyuBook full backup") ; zip.closeEntry()
                    zip.putFile(snapshot, DATABASE_ENTRY)
                    ASSET_DIRECTORIES.forEach { name -> File(context.filesDir, name).takeIf(File::exists)?.let { zip.putTree(it, "files/$name") } }
                } }
                BackupResult(countBooks(snapshot), snapshot.length() + ASSET_DIRECTORIES.sumOf { File(context.filesDir, it).treeSize() })
            } finally {
                work.deleteRecursively()
            }
        }
    }

    override suspend fun restoreFrom(uriString: String): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val work = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
            val extracted = File(work, "payload").apply { mkdirs() }
            try {
                var totalBytes = 0L
                var entries = 0
                val input = context.contentResolver.openInputStream(uriString.toUri()) ?: error("无法读取备份文件")
                input.use { raw -> ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(++entries <= MAX_ENTRIES) { "备份条目过多" }
                        val target = File(extracted, entry.name).canonicalFile
                        require(target.path.startsWith(extracted.canonicalPath + File.separator)) { "备份包含非法路径" }
                        if (entry.isDirectory) target.mkdirs() else {
                            target.parentFile?.mkdirs()
                            target.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read <= 0) break
                                    totalBytes += read
                                    require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "备份解压后体积异常" }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                } }
                val properties = Properties().apply {
                    File(extracted, MANIFEST_ENTRY).takeIf(File::isFile)?.inputStream()?.use(::load) ?: error("不是有效的 KixyuBook 备份")
                }
                require(properties.getProperty("formatVersion")?.toIntOrNull() == BACKUP_VERSION) { "不支持此备份版本" }
                val snapshot = File(extracted, DATABASE_ENTRY)
                require(snapshot.isFile) { "备份缺少数据库" }
                validateAndRebase(snapshot, File(extracted, "files"))
                val bookCount = countBooks(snapshot)
                installRestore(snapshot, File(extracted, "files"))
                restoreSettings(properties)
                BackupResult(bookCount, totalBytes, requiresRestart = true)
            } finally {
                work.deleteRecursively()
            }
        }
    }

    private fun validateAndRebase(snapshot: File, assets: File) {
        SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getInt(0) == DATABASE_VERSION) { "备份数据库版本不兼容" }
            }
            db.rawQuery("SELECT uuid, format, coverPath FROM books", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(0)
                    val format = cursor.getString(1).lowercase()
                    val storedBook = File(assets, "books/$uuid.$format")
                    require(storedBook.isFile) { "备份缺少书籍原文件：$uuid" }
                    val coverName = cursor.getString(2)?.let { File(it).name }
                    val cover = coverName?.let { File(context.filesDir, "covers/$it").absolutePath }
                    db.execSQL("UPDATE books SET storagePath = ?, coverPath = ? WHERE uuid = ?", arrayOf(storedBook.livePath("books"), cover, uuid))
                }
            }
            db.rawQuery("SELECT uuid, filePath FROM user_fonts", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(0)
                    val archived = File(assets, "fonts/${File(cursor.getString(1)).name}")
                    require(archived.isFile && archived.nameWithoutExtension == uuid) { "备份缺少用户字体：$uuid" }
                    db.execSQL("UPDATE user_fonts SET filePath = ? WHERE uuid = ?", arrayOf(archived.livePath("fonts"), uuid))
                }
            }
        }
    }

    private fun installRestore(snapshot: File, assets: File) {
        database.close()
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val replacing = File(dbFile.parentFile, "$DATABASE_NAME.restoring")
        snapshot.copyTo(replacing, overwrite = true)
        listOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm")).forEach { it.delete() }
        check(replacing.renameTo(dbFile)) { "无法安装恢复数据库" }
        ASSET_DIRECTORIES.forEach { name ->
            val live = File(context.filesDir, name)
            live.deleteRecursively()
            File(assets, name).takeIf(File::exists)?.copyRecursively(live, overwrite = true)
        }
    }

    private suspend fun restoreSettings(properties: Properties) {
        settingsRepository.update { current -> current.copy(
            fontSize = properties.float("fontSize", current.fontSize),
            lineHeight = properties.float("lineHeight", current.lineHeight),
            letterSpacing = properties.float("letterSpacing", current.letterSpacing),
            margin = properties.float("margin", current.margin),
            theme = properties.enum("theme", current.theme),
            pageMode = properties.enum("pageMode", current.pageMode),
            customThemeEnabled = properties.getProperty("customThemeEnabled")?.toBooleanStrictOrNull()
                ?: ((properties.getProperty("theme") == "CUSTOM") || current.customThemeEnabled),
            customDayTheme = current.customDayTheme.copy(
                backgroundHex = properties.getProperty(
                    "customDayBackground",
                    properties.getProperty("customBackground", current.customDayTheme.backgroundHex),
                ),
                bodyHex = properties.getProperty(
                    "customDayBody",
                    properties.getProperty("customBody", current.customDayTheme.bodyHex),
                ),
                titleHex = properties.getProperty(
                    "customDayTitle",
                    properties.getProperty("customTitle", current.customDayTheme.titleHex),
                ),
                accentHex = properties.getProperty(
                    "customDayAccent",
                    properties.getProperty("customAccent", current.customDayTheme.accentHex),
                ),
            ),
            customNightTheme = current.customNightTheme.copy(
                backgroundHex = properties.getProperty("customNightBackground", current.customNightTheme.backgroundHex),
                bodyHex = properties.getProperty("customNightBody", current.customNightTheme.bodyHex),
                titleHex = properties.getProperty("customNightTitle", current.customNightTheme.titleHex),
                accentHex = properties.getProperty("customNightAccent", current.customNightTheme.accentHex),
            ),
            fontUuid = properties.getProperty("fontUuid"),
            showStatusBar = properties.boolean("showStatusBar", current.showStatusBar),
            showPageNumber = properties.boolean("showPageNumber", current.showPageNumber),
            volumeKeyPageTurn = properties.boolean("volumeKeyPageTurn", current.volumeKeyPageTurn),
            keepScreenOn = properties.boolean("keepScreenOn", current.keepScreenOn),
            appColorTheme = properties.enum("appColorTheme", current.appColorTheme),
            showChapterTitle = properties.boolean("showChapterTitle", current.showChapterTitle),
        ) }
        settingsRepository.setReadingGoalMinutes(properties.getProperty("readingGoalMinutes")?.toIntOrNull() ?: 30)
    }

    private fun countBooks(snapshot: File): Int = SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM books", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun File.livePath(directory: String) = File(context.filesDir, "$directory/$name").absolutePath

    private companion object {
        const val DATABASE_NAME = "kixyu-books.db"
        const val DATABASE_VERSION = 4
        const val BACKUP_VERSION = 4
        const val MANIFEST_ENTRY = "manifest.properties"
        const val DATABASE_ENTRY = "database/kixyu-books.db"
        const val MAX_ENTRIES = 100_000
        const val MAX_UNCOMPRESSED_BYTES = 16L * 1024 * 1024 * 1024
        val ASSET_DIRECTORIES = listOf("books", "covers", "fonts")
    }
}

private fun ZipOutputStream.putFile(file: File, entryName: String) {
    putNextEntry(ZipEntry(entryName)); file.inputStream().buffered().use { it.copyTo(this) }; closeEntry()
}

private fun ZipOutputStream.putTree(root: File, entryRoot: String) {
    root.walkTopDown().filter(File::isFile).forEach { file ->
        putFile(file, "$entryRoot/${file.relativeTo(root).invariantSeparatorsPath}")
    }
}

private fun File.treeSize(): Long = takeIf(File::exists)?.walkTopDown()?.filter(File::isFile)?.sumOf(File::length) ?: 0L
private fun Properties.float(key: String, fallback: Float) = getProperty(key)?.toFloatOrNull() ?: fallback
private fun Properties.boolean(key: String, fallback: Boolean) = getProperty(key)?.toBooleanStrictOrNull() ?: fallback
private inline fun <reified T : Enum<T>> Properties.enum(key: String, fallback: T): T = getProperty(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
