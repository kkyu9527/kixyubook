package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import com.kixyu9527.kixyubook.core.common.model.singleLineBookHeading
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class EpubBookParser : BookParser {
    override val format = BookFormat.EPUB
    private val packageIndexCache = object : LinkedHashMap<PackageCacheKey, PackageDocument>(
        PACKAGE_INDEX_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PackageCacheKey, PackageDocument>): Boolean =
            size > PACKAGE_INDEX_CACHE_SIZE
    }
    private val cssSourceCache = object : LinkedHashMap<CssSourceCacheKey, ParsedCssSource>(
        CSS_SOURCE_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CssSourceCacheKey, ParsedCssSource>): Boolean =
            size > CSS_SOURCE_CACHE_SIZE
    }

    /**
     * Drops only derived in-memory parsing state. The normalized binary chapter cache is owned by
     * the repository and remains on disk, so reopening a book does not require rebuilding content.
     */
    fun clearMemoryCaches() {
        synchronized(packageIndexCache) { packageIndexCache.clear() }
        synchronized(cssSourceCache) { cssSourceCache.clear() }
    }

    override fun readMetadata(file: File, fallbackTitle: String): DocumentMetadata = ZipFile(file).use { zip ->
        val pkg = readPackage(file, zip)
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
        readIndexedChapters(file) { _, chapter -> emit(chapter) }
    }

    /**
     * Reads the publisher-provided navigation document without parsing every XHTML body.
     * The returned source index remains stable and is used for subsequent lazy chapter reads.
     */
    fun readChapterOutlines(file: File): List<DocumentChapterOutline> = ZipFile(file).use { zip ->
        val pkg = readPackage(file, zip)
        val navigationEntries = readNavigationEntries(zip, pkg)
        val candidates = pkg.spine.mapIndexedNotNull { sourceIndex, id ->
            val item = pkg.manifest[id] ?: return@mapIndexedNotNull null
            val navigation = navigationEntries[item.path.normalizedArchivePath()]
            DocumentChapterOutline(
                sourceIndex = sourceIndex,
                title = navigation?.title ?: item.path.fallbackChapterTitle(sourceIndex),
                volumeTitle = navigation?.volumeTitle,
                volumeIndex = navigation?.volumeIndex,
            )
        }
        val nestedVolumeTitles = navigationEntries.values
            .mapNotNullTo(hashSetOf()) { it.volumeTitle?.normalizedNavigationTitle() }
        val navigated = candidates.filter { outline ->
            val item = pkg.manifest[pkg.spine[outline.sourceIndex]] ?: return@filter false
            item.path.normalizedArchivePath() in navigationEntries &&
                !(outline.volumeTitle == null && outline.title.normalizedNavigationTitle() in nestedVolumeTitles)
        }
        // Prefer the official TOC when it describes a meaningful part of the spine. This omits
        // publisher-only cover/copyright containers while retaining a safe fallback for EPUBs
        // with missing or incomplete navigation documents.
        if (navigated.size >= 2 && navigated.size * 3 >= candidates.size) navigated else candidates
    }

    /** Parses selected spine entries in one ZipFile session for background search indexing. */
    suspend fun readIndexedChapters(
        file: File,
        sourceIndices: Set<Int>? = null,
        emit: suspend (Int, DocumentChapter) -> Unit,
    ) {
        val startedAt = System.nanoTime()
        var emitted = 0
        try {
            ZipFile(file).use { zip ->
                val pkg = readPackage(file, zip)
                pkg.spine.indices.forEach { index ->
                    currentCoroutineContext().ensureActive()
                    if (sourceIndices == null || index in sourceIndices) {
                        readSpineChapter(zip, pkg, index)?.let {
                            emitted++
                            emit(index, it)
                        }
                    }
                }
            }
            DiagnosticLog.record(
                Category.EPUB_PARSE,
                "bulk_parse_finished",
                elapsedMs = startedAt.elapsedMilliseconds(),
                outcome = "success",
                details = mapOf("requested" to (sourceIndices?.size ?: "all"), "emitted" to emitted),
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            DiagnosticLog.record(
                Category.EPUB_PARSE,
                "bulk_parse_finished",
                elapsedMs = startedAt.elapsedMilliseconds(),
                outcome = error::class.simpleName ?: "error",
                details = mapOf("emitted" to emitted),
            )
            throw error
        }
    }

    /** Reads one spine item so large EPUBs remain chapter-lazy in the reader. */
    suspend fun readChapter(
        file: File,
        chapterIndex: Int,
        expectedTitle: String? = null,
        purpose: String = "interactive",
    ): DocumentChapter? {
        val startedAt = System.nanoTime()
        return try {
            val chapter = ZipFile(file).use { zip ->
                val pkg = readPackage(file, zip)
                if (chapterIndex !in pkg.spine.indices) return@use null
                // The directory stores the exact source spine index. An empty cover/back-cover
                // page must remain empty; substituting a nearby readable spine item duplicates
                // another chapter and breaks navigation identity.
                readSpineChapter(zip, pkg, chapterIndex) ?: DocumentChapter(
                    title = expectedTitle?.singleLineBookHeading()?.takeIf(String::isNotBlank)
                        ?: pkg.manifest[pkg.spine[chapterIndex]]?.path?.fallbackChapterTitle(chapterIndex)
                        ?: "第 ${chapterIndex + 1} 章",
                    paragraphs = emptyList(),
                )
            }
            currentCoroutineContext().ensureActive()
            chapter.also {
                DiagnosticLog.record(
                    Category.EPUB_PARSE,
                    "chapter_parse_finished",
                    elapsedMs = startedAt.elapsedMilliseconds(),
                    outcome = if (chapter == null) "missing" else "success",
                    details = mapOf(
                        "book" to file.nameWithoutExtension.take(8),
                        "chapter" to chapterIndex,
                        "purpose" to purpose,
                        "paragraphs" to chapter?.paragraphs?.size,
                        "images" to chapter?.images?.size,
                    ),
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            DiagnosticLog.record(
                Category.EPUB_PARSE,
                "chapter_parse_finished",
                elapsedMs = startedAt.elapsedMilliseconds(),
                outcome = error::class.simpleName ?: "error",
                details = mapOf(
                    "book" to file.nameWithoutExtension.take(8),
                    "chapter" to chapterIndex,
                    "purpose" to purpose,
                ),
            )
            throw error
        }
    }

    private fun readSpineChapter(zip: ZipFile, pkg: PackageDocument, index: Int): DocumentChapter? {
        val id = pkg.spine.getOrNull(index) ?: return null
        val item = pkg.manifest[id] ?: return null
        val entry = zip.findEntry(item.path) ?: return null
        val content = zip.getInputStream(entry).use { input ->
            readXhtml(input, zip, item.path, pkg.manifest.values)
        }
        if (content.blocks.isEmpty()) return null
        // XHTML headings can contain <br>, line separators or zero-width formatting characters.
        // Never let those leak back into the directory after lazy body indexing.
        val heading = content.heading?.singleLineBookHeading()?.takeIf { it.length in 2..80 }
        val fallback = item.path.substringAfterLast('/').substringBeforeLast('.')
        val title = heading ?: fallback.ifBlank { "第 ${index + 1} 章" }
        var removedHeading = false
        val body = content.blocks.filterNot { block ->
            val duplicate = !removedHeading && heading != null && block is XhtmlBlock.Text &&
                block.value.text.normalizedHeading() == title.normalizedHeading()
            if (duplicate) removedHeading = true
            duplicate
        }
        val styledParagraphs = body.mapNotNull { (it as? XhtmlBlock.Text)?.value }
        val paragraphs = styledParagraphs.map(StyledText::text)
        val images = body.mapIndexedNotNull { contentIndex, block ->
            (block as? XhtmlBlock.Image)?.image?.copy(contentIndex = contentIndex)
        }
        // A spine item containing one image and no readable text is a publisher-authored page,
        // not an illustration embedded in reflowable prose. This covers covers, character art,
        // volume plates and SVG image wrappers without relying on fragile file names.
        val presentedImages = if (styledParagraphs.isEmpty() && images.size == 1) {
            listOf(images.single().copy(isFullPage = true))
        } else {
            images
        }
        return DocumentChapter(title, paragraphs, presentedImages, styledParagraphs.map(StyledText::spans))
    }

    private fun readPackage(file: File, zip: ZipFile): PackageDocument {
        val key = PackageCacheKey(
            path = runCatching { file.canonicalPath }.getOrElse { file.absolutePath },
            size = file.length(),
            modifiedAt = file.lastModified(),
        )
        synchronized(packageIndexCache) {
            packageIndexCache[key]?.let { return it }
        }
        val parsed = parsePackage(zip)
        synchronized(packageIndexCache) {
            packageIndexCache[key] = parsed
        }
        return parsed
    }

    private fun parsePackage(zip: ZipFile): PackageDocument {
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

    private fun readNavigationEntries(zip: ZipFile, pkg: PackageDocument): Map<String, NavigationEntry> = buildMap {
        val volumeIndices = linkedMapOf<String, Int>()
        fun volumeIndex(title: String?): Int? = title?.takeIf(String::isNotBlank)?.let { value ->
            volumeIndices.getOrPut(value) { volumeIndices.size }
        }
        val navigationItems = pkg.manifest.values.filter { item ->
            "nav" in item.properties || item.mediaType.equals(NCX_MEDIA_TYPE, ignoreCase = true)
        }
        navigationItems.forEach { item ->
            val document = runCatching { parseXml(zip, item.path) }.getOrNull() ?: return@forEach
            if (item.mediaType.equals(NCX_MEDIA_TYPE, ignoreCase = true)) {
                val points = document.getElementsByTagNameNS("*", "navPoint")
                for (index in 0 until points.length) {
                    val point = points.item(index) as? Element ?: continue
                    val source = (point.getElementsByTagNameNS("*", "content").item(0) as? Element)
                        ?.getAttribute("src").orEmpty()
                    val title = point.getElementsByTagNameNS("*", "navLabel").item(0)
                        ?.textContent?.normalizedNavigationTitle().orEmpty()
                    val parentPoint = generateSequence(point.parentNode) { it.parentNode }
                        .filterIsInstance<Element>()
                        .firstOrNull { it.localName.orEmpty().equals("navPoint", true) }
                    val volumeTitle = parentPoint?.directNavigationLabel()
                    putNavigationEntry(
                        item.path,
                        source,
                        NavigationEntry(title, volumeTitle, volumeIndex(volumeTitle)),
                    )
                }
            } else {
                val anchors = document.getElementsByTagNameNS("*", "a")
                for (index in 0 until anchors.length) {
                    val anchor = anchors.item(index) as? Element ?: continue
                    val ownListItem = generateSequence(anchor.parentNode) { it.parentNode }
                        .filterIsInstance<Element>()
                        .firstOrNull { it.localName.orEmpty().equals("li", true) }
                    val volumeListItem = ownListItem?.let { own ->
                        generateSequence(own.parentNode?.parentNode) { it.parentNode }
                            .filterIsInstance<Element>()
                            .firstOrNull { it.localName.orEmpty().equals("li", true) }
                    }
                    val volumeTitle = volumeListItem?.directNavigationLabel()
                    putNavigationEntry(
                        item.path,
                        anchor.getAttribute("href"),
                        NavigationEntry(
                            anchor.textContent.normalizedNavigationTitle(),
                            volumeTitle,
                            volumeIndex(volumeTitle),
                        ),
                    )
                }
            }
        }
    }

    private fun MutableMap<String, NavigationEntry>.putNavigationEntry(
        navigationPath: String,
        reference: String,
        entry: NavigationEntry,
    ) {
        if (reference.isBlank() || entry.title.length !in 1..MAX_NAVIGATION_TITLE_LENGTH) return
        val resolved = resolveArchivePath(navigationPath, reference).normalizedArchivePath()
        putIfAbsent(resolved, entry)
    }

    private fun Element.directNavigationLabel(): String? {
        var child = firstChild
        while (child != null) {
            val element = child as? Element
            val tag = element?.localName.orEmpty().lowercase()
            if (tag in setOf("a", "span", "navlabel")) {
                return element?.textContent?.normalizedNavigationTitle()?.takeIf(String::isNotBlank)
            }
            child = child.nextSibling
        }
        return null
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
        val stylesheet = readStylesheet(document, zip, xhtmlPath)
        val nodes = document.getElementsByTagNameNS("*", "*")
        var heading: String? = null
        val blocks = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val tag = element.localName.orEmpty().lowercase()
                val readableBlock = tag in CONTENT_TAGS ||
                    (tag in FALLBACK_CONTENT_TAGS && !element.hasDescendantReadableBlock())
                if (readableBlock && !element.hasContentAncestor()) {
                    val styledText = element.toStyledText(stylesheet)
                    if (heading == null && tag in HEADING_TAGS) {
                        heading = styledText.text.takeIf(String::isNotBlank)
                            ?: element.textContent?.singleLineBookHeading()?.takeIf(String::isNotBlank)
                    }
                    if (styledText.text.isBlank()) continue
                    add(XhtmlBlock.Text(styledText))
                    continue
                }
                val reference = element.imageReference(tag) ?: continue
                // Omit a genuinely inline image surrounded by prose. An image wrapped by an
                // otherwise empty <p> is still a block page used by many EPUB generators.
                if (element.hasTextBearingContentAncestor()) continue
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
        // Some fixed-layout EPUB generators put the only page image on the root element through
        // CSS instead of emitting an <img>. Treat it as content only when the XHTML body produced
        // no readable blocks, so decorative backgrounds behind normal prose remain app-owned.
        val presentedBlocks = if (blocks.isEmpty()) {
            document.fullPageBackgroundImage(stylesheet, zip, xhtmlPath, manifest)
                ?.let { listOf(XhtmlBlock.Image(it)) }
                ?: blocks
        } else {
            blocks
        }
        return XhtmlContent(heading, presentedBlocks)
    }

    private fun org.w3c.dom.Document.fullPageBackgroundImage(
        stylesheet: CssStylesheet,
        zip: ZipFile,
        xhtmlPath: String,
        manifest: Collection<ManifestItem>,
    ): DocumentImage? {
        val roots = buildList {
            (getElementsByTagNameNS("*", "body").item(0) as? Element)?.let(::add)
            documentElement?.takeUnless { it in this }?.let(::add)
        }
        roots.forEach { root ->
            val background = root.backgroundImage(stylesheet.rules, xhtmlPath) ?: return@forEach
            val resourcePath = resolveArchivePath(background.basePath, background.reference)
            val entry = zip.findEntry(resourcePath) ?: return@forEach
            val mediaType = manifest.firstOrNull { it.path.equals(resourcePath, true) }?.mediaType
                ?.takeIf { it.startsWith("image/", true) }
                ?: mediaTypeFor(resourcePath)
            if (!mediaType.startsWith("image/")) return@forEach
            val dimensions = zip.readImageDimensions(entry, mediaType)
            return DocumentImage(
                contentIndex = 0,
                resourcePath = entry.name,
                mediaType = mediaType,
                altText = "整页插图",
                intrinsicWidth = dimensions.first,
                intrinsicHeight = dimensions.second,
                isFullPage = true,
                cropToFill = background.cropToFill,
            )
        }
        return null
    }

    private fun readStylesheet(
        document: org.w3c.dom.Document,
        zip: ZipFile,
        xhtmlPath: String,
    ): CssStylesheet {
        val rules = buildList {
            val visited = mutableSetOf<String>()
            fun appendCss(source: String, basePath: String, depth: Int = 0) {
                if (depth < MAX_CSS_IMPORT_DEPTH) {
                    CSS_IMPORT.findAll(source).forEach { match ->
                        val path = resolveArchivePath(basePath, match.groupValues[1])
                        if (!visited.add(path.lowercase())) return@forEach
                        val entry = zip.findEntry(path)?.takeIf {
                            it.size in 1..MAX_CSS_BYTES.toLong()
                        } ?: return@forEach
                        val imported = zip.getInputStream(entry).use {
                            it.readBytes().toString(StandardCharsets.UTF_8)
                        }
                        appendCss(imported, path, depth + 1)
                    }
                }
                addAll(parseCss(source, basePath))
            }
            fun appendCssEntry(rawPath: String, depth: Int = 0) {
                val path = rawPath.lowercase()
                if (!visited.add(path)) return
                val entry = zip.findEntry(rawPath)?.takeIf {
                    it.size in 1..MAX_CSS_BYTES.toLong()
                } ?: return
                val key = CssSourceCacheKey(zip.name, entry.name, entry.crc, entry.size)
                val parsed = synchronized(cssSourceCache) { cssSourceCache[key] } ?: run {
                    val source = zip.getInputStream(entry).use { input ->
                        input.readBytes().toString(StandardCharsets.UTF_8)
                    }
                    ParsedCssSource(
                        imports = CSS_IMPORT.findAll(source).map { it.groupValues[1] }.toList(),
                        rules = parseCss(source, entry.name),
                    ).also { synchronized(cssSourceCache) { cssSourceCache[key] = it } }
                }
                if (depth < MAX_CSS_IMPORT_DEPTH) {
                    parsed.imports.forEach { reference ->
                        appendCssEntry(resolveArchivePath(entry.name, reference), depth + 1)
                    }
                }
                addAll(parsed.rules)
            }
            val styles = document.getElementsByTagNameNS("*", "style")
            for (index in 0 until styles.length) {
                appendCss(styles.item(index)?.textContent.orEmpty(), xhtmlPath)
            }
            val links = document.getElementsByTagNameNS("*", "link")
            for (index in 0 until links.length) {
                val link = links.item(index) as? Element ?: continue
                if ("stylesheet" !in link.getAttribute("rel").lowercase().split(Regex("\\s+"))) continue
                val path = resolveArchivePath(xhtmlPath, link.getAttribute("href"))
                appendCssEntry(path)
            }
        }.mapIndexed { index, rule -> rule.copy(order = index) }
        // Readium applies publisher, reading-system and user layers separately. In the native
        // document model we keep the same boundary: publisher CSS is reduced once to semantic
        // declarations and root variables; layout, typography and user colors remain app-owned.
        val rootVariables = linkedMapOf<String, String>()
        rules.asSequence()
            .filter { it.selector.contains(":root") || it.selector.trim() in setOf("html", "body") }
            .flatMap { it.declarations.entries.asSequence() }
            .filter { it.key.startsWith("--") }
            .forEach { (name, value) -> rootVariables[name] = value }
        return CssStylesheet(rules, rootVariables)
    }

    private fun parseCss(source: String, sourcePath: String): List<CssRule> {
        val clean = source.replace(CSS_COMMENT, "").replace(CSS_IMPORT, "")
        return CSS_RULE.findAll(clean).flatMap { match ->
            val declarations = match.groupValues[2].split(';').mapNotNull { declaration ->
                val separator = declaration.indexOf(':')
                if (separator <= 0) null else {
                    val property = declaration.substring(0, separator).trim().lowercase()
                    if (property !in SUPPORTED_CSS_PROPERTIES && !property.startsWith("--")) null
                    else {
                        val rawValue = declaration.substring(separator + 1)
                            .substringBefore("!important").trim()
                        property to if (property in CASE_SENSITIVE_CSS_PROPERTIES) rawValue else rawValue.lowercase()
                    }
                }
            }.toMap()
            match.groupValues[1].split(',').asSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith('@') }
                .map { selector -> CssRule(selector, declarations, cssSpecificity(selector), sourcePath = sourcePath) }
        }.toList()
    }

    private fun Element.backgroundImage(cssRules: List<CssRule>, xhtmlPath: String): CssBackgroundImage? {
        var reference: String? = getAttribute("background").trim().takeIf(String::isNotBlank)
        var basePath = xhtmlPath
        var cropToFill = false

        fun applyDeclarations(declarations: Map<String, String>, declarationBasePath: String) {
            declarations.forEach { (property, value) ->
                when (property) {
                    "background" -> {
                        reference = value.cssUrlReference()
                        basePath = declarationBasePath
                        cropToFill = CSS_COVER_VALUE.containsMatchIn(value)
                    }
                    "background-image" -> {
                        reference = value.cssUrlReference()
                        basePath = declarationBasePath
                    }
                    "background-size" -> cropToFill = CSS_COVER_VALUE.containsMatchIn(value)
                }
            }
        }

        cssRules.asSequence()
            .filter { matchesCssSelector(it.selector) }
            .sortedWith(compareBy(CssRule::specificity, CssRule::order))
            .forEach { applyDeclarations(it.declarations, it.sourcePath) }

        val inlineDeclarations = linkedMapOf<String, String>()
        getAttribute("style").split(';').forEach { declaration ->
            val separator = declaration.indexOf(':')
            if (separator > 0) {
                inlineDeclarations[declaration.substring(0, separator).trim().lowercase()] =
                    declaration.substring(separator + 1).substringBefore("!important").trim()
            }
        }
        applyDeclarations(inlineDeclarations, xhtmlPath)

        return reference?.takeIf { it.isNotBlank() && !it.startsWith("data:", true) }?.let {
            CssBackgroundImage(it, basePath, cropToFill)
        }
    }

    private fun Element.toStyledText(stylesheet: CssStylesheet): StyledText {
        val builder = StyledTextBuilder()
        fun visit(node: Node, inherited: NormalizedInlineState) {
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> builder.append(node.nodeValue.orEmpty(), inherited)
                Node.ELEMENT_NODE -> {
                    val element = node as Element
                    val tag = element.localName.orEmpty().lowercase()
                    if (tag in NON_CONTENT_TAGS) return
                    if (tag == "br") {
                        builder.appendBreak(inherited)
                        return
                    }
                    // Inline raster images are intentionally omitted. Publisher EPUBs frequently
                    // repeat decorative logos in every chapter and their dimensions cannot be
                    // represented reliably by the text paginator. Independent illustrations are
                    // still emitted as image blocks by readXhtml().
                    if (element.imageReference(tag) != null) return
                    val styles = element.normalizedStyle(stylesheet.rules, inherited)
                    if (styles.hidden) return
                    var child = element.firstChild
                    while (child != null) {
                        visit(child, styles)
                        child = child.nextSibling
                    }
                }
            }
        }
        val ancestors = generateSequence(parentNode as? Element) { it.parentNode as? Element }.toList().asReversed()
        val inherited = ancestors.fold(
            NormalizedInlineState(variables = stylesheet.rootVariables),
        ) { state, element ->
            element.normalizedStyle(stylesheet.rules, state)
        }
        val rootStyle = normalizedStyle(stylesheet.rules, inherited)
        if (rootStyle.hidden) return StyledText("", emptyList())
        if (localName.orEmpty().equals("hr", true)) return StyledText("· · ·", emptyList())
        listMarker()?.let { builder.append(it, rootStyle) }
        visit(this, inherited)
        return builder.build()
    }

    private fun Element.listMarker(): String? {
        if (!localName.orEmpty().equals("li", true)) return null
        val list = parentNode as? Element ?: return "• "
        if (!list.localName.orEmpty().equals("ol", true)) return "• "
        val position = generateSequence(previousSibling) { it.previousSibling }
            .count { it is Element && it.localName.orEmpty().equals("li", true) }
        val start = list.getAttribute("start").toIntOrNull() ?: 1
        return "${start + position}. "
    }

    private fun Element.normalizedStyle(
        cssRules: List<CssRule>,
        inherited: NormalizedInlineState,
    ): NormalizedInlineState {
        val tag = localName.orEmpty().lowercase()
        val styles = inherited.styles.toMutableSet()
        var foreground = inherited.foreground
        var background = inherited.background
        var preserveWhitespace = inherited.preserveWhitespace
        val variables = inherited.variables.toMutableMap()
        var hidden = inherited.hidden || hasAttribute("hidden") || getAttribute("aria-hidden").equals("true", true)
        when (tag) {
            "b", "strong", "h1", "h2", "h3", "h4", "h5", "h6" -> styles += ReaderInlineStyle.BOLD
            "i", "em", "cite", "dfn", "var", "address", "blockquote", "q" -> styles += ReaderInlineStyle.ITALIC
            "a" -> {
                styles += ReaderInlineStyle.UNDERLINE
                foreground = ReaderSemanticColor.ACCENT
            }
            "u", "ins", "abbr" -> styles += ReaderInlineStyle.UNDERLINE
            "s", "strike", "del" -> styles += ReaderInlineStyle.STRIKETHROUGH
            "code", "kbd", "samp", "tt", "pre" -> styles += ReaderInlineStyle.MONOSPACE
            "sup", "rt" -> styles += ReaderInlineStyle.SUPERSCRIPT
            "sub" -> styles += ReaderInlineStyle.SUBSCRIPT
            "mark" -> background = ReaderSemanticColor.YELLOW
        }
        val declarations = linkedMapOf<String, String>()
        cssRules.asSequence()
            .filter { matchesCssSelector(it.selector) }
            .sortedWith(compareBy(CssRule::specificity, CssRule::order))
            .forEach { declarations.putAll(it.declarations) }
        if (tag == "font" && getAttribute("color").isNotBlank()) {
            declarations["color"] = getAttribute("color")
        }
        if (getAttribute("bgcolor").isNotBlank()) declarations["background-color"] = getAttribute("bgcolor")
        getAttribute("style").split(';').forEach { declaration ->
            val separator = declaration.indexOf(':')
            if (separator > 0) declarations[declaration.substring(0, separator).trim().lowercase()] =
                declaration.substring(separator + 1).substringBefore("!important").trim().lowercase()
        }
        declarations.filterKeys { it.startsWith("--") }.forEach { (name, value) -> variables[name] = value }
        val fontShorthand = declarations["font"].orEmpty()
        val weight = declarations["font-weight"].orEmpty().ifBlank {
            fontShorthand.split(Regex("\\s+")).firstOrNull {
                it in setOf("normal", "bold", "bolder", "lighter") || it.toIntOrNull() != null
            }.orEmpty()
        }
        if (weight == "bold" || weight == "bolder" || weight.toIntOrNull()?.let { it >= 600 } == true) {
            styles += ReaderInlineStyle.BOLD
        } else if (weight == "normal" || weight == "lighter" || weight.toIntOrNull()?.let { it < 600 } == true) {
            styles -= ReaderInlineStyle.BOLD
        }
        val fontStyle = declarations["font-style"].orEmpty().ifBlank { fontShorthand }
        if (fontStyle.let { "italic" in it || "oblique" in it }) {
            styles += ReaderInlineStyle.ITALIC
        } else if (declarations["font-style"] == "normal") {
            styles -= ReaderInlineStyle.ITALIC
        }
        val decoration = declarations["text-decoration"].orEmpty() + " " +
            declarations["text-decoration-line"].orEmpty()
        if (decoration.trim().split(Regex("\\s+")).contains("none")) {
            styles -= ReaderInlineStyle.UNDERLINE
            styles -= ReaderInlineStyle.STRIKETHROUGH
        } else {
            if ("underline" in decoration) styles += ReaderInlineStyle.UNDERLINE
            if ("line-through" in decoration) styles += ReaderInlineStyle.STRIKETHROUGH
        }
        val fontFamily = declarations["font-family"].orEmpty().ifBlank { fontShorthand }
        if (fontFamily.contains("mono")) styles += ReaderInlineStyle.MONOSPACE
        val fontVariant = declarations["font-variant-caps"].orEmpty().ifBlank {
            declarations["font-variant"].orEmpty()
        }
        if ("small-caps" in fontVariant) styles += ReaderInlineStyle.SMALL_CAPS
        else if (fontVariant == "normal") styles -= ReaderInlineStyle.SMALL_CAPS
        val borderBottom = declarations["border-bottom"].orEmpty() + " " +
            declarations["border-bottom-style"].orEmpty()
        if (borderBottom.isNotBlank() &&
            borderBottom.split(Regex("\\s+")).none { it in setOf("none", "hidden", "0", "0px") }
        ) styles += ReaderInlineStyle.UNDERLINE
        preserveWhitespace = when (declarations["white-space"]) {
            "pre", "pre-wrap", "pre-line", "break-spaces" -> true
            "normal", "nowrap" -> false
            else -> preserveWhitespace || tag == "pre"
        }
        if (declarations["display"] == "none" || declarations["visibility"] in setOf("hidden", "collapse")) {
            hidden = true
        }
        when (declarations["vertical-align"]) {
            "super" -> {
                styles -= ReaderInlineStyle.SUBSCRIPT
                styles += ReaderInlineStyle.SUPERSCRIPT
            }
            "sub" -> {
                styles -= ReaderInlineStyle.SUPERSCRIPT
                styles += ReaderInlineStyle.SUBSCRIPT
            }
            "baseline" -> {
                styles -= ReaderInlineStyle.SUPERSCRIPT
                styles -= ReaderInlineStyle.SUBSCRIPT
            }
        }
        (declarations["color"] ?: declarations["-webkit-text-fill-color"])
            ?.resolveCssVariables(variables)
            ?.let { color ->
                foreground = when (color.trim()) {
                    "inherit", "currentcolor" -> inherited.foreground
                    "initial", "unset" -> null
                    "transparent" -> foreground
                    else -> color.toReaderSemanticColor() ?: foreground
                }
            }
        if (tag in INLINE_COLOR_TAGS || tag == "mark") {
            (declarations["background-color"] ?: declarations["background"])
                ?.resolveCssVariables(variables)
                ?.let { color ->
                    background = when (val normalized = color.trim()) {
                        "inherit" -> inherited.background
                        "initial", "unset", "transparent", "none" -> inherited.background
                        else -> normalized.toReaderSemanticColor()
                            ?: normalized.substringBefore(' ').toReaderSemanticColor()
                            ?: background
                    }
                }
        }
        return NormalizedInlineState(styles, foreground, background, variables, preserveWhitespace, hidden)
    }

    private fun Element.matchesCssSelector(rawSelector: String): Boolean {
        val selector = rawSelector.replace(CSS_PSEUDO, "").trim()
        if (selector.isBlank()) return false
        val parts = selector.split(CSS_COMBINATOR).filter(String::isNotBlank)
        if (parts.isEmpty()) return false
        var candidate: Element? = this
        for (index in parts.indices.reversed()) {
            val part = parts[index]
            if (index == parts.lastIndex) {
                if (candidate?.matchesCssCompound(part) != true) return false
                candidate = candidate.parentNode as? Element
            } else {
                while (candidate != null && !candidate.matchesCssCompound(part)) {
                    candidate = candidate.parentNode as? Element
                }
                if (candidate == null) return false
                candidate = candidate.parentNode as? Element
            }
        }
        return true
    }

    private fun Element.matchesCssCompound(selector: String): Boolean {
        if (selector.startsWith('@')) return false
        val simpleSelector = selector.replace(CSS_ATTRIBUTE, "")
        val tag = CSS_TAG.find(simpleSelector)?.value?.lowercase()
        val expectedId = CSS_ID.find(simpleSelector)?.groupValues?.get(1)
        val expectedClasses = CSS_CLASS.findAll(simpleSelector).map { it.groupValues[1] }.toList()
        val expectedAttributes = CSS_ATTRIBUTE.findAll(selector).toList()
        if (tag == null && expectedId == null && expectedClasses.isEmpty() && expectedAttributes.isEmpty()) return false
        if (tag != null && tag != "*" && localName.orEmpty().lowercase() != tag) return false
        if (expectedId != null && getAttribute("id") != expectedId) return false
        val classes = getAttribute("class").split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        if (!expectedClasses.all { it in classes }) return false
        return expectedAttributes.all { match ->
            val name = match.groupValues[1].replace('|', ':')
            val operator = match.groupValues[2]
            val expected = match.groupValues[3].trim().trim('"', '\'')
            val actual = getAttribute(name)
            when (operator) {
                "" -> hasAttribute(name)
                "=" -> actual == expected
                "~=" -> expected in actual.split(Regex("\\s+"))
                "|=" -> actual == expected || actual.startsWith("$expected-")
                "^=" -> actual.startsWith(expected)
                "$=" -> actual.endsWith(expected)
                "*=" -> expected in actual
                else -> false
            }
        }
    }

    private fun cssSpecificity(selector: String): Int {
        val withoutPseudo = selector.replace(CSS_PSEUDO, "")
        val idCount = CSS_ID.findAll(withoutPseudo).count()
        val classLikeCount = CSS_CLASS.findAll(withoutPseudo).count() + CSS_ATTRIBUTE.findAll(withoutPseudo).count()
        val tagCount = withoutPseudo.split(CSS_COMBINATOR).count { compound ->
            CSS_TAG.find(compound.trim())?.value?.let { it != "*" } == true
        }
        return idCount * 100 + classLikeCount * 10 + tagCount
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

    private fun Element.hasTextBearingContentAncestor(): Boolean {
        var ancestor: Node? = parentNode
        while (ancestor is Element) {
            if (ancestor.localName.orEmpty().lowercase() in CONTENT_TAGS) {
                return !ancestor.textContent.isNullOrBlank()
            }
            ancestor = ancestor.parentNode
        }
        return false
    }

    private fun Element.hasDescendantReadableBlock(): Boolean {
        val descendants = getElementsByTagNameNS("*", "*")
        for (index in 0 until descendants.length) {
            val descendant = descendants.item(index) as? Element ?: continue
            if (descendant === this) continue
            if (descendant.localName.orEmpty().lowercase() in CONTENT_TAGS + FALLBACK_CONTENT_TAGS) return true
        }
        return false
    }


    private companion object {
        const val PACKAGE_INDEX_CACHE_SIZE = 4
        const val CSS_SOURCE_CACHE_SIZE = 48
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        const val MAX_CSS_BYTES = 1024 * 1024
        const val MAX_CSS_IMPORT_DEPTH = 4
        val CONTENT_TAGS = setOf(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "dt", "dd", "figcaption",
            "caption", "td", "th", "hr",
        )
        val FALLBACK_CONTENT_TAGS = setOf("div", "section", "article", "main", "aside")
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        val NON_CONTENT_TAGS = setOf("style", "script", "noscript", "rp")
        val INLINE_COLOR_TAGS = setOf(
            "a", "span", "font", "em", "i", "strong", "b", "mark", "u", "ins", "s", "strike", "del", "code",
            "kbd", "samp", "tt", "sup", "sub", "ruby", "rt", "abbr", "cite", "dfn", "var", "small", "big", "label",
        )
        val SUPPORTED_CSS_PROPERTIES = setOf(
            "font", "font-family", "font-style", "font-variant", "font-variant-caps", "font-weight",
            "text-decoration", "text-decoration-line", "border-bottom", "border-bottom-style",
            "white-space", "display", "visibility", "vertical-align", "color",
            "-webkit-text-fill-color", "background", "background-color", "background-image", "background-size",
        )
        val CASE_SENSITIVE_CSS_PROPERTIES = setOf("background", "background-image")
        val CSS_COMMENT = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
        val CSS_RULE = Regex("([^{}]+)\\{([^{}]*)\\}")
        val CSS_PSEUDO = Regex("::?[a-zA-Z-]+(?:\\([^)]*\\))?")
        val CSS_COMBINATOR = Regex("\\s*[>+~]\\s*|\\s+")
        val CSS_TAG = Regex("^[a-zA-Z][a-zA-Z0-9_-]*|^\\*")
        val CSS_ID = Regex("#([a-zA-Z0-9_-]+)")
        val CSS_CLASS = Regex("\\.([a-zA-Z0-9_-]+)")
        val CSS_ATTRIBUTE = Regex("\\[\\s*([a-zA-Z0-9_|:-]+)\\s*(?:([~|^$*]?=)\\s*([^\\]]+?))?\\s*]")
        val CSS_IMPORT = Regex(
            "@import\\s+(?:url\\(\\s*)?[\"']?([^\"')\\s;]+)[\"']?\\s*\\)?[^;]*;",
            RegexOption.IGNORE_CASE,
        )
        val CSS_COVER_VALUE = Regex("(?:^|\\s|/)cover(?:$|\\s)", RegexOption.IGNORE_CASE)
    }
}

private fun Long.elapsedMilliseconds(): Long = (System.nanoTime() - this) / 1_000_000L
