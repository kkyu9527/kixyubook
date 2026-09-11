package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.common.repository.TextCorrectionRepository
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.reader.engine.BookParserRegistry
import com.kixyu9527.kixyubook.core.reader.engine.EpubBookParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.nio.file.Files

/** Stages derived paragraphs first. Original assets, chapter IDs and user records are never deleted. */
internal class BookRepairService(
    private val context: Context,
    private val database: KixyuDatabase,
    private val dao: BookDao,
    private val annotations: ReaderAnnotationRepository,
    private val corrections: TextCorrectionRepository,
    private val chapterLoadMutex: Mutex,
    private val clearCaches: (String) -> Unit,
) {
    suspend fun repair(uuid: String, mode: BookRepairMode, progress: suspend (BookRepairProgress) -> Unit): BookRepairOutcome {
        val book = dao.getBook(uuid) ?: error(context.getString(R.string.db_book_removed))
        if (mode == BookRepairMode.CACHE) {
            chapterLoadMutex.withLock { clearCaches(uuid) }
            return BookRepairOutcome(0)
        }
        val chapters = dao.getChapters(uuid)
        if (mode == BookRepairMode.SEARCH_INDEX) {
            database.withTransaction { dao.deleteBookParagraphFts(setOf(uuid)); dao.populateBookParagraphFts(uuid) }
            return BookRepairOutcome(chapters.size)
        }
        val source = File(book.storagePath)
        if (!source.isFile) throw java.io.FileNotFoundException()
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "book-repair-").toFile()
        try {
            var staged = 0
            var compatible = true
            suspend fun stage(index: Int, title: String, paragraphs: List<String>, compareTitle: Boolean) {
                val previous = chapters.getOrNull(index)
                if (previous == null || (compareTitle && previous.title != title)) compatible = false
                File(staging, "$index.json").writeText(JSONArray(paragraphs).toString())
                staged++
                progress(BookRepairProgress(staged, chapters.size))
            }
            if (book.format == BookFormat.EPUB.name) {
                val parser = EpubBookParser()
                chapters.forEachIndexed { index, chapter ->
                    val parsed = parser.readChapter(source, chapter.chapterIndex, chapter.title, "repair")
                        ?: error(context.getString(R.string.db_read_failed))
                    stage(index, parsed.title, parsed.paragraphs, false)
                }
            } else {
                BookParserRegistry().parserFor(BookFormat.TXT).readChapters(source) { chapter ->
                    stage(staged, chapter.title, chapter.paragraphs, true)
                }
            }
            if (!compatible || staged != chapters.size) return BookRepairOutcome(staged, originalPreserved = true)
            fun read(index: Int): List<String> = JSONArray(File(staging, "$index.json").readText()).let { array ->
                List(array.length()) { array.getString(it) }
            }
            return chapterLoadMutex.withLock {
                database.withTransaction {
                    // Re-read anchors in the transaction: edits may have arrived during source parsing.
                    val marks = dao.getBookmarks(uuid)
                    val location = dao.getProgress(uuid)
                    val notes = annotations.getBookAnnotations(uuid).groupBy { it.chapterIndex }
                    val edits = corrections.getBookCorrections(uuid).groupBy { it.chapterIndex }
                    for ((index, chapter) in chapters.withIndex()) {
                        val rows = read(index)
                        val protectedPositions = marks.filter { it.chapterId == chapter.id }.map { it.position } +
                            listOfNotNull(location?.takeIf { it.chapterId == chapter.id }?.paragraphIndex)
                        for (position in protectedPositions) {
                            val previous = dao.getParagraph(chapter.id, position)?.text
                            if (previous != null && rows.getOrNull(position) != previous) compatible = false
                        }
                        if (notes[chapter.chapterIndex].orEmpty().any { !repairAnchorMatches(rows, it.paragraphIndex, it.startOffset, it.endOffset, it.exactText) }) compatible = false
                        if (edits[chapter.chapterIndex].orEmpty().any { !repairAnchorMatches(rows, it.paragraphIndex, it.startOffset, it.endOffset, it.exactText) }) compatible = false
                    }
                    if (!compatible) return@withTransaction BookRepairOutcome(staged, originalPreserved = true)
                    chapters.forEachIndexed { index, chapter -> dao.replaceChapterIndex(chapter.id, chapter.title, read(index)) }
                    BookRepairOutcome(staged)
                }.also { if (!it.originalPreserved) clearCaches(uuid) }
            }
        } finally { staging.deleteRecursively() }
    }
}
