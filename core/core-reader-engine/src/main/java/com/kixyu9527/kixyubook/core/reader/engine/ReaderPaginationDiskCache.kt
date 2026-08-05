package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Regenerable second-level cache. It stores page boundaries only; paragraph text and rich spans
 * remain owned by the first-level Document cache and are reconstructed when a page is restored.
 */
internal class ReaderPaginationDiskCache(private val root: File) {
    fun read(key: PaginationCacheKey, chapter: ReaderChapter): List<ReaderPage>? {
        val file = cacheFile(key)
        if (!file.isFile || file.length() !in 1..MAX_CACHE_FILE_BYTES) return null
        return runCatching {
            val paragraphs = chapter.paragraphs.associateBy { it.index }
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == MAGIC)
                require(input.readInt() == VERSION)
                val pageCount = input.readSafeCount(MAX_PAGES)
                List(pageCount) { pageIndex ->
                    val opening = input.readBoolean()
                    val blockCount = input.readSafeCount(MAX_BLOCKS_PER_PAGE)
                    val blocks = List(blockCount) {
                        val paragraphIndex = input.readInt()
                        val textStart = input.readInt()
                        val visibleLength = input.readInt()
                        val continuation = input.readBoolean()
                        val bottomSpacing = input.readBoolean()
                        val kind = ParagraphKind.entries.getOrNull(input.readUnsignedByte())
                            ?: error("Invalid paragraph kind")
                        val imageWidthDp = Float.fromBits(input.readInt())
                        val imageHeightDp = Float.fromBits(input.readInt())
                        val paragraph = requireNotNull(paragraphs[paragraphIndex])
                        require(paragraph.kind == kind)
                        require(textStart >= 0 && visibleLength >= 0)
                        val textEnd = textStart + visibleLength
                        require(textEnd in textStart..paragraph.text.length)
                        DocumentBlock(
                            paragraphIndex = paragraph.index,
                            fullText = paragraph.text,
                            visibleText = paragraph.text.substring(textStart, textEnd),
                            continuation = continuation,
                            bottomSpacing = bottomSpacing,
                            kind = paragraph.kind,
                            resourcePath = paragraph.resourcePath,
                            mediaType = paragraph.mediaType,
                            intrinsicWidth = paragraph.intrinsicWidth,
                            intrinsicHeight = paragraph.intrinsicHeight,
                            imageWidthDp = imageWidthDp,
                            imageHeightDp = imageHeightDp,
                            spans = paragraph.spans.sliceForText(textStart, textEnd),
                            textStart = textStart,
                            isFullPageImage = paragraph.isFullPageImage,
                            cropImageToFill = paragraph.cropImageToFill,
                        )
                    }
                    ReaderPage(pageIndex, chapter.index, chapter.title, opening, blocks)
                }.also { require(it.isNotEmpty()) }
            }
        }.onSuccess {
            file.setLastModified(System.currentTimeMillis())
        }.getOrElse {
            file.delete()
            null
        }
    }

    fun write(key: PaginationCacheKey, pages: List<ReaderPage>) = synchronized(IO_LOCK) {
        if (pages.isEmpty()) return@synchronized
        val target = cacheFile(key)
        val temporary = File(target.parentFile, "${target.name}$TEMPORARY_SUFFIX")
        runCatching {
            target.parentFile?.mkdirs()
            // Cache maintenance runs on ReaderPaginationCoordinator's IO scope, never while the
            // navigation enter transition is composing its first frame.
            target.parentFile?.walkTopDown()?.filter { it.isFile && it.name.endsWith(TEMPORARY_SUFFIX) }
                ?.forEach(File::delete)
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(pages.size)
                pages.forEach { page ->
                    output.writeBoolean(page.isChapterOpening)
                    output.writeInt(page.blocks.size)
                    page.blocks.forEach { block ->
                        output.writeInt(block.paragraphIndex)
                        output.writeInt(block.textStart)
                        output.writeInt(block.visibleText.length)
                        output.writeBoolean(block.continuation)
                        output.writeBoolean(block.bottomSpacing)
                        output.writeByte(block.kind.ordinal)
                        output.writeInt(block.imageWidthDp.toBits())
                        output.writeInt(block.imageHeightDp.toBits())
                    }
                }
            }
            require(temporary.length() <= MAX_CACHE_FILE_BYTES)
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            writesSincePrune++
            if (writesSincePrune >= PRUNE_EVERY_WRITES) {
                writesSincePrune = 0
                pruneLocked()
            }
        }.onFailure { temporary.delete() }
    }

    private fun cacheFile(key: PaginationCacheKey): File {
        val bookDirectory = File(root, key.bookUuid.safePathSegment())
        val contentPrefix = key.contentHash.safePathSegment().take(20).ifBlank { "content" }
        return File(bookDirectory, "$contentPrefix-${key.chapterId}-${key.fingerprint()}.pbin")
    }

    private fun pruneLocked() {
        val files = root.walkTopDown().filter { it.isFile && it.extension == "pbin" }
            .sortedByDescending(File::lastModified).toList()
        var retainedBytes = 0L
        files.forEachIndexed { index, file ->
            retainedBytes += file.length()
            if (index >= MAX_CACHE_FILES || retainedBytes > MAX_TOTAL_CACHE_BYTES) file.delete()
        }
        root.walkBottomUp().filter { it != root && it.isDirectory && it.list().isNullOrEmpty() }
            .forEach(File::delete)
    }

    private fun PaginationCacheKey.fingerprint(): String {
        val signature = buildString {
            append(VERSION).append('|').append(chapterId).append('|').append(chapterTitle).append('|')
            append(spec.viewportWidthDp.toBits()).append('|').append(spec.viewportHeightDp.toBits()).append('|')
            append(spec.fontSizeSp.toBits()).append('|').append(spec.lineHeightMultiplier.toBits()).append('|')
            append(spec.letterSpacingEm.toBits()).append('|').append(spec.horizontalMarginDp.toBits()).append('|')
            append(fontIdentity.orEmpty()).append('|').append(showRegularChapterTitle).append('|')
            append(density.toBits()).append('|').append(fontScale.toBits()).append('|').append(layoutDirection.name)
        }
        return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    internal companion object {
        val IO_LOCK = Any()
        var writesSincePrune = 0
    }
}

/** Keeps deletion semantics symmetrical with the first-level EPUB cache. */
object ReaderPaginationCacheMaintenance {
    fun clearBook(noBackupFilesDir: File, bookUuid: String) {
        synchronized(ReaderPaginationDiskCache.IO_LOCK) {
            File(File(noBackupFilesDir, CACHE_ROOT_NAME), bookUuid.safePathSegment())
                .takeIf(File::exists)?.deleteRecursively()
        }
    }
}

internal fun readerPaginationCacheRoot(noBackupFilesDir: File): File =
    File(noBackupFilesDir, CACHE_ROOT_NAME)

private fun DataInputStream.readSafeCount(maximum: Int): Int = readInt().also { require(it in 0..maximum) }
private fun String.safePathSegment() = replace(Regex("[^a-zA-Z0-9._-]"), "_")

private const val CACHE_ROOT_NAME = "reader-pages"
private const val MAGIC = 0x4B585047
// Version 3 adds dedicated full-page image pagination and invalidates constrained illustration
// dimensions previously cached for image-only spine items.
private const val VERSION = 3
private const val MAX_PAGES = 100_000
private const val MAX_BLOCKS_PER_PAGE = 10_000
private const val MAX_CACHE_FILES = 1_500
private const val MAX_CACHE_FILE_BYTES = 8L * 1024L * 1024L
private const val MAX_TOTAL_CACHE_BYTES = 96L * 1024L * 1024L
private const val TEMPORARY_SUFFIX = ".tmp"
private const val PRUNE_EVERY_WRITES = 32
