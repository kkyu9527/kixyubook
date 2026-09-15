package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.repository.MetadataRefreshResult
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.reader.engine.BookParser
import com.kixyu9527.kixyubook.core.reader.engine.LocalMetadata
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Recognition never downgrades a known author to the unknown-author placeholder. */
internal fun recognizedAuthor(parsed: String, current: String): String {
    val trimmed = parsed.trim().ifBlank { LocalMetadata.UNKNOWN_AUTHOR }
    return if (trimmed == LocalMetadata.UNKNOWN_AUTHOR && current.isNotBlank()) current else trimmed
}

/**
 * Re-reads author/title/description from the stored source and updates only the book row.
 *
 * The chapter table, bookmarks, annotations, search index and reading progress are deliberately
 * untouched: recognition must never rebuild the reading state. A field the user edited by hand is
 * preserved through the field-level `userEdited*` flags on the book row.
 *
 * The decision and the write happen inside one transaction that re-reads the book row. A manual
 * edit that lands while the (slow) parsing runs therefore wins instead of being overwritten with
 * the value captured before parsing. `book.originalPath` is a content/document URI, not a file
 * name, so the current title is the only safe fallback for the filename source.
 */
internal suspend fun refreshBookMetadata(
    context: Context,
    database: KixyuDatabase,
    dao: BookDao,
    parser: BookParser,
    syncMutations: SyncMutationRecorder,
    bookUuid: String,
    rules: List<LocalMetadata.FilenameRule> = emptyList(),
    specs: List<com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec> = emptyList(),
): MetadataRefreshResult {
    val entry = dao.getBook(bookUuid) ?: error(context.getString(R.string.db_book_missing))
    val source = File(entry.storagePath)
    require(source.isFile) { context.getString(R.string.db_read_failed) }
    val folderTitleFallback = entry.originalDisplayName.substringBeforeLast('.').ifBlank { entry.originalDisplayName }
    val parsed = parser.readMetadata(source, entry.title, entry.originalDisplayName, rules, specs)
        .withFolderFallback(entry.originalFolderName.takeIf(String::isNotBlank), folderTitleFallback)
    // Cover files and the row that points at them must agree: write through a temp file, commit
    // the path, then drop the old variants. The lock also keeps two refreshes of the same book
    // from deleting each other's freshly written cover.
    return coverMutationMutex.withLock {
        val coverName = parsed.coverBytes?.let { "$bookUuid.${parsed.coverExtension}" }
        val coverTemp = parsed.coverBytes?.let { bytes ->
            File(context.filesDir, "covers").apply { mkdirs() }
            File(context.filesDir, "covers/$coverName").tempSibling().also { it.writeBytes(bytes) }
        }
        val result = try {
            database.withTransaction {
                // Re-read inside the transaction: a user edit that committed during parsing is
                // visible here and must not be replaced by the pre-parse snapshot.
                val current = dao.getBook(bookUuid) ?: error(context.getString(R.string.db_book_missing))
                // Field ownership is persisted on the book; the pruned journal must never decide it.
                val preservedTitle = current.userEditedTitle
                val preservedAuthor = current.userEditedAuthor
                val preservedDescription = current.userEditedDescription
                val result = MetadataRefreshResult(
                    title = if (preservedTitle) current.title else parsed.title.trim().ifBlank { current.title },
                    author = if (preservedAuthor) {
                        current.author
                    } else {
                        recognizedAuthor(parsed.author, current.author)
                    },
                    description = if (preservedDescription) current.description else parsed.description.trim(),
                    preservedTitle = preservedTitle,
                    preservedAuthor = preservedAuthor,
                    preservedDescription = preservedDescription,
                )
                dao.updateBookMetadata(bookUuid, result.title, result.author, result.description)
                // TXT recognition has no sort/series data; the DAO keeps existing values when a
                // parsed field is absent instead of clearing what sync brought from another device.
                dao.updateBookSortMetadata(bookUuid, parsed.titleSort, parsed.seriesName, parsed.seriesIndex)
                if (coverName != null) {
                    dao.updateBookCover(bookUuid, File(context.filesDir, "covers/$coverName").absolutePath)
                }
                syncMutations.record(SyncEntityType.BOOK, bookUuid)
                result
            }
        } catch (error: Exception) {
            coverTemp?.delete()
            throw error
        }
        if (coverTemp != null) {
            val coverFile = File(context.filesDir, "covers/$coverName")
            if (!coverTemp.renameTo(coverFile)) {
                coverFile.writeBytes(coverTemp.readBytes())
                coverTemp.delete()
            }
            File(context.filesDir, "covers").listFiles()
                ?.filter { it.isFile && it.name.startsWith("$bookUuid.") && it.name != coverName }
                ?.forEach(File::delete)
        }
        result
    }
}

private val coverMutationMutex = Mutex()

private fun File.tempSibling(): File = File(parentFile, "$name.tmp")
