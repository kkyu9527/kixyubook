package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.BookSearchResult
import com.kixyu9527.kixyubook.core.common.model.SearchMatch
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.util.TreeMap

/** Only locations stay resident for large searches; preview text spills into a disposable file. */
internal class SearchResultStore(private val directory: File? = null, private val spillAt: Int = 2_000) : Closeable {
    private data class Key(val chapter: Int, val paragraph: Int, val id: Long) : Comparable<Key> {
        override fun compareTo(other: Key) = compareValuesBy(this, other, Key::chapter, Key::paragraph, Key::id)
    }
    private data class Entry(var memory: BookSearchResult?, var offset: Long = -1)
    private val entries = TreeMap<Key, Entry>()
    private var file: File? = null
    private var disk: RandomAccessFile? = null
    private var closed = false
    val count: Int @Synchronized get() = entries.size

    @Synchronized fun add(values: List<BookSearchResult>) {
        if (closed) return
        values.forEach { value ->
            val key = Key(value.chapterIndex, value.paragraphIndex, value.chapterId)
            if (key !in entries) {
                val entry = Entry(value)
                disk?.let { write(entry, value) }
                entries[key] = entry
            }
        }
        if (disk == null && directory != null && entries.size > spillAt) {
            directory.mkdirs()
            synchronized(initializedDirectories) {
                if (initializedDirectories.add(directory.canonicalPath)) {
                    // The first spill in this process can remove files left by process death.
                    // Later stores must never remove another live reader's results.
                    directory.listFiles().orEmpty().filter { it.name.startsWith("results-") && it.extension == "tmp" }
                        .forEach { it.delete() }
                }
            }
            file = File.createTempFile("results-", ".tmp", directory)
            disk = RandomAccessFile(file!!, "rw")
            entries.values.forEach { entry -> write(entry, checkNotNull(entry.memory)) }
        }
    }

    data class Page(val rows: List<BookSearchResult>, val start: Int, val total: Int)
    @Synchronized fun indexOf(result: BookSearchResult): Int {
        val key = Key(result.chapterIndex, result.paragraphIndex, result.chapterId)
        return if (entries.containsKey(key)) entries.headMap(key).size else -1
    }
    @Synchronized fun page(requestedStart: Int): Page {
        if (closed) return Page(emptyList(), 0, 0)
        val paged = disk != null
        val start = if (paged) requestedStart.coerceIn(0, (entries.size - 1).coerceAtLeast(0)) / PAGE_SIZE * PAGE_SIZE else 0
        val rows = entries.entries.asSequence().drop(start).take(if (paged) PAGE_SIZE else Int.MAX_VALUE).map { (key, entry) ->
            entry.memory ?: checkNotNull(disk).let { source ->
                source.seek(entry.offset)
                val title = readText(source)
                val text = readText(source)
                val matchCount = source.readInt()
                require(matchCount in 0..text.length) { "Invalid match count" }
                val matches = List(matchCount) { SearchMatch(source.readInt(), source.readInt()) }
                BookSearchResult(key.id, title, key.chapter, key.paragraph, text, matches)
            }
        }.toList()
        return Page(rows, start, entries.size)
    }

    private fun write(entry: Entry, result: BookSearchResult) {
        val target = checkNotNull(disk)
        target.seek(target.length())
        entry.offset = target.filePointer
        listOf(result.chapterTitle, result.text).forEach { text ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            target.writeInt(bytes.size); target.write(bytes)
        }
        target.writeInt(result.matches.size)
        result.matches.forEach { match ->
            target.writeInt(match.start)
            target.writeInt(match.length)
        }
        entry.memory = null
    }

    private fun readText(source: RandomAccessFile): String {
        val size = source.readInt()
        require(size >= 0 && size <= source.length() - source.filePointer)
        return ByteArray(size).also(source::readFully).toString(Charsets.UTF_8)
    }

    @Synchronized override fun close() {
        closed = true
        disk?.close(); disk = null
        file?.delete(); file = null
        entries.clear()
    }

    companion object {
        const val PAGE_SIZE = 200
        private val initializedDirectories = mutableSetOf<String>()
    }
}
