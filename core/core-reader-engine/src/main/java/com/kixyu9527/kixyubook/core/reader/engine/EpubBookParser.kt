package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.BookFormat
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubBookParser : BookParser {
    override val format = BookFormat.EPUB

    override fun readMetadata(file: File, fallbackTitle: String): DocumentMetadata = ZipFile(file).use { zip ->
        val pkg = readPackage(zip)
        val coverItem = pkg.manifest.values.firstOrNull { "cover-image" in it.properties }
            ?: pkg.coverId?.let(pkg.manifest::get)
        val cover = coverItem?.let { item ->
            zip.findEntry(item.path)?.takeIf { it.size in 1..MAX_COVER_BYTES.toLong() }
                ?.let { zip.getInputStream(it).use { input -> input.readBytes() } }
        }
        DocumentMetadata(
            identityHint = pkg.identifier.takeIf { it.startsWith("urn:uuid:", true) }?.substringAfterLast(':'),
            title = pkg.title.ifBlank { fallbackTitle.substringBeforeLast('.') },
            author = pkg.author.ifBlank { "未知作者" },
            description = pkg.description,
            coverBytes = cover,
            coverExtension = coverItem?.mediaType?.substringAfter('/')?.substringBefore('+') ?: "jpg",
        )
    }

    override suspend fun readChapters(file: File, emit: suspend (DocumentChapter) -> Unit) {
        ZipFile(file).use { zip ->
            val pkg = readPackage(zip)
            pkg.spine.indices.forEach { index ->
                readSpineChapter(zip, pkg, index)?.let { emit(it) }
            }
        }
    }

    /** Reads one spine item so large EPUBs remain chapter-lazy in the reader. */
    fun readChapter(
        file: File,
        chapterIndex: Int,
        expectedTitle: String? = null,
    ): DocumentChapter? = ZipFile(file).use { zip ->
        val pkg = readPackage(zip)
        val nearby = buildList {
            add(chapterIndex)
            for (distance in 1..MAX_SPINE_LOOKAROUND) {
                add(chapterIndex + distance)
                add(chapterIndex - distance)
            }
        }.filter { it in pkg.spine.indices }.distinct()
        var fallback: DocumentChapter? = null
        for (spineIndex in nearby) {
            val chapter = readSpineChapter(zip, pkg, spineIndex) ?: continue
            if (fallback == null) fallback = chapter
            if (expectedTitle.isNullOrBlank() ||
                chapter.title.normalizedHeading() == expectedTitle.normalizedHeading()
            ) {
                return@use chapter
            }
        }
        fallback
    }

    private fun readSpineChapter(zip: ZipFile, pkg: PackageDocument, index: Int): DocumentChapter? {
        val id = pkg.spine.getOrNull(index) ?: return null
        val item = pkg.manifest[id] ?: return null
        val entry = zip.findEntry(item.path) ?: return null
        val content = zip.getInputStream(entry).use { input ->
            readXhtml(input, zip, item.path, pkg.manifest.values)
        }
        if (content.blocks.isEmpty()) return null
        val heading = content.heading?.takeIf { it.length in 2..80 }
        val fallback = item.path.substringAfterLast('/').substringBeforeLast('.')
        val title = heading ?: fallback.ifBlank { "第 ${index + 1} 章" }
        var removedHeading = false
        val body = content.blocks.filterNot { block ->
            val duplicate = !removedHeading && heading != null && block is XhtmlBlock.Text &&
                block.value.normalizedHeading() == title.normalizedHeading()
            if (duplicate) removedHeading = true
            duplicate
        }
        val paragraphs = body.mapNotNull { (it as? XhtmlBlock.Text)?.value }
        val images = body.mapIndexedNotNull { contentIndex, block ->
            (block as? XhtmlBlock.Image)?.image?.copy(contentIndex = contentIndex)
        }
        return DocumentChapter(title, paragraphs, images)
    }

    private fun readPackage(zip: ZipFile): PackageDocument {
        val container = parseXml(zip, "META-INF/container.xml")
        val root = container.getElementsByTagNameNS("*", "rootfile").item(0) as? Element
            ?: error("EPUB 缺少 container rootfile")
        val opfPath = root.getAttribute("full-path")
        val document = parseXml(zip, opfPath)
        val metadata = document.getElementsByTagNameNS("*", "metadata").item(0) as? Element
        val manifest = linkedMapOf<String, ManifestItem>()
        val items = document.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) (items.item(i) as? Element)?.let { item ->
            manifest[item.getAttribute("id")] = ManifestItem(
                resolveArchivePath(opfPath, item.getAttribute("href")),
                item.getAttribute("media-type"),
                item.getAttribute("properties").split(' ').filter(String::isNotBlank).toSet(),
            )
        }
        val spine = buildList {
            val refs = document.getElementsByTagNameNS("*", "itemref")
            for (i in 0 until refs.length) {
                (refs.item(i) as? Element)?.getAttribute("idref")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val coverId = metadata?.let { element ->
            val nodes = element.getElementsByTagNameNS("*", "meta")
            (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                .firstOrNull { it.getAttribute("name").equals("cover", true) }?.getAttribute("content")
        }
        return PackageDocument(
            metadata?.firstText("identifier").orEmpty(),
            metadata?.firstText("title").orEmpty(),
            metadata?.firstText("creator").orEmpty(),
            metadata?.firstText("description").orEmpty(),
            coverId,
            manifest,
            spine,
        )
    }

    private fun parseXml(zip: ZipFile, path: String) = newDocumentBuilder().parse(
        zip.getInputStream(zip.findEntry(path) ?: error("EPUB 缺少 $path")),
    )

    private fun newDocumentBuilder() = DocumentBuilderFactory.newInstance().run {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        newDocumentBuilder()
    }

    private fun Element.firstText(name: String) =
        getElementsByTagNameNS("*", name).item(0)?.textContent?.trim().orEmpty()

    private fun readXhtml(
        input: java.io.InputStream,
        zip: ZipFile,
        xhtmlPath: String,
        manifest: Collection<ManifestItem>,
    ): XhtmlContent {
        val document = newDocumentBuilder().parse(input)
        val nodes = document.getElementsByTagNameNS("*", "*")
        var heading: String? = null
        val blocks = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val tag = element.localName.orEmpty().lowercase()
                if (tag in CONTENT_TAGS && !element.hasContentAncestor()) {
                    val text = element.textContent.replace(Regex("\\s+"), " ").trim()
                        .takeIf(String::isNotBlank) ?: continue
                    if (heading == null && tag in HEADING_TAGS) heading = text
                    add(XhtmlBlock.Text(text))
                    continue
                }
                val reference = element.imageReference(tag) ?: continue
                val resourcePath = resolveArchivePath(xhtmlPath, reference)
                val entry = zip.findEntry(resourcePath) ?: continue
                val mediaType = manifest.firstOrNull { it.path.equals(resourcePath, true) }?.mediaType
                    ?.takeIf { it.startsWith("image/", true) }
                    ?: mediaTypeFor(resourcePath)
                if (!mediaType.startsWith("image/")) continue
                val dimensions = zip.readImageDimensions(entry, mediaType)
                add(
                    XhtmlBlock.Image(
                        DocumentImage(
                            contentIndex = 0,
                            resourcePath = entry.name,
                            mediaType = mediaType,
                            altText = element.getAttribute("alt").ifBlank { element.getAttribute("title") },
                            intrinsicWidth = dimensions.first,
                            intrinsicHeight = dimensions.second,
                        ),
                    ),
                )
            }
        }
        return XhtmlContent(heading, blocks)
    }

    private fun Element.imageReference(tag: String): String? {
        val value = when (tag) {
            "img" -> getAttribute("src").ifBlank {
                getAttribute("srcset").substringBefore(',').trim().substringBefore(' ')
            }
            "image" -> getAttribute("href").ifBlank {
                getAttributeNS("http://www.w3.org/1999/xlink", "href")
            }
            "object" -> getAttribute("data")
            else -> ""
        }
        return value.trim().takeIf { it.isNotBlank() && !it.startsWith("data:", true) }
    }

    private fun Element.hasContentAncestor(): Boolean {
        var ancestor: Node? = parentNode
        while (ancestor is Element) {
            if (ancestor.localName.orEmpty().lowercase() in CONTENT_TAGS) return true
            ancestor = ancestor.parentNode
        }
        return false
    }

    private data class PackageDocument(
        val identifier: String,
        val title: String,
        val author: String,
        val description: String,
        val coverId: String?,
        val manifest: Map<String, ManifestItem>,
        val spine: List<String>,
    )

    private data class ManifestItem(
        val path: String,
        val mediaType: String,
        val properties: Set<String>,
    )

    private sealed interface XhtmlBlock {
        data class Text(val value: String) : XhtmlBlock
        data class Image(val image: DocumentImage) : XhtmlBlock
    }

    private data class XhtmlContent(val heading: String?, val blocks: List<XhtmlBlock>)

    private companion object {
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        const val MAX_SPINE_LOOKAROUND = 12
        val CONTENT_TAGS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote")
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    }
}

private const val MAX_IMAGE_HEADER_BYTES = 512 * 1024
private val JPEG_START_OF_FRAME = setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)

private fun resolveArchivePath(baseFile: String, rawReference: String): String {
    val clean = rawReference.substringBefore('#').substringBefore('?').replace('\\', '/')
    val combined = if (clean.startsWith('/')) clean else {
        val directory = baseFile.substringBeforeLast('/', "")
        if (directory.isBlank()) clean else "$directory/$clean"
    }
    val segments = ArrayDeque<String>()
    combined.split('/').forEach { rawSegment ->
        val segment = runCatching {
            java.net.URLDecoder.decode(rawSegment.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(rawSegment)
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString("/")
}

private fun ZipFile.findEntry(path: String): ZipEntry? = getEntry(path) ?: entries().asSequence()
    .firstOrNull { it.name.equals(path, ignoreCase = true) }

private fun mediaTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg", "svgz" -> "image/svg+xml"
    else -> "application/octet-stream"
}

private fun ZipFile.readImageDimensions(entry: ZipEntry, mediaType: String): Pair<Int, Int> {
    val bytes = getInputStream(entry).use { input ->
        val limit = entry.size.takeIf { it > 0 }?.coerceAtMost(MAX_IMAGE_HEADER_BYTES.toLong())
            ?.toInt() ?: MAX_IMAGE_HEADER_BYTES
        val output = java.io.ByteArrayOutputStream(limit.coerceAtMost(64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        output.toByteArray()
    }
    return when {
        mediaType.equals("image/png", true) && bytes.size >= 24 ->
            bytes.bigEndianInt(16) to bytes.bigEndianInt(20)
        mediaType.equals("image/gif", true) && bytes.size >= 10 ->
            bytes.littleEndianShort(6) to bytes.littleEndianShort(8)
        mediaType.equals("image/jpeg", true) -> bytes.jpegDimensions()
        mediaType.equals("image/webp", true) -> bytes.webpDimensions()
        mediaType.contains("svg", true) -> bytes.svgDimensions()
        else -> 0 to 0
    }.let { (width, height) -> width.coerceAtLeast(0) to height.coerceAtLeast(0) }
}

private fun ByteArray.bigEndianInt(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 24) or ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or (this[offset + 3].toInt() and 0xFF)

private fun ByteArray.littleEndianShort(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.jpegDimensions(): Pair<Int, Int> {
    if (size < 4 || this[0] != 0xFF.toByte() || this[1] != 0xD8.toByte()) return 0 to 0
    var offset = 2
    while (offset + 8 < size) {
        if (this[offset] != 0xFF.toByte()) { offset++; continue }
        val marker = this[offset + 1].toInt() and 0xFF
        if (marker in JPEG_START_OF_FRAME) {
            val height = ((this[offset + 5].toInt() and 0xFF) shl 8) or (this[offset + 6].toInt() and 0xFF)
            val width = ((this[offset + 7].toInt() and 0xFF) shl 8) or (this[offset + 8].toInt() and 0xFF)
            return width to height
        }
        if (offset + 3 >= size) break
        val length = ((this[offset + 2].toInt() and 0xFF) shl 8) or (this[offset + 3].toInt() and 0xFF)
        if (length < 2) break
        offset += length + 2
    }
    return 0 to 0
}

private fun ByteArray.webpDimensions(): Pair<Int, Int> {
    if (size < 30 || decodeToString(0, 4) != "RIFF" || decodeToString(8, 12) != "WEBP") return 0 to 0
    return when (decodeToString(12, 16)) {
        "VP8X" -> {
            val width = 1 + (this[24].toInt() and 0xFF) + ((this[25].toInt() and 0xFF) shl 8) + ((this[26].toInt() and 0xFF) shl 16)
            val height = 1 + (this[27].toInt() and 0xFF) + ((this[28].toInt() and 0xFF) shl 8) + ((this[29].toInt() and 0xFF) shl 16)
            width to height
        }
        else -> 0 to 0
    }
}

private fun ByteArray.svgDimensions(): Pair<Int, Int> {
    val source = decodeToString().take(64 * 1024)
    val viewBox = Regex("""viewBox\s*=\s*[\"']\s*[-.\d]+\s+[-.\d]+\s+([.\d]+)\s+([.\d]+)""", RegexOption.IGNORE_CASE)
        .find(source)
    if (viewBox != null) {
        return viewBox.groupValues[1].toFloatOrNull()?.toInt().orZero() to
            viewBox.groupValues[2].toFloatOrNull()?.toInt().orZero()
    }
    fun dimension(name: String) = Regex("""$name\s*=\s*[\"']\s*([.\d]+)""", RegexOption.IGNORE_CASE)
        .find(source)?.groupValues?.get(1)?.toFloatOrNull()?.toInt().orZero()
    return dimension("width") to dimension("height")
}

private fun Int?.orZero() = this ?: 0

private fun String.normalizedHeading(): String =
    trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')
