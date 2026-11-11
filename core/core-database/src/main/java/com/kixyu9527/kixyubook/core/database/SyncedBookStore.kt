package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.reader.engine.BookParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Shelf metadata and file installation have different lifetimes, but retain one stable book row. */
internal class SyncedBookStore(
    private val context: Context,
    private val database: KixyuDatabase,
    private val parserFor: (BookFormat) -> BookParser,
    private val afterInstall: (SyncedBook) -> Unit = {},
    private val prepareDirectory: suspend (SyncedBook, File, BookParser) -> Unit,
) {
    private val dao = database.bookDao()

    suspend fun register(book: SyncedBook): Boolean = withContext(Dispatchers.IO) {
        LibraryStorageGate.mutex.withLock {
            LibraryStorageGate.requireWritable()
            database.withTransaction {
                if (database.syncDao().pendingCount(SyncEntityType.BOOK.name, book.uuid) > 0) return@withTransaction false
                if (dao.bookExists(book.uuid)) return@withTransaction true
                if (dao.findUuidByHash(book.contentHash) != null) return@withTransaction false
                dao.insertBook(book.toCloudEntry())
                true
            }
        }
    }

    suspend fun install(book: SyncedBook, sourceFilePath: String, requireExisting: Boolean): Boolean = withContext(Dispatchers.IO) {
        LibraryStorageGate.mutex.withLock {
            LibraryStorageGate.requireWritable()
            val existing = dao.getBook(book.uuid)
            if (requireExisting && existing == null) return@withLock false
            if (existing != null && existing.storagePath.isNotBlank() && File(existing.storagePath).isFile &&
                dao.getChapterCount(book.uuid) > 0) return@withLock true
            if (existing == null && dao.findUuidByHash(book.contentHash) != null) return@withLock false
            require(existing == null || existing.contentHash == book.contentHash)
            val source = File(sourceFilePath)
            require(source.isFile) { context.getString(R.string.db_cloud_file_missing) }
            reportBookPreparation(BookPreparationStage.VERIFYING)
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    digest.update(buffer, 0, count)
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == book.contentHash) {
                context.getString(R.string.db_cloud_hash_failed)
            }
            val extension = if (book.format == BookFormat.EPUB) "epub" else "txt"
            val stored = File(context.filesDir, "books/${book.uuid}.$extension").also { it.parentFile?.mkdirs() }
            val usingExistingSource = source.canonicalFile == stored.canonicalFile
            val chapterCountBefore = dao.getChapterCount(book.uuid)
            var cover: File? = null
            var inserted = false
            try {
                if (!usingExistingSource) source.copyTo(stored, overwrite = true)
                val parser = parserFor(book.format)
                reportBookPreparation(BookPreparationStage.READING_METADATA)
                val metadata = parser.readMetadata(stored, book.title, sourceName = "")
                metadata.coverBytes?.let { bytes ->
                    cover = File(context.filesDir, "covers/${book.uuid}.${metadata.coverExtension}").also {
                        it.parentFile?.mkdirs(); it.writeBytes(bytes)
                    }
                }
                if (existing == null) {
                    dao.insertBook(book.toCloudEntry().copy(
                        titleSort = book.titleSort.ifBlank { metadata.titleSort },
                        seriesName = book.seriesName.ifBlank { metadata.seriesName },
                        seriesIndex = book.seriesIndex ?: metadata.seriesIndex,
                    ))
                    inserted = true
                }
                // No delete/reinsert: existing notes, pending bookmarks and sessions keep their FK.
                reportBookPreparation(BookPreparationStage.PREPARING_DIRECTORY)
                prepareDirectory(book, stored, parser)
                require(dao.getChapterCount(book.uuid) > 0) { context.getString(R.string.db_no_chapters) }
                // Publish the local path only after the directory exists. A process death before
                // this point leaves a cloud entry, never a falsely ready but permanently blank book.
                dao.installBookSource(book.uuid, stored.absolutePath, cover?.absolutePath)
                reportBookSource(book.format, stored.length(), true, dao.getChapterCount(book.uuid))
                runCatching { afterInstall(book) }.onFailure { error ->
                    com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.record(
                        com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category.LIBRARY,
                        "post_commit_cleanup_failed", outcome = "failure",
                        details = mapOf("book" to book.uuid.take(8), "error" to error.message))
                }
                true
            } catch (error: Throwable) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    database.withTransaction {
                        if (inserted) dao.deleteBook(book.uuid)
                        else if (existing != null) {
                            dao.installBookSource(book.uuid, existing.storagePath, existing.coverPath)
                            dao.updateBookCover(book.uuid, existing.coverPath)
                            if (chapterCountBefore == 0) dao.deleteChapters(book.uuid)
                        }
                    }
                    if (!usingExistingSource) stored.delete()
                    if (cover?.absolutePath != existing?.coverPath) cover?.delete()
                }
                throw error
            }
        }
    }
}

private fun SyncedBook.toCloudEntry() = BookEntity(
    uuid = uuid, title = title, author = author, description = description,
    coverPath = null, format = format.name, originalPath = "google-drive://$uuid", storagePath = "",
    createdTime = createdTime, contentHash = contentHash, category = category,
    originalDisplayName = originalDisplayName, titleSort = titleSort, seriesName = seriesName, seriesIndex = seriesIndex,
    userEditedTitle = metadataOwnership?.title ?: false,
    userEditedAuthor = metadataOwnership?.author ?: false,
    userEditedDescription = metadataOwnership?.description ?: false,
)
