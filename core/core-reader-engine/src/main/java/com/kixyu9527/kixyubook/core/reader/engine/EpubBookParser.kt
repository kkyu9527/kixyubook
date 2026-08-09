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
        val rawCandidates = pkg.spine.mapIndexedNotNull { sourceIndex, id ->
            val item = pkg.manifest[id] ?: return@mapIndexedNotNull null
            val navigation = navigationEntries[item.path.normalizedArchivePath()]
            DocumentChapterOutline(
                sourceIndex = sourceIndex,
                title = navigation?.title ?: item.path.fallbackChapterTitle(sourceIndex),
                volumeTitle = navigation?.volumeTitle,
                volumeIndex = navigation?.volumeIndex,
            )
        }
        val inferredVolumePages = hashSetOf<Int>()
        val candidates = rawCandidates.mapIndexed { position, outline ->
            val item = pkg.manifest[pkg.spine[outline.sourceIndex]] ?: return@mapIndexed outline
            val semanticTitle = outline.title.semanticEpubSectionTitle()
                ?: item.path.substringAfterLast('/').substringBeforeLast('.').semanticEpubSectionTitle()
            if (semanticTitle != null) return@mapIndexed outline.copy(title = semanticTitle)
            if (!outline.title.isGenericEpubChapterTitle()) return@mapIndexed outline

            val nextOutline = rawCandidates.getOrNull(position + 1)
            val previousVolume = rawCandidates.getOrNull(position - 1)?.volumeTitle
            val nextVolume = nextOutline?.volumeTitle?.takeIf { volume ->
                volume.isNotBlank() && volume != previousVolume
            }
            val shouldInspectBody = outline.sourceIndex < FRONT_MATTER_INSPECTION_LIMIT || nextVolume != null
            val inspection = if (shouldInspectBody) {
                runCatching { inspectSpineOutline(zip, pkg, outline.sourceIndex) }.getOrNull()
            } else {
                null
            }
            val inferredVolumeTitle = nextVolume?.takeIf { inspection?.isImageOnly == true }
            if (inferredVolumeTitle != null) inferredVolumePages += outline.sourceIndex
            outline.copy(title = inspection?.title ?: inferredVolumeTitle ?: item.path.fallbackChapterTitle(outline.sourceIndex))
        }
        val navigated = candidates.filter { outline ->
            val item = pkg.manifest[pkg.spine[outline.sourceIndex]] ?: return@filter false
            item.path.normalizedArchivePath() in navigationEntries || outline.sourceIndex in inferredVolumePages
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
                readSpineChapter(zip, pkg, chapterIndex)?.let { parsed ->
                    val stableExpectedTitle = expectedTitle?.singleLineBookHeading()
                        ?.takeIf(String::isNotBlank)
                    if (parsed.title.isGenericEpubChapterTitle() && stableExpectedTitle != null) {
                        parsed.copy(title = stableExpectedTitle)
                    } else {
                        parsed
                    }
                } ?: DocumentChapter(
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
        val heading = content.heading?.singleLineBookHeading()?.takeIf(String::isMeaningfulShortEpubHeading)
        val title = heading ?: item.path.fallbackChapterTitle(index)
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

    private fun inspectSpineOutline(
        zip: ZipFile,
        pkg: PackageDocument,
        index: Int,
    ): SpineOutlineInspection? {
        val id = pkg.spine.getOrNull(index) ?: return null
        val item = pkg.manifest[id] ?: return null
        val entry = zip.findEntry(item.path) ?: return null
        val content = zip.getInputStream(entry).use { input ->
            readXhtml(input, zip, item.path, pkg.manifest.values)
        }
        val heading = content.heading?.singleLineBookHeading()
            ?.takeIf(String::isMeaningfulShortEpubHeading)
        val shortFrontMatter = content.blocks.asSequence()
            .filterIsInstance<XhtmlBlock.Text>()
            .map { it.value.text.singleLineBookHeading() }
            .firstOrNull { it.semanticEpubSectionTitle() != null }
            ?.semanticEpubSectionTitle()
        val textBlocks = content.blocks.count { it is XhtmlBlock.Text }
        val imageBlocks = content.blocks.count { it is XhtmlBlock.Image }
        return SpineOutlineInspection(
            title = heading ?: shortFrontMatter,
            isImageOnly = textBlocks == 0 && imageBlocks == 1,
        )
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

    private companion object {
        const val PACKAGE_INDEX_CACHE_SIZE = 4
        const val CSS_SOURCE_CACHE_SIZE = 48
        const val FRONT_MATTER_INSPECTION_LIMIT = 16
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
    }

}

private data class SpineOutlineInspection(
    val title: String?,
    val isImageOnly: Boolean,
)

private fun Long.elapsedMilliseconds(): Long = (System.nanoTime() - this) / 1_000_000L
