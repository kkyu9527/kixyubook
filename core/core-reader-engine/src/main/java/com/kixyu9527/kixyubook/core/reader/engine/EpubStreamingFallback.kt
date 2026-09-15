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

/**
 * Collects only the reading-order idrefs. A second pass over the same OPF can then keep every
 * manifest entry the reader actually needs even when the decorative manifest exceeds its cap.
 */
internal fun readSpineRefsStreaming(input: InputStream, lenient: Boolean = false): Set<String> {
    val spine = linkedSetOf<String>()
    parseSax(input, lenient, object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            if (elementName(localName, qName) == "itemref") {
                val idref = attributes.value("idref").takeIf(String::isNotBlank) ?: return
                if (spine.size >= MAX_STREAMED_SPINE_ITEMS) throw EpubDomLimitExceeded("EPUB spine")
                spine.add(idref)
            }
        }
    })
    return spine
}

internal fun readPackageStreaming(
    input: InputStream,
    opfPath: String,
    lenient: Boolean = false,
    requiredIds: Set<String> = emptySet(),
    manifestLimit: Int = MAX_STREAMED_MANIFEST_ITEMS,
): PackageDocument {
    val manifest = linkedMapOf<String, ManifestItem>()
    val spine = mutableListOf<String>()
    val metadata = linkedMapOf<String, StringBuilder>()
    val creatorNames = mutableListOf<StringBuilder>()
    val creatorIds = mutableListOf<String>()
    val creatorInlineRoles = mutableListOf<String>()
    val rolesByRefineId = mutableMapOf<String, String>()
    val roleBuilders = mutableMapOf<String, StringBuilder>()
    val creatorFileAs = mutableListOf<String>()
    val fileAsByRefineId = mutableMapOf<String, String>()
    val fileAsBuilders = mutableMapOf<String, StringBuilder>()
    val titleBuilders = mutableListOf<StringBuilder>()
    val titleIds = mutableListOf<String>()
    val titleTypeBuilders = mutableMapOf<String, StringBuilder>()
    val guideTitlePages = mutableListOf<String>()
    var titleSort = ""
    var seriesName = ""
    var seriesIndex: Double? = null
    val descriptions = mutableListOf<StringBuilder>()
    var capture: String? = null
    var captureBuilder: StringBuilder? = null
    var pendingRoleRefine: String? = null
    var pendingTitleTypeRefine: String? = null
    var pendingFileAsRefine: String? = null
    var metadataDepth = 0
    var coverId: String? = null
    parseSax(input, lenient, object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = elementName(localName, qName)
            when (name) {
                "metadata" -> metadataDepth++
                "identifier" -> if (metadataDepth > 0) {
                    val builder = metadata.getOrPut(name) { StringBuilder() }
                    capture = name
                    captureBuilder = builder
                }
                "title" -> if (metadataDepth > 0) {
                    val builder = StringBuilder()
                    titleBuilders += builder
                    titleIds += attributes.value("id")
                    capture = name
                    captureBuilder = builder
                }
                "creator" -> if (metadataDepth > 0) {
                    val builder = StringBuilder()
                    creatorNames += builder
                    creatorIds += attributes.value("id")
                    creatorInlineRoles += attributes.value("role").ifBlank { attributes.value("opf:role") }
                    creatorFileAs += attributes.value("file-as").ifBlank { attributes.value("opf:file-as") }
                    capture = name
                    captureBuilder = builder
                }
                "description" -> if (metadataDepth > 0) {
                    val builder = StringBuilder()
                    descriptions += builder
                    capture = name
                    captureBuilder = builder
                }
                "meta" -> if (metadataDepth > 0) {
                    val metaName = attributes.value("name")
                    if (metaName.equals("cover", true)) {
                        coverId = attributes.value("content").takeIf(String::isNotBlank)
                    }
                    when (metaName.lowercase()) {
                        "calibre:title_sort" -> titleSort = attributes.value("content").trim()
                        "calibre:series" -> seriesName = attributes.value("content").trim()
                        "calibre:series_index" ->
                            seriesIndex = attributes.value("content").trim().toDoubleOrNull()
                    }
                    // EPUB 3 roles/title types are text content of <meta refines="#id" property="…">.
                    val refine = attributes.value("refines").removePrefix("#")
                    if (attributes.value("property").equals("role", true) && refine.isNotBlank()) {
                        val builder = StringBuilder()
                        rolesByRefineId[refine] = ""
                        pendingRoleRefine = refine
                        capture = "meta-role"
                        captureBuilder = builder
                        roleBuilders[refine] = builder
                    }
                    if (attributes.value("property").equals("title-type", true) && refine.isNotBlank()) {
                        val builder = StringBuilder()
                        titleTypeBuilders[refine] = builder
                        pendingTitleTypeRefine = refine
                        capture = "meta-title-type"
                        captureBuilder = builder
                    }
                    if (attributes.value("property").equals("file-as", true) && refine.isNotBlank()) {
                        val builder = StringBuilder()
                        fileAsBuilders[refine] = builder
                        fileAsByRefineId[refine] = ""
                        pendingFileAsRefine = refine
                        capture = "meta-file-as"
                        captureBuilder = builder
                    }
                }
                "reference" -> if (metadataDepth == 0) {
                    val type = attributes.value("type").lowercase()
                    if (type == "title-page" || type == "introduction") {
                        val href = attributes.value("href")
                        if (href.isNotBlank()) guideTitlePages += resolveArchivePath(opfPath, href)
                    }
                }
                "item" -> {
                    val id = attributes.value("id")
                    val href = attributes.value("href")
                    // Keep every entry the reading order references; cap only the decorative rest.
                    // Dropping a spine's item would silently lose that chapter's content.
                    if (id.isNotBlank() && href.isNotBlank() &&
                        (id in requiredIds || manifest.size < manifestLimit)
                    ) {
                        manifest[id] = ManifestItem(
                            path = resolveArchivePath(opfPath, href),
                            mediaType = attributes.value("media-type"),
                            properties = attributes.value("properties").split(' ').filter(String::isNotBlank).toSet(),
                        )
                    }
                }
                "itemref" -> {
                    val idref = attributes.value("idref").takeIf(String::isNotBlank)
                    if (idref != null) {
                        // The reading order itself is never truncated silently.
                        if (spine.size >= MAX_STREAMED_SPINE_ITEMS) throw EpubDomLimitExceeded("EPUB spine")
                        spine.add(idref)
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            val target = captureBuilder ?: return
            // Metadata is presentation-only. Do not let a malformed description consume memory
            // that should be reserved for the actual book body.
            if (target.length < MAX_STREAMED_METADATA_CHARS) {
                target.append(ch, start, minOf(length, MAX_STREAMED_METADATA_CHARS - target.length))
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            if (name == "meta" && pendingRoleRefine != null) {
                rolesByRefineId[pendingRoleRefine!!] = roleBuilders[pendingRoleRefine]?.toString()?.trim().orEmpty()
                pendingRoleRefine = null
            }
            if (name == "meta" && pendingTitleTypeRefine != null) {
                pendingTitleTypeRefine = null
            }
            if (name == "meta" && pendingFileAsRefine != null) {
                fileAsByRefineId[pendingFileAsRefine!!] = fileAsBuilders[pendingFileAsRefine]?.toString()?.trim().orEmpty()
                pendingFileAsRefine = null
            }
            if (capture == name || ((capture == "meta-role" || capture == "meta-title-type" || capture == "meta-file-as") && name == "meta")) {
                capture = null
                captureBuilder = null
            }
            if (name == "metadata") metadataDepth--
        }
    })
    val creators = creatorNames.mapIndexed { index, name ->
        val id = creatorIds.getOrNull(index).orEmpty()
        LocalMetadata.EpubCreator(
            name = name.toString(),
            role = creatorInlineRoles.getOrNull(index).orEmpty().ifBlank {
                rolesByRefineId[id].orEmpty()
            },
            sortName = creatorFileAs.getOrNull(index).orEmpty().ifBlank { fileAsByRefineId[id].orEmpty() },
        )
    }
    val mainTitleIndex = titleBuilders.indices.firstOrNull { index ->
        titleTypeBuilders[titleIds.getOrNull(index).orEmpty()]?.toString()?.trim()
            ?.contains("main", ignoreCase = true) == true
    }
    val title = (mainTitleIndex ?: titleBuilders.indexOfFirst { it.isNotBlank() })
        .takeIf { it >= 0 }
        ?.let { titleBuilders[it].toString() }
        .orEmpty()
        .replace(Regex("\\s+"), " ")
        .trim()
    return PackageDocument(
        identifier = metadata["identifier"].normalizedMetadata(),
        title = title,
        authors = LocalMetadata.selectEpubAuthors(creators),
        descriptions = LocalMetadata.selectEpubDescriptions(descriptions.map { it.toString() }),
        coverId = coverId,
        manifest = manifest,
        spine = spine,
        titleSort = titleSort,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        guideTitlePages = guideTitlePages,
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
        // Never truncate silently: exceeding the cap means the chapter cannot be represented
        // completely, so report it instead of returning a partial "successful" parse.
        if (blocks.size >= MAX_STREAMED_CHAPTER_BLOCKS || streamedTextChars >= MAX_STREAMED_CHAPTER_CHARS) {
            throw EpubDomLimitExceeded(xhtmlPath)
        }
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
            if (name == "img") {
                if (blocks.size >= MAX_STREAMED_CHAPTER_BLOCKS) throw EpubDomLimitExceeded(xhtmlPath)
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
