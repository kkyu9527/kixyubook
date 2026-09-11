package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.diagnostics.*
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SAF export owns output streams and partial-file cleanup, independently of reader paging. */
internal class BookExportService(
    private val context: Context,
    private val dao: BookDao,
    private val annotations: com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository,
    private val getChapter: suspend (String, Int) -> ChapterContent?,
) {
    suspend fun exportAnnotations(bookUuid: String, uriString: String, format: AnnotationExportFormat): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val book = dao.getBook(bookUuid) ?: error(context.getString(R.string.db_book_removed))
            val entries = annotations.getBookAnnotations(bookUuid)
            val titles = dao.getChapters(bookUuid).associate { it.chapterIndex to it.title }
            context.contentResolver.openOutputStream(uriString.toUri(), "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.writeAnnotationExport(book.title, book.author, titles, entries, format)
            } ?: error(context.getString(R.string.db_write_location_failed))
            Result.success(Unit)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
    suspend fun exportBook(bookUuid: String, uriString: String): Result<Unit> = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            val book = dao.getBook(bookUuid) ?: error(context.getString(R.string.db_book_removed))
            val destination = uriString.toUri()
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    dao.getChapters(bookUuid).forEachIndexed { index, chapter ->
                        (getChapter(bookUuid, chapter.chapterIndex) ?: error(context.getString(R.string.db_read_failed))).let { content ->
                            if (index > 0) writer.appendLine()
                            writer.appendLine(content.chapter.title)
                            content.paragraphs.asSequence()
                                .filter { it.kind == ParagraphKind.TEXT && it.text.isNotBlank() }
                                .forEach { paragraph ->
                                    writer.appendLine()
                                    writer.appendLine(paragraph.text)
                                }
                        }
                    }
                }
            } ?: error(context.getString(R.string.db_write_location_failed))
            Unit
        }.onSuccess {
            DiagnosticLog.record(
                Category.LIBRARY,
                "book_exported",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                details = mapOf("book" to bookUuid.shortDiagnosticId()),
            )
        }.onFailure { error ->
            val failure = error.toDiagnosticFailure()
            DiagnosticLog.record(
                Category.LIBRARY,
                "book_export_failed",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = failure.outcome,
                details = mapOf(
                    "book" to bookUuid.shortDiagnosticId(),
                    "reason" to failure.reason,
                ),
            )
        }
    }

    suspend fun exportBooks(
        bookUuids: Set<String>,
        directoryUriString: String,
    ): BookExportSummary = withContext(Dispatchers.IO) {
        val treeUri = directoryUriString.toUri()
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val books = dao.getBooks(bookUuids).associateBy { it.uuid }
        val failedTitles = mutableListOf<String>()
        var exportedCount = 0
        bookUuids.forEach { uuid ->
            val book = books[uuid]
            if (book == null) {
                failedTitles += uuid
                return@forEach
            }
            val destination = runCatching {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parent,
                    "text/plain",
                    correctedExportFileName(book.title, book.format),
                ) ?: error(context.getString(R.string.db_create_export_failed))
            }.getOrElse {
                failedTitles += book.title
                return@forEach
            }
            exportBook(uuid, destination.toString())
                .onSuccess { exportedCount++ }
                .onFailure {
                    failedTitles += book.title
                    runCatching {
                        DocumentsContract.deleteDocument(context.contentResolver, destination)
                    }
                }
        }
        BookExportSummary(
            exportedCount = exportedCount,
            failedTitles = failedTitles,
            directoryUri = directoryUriString,
        )
    }

}
