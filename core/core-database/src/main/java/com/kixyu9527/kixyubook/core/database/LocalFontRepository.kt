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
import com.kixyu9527.kixyubook.core.database.dao.RepairDao
import com.kixyu9527.kixyubook.core.database.entity.PendingRepairEntity
import com.kixyu9527.kixyubook.core.database.entity.UserFontEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    private val repairDao: RepairDao,
    private val settingsRepository: ReaderSettingsRepository,
) : FontRepository {
    private val mutationMutex = LibraryStorageGate.mutex

    override fun observeFonts(): kotlinx.coroutines.flow.Flow<List<UserFont>> = flow {
        // Self-heal backstop: a required repair that failed earlier is retried whenever fonts are
        // observed (reader and settings both collect this), so a dangling reference cannot survive.
        try {
            processPendingRepairs()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.record(
                com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category.LIBRARY,
                "font_reference_repair_retried",
                outcome = "failure",
                details = mapOf("error" to (error.message ?: error::class.java.simpleName)),
            )
        }
        emitAll(dao.observeFonts().map { list -> list.map { UserFont(it.uuid, it.name, it.filePath, it.createdTime) } })
    }

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
            val file = fontFile(fontUuid)
            deleteFontTransaction(fontUuid)
            // The reference repair follows the committed delete: a rollback leaves the settings
            // untouched, and a failed repair stays queued for a retry.
            processPendingRepairs()
            file?.delete()
            pruneUnreferencedFonts()
        }
    }

    override suspend fun deleteFontRemote(fontUuid: String): Unit = withContext(Dispatchers.IO) {
        // Runs inside the caller's tombstone transaction: only the row delete and the repair
        // intent participate, so the storage lock is never taken while that transaction is open.
        val file = fontFile(fontUuid)
        deleteFontTransaction(fontUuid)
        deferRequiredRepair { processPendingRepairs() }
        deferFileCleanup {
            mutationMutex.withLock {
                file?.delete()
                pruneUnreferencedFonts()
            }
        }
    }

    override suspend fun repairPendingReferences() = withContext(Dispatchers.IO) {
        mutationMutex.withLock { processPendingRepairs() }
    }

    /** Retries every queued cross-store repair; a failure keeps its row for the next attempt. */
    private suspend fun processPendingRepairs() {
        repairDao.getAll().forEach { repair ->
            when (repair.kind) {
                REPAIR_FONT_REFERENCE -> {
                    settingsRepository.update { current ->
                        if (current.fontUuid == repair.target) {
                            current.copy(fontUuid = null)
                        } else {
                            current
                        }
                    }
                    repairDao.delete(repair.id)
                }
                else -> repairDao.delete(repair.id)
            }
        }
    }

    private suspend fun fontFile(fontUuid: String): File? =
        dao.getFont(fontUuid)?.filePath?.let(::File)

    private suspend fun deleteFontTransaction(fontUuid: String) {
        database.withTransaction {
            dao.delete(fontUuid)
            // The repair intent lives in the same transaction as the delete: a rollback keeps both.
            repairDao.insertIgnoring(
                PendingRepairEntity(
                    kind = REPAIR_FONT_REFERENCE,
                    target = fontUuid,
                    createdTime = System.currentTimeMillis(),
                ),
            )
            syncMutations.record(SyncEntityType.FONT, fontUuid, SyncMutationOperation.DELETE)
        }
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
    private companion object {
        const val REPAIR_FONT_REFERENCE = "font_reference"
    }
}
