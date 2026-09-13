package com.kixyu9527.kixyubook.core.database

import android.content.Context
import com.kixyu9527.kixyubook.core.common.configuration.*
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.ReadingReminderRepository
import org.json.JSONObject
import android.database.sqlite.SQLiteDatabase
import android.os.storage.StorageManager
import androidx.core.net.toUri
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.PageTurnAnimation
import com.kixyu9527.kixyubook.core.common.model.MAX_GLASS_FROST_LEVEL
import com.kixyu9527.kixyubook.core.common.model.MIN_GLASS_FROST_LEVEL
import com.kixyu9527.kixyubook.core.common.model.legacyGlassBlurRadiusToFrostLevel
import com.kixyu9527.kixyubook.core.common.repository.BackupRepository
import com.kixyu9527.kixyubook.core.common.repository.BackupPreview
import com.kixyu9527.kixyubook.core.common.repository.BackupResult
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.kixyu9527.kixyubook.core.common.repository.BackupRecoveryException
import com.kixyu9527.kixyubook.core.common.repository.withoutRecordingSyncMutations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
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
    private val libraryPreferences: LibraryPreferencesRepository,
    private val readingReminders: ReadingReminderRepository,
    private val bookSettings: com.kixyu9527.kixyubook.core.common.repository.BookSettingsRepository,
) : BackupRepository {
    private val operationMutex = Mutex()

    override suspend fun inspect(uriString: String): Result<BackupPreview> = withContext(Dispatchers.IO) {
        operationMutex.withLock { runCatching {
            cleanupBackupWorkDirectories()
            val work = File(context.cacheDir, "$INSPECT_WORK_PREFIX${UUID.randomUUID()}").apply { mkdirs() }
            val extracted = File(work, "payload").apply { mkdirs() }
            try {
                val totalBytes = extractArchive(uriString, extracted, work)
                val properties = loadManifest(extracted)
                val formatVersion = requireSupportedFormat(properties)
                val snapshot = File(extracted, DATABASE_ENTRY).also {
                    require(it.isFile) { context.getString(R.string.backup_no_database) }
                }
                val integrityProtected = verifyIntegrity(properties, extracted)
                validateAndRebase(snapshot, File(extracted, "files"))
                BackupPreview(
                    uriString = uriString,
                    createdTime = properties.getProperty("createdTime")?.toLongOrNull() ?: 0L,
                    bookCount = countBooks(snapshot),
                    totalBytes = totalBytes,
                    formatVersion = formatVersion,
                    integrityProtected = integrityProtected,
                )
            } finally {
                work.deleteRecursively()
            }
        } }
    }

    override suspend fun exportTo(uriString: String): Result<BackupResult> = withContext(Dispatchers.IO) {
        operationMutex.withLock { runCatching {
            cleanupBackupWorkDirectories()
            var pinned: PinnedBackup? = null
            var lastUnavailable: BackupResourceUnavailable? = null
            for (attempt in 0 until BACKUP_SNAPSHOT_ATTEMPTS) {
                try {
                    pinned = pinConsistentBackup()
                    break
                } catch (unavailable: BackupResourceUnavailable) {
                    // A referenced resource vanished between reading the settings and pinning them.
                    // Rebuild from a fresh snapshot rather than exporting a dangling reference.
                    lastUnavailable = unavailable
                }
            }
            val backup = pinned ?: throw (lastUnavailable ?: IllegalStateException(context.getString(R.string.backup_create_failed)))
            try {
                writeBackup(uriString, backup)
            } finally {
                backup.work.deleteRecursively()
            }
        } }
    }

    /**
     * Pins a database snapshot, the assets it references and every font the portable settings
     * reference, then verifies they agree. The settings are read outside the storage lock (the lock
     * must stay free for imports/deletions), so a font selected or deleted in between can make the
     * two views disagree; [BackupResourceUnavailable] asks the caller to retry from a fresh snapshot
     * instead of silently writing an archive whose settings reference an absent font.
     */
    private suspend fun pinConsistentBackup(): PinnedBackup {
        val work = File(context.cacheDir, "$BACKUP_WORK_PREFIX${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val snapshot = File(work, DATABASE_NAME)
            val escapedSnapshotPath = snapshot.absolutePath.replace("'", "''")
            // Pin files while taking the DB snapshot. Compression and hashing then operate on
            // private copies, so the user can keep reading or remove books afterwards.
            val snapshotAssets = LibraryStorageGate.mutex.withLock {
                database.openHelper.writableDatabase.execSQL("VACUUM INTO '$escapedSnapshotPath'")
                collectReferencedAssets(snapshot).map { asset ->
                    val copy = File(work, asset.entryName)
                    copy.parentFile?.mkdirs()
                    asset.file.copyTo(copy)
                    BackupAsset(copy, asset.entryName)
                }
            }
            val settings = settingsRepository.settings.first()
            val goal = settingsRepository.readingGoalMinutes.first()
            val bookOverrides = bookSettings.overrides.first()
            val referencedFonts = collectReferencedFonts(settings.fontUuid, bookOverrides)
            val assets = LibraryStorageGate.mutex.withLock {
                pinReferencedFonts(snapshot, work, snapshotAssets, referencedFonts)
            }
            val portableSettings = settingsPayloadJson(settings, goal,
                libraryPreferences.preferences.first(), readingReminders.readingReminder.first(),
                bookOverrides = bookOverrides)
            val bookCount = countBooks(snapshot)
            val totalBytes = snapshot.length() + assets.sumOf { it.file.length() }
            return PinnedBackup(work, snapshot, assets, bookCount, totalBytes, portableSettings)
        } catch (failure: Throwable) {
            work.deleteRecursively()
            throw failure
        }
    }

    private suspend fun writeBackup(uriString: String, backup: PinnedBackup): BackupResult {
        val properties = Properties().apply {
            setProperty("formatVersion", BACKUP_VERSION.toString())
            setProperty("createdTime", System.currentTimeMillis().toString())
            setProperty("bookCount", backup.bookCount.toString())
            setProperty("totalBytes", backup.totalBytes.toString())
            setProperty("integrityVersion", INTEGRITY_VERSION.toString())
            setProperty("databaseSha256", backup.snapshot.sha256())
            setProperty("assetCount", backup.assets.size.toString())
            backup.assets.forEachIndexed { index, asset ->
                setProperty("asset.$index.path", asset.entryName)
                setProperty("asset.$index.sha256", asset.file.sha256())
            }
            setProperty("portableSettings", backup.portableSettings.toString())
        }
        val output = context.contentResolver.openOutputStream(uriString.toUri(), "w") ?: error(context.getString(R.string.backup_create_failed))
        output.use { raw -> ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY)); properties.store(zip, "KixyuBook full backup") ; zip.closeEntry()
            zip.putFile(backup.snapshot, DATABASE_ENTRY)
            backup.assets.forEach { asset -> zip.putFile(asset.file, asset.entryName) }
        } }
        return BackupResult(backup.bookCount, backup.totalBytes)
    }

    override suspend fun restoreFrom(uriString: String): Result<BackupResult> = withContext(Dispatchers.IO) {
        operationMutex.withLock { runCatching {
            cleanupBackupWorkDirectories()
            val work = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
            val extracted = File(work, "payload").apply { mkdirs() }
            try {
                val totalBytes = extractArchive(uriString, extracted, work)
                val properties = loadManifest(extracted)
                requireSupportedFormat(properties)
                val snapshot = File(extracted, DATABASE_ENTRY)
                require(snapshot.isFile) { context.getString(R.string.backup_no_database) }
                verifyIntegrity(properties, extracted)
                validateAndRebase(snapshot, File(extracted, "files"))
                ensureRestoreInstallSpace(snapshot, File(extracted, "files"))
                val bookCount = countBooks(snapshot)
                installRestore(snapshot, File(extracted, "files"), properties)
                BackupResult(bookCount, totalBytes, requiresRestart = true)
            } finally {
                work.deleteRecursively()
            }
        } }
    }

    private fun extractArchive(uriString: String, extracted: File, work: File): Long {
        var totalBytes = 0L
        var entries = 0
        val names = hashSetOf<String>()
        val input = context.contentResolver.openInputStream(uriString.toUri()) ?: error(context.getString(R.string.backup_read_failed))
        input.use { raw -> ZipInputStream(BufferedInputStream(raw)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= MAX_ENTRIES) { context.getString(R.string.backup_too_many_entries) }
                require(names.add(entry.name)) { context.getString(R.string.backup_duplicate_entry, entry.name) }
                val target = File(extracted, entry.name).canonicalFile
                require(target.path.startsWith(extracted.canonicalPath + File.separator)) { context.getString(R.string.backup_invalid_path) }
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            totalBytes += read
                            require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { context.getString(R.string.backup_abnormal_size) }
                            if (totalBytes % RESTORE_SPACE_CHECK_INTERVAL_BYTES < read) {
                                require(allocatableBytes(work) >= RESTORE_WORKING_SPACE_RESERVE_BYTES) {
                                    context.getString(R.string.backup_extract_no_space)
                                }
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        } }
        return totalBytes
    }

    private fun loadManifest(extracted: File): Properties = Properties().apply {
        File(extracted, MANIFEST_ENTRY).takeIf(File::isFile)?.inputStream()?.use(::load)
            ?: error(context.getString(R.string.backup_invalid))
    }

    private fun requireSupportedFormat(properties: Properties): Int =
        properties.getProperty("formatVersion")?.toIntOrNull()?.also { version ->
            require(version in 5..BACKUP_VERSION) { context.getString(R.string.backup_version_unsupported) }
        } ?: error(context.getString(R.string.backup_version_missing))

    /** Legacy v5 backups remain restorable; newly exported v5 backups carry a complete hash list. */
    private fun verifyIntegrity(properties: Properties, extracted: File): Boolean {
        val integrityVersion = properties.getProperty("integrityVersion")?.toIntOrNull() ?: return false
        require(integrityVersion == INTEGRITY_VERSION) { context.getString(R.string.backup_integrity_unsupported) }
        val snapshot = File(extracted, DATABASE_ENTRY)
        require(snapshot.sha256() == properties.getProperty("databaseSha256")) { context.getString(R.string.backup_database_invalid) }
        val assetCount = properties.getProperty("assetCount")?.toIntOrNull()
            ?: error(context.getString(R.string.backup_manifest_missing))
        require(assetCount in 0..MAX_ENTRIES) { context.getString(R.string.backup_manifest_invalid) }
        repeat(assetCount) { index ->
            val path = properties.getProperty("asset.$index.path") ?: error(context.getString(R.string.backup_manifest_incomplete))
            val expected = properties.getProperty("asset.$index.sha256") ?: error(context.getString(R.string.backup_manifest_incomplete))
            val file = File(extracted, path).canonicalFile
            require(file.path.startsWith(extracted.canonicalPath + File.separator) && file.isFile) {
                context.getString(R.string.backup_asset_missing, path)
            }
            require(file.sha256() == expected) { context.getString(R.string.backup_asset_invalid, path) }
        }
        return true
    }

    private fun validateAndRebase(snapshot: File, assets: File) {
        SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { cursor ->
                require(
                    cursor.moveToFirst() && cursor.getInt(0) in SUPPORTED_BACKUP_DATABASE_VERSIONS,
                ) { context.getString(R.string.backup_database_version) }
            }
            db.rawQuery("SELECT uuid, format, coverPath FROM books", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(0)
                    val format = cursor.getString(1).lowercase()
                    val storedBook = File(assets, "books/$uuid.$format")
                    require(storedBook.isFile) { context.getString(R.string.backup_book_missing, uuid) }
                    val coverName = cursor.getString(2)?.let { File(it).name }
                    val cover = coverName?.let { File(context.filesDir, "covers/$it").absolutePath }
                    db.execSQL("UPDATE books SET storagePath = ?, coverPath = ? WHERE uuid = ?", arrayOf(storedBook.livePath("books"), cover, uuid))
                }
            }
            db.rawQuery("SELECT uuid, filePath FROM user_fonts", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(0)
                    val archived = File(assets, "fonts/${File(cursor.getString(1)).name}")
                    require(archived.isFile && archived.nameWithoutExtension == uuid) { context.getString(R.string.backup_font_missing, uuid) }
                    db.execSQL("UPDATE user_fonts SET filePath = ? WHERE uuid = ?", arrayOf(archived.livePath("fonts"), uuid))
                }
            }
        }
    }

    private fun ensureRestoreInstallSpace(snapshot: File, assets: File) {
        val installBytes = snapshot.length() + assets.walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
        val requiredBytes = installBytes + RESTORE_WORKING_SPACE_RESERVE_BYTES
        require(allocatableBytes(context.filesDir) >= requiredBytes) {
            val requiredMegabytes = (requiredBytes + BYTES_PER_MEBIBYTE - 1) / BYTES_PER_MEBIBYTE
            context.getString(R.string.backup_space_required, requiredMegabytes)
        }
    }

    private fun allocatableBytes(path: File): Long {
        val storageManager = context.getSystemService(StorageManager::class.java)
        return storageManager.getAllocatableBytes(storageManager.getUuidForPath(path))
    }

    private suspend fun installRestore(snapshot: File, assets: File, properties: Properties): Unit = LibraryStorageGate.mutex.withLock {
        val oldSettings = settingsRepository.settings.first()
        val oldGoal = settingsRepository.readingGoalMinutes.first()
        val oldLibrary = libraryPreferences.preferences.first()
        val oldReminder = readingReminders.readingReminder.first()
        val oldBookSettings = bookSettings.overrides.first()
        context.getSharedPreferences("recovery_ui", Context.MODE_PRIVATE).edit()
            .putString("style", oldSettings.appUiStyle.name).commit()
        val journal = backupRestoreJournal(context)
        try {
            journal.requireRecovered()
        } catch (failure: Exception) {
            throw BackupRecoveryException(context.getString(R.string.backup_restart_recovery), failure)
        }
        var databaseClosed = false
        var settingsTouched = false
        var committed = false
        try {
            journal.prepare(buildMap {
                put("database", snapshot)
                ASSET_DIRECTORIES.forEach { name -> File(assets, name).takeIf(File::exists)?.let { put(name, it) } }
            })
            currentCoroutineContext().ensureActive()
            // Once switching starts, coroutine cancellation must not strand an open process in
            // the middle. Actual process death is handled by the on-disk journal at startup.
            withContext(NonCancellable) {
                withoutRecordingSyncMutations {
                    settingsTouched = true
                    restoreSettings(properties)
                }
                databaseClosed = true
                database.close()
                journal.install()
                journal.commit()
                committed = true
            }
            runCatching { File(context.noBackupFilesDir, EPUB_CACHE_DIRECTORY).deleteRecursively() }
        } catch (failure: Exception) {
            // Returning from NonCancellable can deliver a pending cancellation after commit.
            // The restored database and settings must remain a pair in that case.
            if (committed) throw BackupRecoveryException(context.getString(R.string.backup_restart_recovery), failure)
            // Keep the journal if either rollback fails. Never discard the only remaining copy
            // of the old library; startup retries rollback before opening Room or DataStore.
            withContext(NonCancellable) {
                try {
                    journal.rollback(includePreferences = false)
                    if (settingsTouched) withoutRecordingSyncMutations {
                        settingsRepository.update { oldSettings }
                        settingsRepository.setReadingGoalMinutes(oldGoal)
                        libraryPreferences.replace(oldLibrary)
                        readingReminders.replace(oldReminder)
                        bookSettings.replaceAll(oldBookSettings)
                    }
                    journal.cleanup()
                } catch (rollbackFailure: Exception) {
                    failure.addSuppressed(rollbackFailure)
                    throw BackupRecoveryException(context.getString(R.string.backup_restart_recovery), failure)
                }
            }
            if (databaseClosed) throw BackupRecoveryException(context.getString(R.string.backup_restart_recovery), failure)
            if (failure is CancellationException) throw failure
            throw failure
        }
        Unit
    }

    private fun cleanupBackupWorkDirectories() {
        context.cacheDir.listFiles().orEmpty().forEach { file ->
            if (
                file.name.startsWith(BACKUP_WORK_PREFIX) ||
                file.name.startsWith(RESTORE_WORK_PREFIX) ||
                file.name.startsWith(INSPECT_WORK_PREFIX)
            ) {
                file.deleteRecursively()
            }
        }
        File(context.getDatabasePath(DATABASE_NAME).parentFile, "$DATABASE_NAME.restoring").delete()
    }

    private suspend fun restoreSettings(properties: Properties) {
        properties.getProperty("portableSettings")?.let { encoded ->
            val snapshot = JSONObject(encoded)
            require(snapshot.optInt("schema", 1) <= 4) { context.getString(R.string.backup_version_unsupported) }
            settingsRepository.update { jsonToSettings(snapshot.getJSONObject("reader")) }
            settingsRepository.setReadingGoalMinutes(snapshot.optInt("readingGoalMinutes", 30))
            snapshot.optJSONObject("library")?.let { libraryPreferences.replace(jsonToLibraryPreferences(it)) }
            snapshot.optJSONObject("readingReminder")?.let { readingReminders.replace(jsonToReadingReminder(it)) }
            bookSettings.replaceAll(decodeBookSettings(snapshot.optJSONObject("bookOverrides")))
            return
        }
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
            hideNavigationBar = properties.boolean("hideNavigationBar", current.hideNavigationBar),
            showPageNumber = properties.boolean("showPageNumber", current.showPageNumber),
            volumeKeyPageTurn = properties.boolean("volumeKeyPageTurn", current.volumeKeyPageTurn),
            keepScreenOn = properties.boolean("keepScreenOn", current.keepScreenOn),
            appColorTheme = properties.enum("appColorTheme", current.appColorTheme),
            appUiStyle = properties.enum("appUiStyle", current.appUiStyle),
            glassEffectEnabled = properties.boolean("glassEffectEnabled", current.glassEffectEnabled),
            glassFrostLevel = (
                properties.getProperty("glassFrostLevel")?.toFloatOrNull()
                    ?: properties.getProperty("glassBlurRadius")?.toFloatOrNull()
                        ?.let(::legacyGlassBlurRadiusToFrostLevel)
                    ?: current.glassFrostLevel
                ).coerceIn(MIN_GLASS_FROST_LEVEL, MAX_GLASS_FROST_LEVEL),
            predictiveBackEnabled = properties.boolean(
                "predictiveBackEnabled",
                current.predictiveBackEnabled,
            ),
            showChapterTitle = properties.boolean("showChapterTitle", current.showChapterTitle),
            showReadingTime = properties.boolean("showReadingTime", current.showReadingTime),
            showBatteryLevel = properties.boolean("showBatteryLevel", current.showBatteryLevel),
            brightnessMode = properties.enum("brightnessMode", current.brightnessMode),
            brightness = properties.float("brightness", current.brightness).coerceIn(.05f, 1f),
            pageTurnAnimation = properties.getProperty("pageTurnAnimation")
                ?.let(PageTurnAnimation::valueOf)
                ?: current.pageTurnAnimation,
        ) }
        settingsRepository.setReadingGoalMinutes(properties.getProperty("readingGoalMinutes")?.toIntOrNull() ?: 30)
    }

    private fun countBooks(snapshot: File): Int = SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM books", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * Builds the archive from live database references instead of copying whole directories.
     * Failed imports or older versions may leave orphaned files behind; those files are neither
     * required for a complete restore nor appropriate to silently retain in every future backup.
     */
    private fun collectReferencedAssets(snapshot: File): List<BackupAsset> {
        val assets = LinkedHashMap<String, BackupAsset>()
        SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT uuid, format, coverPath FROM books", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(0)
                    val format = cursor.getString(1).lowercase()
                    val book = File(context.filesDir, "books/$uuid.$format")
                    require(book.isFile) { context.getString(R.string.backup_original_book_missing, uuid) }
                    assets["files/books/$uuid.$format"] = BackupAsset(book, "files/books/$uuid.$format")

                    cursor.getString(2)?.let { coverPath ->
                        val cover = File(context.filesDir, "covers/${File(coverPath).name}")
                        if (cover.isFile) {
                            val entryName = "files/covers/${cover.name}"
                            assets[entryName] = BackupAsset(cover, entryName)
                        }
                    }
                }
            }
            db.rawQuery("SELECT uuid, filePath FROM user_fonts", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(0)
                    val font = File(context.filesDir, "fonts/${File(cursor.getString(1)).name}")
                    require(font.isFile && font.nameWithoutExtension == uuid) { context.getString(R.string.backup_original_font_missing, uuid) }
                    val entryName = "files/fonts/${font.name}"
                    assets[entryName] = BackupAsset(font, entryName)
                }
            }
        }
        return assets.values.toList()
    }

    private fun collectReferencedFonts(fontUuid: String?, overrides: Map<String, String>): Set<String> = buildSet {
        fontUuid?.takeIf { it.isNotBlank() }?.let(::add)
        overrides.values.forEach { patch ->
            val referenced = runCatching { JSONObject(patch).optString("fontUuid") }.getOrNull()
            if (!referenced.isNullOrBlank()) add(referenced)
        }
    }

    /**
     * Copies every font referenced by the portable settings into the archive and declares it in the
     * pinned database, so the archive never references a resource it does not contain. The snapshot
     * predates the settings read, so fonts selected after it must be added here. A font that no
     * longer exists means the settings view is stale: report it instead of silently dropping the
     * reference, letting the caller rebuild a consistent snapshot. Database failures must surface,
     * never be swallowed, or the archive would be inconsistent while still reporting success.
     */
    private suspend fun pinReferencedFonts(
        snapshot: File,
        work: File,
        assets: List<BackupAsset>,
        fontUuids: Set<String>,
    ): List<BackupAsset> {
        var pinned = assets
        val pinnedFontUuids = pinned.asSequence()
            .map { it.entryName }
            .filter { it.startsWith(FONT_ENTRY_PREFIX) }
            .mapTo(mutableSetOf()) { it.substringAfterLast('/').substringBeforeLast('.') }
        for (fontUuid in fontUuids) {
            if (fontUuid in pinnedFontUuids) continue
            val font = database.fontDao().getFont(fontUuid)?.takeIf { File(it.filePath).isFile }
                ?: throw BackupResourceUnavailable(context.getString(R.string.backup_original_font_missing, fontUuid))
            val source = File(font.filePath)
            val entryName = "$FONT_ENTRY_PREFIX${source.name}"
            val copy = File(work, entryName)
            copy.parentFile?.mkdirs()
            source.copyTo(copy)
            SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    "INSERT OR IGNORE INTO user_fonts (uuid, name, filePath, createdTime) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(font.uuid, font.name, font.filePath, font.createdTime),
                )
            }
            pinned = pinned + BackupAsset(copy, entryName)
            pinnedFontUuids += fontUuid
        }
        return pinned
    }

    private fun File.livePath(directory: String) = File(context.filesDir, "$directory/$name").absolutePath

    internal companion object {
        const val DATABASE_NAME = "kixyu-books.db"
        const val BACKUP_VERSION = 6
        const val MANIFEST_ENTRY = "manifest.properties"
        const val DATABASE_ENTRY = "database/kixyu-books.db"
        const val EPUB_CACHE_DIRECTORY = "epub-chapters"
        const val BACKUP_WORK_PREFIX = "backup-"
        const val RESTORE_WORK_PREFIX = "restore-"
        const val INSPECT_WORK_PREFIX = "inspect-"
        const val INTEGRITY_VERSION = 1
        const val MAX_ENTRIES = 100_000
        const val MAX_UNCOMPRESSED_BYTES = 16L * 1024 * 1024 * 1024
        const val BYTES_PER_MEBIBYTE = 1024L * 1024
        const val RESTORE_WORKING_SPACE_RESERVE_BYTES = 16L * BYTES_PER_MEBIBYTE
        const val RESTORE_SPACE_CHECK_INTERVAL_BYTES = 8L * BYTES_PER_MEBIBYTE
        const val FONT_ENTRY_PREFIX = "files/fonts/"
        const val BACKUP_SNAPSHOT_ATTEMPTS = 3
        val SUPPORTED_BACKUP_DATABASE_VERSIONS = 6..KIXYU_DATABASE_VERSION
        val ASSET_DIRECTORIES = listOf("books", "covers", "fonts")
    }
}

private data class BackupAsset(val file: File, val entryName: String)

private class PinnedBackup(
    val work: File,
    val snapshot: File,
    val assets: List<BackupAsset>,
    val bookCount: Int,
    val totalBytes: Long,
    val portableSettings: JSONObject,
)

/** A settings-referenced resource disappeared; the caller must rebuild a consistent snapshot. */
private class BackupResourceUnavailable(message: String) : Exception(message)

private fun ZipOutputStream.putFile(file: File, entryName: String) {
    putNextEntry(ZipEntry(entryName)); file.inputStream().buffered().use { it.copyTo(this) }; closeEntry()
}
private fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
private fun Properties.float(key: String, fallback: Float) = getProperty(key)?.toFloatOrNull() ?: fallback
private fun Properties.boolean(key: String, fallback: Boolean) = getProperty(key)?.toBooleanStrictOrNull() ?: fallback
private inline fun <reified T : Enum<T>> Properties.enum(key: String, fallback: T): T = getProperty(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
