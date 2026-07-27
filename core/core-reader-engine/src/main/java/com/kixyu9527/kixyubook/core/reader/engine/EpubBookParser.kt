package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.BookFormat
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.net.URI
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubBookParser : BookParser {
    override val format = BookFormat.EPUB

    override fun readMetadata(file: File, fallbackTitle: String): DocumentMetadata = ZipFile(file).use { zip ->
        val pkg = readPackage(zip)
        val coverItem = pkg.manifest.values.firstOrNull { "cover-image" in it.properties }
            ?: pkg.coverId?.let(pkg.manifest::get)
        val cover = coverItem?.let { item ->
            zip.getEntry(item.path)?.takeIf { it.size in 1..MAX_COVER_BYTES.toLong() }
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
            pkg.spine.forEachIndexed { index, id ->
                val item = pkg.manifest[id] ?: return@forEachIndexed
                val entry = zip.getEntry(item.path) ?: return@forEachIndexed
                val content = zip.getInputStream(entry).use(::readXhtml)
                if (content.paragraphs.isNotEmpty()) {
                    val heading = content.heading?.takeIf { it.length in 2..80 }
                    val fallback = item.path.substringAfterLast('/').substringBeforeLast('.')
                    val title = heading ?: fallback.ifBlank { "第 ${index + 1} 章" }
                    val body = if (heading == null) content.paragraphs else {
                        content.paragraphs.dropWhile { it.normalizedHeading() == title.normalizedHeading() }
                    }
                    emit(DocumentChapter(title, body))
                }
            }
        }
    }

    private fun readPackage(zip: ZipFile): PackageDocument {
        val container = parseXml(zip, "META-INF/container.xml")
        val root = container.getElementsByTagNameNS("*", "rootfile").item(0) as? Element ?: error("EPUB 缺少 container rootfile")
        val opfPath = root.getAttribute("full-path")
        val document = parseXml(zip, opfPath)
        val metadata = document.getElementsByTagNameNS("*", "metadata").item(0) as? Element
        val manifest = linkedMapOf<String, ManifestItem>()
        val items = document.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) (items.item(i) as? Element)?.let { item ->
            manifest[item.getAttribute("id")] = ManifestItem(
                resolveZipPath(opfPath, item.getAttribute("href")), item.getAttribute("media-type"),
                item.getAttribute("properties").split(' ').filter(String::isNotBlank).toSet(),
            )
        }
        val spine = buildList {
            val refs = document.getElementsByTagNameNS("*", "itemref")
            for (i in 0 until refs.length) (refs.item(i) as? Element)?.getAttribute("idref")?.takeIf(String::isNotBlank)?.let(::add)
        }
        val coverId = metadata?.let { element ->
            val nodes = element.getElementsByTagNameNS("*", "meta")
            (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                .firstOrNull { it.getAttribute("name").equals("cover", true) }?.getAttribute("content")
        }
        return PackageDocument(
            metadata?.firstText("identifier").orEmpty(), metadata?.firstText("title").orEmpty(),
            metadata?.firstText("creator").orEmpty(), metadata?.firstText("description").orEmpty(), coverId, manifest, spine,
        )
    }

    private fun parseXml(zip: ZipFile, path: String) = DocumentBuilderFactory.newInstance().run {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        newDocumentBuilder().parse(zip.getInputStream(zip.getEntry(path) ?: error("EPUB 缺少 $path")))
    }

    private fun Element.firstText(name: String) = getElementsByTagNameNS("*", name).item(0)?.textContent?.trim().orEmpty()
    private fun resolveZipPath(opf: String, href: String): String =
        URI(null, null, "/" + opf.substringBeforeLast('/', "") + "/", null)
            .resolve(href.substringBefore('#')).normalize().path.removePrefix("/")

    private fun readXhtml(input: java.io.InputStream): XhtmlContent {
        val document = DocumentBuilderFactory.newInstance().run {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            newDocumentBuilder().parse(input)
        }
        val nodes = document.getElementsByTagNameNS("*", "*")
        var heading: String? = null
        val paragraphs = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (element.localName.orEmpty().lowercase() !in CONTENT_TAGS || element.hasContentAncestor()) continue
                val text = element.textContent.replace(Regex("\\s+"), " ").trim().takeIf(String::isNotBlank) ?: continue
                if (heading == null && element.localName.orEmpty().lowercase() in HEADING_TAGS) heading = text
                add(text)
            }
        }
        return XhtmlContent(heading, paragraphs)
    }

    private fun Element.hasContentAncestor(): Boolean {
        var ancestor: Node? = parentNode
        while (ancestor is Element) {
            if (ancestor.localName.orEmpty().lowercase() in CONTENT_TAGS) return true
            ancestor = ancestor.parentNode
        }
        return false
    }

    private data class PackageDocument(val identifier: String, val title: String, val author: String, val description: String, val coverId: String?, val manifest: Map<String, ManifestItem>, val spine: List<String>)
    private data class ManifestItem(val path: String, val mediaType: String, val properties: Set<String>)
    private data class XhtmlContent(val heading: String?, val paragraphs: List<String>)
    private companion object {
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        val CONTENT_TAGS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote")
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    }
}

private fun String.normalizedHeading(): String = trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')
