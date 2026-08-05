package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import com.kixyu9527.kixyubook.core.reader.engine.DocumentImage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Regenerable binary cache for the EPUB data that Room's searchable text rows intentionally omit. */
internal class EpubChapterCache(private val root: File) {
    init {
        // A process death can interrupt the atomic replacement below. Temporary files are never
        // readable cache entries, so they are safe to remove when the cache is opened again.
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(TEMPORARY_SUFFIX) }
            .forEach(File::delete)
    }

    fun read(bookUuid: String, contentHash: String, chapterIndex: Int): DocumentChapter? {
        val file = cacheFile(bookUuid, contentHash, chapterIndex)
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == MAGIC)
                require(input.readInt() == VERSION)
                val title = input.readSizedString()
                val paragraphs = List(input.readSafeCount(MAX_PARAGRAPHS)) { input.readSizedString() }
                val images = List(input.readSafeCount(MAX_IMAGES)) {
                    DocumentImage(
                        contentIndex = input.readInt(),
                        resourcePath = input.readSizedString(),
                        mediaType = input.readSizedString(),
                        altText = input.readSizedString(),
                        intrinsicWidth = input.readInt(),
                        intrinsicHeight = input.readInt(),
                        isFullPage = input.readBoolean(),
                        cropToFill = input.readBoolean(),
                    )
                }
                val paragraphSpans = List(input.readSafeCount(MAX_PARAGRAPHS)) {
                    List(input.readSafeCount(MAX_SPANS_PER_PARAGRAPH)) {
                        val start = input.readInt()
                        val end = input.readInt()
                        val styleMask = input.readInt()
                        ReaderTextSpan(
                            start = start,
                            end = end,
                            styles = ReaderInlineStyle.entries.filterIndexed { index, _ ->
                                styleMask and (1 shl index) != 0
                            }.toSet(),
                            foreground = input.readEnumOrNull<ReaderSemanticColor>(),
                            background = input.readEnumOrNull<ReaderSemanticColor>(),
                        )
                    }
                }
                DocumentChapter(title, paragraphs, images, paragraphSpans)
            }
        }.getOrElse {
            file.delete()
            null
        }
    }

    fun contains(bookUuid: String, contentHash: String, chapterIndex: Int): Boolean =
        cacheFile(bookUuid, contentHash, chapterIndex).isFile

    fun write(bookUuid: String, contentHash: String, chapterIndex: Int, chapter: DocumentChapter) {
        val target = cacheFile(bookUuid, contentHash, chapterIndex)
        val temporary = File(target.parentFile, "${target.name}$TEMPORARY_SUFFIX")
        runCatching {
            target.parentFile?.mkdirs()
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeSizedString(chapter.title)
                output.writeInt(chapter.paragraphs.size)
                chapter.paragraphs.forEach(output::writeSizedString)
                output.writeInt(chapter.images.size)
                chapter.images.forEach { image ->
                    output.writeInt(image.contentIndex)
                    output.writeSizedString(image.resourcePath)
                    output.writeSizedString(image.mediaType)
                    output.writeSizedString(image.altText)
                    output.writeInt(image.intrinsicWidth)
                    output.writeInt(image.intrinsicHeight)
                    output.writeBoolean(image.isFullPage)
                    output.writeBoolean(image.cropToFill)
                }
                output.writeInt(chapter.paragraphSpans.size)
                chapter.paragraphSpans.forEach { spans ->
                    output.writeInt(spans.size)
                    spans.forEach { span ->
                        output.writeInt(span.start)
                        output.writeInt(span.end)
                        output.writeInt(span.styles.fold(0) { mask, style -> mask or (1 shl style.ordinal) })
                        output.writeInt(span.foreground?.ordinal ?: NO_ENUM)
                        output.writeInt(span.background?.ordinal ?: NO_ENUM)
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }.onFailure { temporary.delete() }
    }

    fun clearBook(bookUuid: String) {
        File(root, bookUuid.safePathSegment()).takeIf(File::exists)?.deleteRecursively()
    }

    fun retainBooks(bookUuids: Set<String>) {
        val retainedDirectories = bookUuids.mapTo(hashSetOf(), String::safePathSegment)
        root.listFiles().orEmpty().forEach { entry ->
            if (entry.name !in retainedDirectories) entry.deleteRecursively()
        }
    }

    private fun cacheFile(bookUuid: String, contentHash: String, chapterIndex: Int) = File(
        File(root, bookUuid.safePathSegment()),
        "${contentHash.safePathSegment().take(20)}-$chapterIndex.bin",
    )
}

private fun DataInputStream.readSafeCount(maximum: Int): Int = readInt().also { require(it in 0..maximum) }

private fun DataInputStream.readSizedString(): String {
    val byteCount = readSafeCount(MAX_STRING_BYTES)
    return ByteArray(byteCount).also(::readFully).toString(Charsets.UTF_8)
}

private fun DataOutputStream.writeSizedString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= MAX_STRING_BYTES)
    writeInt(bytes.size)
    write(bytes)
}

private inline fun <reified T : Enum<T>> DataInputStream.readEnumOrNull(): T? {
    val ordinal = readInt()
    if (ordinal == NO_ENUM) return null
    return enumValues<T>().getOrNull(ordinal) ?: error("Invalid enum ordinal")
}

private fun String.safePathSegment() = replace(Regex("[^a-zA-Z0-9._-]"), "_")

private const val MAGIC = 0x4B584543
// Version 6 also persists whether publisher CSS requests a cover-style crop.
private const val VERSION = 6
private const val NO_ENUM = -1
private const val MAX_PARAGRAPHS = 100_000
private const val MAX_IMAGES = 10_000
private const val MAX_SPANS_PER_PARAGRAPH = 100_000
private const val MAX_STRING_BYTES = 16 * 1024 * 1024
private const val TEMPORARY_SUFFIX = ".tmp"
