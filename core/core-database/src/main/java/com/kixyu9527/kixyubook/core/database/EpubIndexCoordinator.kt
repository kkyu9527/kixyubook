package com.kixyu9527.kixyubook.core.database

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticFailure
import com.kixyu9527.kixyubook.core.common.diagnostics.toDiagnosticFailure
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.singleLineBookHeading
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapterOutline
import com.kixyu9527.kixyubook.core.reader.engine.EpubBookParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

internal class EpubIndexCoordinator(
    private val database: KixyuDatabase,
    private val dao: BookDao,
    private val parseCoordinator: EpubParseCoordinator,
    private val chapterCache: EpubChapterCache,
    private val chapterLoadMutex: Mutex,
    private val storageMutationMutex: Mutex,
    private val derivedDataVersions: SharedPreferences,
    private val scheduleIndex: () -> Unit,
) {
    // Background indexing owns a separate parser so a cooperatively cancelled XML parse can never
    // mutate the foreground reader's package or stylesheet caches.
    private val backgroundParser = EpubBookParser()

    /** EPUB outlines are derived data. Add newly recognized publisher volume pages in place. */
    suspend fun upgradeDirectoryDataIfNeeded() {
        if (derivedDataVersions.getInt(KEY_EPUB_DIRECTORY_VERSION, 0) >= EPUB_DIRECTORY_VERSION) return
        var inserted = 0
        var updated = 0
        var failures = 0
        var firstFailure: DiagnosticFailure? = null
        var firstFailedBook: String? = null
        storageMutationMutex.withLock {
            if (derivedDataVersions.getInt(KEY_EPUB_DIRECTORY_VERSION, 0) >= EPUB_DIRECTORY_VERSION) {
                return@withLock
            }
            val parser = EpubBookParser()
            dao.getAllBooks()
                .asSequence()
                .filter { it.format == BookFormat.EPUB.name && File(it.storagePath).isFile }
                .forEach { book ->
                    try {
                        val outlines = parser.readChapterOutlines(File(book.storagePath))
                        database.withTransaction {
                            val existingBySourceIndex = dao.getChapters(book.uuid).associateBy(ChapterEntity::chapterIndex)
                            outlines.forEach { outline ->
                                val existing = existingBySourceIndex[outline.sourceIndex]
                                if (existing == null) {
                                    dao.insertChapter(
                                        ChapterEntity(
                                            bookUuid = book.uuid,
                                            title = outline.title,
                                            chapterIndex = outline.sourceIndex,
                                            volumeTitle = outline.volumeTitle,
                                            volumeIndex = outline.volumeIndex,
                                            indexed = false,
                                            chapterKey = stableIndexChapterKey(book.uuid, outline.sourceIndex, outline.title),
                                        ),
                                    )
                                    inserted++
                                } else if (
                                    existing.title != outline.title ||
                                    existing.volumeTitle != outline.volumeTitle ||
                                    existing.volumeIndex != outline.volumeIndex
                                ) {
                                    dao.updateChapterOutline(
                                        existing.id,
                                        outline.title,
                                        outline.volumeTitle,
                                        outline.volumeIndex,
                                    )
                                    updated++
                                }
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failures++
                        if (firstFailure == null) {
                            firstFailure = error.toDiagnosticFailure()
                            firstFailedBook = book.uuid.shortIndexDiagnosticId()
                        }
                    }
                }
            derivedDataVersions.edit { putInt(KEY_EPUB_DIRECTORY_VERSION, EPUB_DIRECTORY_VERSION) }
        }
        if (inserted > 0) scheduleIndex()
        DiagnosticLog.record(
            Category.EPUB_PARSE,
            "directory_upgrade_finished",
            outcome = if (failures == 0) "success" else "partial",
            details = mapOf(
                "inserted" to inserted,
                "updated" to updated,
                "failures" to failures,
                "firstFailedBook" to firstFailedBook,
                "failureType" to firstFailure?.outcome,
                "firstFailureReason" to firstFailure?.reason,
            ),
        )
    }

    suspend fun registerDirectory(
        bookUuid: String,
        source: File,
        parser: EpubBookParser,
    ): List<DocumentChapterOutline> {
        val outlines = parser.readChapterOutlines(source)
        if (outlines.isEmpty()) return emptyList()
        database.withTransaction {
            dao.insertChapters(
                outlines.map { outline ->
                    ChapterEntity(
                        bookUuid = bookUuid,
                        title = outline.title,
                        chapterIndex = outline.sourceIndex,
                        volumeTitle = outline.volumeTitle,
                        volumeIndex = outline.volumeIndex,
                        indexed = false,
                        chapterKey = stableIndexChapterKey(bookUuid, outline.sourceIndex, outline.title),
                    )
                },
            )
        }
        return outlines
    }

    /** Durable WorkManager entry point. Every completed chapter is an independent checkpoint. */
    suspend fun continueIndex(bookUuid: String) = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val initialBook = dao.getBook(bookUuid)?.takeIf { it.format == BookFormat.EPUB.name }
            ?: return@withContext
        val requested = dao.getUnindexedChapters(bookUuid).size
        if (requested == 0) return@withContext
        var indexed = 0
        var preempted = 0
        var activeChapterIndex: Int? = null
        try {
            while (true) {
                val book = dao.getBook(bookUuid)?.takeIf { it.format == BookFormat.EPUB.name } ?: break
                val target = dao.getUnindexedChapters(bookUuid).firstOrNull() ?: break
                activeChapterIndex = target.chapterIndex
                var parsed: DocumentChapter? = null
                val completed = parseCoordinator.background {
                    parsed = backgroundParser.readChapter(
                        File(book.storagePath),
                        target.chapterIndex,
                        target.title,
                        purpose = "index",
                    )
                }
                if (!completed) {
                    preempted++
                    continue
                }
                val chapter = parsed
                val chapterStillAvailable = chapterLoadMutex.withLock {
                    // Parsing deliberately runs outside this lock so foreground reading can preempt it.
                    // Re-read before writing because the reader may already have indexed the chapter,
                    // or the user may have deleted/re-imported the book while parsing was in progress.
                    val current = dao.getChapter(bookUuid, target.chapterIndex)
                        ?: return@withLock false
                    if (!current.indexed) {
                        if (chapter == null) {
                            // Mark unreadable spine entries so one malformed publisher page cannot create
                            // an infinite retry loop. The directory remains available and later chapters proceed.
                            dao.markChapterIndexed(current.id, current.title)
                        } else {
                            dao.replaceChapterIndex(current.id, chapter.title, chapter.paragraphs)
                        }
                    }
                    true
                }
                if (!chapterStillAvailable) continue
                if (chapter != null) {
                    chapterCache.write(bookUuid, book.contentHash, target.chapterIndex, chapter)
                }
                indexed++
                activeChapterIndex = null
            }
            DiagnosticLog.record(
                Category.EPUB_PARSE,
                "background_index_finished",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = "success",
                details = mapOf(
                    "book" to initialBook.uuid.shortIndexDiagnosticId(),
                    "requested" to requested,
                    "indexed" to indexed,
                    "preempted" to preempted,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val failure = error.toDiagnosticFailure()
            DiagnosticLog.record(
                Category.EPUB_PARSE,
                "background_index_finished",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = failure.outcome,
                details = mapOf(
                    "book" to initialBook.uuid.shortIndexDiagnosticId(),
                    "chapter" to activeChapterIndex,
                    "requested" to requested,
                    "indexed" to indexed,
                    "preempted" to preempted,
                    "reason" to failure.reason,
                )
            )
            throw error
        }
    }

    suspend fun continueAll() = withContext(Dispatchers.IO) {
        while (true) {
            val nextBook = dao.getBooksPendingEpubIndex().firstOrNull() ?: return@withContext
            continueIndex(nextBook)
        }
    }}

private const val KEY_EPUB_DIRECTORY_VERSION = "epub_directory_version"
private const val EPUB_DIRECTORY_VERSION = 2

private fun String.shortIndexDiagnosticId(): String = take(8)

private fun stableIndexChapterKey(bookUuid: String, index: Int, title: String): String {
    val input = "$bookUuid|$index|${title.singleLineBookHeading().lowercase()}"
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        .take(16).joinToString("") { "%02x".format(it) }
}
