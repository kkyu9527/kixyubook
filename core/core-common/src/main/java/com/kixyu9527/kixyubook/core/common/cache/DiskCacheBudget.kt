package com.kixyu9527.kixyubook.core.common.cache

import java.io.File

/** Index only regenerable files in the supplied private cache root, never original book assets. */
class DiskCacheBudget(
    private val root: File,
    private val extension: String,
    private val maximumBytes: Long,
    private val maximumEntries: Int = Int.MAX_VALUE,
) {
    private val entries = LinkedHashMap<File, Long>(16, .75f, true)
    private var bytes = 0L

    init {
        root.walkTopDown().filter { it.isFile && it.extension == extension }
            .sortedBy(File::lastModified).forEach { file ->
                entries[file] = file.length(); bytes += file.length()
            }
        prune()
    }

    @Synchronized fun touch(file: File) {
        entries[file] // update LRU order without walking the directory
        file.setLastModified(System.currentTimeMillis())
    }
    @Synchronized fun written(file: File) {
        require(file.parentFile != null && file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        bytes -= entries.remove(file) ?: 0
        entries[file] = file.length()
        bytes += file.length()
        prune()
    }
    private fun prune() {
        val iterator = entries.entries.iterator()
        while ((bytes > maximumBytes || entries.size > maximumEntries) && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.delete() || !entry.key.exists()) {
                bytes -= entry.value
                iterator.remove()
            }
        }
    }
}
