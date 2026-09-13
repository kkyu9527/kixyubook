package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.singleLineBookHeading
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/**
 * Low-memory fallbacks for publisher files that are too large or too deeply structured for DOM.
 * They intentionally preserve readable content before decorative CSS and directory hierarchy.
 */
internal fun readContainerRootfileStreaming(input: InputStream, lenient: Boolean = false): String {
    var rootfile = ""
    parseSax(input, lenient, object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            if (elementName(localName, qName) == "rootfile" && rootfile.isBlank()) {
                rootfile = attributes.value("full-path")
            }
        }
    })
    return rootfile.ifBlank { error("EPUB 缺少 container rootfile") }
}

internal fun readPackageStreaming(
    input: InputStream,
    opfPath: String,
    lenient: Boolean = false,
): PackageDocument {
    val manifest = linkedMapOf<String, ManifestItem>()
    val spine = mutableListOf<String>()
    val metadata = linkedMapOf<String, StringBuilder>()
    var capture: String? = null
    var metadataDepth = 0
    var coverId: String? = null
    parseSax(input, lenient, object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = elementName(localName, qName)
            when (name) {
                "metadata" -> metadataDepth++
                "identifier", "title", "creator", "description" -> if (metadataDepth > 0) {
                    capture = name
                    metadata.getOrPut(name) { StringBuilder() }
                }
                "meta" -> if (metadataDepth > 0 && attributes.value("name").equals("cover", true)) {
                    coverId = attributes.value("content").takeIf(String::isNotBlank)
                }
                "item" -> {
                    val id = attributes.value("id")
                    val href = attributes.value("href")
                    // Ignore items past the cap instead of failing: the fallback exists for large
                    // legitimate books, but a hostile OPF must not grow this map without bound.
                    if (id.isNotBlank() && href.isNotBlank() && manifest.size < MAX_STREAMED_MANIFEST_ITEMS) {
                        manifest[id] = ManifestItem(
                            path = resolveArchivePath(opfPath, href),
                            mediaType = attributes.value("media-type"),
                            properties = attributes.value("properties").split(' ').filter(String::isNotBlank).toSet(),
                        )
                    }
                }
                "itemref" -> if (spine.size < MAX_STREAMED_SPINE_ITEMS) {
                    attributes.value("idref").takeIf(String::isNotBlank)?.let(spine::add)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            val target = capture?.let(metadata::get) ?: return
            // Metadata is presentation-only. Do not let a malformed description consume memory
            // that should be reserved for the actual book body.
            if (target.length < MAX_STREAMED_METADATA_CHARS) {
                target.append(ch, start, minOf(length, MAX_STREAMED_METADATA_CHARS - target.length))
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            if (capture == name) capture = null
            if (name == "metadata") metadataDepth--
        }
    })
    return PackageDocument(
        identifier = metadata["identifier"].normalizedMetadata(),
        title = metadata["title"].normalizedMetadata(),
        author = metadata["creator"].normalizedMetadata(),
        description = metadata["description"].normalizedMetadata(),
        coverId = coverId,
        manifest = manifest,
        spine = spine,
    )
}

internal fun readXhtmlStreaming(
    input: InputStream,
    zip: ZipFile,
    xhtmlPath: String,
    manifest: Collection<ManifestItem>,
    lenient: Boolean = false,
): XhtmlContent {
    val blocks = mutableListOf<XhtmlBlock>()
    var heading: String? = null
    var ignoredDepth = 0
    var activeDepth = -1
    var activeTag = ""
    var depth = 0
    var streamedTextChars = 0
    var buffer = StringBuilder()

    fun appendTextBlock(rawValue: String) {
        val value = rawValue.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex(" *\\n+ *"), "\n")
            .trim()
        if (value.isBlank()) return
        // Truncate rather than fail: the streaming fallback is the last resort for large books,
        // but an oversized chapter must not grow the block list without bound.
        if (blocks.size >= MAX_STREAMED_CHAPTER_BLOCKS || streamedTextChars >= MAX_STREAMED_CHAPTER_CHARS) return
        streamedTextChars += value.length
        if (heading == null && activeTag in HEADING_TAGS) heading = value.singleLineBookHeading()
        blocks += XhtmlBlock.Text(StyledText(value, emptyList()))
    }

    fun drainText(force: Boolean) {
        while (buffer.length >= STREAMED_TEXT_CHUNK_CHARS || force && buffer.isNotEmpty()) {
            val upperBound = minOf(buffer.length, STREAMED_TEXT_CHUNK_CHARS)
            val splitAt = if (upperBound == buffer.length) {
                upperBound
            } else {
                buffer.readableSplitIndex(
                    endExclusive = upperBound,
                    minimumIndex = STREAMED_TEXT_CHUNK_CHARS / 2,
                )
                    .takeIf { it >= STREAMED_TEXT_CHUNK_CHARS / 2 }
                    ?: upperBound
            }
            appendTextBlock(buffer.substring(0, splitAt))
            buffer.delete(0, splitAt)
        }
    }

    parseSax(input, lenient, object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = elementName(localName, qName)
            depth++
            if (name in STREAM_IGNORED_TAGS) ignoredDepth++
            if (ignoredDepth > 0) return
            if (name in CONTENT_TAGS || (name in FALLBACK_CONTENT_TAGS && activeDepth < 0)) {
                if (activeDepth < 0) {
                    activeDepth = depth
                    activeTag = name
                    buffer = StringBuilder()
                }
            }
            if (name == "br" && activeDepth >= 0) buffer.append('\n')
            if (name == "img" && blocks.size < MAX_STREAMED_CHAPTER_BLOCKS) {
                val reference = attributes.value("src").ifBlank { attributes.value("href") }
                val resourcePath = resolveArchivePath(xhtmlPath, reference)
                val entry = zip.findEntry(resourcePath)
                if (entry != null) {
                    val mediaType = manifest.firstOrNull { it.path.equals(resourcePath, true) }?.mediaType
                        ?.takeIf { it.startsWith("image/", true) } ?: mediaTypeFor(resourcePath)
                    if (mediaType.startsWith("image/")) {
                        val dimensions = zip.readImageDimensions(entry, mediaType)
                        blocks += XhtmlBlock.Image(
                            DocumentImage(
                                contentIndex = 0,
                                resourcePath = entry.name,
                                mediaType = mediaType,
                                altText = attributes.value("alt").ifBlank { attributes.value("title") },
                                intrinsicWidth = dimensions.first,
                                intrinsicHeight = dimensions.second,
                            ),
                        )
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (ignoredDepth == 0 && activeDepth >= 0) {
                buffer.append(ch, start, length)
                drainText(force = false)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            if (ignoredDepth == 0 && depth == activeDepth) {
                drainText(force = true)
                activeDepth = -1
                activeTag = ""
            }
            if (name in STREAM_IGNORED_TAGS) ignoredDepth--
            depth--
        }
    })
    if (activeDepth >= 0) drainText(force = true)
    return XhtmlContent(heading, blocks)
}

private fun parseSax(input: InputStream, lenient: Boolean, handler: DefaultHandler) {
    val factory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        // Strict parsing forbids DOCTYPE. A lenient retry allows an internal DTD (some publishers
        // ship one) while still disabling external entities, so XXE stays blocked either way.
        if (!lenient) runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
    }
    factory.newSAXParser().parse(input, handler)
}

private fun elementName(localName: String?, qName: String?) =
    localName?.takeIf(String::isNotBlank)?.lowercase()
        ?: qName.orEmpty().substringAfter(':').lowercase()

private fun Attributes.value(name: String): String =
    getValue(name) ?: (0 until length).firstNotNullOfOrNull { index ->
        getValue(index).takeIf {
            (getLocalName(index).takeIf(String::isNotBlank) ?: getQName(index).substringAfter(':'))
                .equals(name, true)
        }
    }.orEmpty()

private fun StringBuilder?.normalizedMetadata() = this?.toString().orEmpty()
    .replace(Regex("\\s+"), " ").trim()

private const val MAX_STREAMED_METADATA_CHARS = 64 * 1024
private const val STREAMED_TEXT_CHUNK_CHARS = 64 * 1024
private const val MAX_STREAMED_MANIFEST_ITEMS = 200_000
private const val MAX_STREAMED_SPINE_ITEMS = 100_000
private const val MAX_STREAMED_CHAPTER_BLOCKS = 1_000_000
private const val MAX_STREAMED_CHAPTER_CHARS = 64 * 1024 * 1024
private val STREAM_IGNORED_TAGS = setOf("style", "script", "noscript", "head")
