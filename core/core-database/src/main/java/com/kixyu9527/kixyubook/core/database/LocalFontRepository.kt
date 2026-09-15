package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.graphics.Typeface
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.entity.UserFontEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import androidx.room.withTransaction
import javax.inject.Singleton

@Singleton
class LocalFontRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: FontDao,
    private val syncMutations: SyncMutationRecorder,
    private val database: KixyuDatabase,
    private val settingsRepository: ReaderSettingsRepository,
) : FontRepository {
    private val mutationMutex = LibraryStorageGate.mutex

    override fun observeFonts() = dao.observeFonts().map { list -> list.map { UserFont(it.uuid, it.name, it.filePath, it.createdTime) } }

    override suspend fun importFont(uriString: String): Result<UserFont> = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            pruneUnreferencedFonts()
            var target: File? = null
            runCatching {
                val uri = uriString.toUri()
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "自定义字体"
                require(name.endsWith(".ttf", true) || name.endsWith(".otf", true)) { context.getString(R.string.db_font_format) }
                val uuid = UUID.randomUUID().toString()
                val fontFile = File(context.filesDir, "fonts/$uuid.${name.substringAfterLast('.').lowercase()}")
                    .also { it.parentFile?.mkdirs() }
                target = fontFile
                context.contentResolver.openInputStream(uri)?.use { input -> fontFile.outputStream().use(input::copyTo) } ?: error(context.getString(R.string.db_font_read_failed))
                Typeface.createFromFile(fontFile)
                val model = UserFont(uuid, name.substringBeforeLast('.'), fontFile.absolutePath, System.currentTimeMillis())
                database.withTransaction {
                    dao.insert(UserFontEntity(model.uuid, model.name, model.filePath, model.createdTime))
                    syncMutations.record(SyncEntityType.FONT, model.uuid)
                }
                model
            }.onFailure { failure ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    target?.let { file ->
                        // A cancellation can arrive just after Room commits. Never remove a
                        // font still referenced by that committed row.
                        if (runCatching { dao.getFont(file.nameWithoutExtension) == null }.getOrDefault(false)) file.delete()
                    }
                }
                if (failure is kotlinx.coroutines.CancellationException) throw failure
            }
        }
    }

    override suspend fun deleteFont(fontUuid: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val file = deleteFontRow(fontUuid)
            clearFontReference(fontUuid)
            file?.delete()
            pruneUnreferencedFonts()
        }
    }

    override suspend fun deleteFontRemote(fontUuid: String): Unit = withContext(Dispatchers.IO) {
        // Runs inside the caller's tombstone transaction: only the row delete participates, so the
        // storage lock is never taken while that transaction is open (imports take it first).
        val file = deleteFontRow(fontUuid)
        deferFileCleanup {
            mutationMutex.withLock {
                clearFontReference(fontUuid)
                file?.delete()
                pruneUnreferencedFonts()
            }
        }
    }

    /**
     * Clears a global font reference. It runs after the row removal commits, so a rolled-back
     * deletion never leaves the configuration pointing at a missing font.
     */
    private suspend fun clearFontReference(fontUuid: String) {
        val current = settingsRepository.settings.first()
        if (current.fontUuid == fontUuid) {
            settingsRepository.update { it.copy(fontUuid = null) }
        }
    }

    private suspend fun deleteFontRow(fontUuid: String): File? {
        val file = dao.getFont(fontUuid)?.filePath?.let(::File)
        database.withTransaction {
            dao.delete(fontUuid)
            syncMutations.record(SyncEntityType.FONT, fontUuid, SyncMutationOperation.DELETE)
        }
        return file
    }

    override suspend fun storeSyncedFont(
        uuid: String,
        name: String,
        createdTime: Long,
        sourceFile: File,
    ): UserFont = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            dao.getFont(uuid)?.let { existing ->
                return@withLock UserFont(existing.uuid, existing.name, existing.filePath, existing.createdTime)
            }
            // A prune before the copy would otherwise consider every not-yet-inserted font file
            // garbage; the lock keeps both operations on the same critical section.
            pruneUnreferencedFonts()
            val fontFile = File(context.filesDir, "fonts/$uuid.ttf").also { it.parentFile?.mkdirs() }
            sourceFile.copyTo(fontFile, overwrite = true)
            val model = UserFont(uuid, name, fontFile.absolutePath, createdTime)
            database.withTransaction {
                dao.insert(UserFontEntity(model.uuid, model.name, model.filePath, model.createdTime))
            }
            model
        }
    }

    override suspend fun getFont(fontUuid: String) = withContext(Dispatchers.IO) {
        dao.getFont(fontUuid)?.let { UserFont(it.uuid, it.name, it.filePath, it.createdTime) }
    }

    private suspend fun pruneUnreferencedFonts() {
        val retained = dao.getAllFonts().mapTo(hashSetOf()) { File(it.filePath).absolutePath }
        val directory = File(context.filesDir, "fonts")
        directory.listFiles().orEmpty().forEach { entry ->
            if (!entry.isFile || entry.absolutePath !in retained) entry.deleteRecursively()
        }
        directory.delete()
    }
}
