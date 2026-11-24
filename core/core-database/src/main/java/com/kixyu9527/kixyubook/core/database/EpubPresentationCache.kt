package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.cache.DiskCacheBudget
import com.kixyu9527.kixyubook.core.common.cache.ReaderCacheBudget
import com.kixyu9527.kixyubook.core.common.cache.CacheDiagnostics
import com.kixyu9527.kixyubook.core.common.model.EpubBrowserPlan
import com.kixyu9527.kixyubook.core.common.model.EpubNavigationEntry
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest

internal data class EpubPresentation(val navigation: List<EpubNavigationEntry>, val browser: EpubBrowserPlan?)

/** Derived, bounded data, not user data. Reopening a book must not rescan every XHTML. */
internal class EpubPresentationCache(private val root: File) {
    private val budget by lazy { DiskCacheBudget(root, "bin", ReaderCacheBudget.EPUB_PRESENTATION_DISK_BYTES) }
    private val diagnostics = CacheDiagnostics.named("epub-presentation")

    @Synchronized fun load(bookUuid: String, hash: String, source: File,
        parse: () -> EpubPresentation,
    ): EpubPresentation {
        val owner = digest(bookUuid) + "."
        val file = File(root, owner + digest(hash) + ".bin")
        val signature = "${source.canonicalPath}:${source.length()}:${source.lastModified()}:$hash"
        if (file.isFile && file.length() <= MAX_BYTES) {
            val cached = runCatching {
                DataInputStream(file.inputStream().buffered()).use { input ->
                    require(input.readInt() == MAGIC && input.readInt() == VERSION)
                    require(input.readUTF() == signature)
                    val navigation = List(input.count()) {
                        EpubNavigationEntry(input.readInt(), input.readUTF(), input.readUTF(), input.readInt(),
                            List(input.count()) { input.readInt() })
                    }
                    val browser = if (!input.readBoolean()) null else {
                        val fixed = input.readBoolean(); val reason = input.readUTF()
                        EpubBrowserPlan(List(input.count()) { input.readUTF() }, fixed, reason)
                    }
                    EpubPresentation(navigation, browser)
                }
            }.getOrNull()
            if (cached != null) {
                diagnostics.hits.incrementAndGet()
                runCatching { budget.touch(file) }
                return cached
            }
        }
        diagnostics.misses.incrementAndGet()
        val parsed = parse() // Parser failure is real; cache failure must never prevent reading.
        diagnostics.parsed(signature)
        var temporary: File? = null
        runCatching {
            root.mkdirs()
            temporary = File.createTempFile(owner, ".tmp", root)
            DataOutputStream(requireNotNull(temporary).outputStream().buffered()).use { output ->
                output.writeInt(MAGIC); output.writeInt(VERSION); output.writeUTF(signature)
                output.writeInt(parsed.navigation.size)
                parsed.navigation.forEach {
                    output.writeInt(it.sourceIndex); output.writeUTF(it.title); output.writeUTF(it.target)
                    output.writeInt(it.depth); output.writeInt(it.titlePageSourceIndices.size)
                    it.titlePageSourceIndices.forEach(output::writeInt)
                }
                output.writeBoolean(parsed.browser != null)
                parsed.browser?.let {
                    output.writeBoolean(it.fixedLayout); output.writeUTF(it.reason)
                    output.writeInt(it.spinePaths.size); it.spinePaths.forEach(output::writeUTF)
                }
            }
            require(requireNotNull(temporary).length() <= MAX_BYTES)
            try {
                Files.move(requireNotNull(temporary).toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(requireNotNull(temporary).toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            budget.written(file)
        }
        temporary?.delete()
        return parsed
    }

    @Synchronized fun clearBook(uuid: String) {
        val owner = digest(uuid) + "."
        root.listFiles().orEmpty().filter { it.isFile && it.name.startsWith(owner) && it.extension in setOf("bin", "tmp") }
            .forEach(File::delete)
    }

    @Synchronized fun retainBooks(uuids: Set<String>) {
        val owners = uuids.mapTo(hashSetOf(), ::digest)
        val stale = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        root.listFiles().orEmpty().filter { it.isFile && it.name.substringBefore('.').matches(Regex("[a-f0-9]{64}")) &&
            it.extension in setOf("bin", "tmp") &&
            (it.name.substringBefore('.') !in owners || it.extension == "tmp" && it.lastModified() < stale) }
            .forEach(File::delete)
    }

    private fun DataInputStream.count() = readInt().also { require(it in 0..200_000) }
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAGIC = 0x4b584550
        // Bump when navigation-alias or browser-routing semantics change, not only the codec.
        const val VERSION = 1
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}
