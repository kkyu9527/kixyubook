package com.kixyu9527.kixyubook.core.reader.engine
import com.kixyu9527.kixyubook.core.common.cache.ReaderCacheBudget
import com.kixyu9527.kixyubook.core.common.cache.WeightedLruCache

import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.diagnostics.toDiagnosticFailure
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.EpubLinkResult
import com.kixyu9527.kixyubook.core.common.model.singleLineBookHeading
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureListener
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry
import org.w3c.dom.Element
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class EpubBookParser : BookParser, MemoryPressureListener {
    override val format = BookFormat.EPUB
    private val packageIndexCache = WeightedLruCache<PackageCacheKey, PackageDocument>(
        ReaderCacheBudget.EPUB_PACKAGE_MEMORY_BYTES, PACKAGE_INDEX_CACHE_SIZE,
        diagnosticsName = "epub-package",
    ) { document ->
        256L + (document.identifier.length + document.title.length +
            document.authors.sumOf { it.length } + document.descriptions.sumOf { it.length }) * 2L +
            document.spine.sumOf { 40L + it.length * 2L } + document.manifest.entries.sumOf { (key, item) ->
                128L + (key.length + item.path.length + item.mediaType.length) * 2L + item.properties.sumOf { 40L + it.length * 2L }
            }
    }
    private val cssSourceCache = WeightedLruCache<CssSourceCacheKey, ParsedCssSource>(
        ReaderCacheBudget.EPUB_CSS_MEMORY_BYTES, CSS_SOURCE_CACHE_SIZE,
        diagnosticsName = "epub-css",
    ) { source ->
        128L + source.imports.sumOf { 40L + it.length * 2L } + source.rules.sumOf { rule ->
            128L + (rule.selector.length + rule.sourcePath.length) * 2L + rule.declarations.entries.sumOf { (key, value) ->
                80L + (key.length + value.length) * 2L
            }
        }
    }

    init {
        MemoryPressureRegistry.register(this)
    }

    /**
     * Drops only derived in-memory parsing state. The normalized binary chapter cache is owned by
     * the repository and remains on disk, so reopening a book does not require rebuilding content.
     */
    fun clearMemoryCaches() {
        synchronized(packageIndexCache) { packageIndexCache.clear() }
        synchronized(cssSourceCache) { cssSourceCache.clear() }
    }

    fun resolveLink(file: File, target: String): EpubLinkResult? = ZipFile(file).use { zip ->
        val pkg = readPackage(file, zip)
        val path = target.substringBefore('#').ifBlank {
            pkg.spine.firstOrNull()?.let(pkg.manifest::get)?.path.orEmpty()
        }
        val chapterIndex = pkg.spine.indexOfFirst { id -> pkg.manifest[id]?.path.equals(path, true) }
        if (chapterIndex < 0) return@use null
        val fragment = target.substringAfter('#', "").trim()
        if (fragment.isEmpty()) return@use EpubLinkResult.Location(chapterIndex)
        val entry = zip.findEntry(path) ?: return@use EpubLinkResult.Location(chapterIndex)
        val document = try {
            zip.openBoundedEntry(entry, MAX_EPUB_XHTML_BYTES).use { input ->
                newDocumentBuilder().parse(input).also(::validateXmlDocument)
            }
        } catch (_: EpubDomLimitExceeded) {
            return@use EpubLinkResult.Location(chapterIndex)
        }
        val nodes = document.getElementsByTagNameNS("*", "*")
        val targetElement = (0 until nodes.length).asSequence()
            .mapNotNull { nodes.item(it) as? Element }
            .firstOrNull { it.getAttribute("id") == fragment || it.getAttribute("xml:id") == fragment }
            ?: return@use EpubLinkResult.Location(chapterIndex)
        val noteContainer = generateSequence(targetElement as Element?) { it.parentNode as? Element }
            .firstOrNull { element ->
                element.getAttributeNS("http://www.idpf.org/2007/ops", "type")
                    .contains("note", ignoreCase = true) ||
                    element.getAttribute("epub:type").contains("note", ignoreCase = true) ||
                    element.getAttribute("role").contains("doc-footnote", ignoreCase = true) ||
                    element.getAttribute("class").contains("footnote", ignoreCase = true) ||
                    element.localName.orEmpty().equals("aside", ignoreCase = true) ||
                    NOTE_FRAGMENT_PATTERN.containsMatchIn(element.getAttribute("id"))
            }
        if (noteContainer != null) {
            val text = noteContainer.textContent.orEmpty().replace(Regex("\\s+"), " ").trim()
            return@use text.takeIf(String::isNotBlank)?.let {
                EpubLinkResult.Footnote(noteContainer.getAttribute("title").ifBlank { "注释" }, it)
            }
        }
        val textElements = mutableListOf<Pair<Element, StyledText>>()
        val content = try {
            readXhtml(document, zip, path, pkg.manifest.values) { element, text ->
                textElements += element to text
            }
        } catch (_: EpubDomLimitExceeded) {
            return@use EpubLinkResult.Location(chapterIndex)
        }
        val heading = content.heading?.singleLineBookHeading()?.takeIf(String::isMeaningfulShortEpubHeading)
        var removedHeading = false
        val body = textElements.filterNot { (_, text) ->
            val duplicate = !removedHeading && heading != null &&
                text.text.normalizedHeading() == heading.normalizedHeading()
            if (duplicate) removedHeading = true
            duplicate
        }
        val nodeIndices = java.util.IdentityHashMap<org.w3c.dom.Node, Int>().apply {
            for (index in 0 until nodes.length) put(nodes.item(index), index)
        }
        val targetNodeIndex = checkNotNull(nodeIndices[targetElement])
        val paragraph = body.indexOfFirst { (element, _) ->
            generateSequence(targetElement as org.w3c.dom.Node?) { it.parentNode }.any { it === element } ||
                checkNotNull(nodeIndices[element]) >= targetNodeIndex
        }.takeIf { it >= 0 } ?: body.lastIndex.coerceAtLeast(0)
        EpubLinkResult.Location(chapterIndex, paragraph)
    }

    override fun onMemoryPressure(level: MemoryPressureLevel) {
        clearMemoryCaches()
    }

    override fun readMetadata(
        file: File,
        fallbackTitle: String,
        sourceName: String,
        rules: List<LocalMetadata.FilenameRule>,
        specs: List<com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec>,
    ): DocumentMetadata = ZipFile(file).use { zip ->
        val pkg = readPackage(file, zip)
        val coverItem = pkg.manifest.values.firstOrNull { "cover-image" in it.properties }
            ?: pkg.coverId?.let(pkg.manifest::get)
        val cover = coverItem?.let { item ->
            // Bound the read by the declared size *and* the actual stream: a falsified ZipEntry.size
            // must not let a small cover entry inflate into an unbounded allocation.
            zip.findEntry(item.path)?.takeIf { it.size in 1..MAX_COVER_BYTES.toLong() }?.let { entry ->
                runCatching { zip.openBoundedEntry(entry, MAX_COVER_BYTES).use { it.readBytes() } }.getOrNull()
            }
        }
        // The file name is an independent source: OPF metadata missing an author falls back to
        // 《书名》作者：某某.txt patterns instead of leaving the book anonymous.
        val fileCandidates = if (sourceName.isBlank()) {
            LocalMetadata.ExtractedMetadata(emptyList(), emptyList(), emptyList(), emptySet())
        } else {
            LocalMetadata.parseFileName(sourceName, rules)
        }
        val descriptions = pkg.descriptions.ifEmpty { readFrontMatterDescriptions(zip, pkg) }
        val fromSpec = LocalMetadata.specCandidates(specs, sourceName)
        DocumentMetadata(
            identityHint = pkg.identifier.takeIf { it.startsWith("urn:uuid:", true) }?.substringAfterLast(':'),
            title = pkg.title.ifBlank {
                LocalMetadata.mergeTitles(fromSpec.titles + fileCandidates.titles, fallbackTitle)
            },
            author = LocalMetadata.mergeAuthors(
                pkg.authors.map { LocalMetadata.MetadataCandidate(it, LocalMetadata.MetadataSource.EPUB_ROLE) } +
                    fromSpec.authors +
                    fileCandidates.authors,
            ),
            description = LocalMetadata.mergeDescriptions(descriptions),
            coverBytes = cover,
            coverExtension = coverItem?.mediaType?.substringAfter('/')?.substringBefore('+') ?: "jpg",
            titleSort = pkg.titleSort,
            seriesName = pkg.seriesName,
            seriesIndex = pkg.seriesIndex,
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
        val navigationEntries = linkedMapOf<String, NavigationEntry>().apply {
            readNavigationEntries(zip, pkg).forEach { (target, entry) ->
                putIfAbsent(target.normalizedArchivePath(), entry)
            }
        }
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
            outline.copy(title = inspection?.title ?: inferredVolumeTitle ?: item.path.fallbackChapterTitle(outline.sourceIndex))
        }
        // The publisher's TOC is navigation, not a whitelist of readable spine resources.
        // Keep every source index so unlisted prologues/interludes remain readable and searchable.
        adoptVolumeOpeningPages(candidates, pkg)
    }

    /**
     * Some publishers leave a volume's foreword/opening page out of the navigation document. With
     * no volume of its own it surfaced as an extra top-level row right before that volume. Adopt
     * the following volume's title so the directory claims the page as the volume's own opening
     * content instead of rendering "foreword2" as a sibling chapter.
     */
    private fun adoptVolumeOpeningPages(
        outlines: List<DocumentChapterOutline>,
        pkg: PackageDocument,
    ): List<DocumentChapterOutline> {
        if (outlines.none { it.volumeTitle == null }) return outlines
        return outlines.mapIndexed { index, outline ->
            if (outline.volumeTitle != null) return@mapIndexed outline
            val nextVolume = outlines.getOrNull(index + 1)?.volumeTitle?.takeIf(String::isNotBlank)
                ?: return@mapIndexed outline
            val item = pkg.manifest[pkg.spine.getOrNull(outline.sourceIndex).orEmpty()]
                ?: return@mapIndexed outline
            val fileName = item.path.substringAfterLast('/').substringBeforeLast('.')
            if (!fileName.isVolumeOpeningPageName() && !outline.title.isVolumeOpeningPageName()) {
                return@mapIndexed outline
            }
            outline.copy(title = nextVolume)
        }
    }

    fun readNavigation(file: File): List<com.kixyu9527.kixyubook.core.common.model.EpubNavigationEntry> = ZipFile(file).use { zip ->
        val pkg = readPackage(file, zip)
        val sourceIndices = pkg.spine.mapIndexedNotNull { index, id ->
            pkg.manifest[id]?.path?.normalizedArchivePath()?.let { it to index }
        }.toMap()
        readNavigationEntries(zip, pkg).mapNotNull { (target, entry) ->
            sourceIndices[target.normalizedArchivePath()]?.let { index ->
                com.kixyu9527.kixyubook.core.common.model.EpubNavigationEntry(index, entry.title, target, entry.depth)
            }
        }
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
            val failure = error.toDiagnosticFailure()
            DiagnosticLog.record(
                Category.EPUB_PARSE,
                "bulk_parse_finished",
                elapsedMs = startedAt.elapsedMilliseconds(),
                outcome = failure.outcome,
                details = mapOf(
                    "book" to file.nameWithoutExtension.take(8),
                    "requested" to (sourceIndices?.size ?: "all"),
                    "emitted" to emitted,
                    "reason" to failure.reason,
                ),
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
        com.kixyu9527.kixyubook.core.common.cache.CacheDiagnostics.named("epub-body").parsed(
            listOf(file.absolutePath, file.lastModified(), chapterIndex),
        )
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
                    // The directory is the source of truth for grouping. A volume's opening page is
                    // recognized by its file name ("foreword2.xhtml") and adopted into the volume,
                    // but its body may carry any heading ("序", "致读者", ...). The background index
                    // must not overwrite the directory title, so any page the outline recognized as
                    // a volume opening keeps its stored title instead of using a heading whitelist.
                    val isVolumeOpeningPage = pkg.manifest[pkg.spine.getOrNull(chapterIndex).orEmpty()]
                        ?.path
                        ?.substringAfterLast('/')
                        ?.substringBeforeLast('.')
                        ?.isVolumeOpeningPageName() == true
                    val headingIsReplaceable = parsed.title.isGenericEpubChapterTitle() ||
                        parsed.title.semanticEpubSectionTitle() != null ||
                        isVolumeOpeningPage
                    if (headingIsReplaceable && stableExpectedTitle != null) {
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
            val failure = error.toDiagnosticFailure()
            DiagnosticLog.record(
                Category.EPUB_PARSE,
                "chapter_parse_finished",
                elapsedMs = startedAt.elapsedMilliseconds(),
                outcome = failure.outcome,
                details = mapOf(
                    "book" to file.nameWithoutExtension.take(8),
                    "chapter" to chapterIndex,
                    "purpose" to purpose,
                    "reason" to failure.reason,
                ),
            )
            throw error
        }
    }

    private fun readSpineChapter(zip: ZipFile, pkg: PackageDocument, index: Int): DocumentChapter? {
        val id = pkg.spine.getOrNull(index) ?: return null
        val item = pkg.manifest[id] ?: return null
        val entry = zip.findEntry(item.path) ?: return null
        val content = readXhtmlWithFallback(zip, entry, item.path, pkg.manifest.values)
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
        val content = readXhtmlWithFallback(zip, entry, item.path, pkg.manifest.values)
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
        val containerEntry = zip.findEntry("META-INF/container.xml")
            ?: error("EPUB 缺少 META-INF/container.xml")
        val opfPath = try {
            val container = zip.openBoundedEntry(containerEntry, MAX_EPUB_XML_BYTES).use { input ->
                newDocumentBuilder().parse(input).also(::validateXmlDocument)
            }
            (container.getElementsByTagNameNS("*", "rootfile").item(0) as? Element)
                ?.getAttribute("full-path")
                ?.takeIf(String::isNotBlank)
                ?: error("EPUB 缺少 container rootfile")
        } catch (_: Exception) {
            // As with the OPF, a large container must still get the lenient streaming retry.
            readContainerRootfileWithRetry(zip, containerEntry)
        }
        val opfEntry = zip.findEntry(opfPath) ?: error("EPUB 缺少 $opfPath")
        return try {
            parsePackageDom(zip, opfPath)
        } catch (_: Exception) {
            // The DOM path fails either on the size budget (large OPF) or on the XML itself
            // (DOCTYPE). Both must run the streaming path, which retries leniently on parse errors.
            parsePackageStreaming(zip, opfEntry, opfPath, lenient = false)
        }
    }

    private fun readContainerRootfileWithRetry(
        zip: ZipFile,
        containerEntry: java.util.zip.ZipEntry,
    ): String = try {
        zip.openBoundedEntry(containerEntry, MAX_STREAMED_EPUB_XML_BYTES).use { readContainerRootfileStreaming(it) }
    } catch (error: Exception) {
        if (error is EpubDomLimitExceeded) throw error
        zip.openBoundedEntry(containerEntry, MAX_STREAMED_EPUB_XML_BYTES).use {
            readContainerRootfileStreaming(it, lenient = true)
        }
    }

    private fun parsePackageStreaming(
        zip: ZipFile,
        opfEntry: java.util.zip.ZipEntry,
        opfPath: String,
        lenient: Boolean,
    ): PackageDocument = try {
        readPackageTwoPass(zip, opfEntry, opfPath, lenient)
    } catch (error: Exception) {
        // A DOCTYPE or malformed prolog defeats the strict streaming parser too; retry leniently.
        // A size limit is not a parse error, so it must not loop into the lenient pass.
        if (lenient || error is EpubDomLimitExceeded) throw error
        readPackageTwoPass(zip, opfEntry, opfPath, lenient = true)
    }

    private fun readPackageTwoPass(
        zip: ZipFile,
        opfEntry: java.util.zip.ZipEntry,
        opfPath: String,
        lenient: Boolean,
    ): PackageDocument {
        // Two passes over the OPF: collect the reading order first, then keep every manifest entry
        // it references. Decorative entries past the cap are dropped, but a chapter the reader can
        // reach is never lost to the manifest limit.
        val required = zip.openBoundedEntry(opfEntry, MAX_STREAMED_EPUB_XML_BYTES).use {
            readSpineRefsStreaming(it, lenient)
        }
        return zip.openBoundedEntry(opfEntry, MAX_STREAMED_EPUB_XML_BYTES).use {
            readPackageStreaming(it, opfPath, lenient, required)
        }
    }

    private fun parsePackageDom(zip: ZipFile, opfPath: String, lenient: Boolean = false): PackageDocument {
        val document = parseXml(zip, opfPath, lenient)
        val metadata = document.getElementsByTagNameNS("*", "metadata").item(0) as? Element
        val manifest = linkedMapOf<String, ManifestItem>()
        val items = document.getElementsByTagNameNS("*", "item")
        if (items.length > MAX_MANIFEST_ITEMS) throw EpubDomLimitExceeded("EPUB manifest")
        for (i in 0 until items.length) (items.item(i) as? Element)?.let { item ->
            manifest[item.getAttribute("id")] = ManifestItem(
                resolveArchivePath(opfPath, item.getAttribute("href")),
                item.getAttribute("media-type"),
                item.getAttribute("properties").split(' ').filter(String::isNotBlank).toSet(),
            )
        }
        val spine = buildList {
            val refs = document.getElementsByTagNameNS("*", "itemref")
            if (refs.length > MAX_SPINE_ITEMS) throw EpubDomLimitExceeded("EPUB spine")
            for (i in 0 until refs.length) {
                (refs.item(i) as? Element)?.getAttribute("idref")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val coverId = metadata?.let { element ->
            val nodes = element.getElementsByTagNameNS("*", "meta")
            (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                .firstOrNull { it.getAttribute("name").equals("cover", true) }?.getAttribute("content")
        }
        val extras = metadata?.packageExtras() ?: PackageExtras()
        return PackageDocument(
            metadata?.firstText("identifier").orEmpty(),
            metadata?.mainTitle().orEmpty(),
            authors = metadata?.let { element -> LocalMetadata.selectEpubAuthors(element.domCreators()) }.orEmpty(),
            descriptions = metadata?.let { element ->
                LocalMetadata.selectEpubDescriptions(element.domDescriptions())
            }.orEmpty(),
            coverId,
            manifest,
            spine,
            titleSort = extras.titleSort,
            seriesName = extras.seriesName,
            seriesIndex = extras.seriesIndex,
            guideTitlePages = document.guideTitlePages(opfPath),
        )
    }

    private data class PackageExtras(
        val titleSort: String = "",
        val seriesName: String = "",
        val seriesIndex: Double? = null,
    )

    private fun Element.packageExtras(): PackageExtras {
        var titleSort = ""
        var seriesName = ""
        var seriesIndex: Double? = null
        val metas = getElementsByTagNameNS("*", "meta")
        for (index in 0 until metas.length) {
            val element = metas.item(index) as? Element ?: continue
            when (element.getAttribute("name").lowercase()) {
                "calibre:title_sort" -> titleSort = element.getAttribute("content").trim()
                "calibre:series" -> seriesName = element.getAttribute("content").trim()
                "calibre:series_index" ->
                    seriesIndex = element.getAttribute("content").trim().toDoubleOrNull()
            }
        }
        if (seriesName.isBlank()) {
            // EPUB 3 spells the series as a belongs-to-collection collection.
            for (index in 0 until metas.length) {
                val element = metas.item(index) as? Element ?: continue
                if (element.getAttribute("property").equals("belongs-to-collection", true)) {
                    seriesName = element.textContent.orEmpty().trim()
                    break
                }
            }
        }
        return PackageExtras(titleSort = titleSort, seriesName = seriesName, seriesIndex = seriesIndex)
    }

    private fun org.w3c.dom.Document.guideTitlePages(opfPath: String): List<String> {
        val references = getElementsByTagNameNS("*", "reference")
        val pages = mutableListOf<String>()
        for (index in 0 until references.length) {
            val element = references.item(index) as? Element ?: continue
            val type = element.getAttribute("type").lowercase()
            if (type != "title-page" && type != "introduction") continue
            val href = element.getAttribute("href").takeIf(String::isNotBlank) ?: continue
            pages += resolveArchivePath(opfPath, href)
        }
        return pages
    }

    /**
     * Reads creators and their roles from the DOM. EPUB 2 puts the role in the `opf:role`
     * attribute, EPUB 3 refines it with a separate `<meta property="role">`; both are resolved
     * before selection so translators/editors never masquerade as authors.
     */
    private fun Element.domCreators(): List<LocalMetadata.EpubCreator> {
        val nodes = getElementsByTagNameNS("*", "creator")
        val creators = (0 until nodes.length).mapNotNull { index ->
            val element = nodes.item(index) as? Element ?: return@mapNotNull null
            DomCreator(
                id = element.getAttribute("id"),
                role = element.getAttributeNS(OPF_NAMESPACE, "role")
                    .ifBlank { element.getAttribute("opf:role") },
                fileAs = element.getAttributeNS(OPF_NAMESPACE, "file-as")
                    .ifBlank { element.getAttribute("opf:file-as") },
                name = element.textContent.orEmpty(),
            )
        }
        val refineRoles = mutableMapOf<String, String>()
        val refineFileAs = mutableMapOf<String, String>()
        val metas = getElementsByTagNameNS("*", "meta")
        for (index in 0 until metas.length) {
            val element = metas.item(index) as? Element ?: continue
            val id = element.getAttribute("refines").removePrefix("#")
            if (id.isBlank()) continue
            when {
                element.getAttribute("property").equals("role", true) ->
                    refineRoles[id] = element.textContent.orEmpty()
                element.getAttribute("property").equals("file-as", true) ->
                    refineFileAs[id] = element.textContent.orEmpty()
            }
        }
        return creators.map { creator ->
            LocalMetadata.EpubCreator(
                name = creator.name,
                role = creator.role.ifBlank { refineRoles[creator.id].orEmpty() },
                sortName = creator.fileAs.ifBlank { refineFileAs[creator.id].orEmpty() },
            )
        }
    }

    /** EPUB 3 can declare the main title among subtitles; the first element is not always it. */
    private fun Element.mainTitle(): String {
        val titleNodes = getElementsByTagNameNS("*", "title")
        val candidates = (0 until titleNodes.length).mapNotNull { index ->
            val element = titleNodes.item(index) as? Element ?: return@mapNotNull null
            element.getAttribute("id") to element.textContent.orEmpty().trim()
        }.filter { it.second.isNotEmpty() }
        if (candidates.isEmpty()) return ""
        val titleTypes = mutableMapOf<String, String>()
        val metas = getElementsByTagNameNS("*", "meta")
        for (index in 0 until metas.length) {
            val element = metas.item(index) as? Element ?: continue
            if (!element.getAttribute("property").equals("title-type", true)) continue
            val id = element.getAttribute("refines").removePrefix("#")
            if (id.isNotBlank()) titleTypes[id] = element.textContent.orEmpty().trim().lowercase()
        }
        val main = candidates.firstOrNull { titleTypes[it.first].orEmpty().contains("main") }
        return (main ?: candidates.first()).second
    }

    private fun Element.domDescriptions(): List<String> {
        val nodes = getElementsByTagNameNS("*", "description")
        return (0 until nodes.length).map { index -> nodes.item(index)?.textContent.orEmpty() }
    }

    /**
     * A missing OPF description is not a reason to use chapter one as a synopsis. Only a page that
     * a navigation entry or a front-matter heading explicitly names as an introduction is read.
     */
    private fun readFrontMatterDescriptions(zip: ZipFile, pkg: PackageDocument): List<String> {
        val manifest = pkg.manifest.values
        runCatching { readNavigationEntries(zip, pkg) }.getOrDefault(emptyMap())
            .entries
            .filter { LocalMetadata.isDescriptionHeading(it.value.title) }
            .take(MAX_INTRO_PAGE_DOCS)
            .forEach { (target, _) ->
                readDescriptionFromPath(zip, target.substringBefore('#'), target.substringAfter('#', ""), manifest)
                    ?.let { text -> return listOf(text) }
            }
        pkg.guideTitlePages.forEach { path ->
            readDescriptionFromPath(zip, path, "", manifest)?.let { text -> return listOf(text) }
        }
        var scanned = 0
        for (id in pkg.spine) {
            if (scanned >= MAX_FRONT_MATTER_DOCS) break
            val item = pkg.manifest[id] ?: continue
            if (!item.mediaType.contains("xhtml", ignoreCase = true)) continue
            scanned++
            val content = readContent(zip, item.path, manifest) ?: continue
            val heading = content.heading.orEmpty()
            if (heading.isNotBlank() && LocalMetadata.isChapterHeading(heading)) break
            if (heading.isNotBlank() && LocalMetadata.isDescriptionHeading(heading)) {
                content.descriptionText(heading)?.let { return listOf(it) }
            }
        }
        return emptyList()
    }

    private fun readDescriptionFromPath(
        zip: ZipFile,
        path: String,
        fragment: String,
        manifest: Collection<ManifestItem>,
    ): String? {
        val entry = zip.findEntry(path) ?: return null
        val document = parseXhtmlDocument(zip, entry) ?: return null
        // Keep the element with every text block so a #fragment anchor or the introduction
        // heading can start the block and the following chapter heading can end it.
        val textElements = mutableListOf<Pair<Element, String>>()
        val content = readXhtml(document, zip, path, manifest) { element, text ->
            textElements += element to text.text
        }
        val anchor = fragment.takeIf(String::isNotBlank)?.let { id ->
            val nodes = document.getElementsByTagNameNS("*", "*")
            (0 until nodes.length).asSequence()
                .mapNotNull { nodes.item(it) as? Element }
                .firstOrNull { it.getAttribute("id") == id || it.getAttribute("xml:id") == id }
        }
        val startIndex = when {
            anchor != null -> textElements.indexOfFirst { (element, _) ->
                generateSequence(element as org.w3c.dom.Node?) { it.parentNode }.any { it === anchor }
            }
            else -> textElements.indexOfFirst { (_, text) -> LocalMetadata.isDescriptionHeading(text) }
        }
        if (startIndex < 0) return null
        val paragraphs = mutableListOf<String>()
        for (index in startIndex until textElements.size) {
            val text = textElements[index].second.trim()
            if (text.isBlank()) continue
            if (index == startIndex && LocalMetadata.isDescriptionHeading(text)) continue
            if (LocalMetadata.isChapterHeading(text)) break
            paragraphs += text
        }
        if (paragraphs.isEmpty()) {
            // The anchor path may still resolve through the block list even when the heading is
            // absent; fall back to the parsed content rather than returning nothing.
            content.heading?.takeIf(LocalMetadata::isDescriptionHeading)?.let { heading ->
                return content.descriptionText(heading)
            }
            return null
        }
        return LocalMetadata.cleanDescription(paragraphs.joinToString("\n")).takeIf { it.isNotBlank() }
    }

    private fun readContent(
        zip: ZipFile,
        path: String,
        manifest: Collection<ManifestItem>,
    ): XhtmlContent? = zip.findEntry(path)?.let { entry ->
        // Reuse the chapter reader's fallback: a DOCTYPE or an oversized front page must not make
        // the description silently disappear.
        runCatching { readXhtmlWithFallback(zip, entry, path, manifest) }.getOrNull()
    }

    /** Parses a metadata XHTML with the same lenient retry the chapter reader uses. */
    private fun parseXhtmlDocument(zip: ZipFile, entry: java.util.zip.ZipEntry): org.w3c.dom.Document? {
        val strict = runCatching {
            zip.openBoundedEntry(entry, MAX_EPUB_XHTML_BYTES).use { input ->
                newDocumentBuilder().parse(input).also(::validateXmlDocument)
            }
        }
        if (strict.isSuccess) return strict.getOrNull()
        if (strict.exceptionOrNull() is EpubDomLimitExceeded) return null
        return runCatching {
            zip.openBoundedEntry(entry, MAX_EPUB_XHTML_BYTES).use { input ->
                newDocumentBuilder(lenient = true).parse(input).also(::validateXmlDocument)
            }
        }.getOrNull()
    }

    private fun XhtmlContent.descriptionText(heading: String): String? {
        var skippedHeading = heading.isBlank()
        val paragraphs = mutableListOf<String>()
        for (block in blocks) {
            val text = (block as? XhtmlBlock.Text)?.value?.text?.trim().orEmpty()
            if (text.isBlank()) continue
            if (!skippedHeading && text.normalizedHeading() == heading.normalizedHeading()) {
                skippedHeading = true
                continue
            }
            // A synopsis page sometimes contains the first chapter in the same XHTML; stop there
            // instead of collecting the chapter title and its body.
            if (LocalMetadata.isChapterHeading(text)) break
            paragraphs += text
        }
        if (paragraphs.isEmpty()) return null
        return LocalMetadata.cleanDescription(paragraphs.joinToString("\n")).takeIf { it.isNotBlank() }
    }

    private data class DomCreator(val id: String, val role: String, val fileAs: String, val name: String)

    private fun readNavigationEntries(zip: ZipFile, pkg: PackageDocument): Map<String, NavigationEntry> = buildMap {
        val volumeIndices = linkedMapOf<String, Int>()
        fun volumeIndex(title: String?): Int? = title?.takeIf(String::isNotBlank)?.let { value ->
            volumeIndices.getOrPut(value) { volumeIndices.size }
        }
        val navigationItems = pkg.manifest.values.filter { item ->
            "nav" in item.properties || item.mediaType.equals(NCX_MEDIA_TYPE, ignoreCase = true)
        }.sortedBy { if ("nav" in it.properties) 0 else 1 }
        navigationItems.forEach { item ->
            // EPUB 3 TOC takes precedence; NCX is a fallback, not a second directory to merge.
            if (isNotEmpty()) return@forEach
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
                        NavigationEntry(title, volumeTitle, volumeIndex(volumeTitle),
                            generateSequence(point.parentNode) { it.parentNode }.filterIsInstance<Element>()
                                .count { it.localName == "navPoint" }),
                    )
                }
            } else {
                val anchors = document.getElementsByTagNameNS("*", "a")
                for (index in 0 until anchors.length) {
                    val anchor = anchors.item(index) as? Element ?: continue
                    val nav = generateSequence(anchor.parentNode) { it.parentNode }.filterIsInstance<Element>()
                        .firstOrNull { it.localName == "nav" }
                    val navType = nav?.getAttributeNS("http://www.idpf.org/2007/ops", "type")
                        .orEmpty().ifBlank { nav?.getAttribute("epub:type").orEmpty() }
                    if (navType.isNotBlank() && "toc" !in navType.split(' ')) continue
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
                            (generateSequence(anchor.parentNode) { it.parentNode }.filterIsInstance<Element>()
                                .count { it.localName == "li" } - 1).coerceAtLeast(0),
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
        val resolved = resolveArchivePath(navigationPath, reference)
        val fragment = reference.substringAfter('#', "")
        putIfAbsent(resolved + if (fragment.isBlank()) "" else "#$fragment", entry)
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

    private fun parseXml(zip: ZipFile, path: String, lenient: Boolean = false): org.w3c.dom.Document {
        val entry = zip.findEntry(path) ?: error("EPUB 缺少 $path")
        return zip.openBoundedEntry(entry, MAX_EPUB_XML_BYTES, path).use { input ->
            newDocumentBuilder(lenient).parse(input).also(::validateXmlDocument)
        }
    }

    private fun validateXmlDocument(document: org.w3c.dom.Document) {
        if (document.getElementsByTagNameNS("*", "*").length > MAX_XML_ELEMENTS) {
            throw EpubDomLimitExceeded("EPUB XML")
        }
    }

    private fun newDocumentBuilder(lenient: Boolean = false) = DocumentBuilderFactory.newInstance().run {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // Lenient parsing permits an internal DTD but still blocks external entities.
        if (!lenient) runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
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
        val document = newDocumentBuilder().parse(input).also(::validateXmlDocument)
        return readXhtml(document, zip, xhtmlPath, manifest)
    }

    private fun readXhtml(
        document: org.w3c.dom.Document,
        zip: ZipFile,
        xhtmlPath: String,
        manifest: Collection<ManifestItem>,
        onText: (Element, StyledText) -> Unit = { _, _ -> },
    ): XhtmlContent {
        val stylesheet = readStylesheet(document, zip, xhtmlPath)
        val nodes = document.getElementsByTagNameNS("*", "*")
        var heading: String? = null
        var accumulatedTextChars = 0
        val blocks = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val tag = element.localName.orEmpty().lowercase()
                val readableBlock = tag in CONTENT_TAGS ||
                    (tag in FALLBACK_CONTENT_TAGS && !element.hasDescendantReadableBlock())
                if (readableBlock && !element.hasContentAncestor()) {
                    val styledText = element.toStyledText(stylesheet, xhtmlPath)
                    if (heading == null && tag in HEADING_TAGS) {
                        heading = styledText.text.takeIf(String::isNotBlank)
                            ?: element.textContent?.singleLineBookHeading()?.takeIf(String::isNotBlank)
                    }
                    if (styledText.text.isBlank()) continue
                    onText(element, styledText)
                    accumulatedTextChars += styledText.text.length
                    if (accumulatedTextChars > MAX_CHAPTER_TEXT_CHARS || size >= MAX_CHAPTER_BLOCKS) {
                        throw EpubDomLimitExceeded(xhtmlPath)
                    }
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
                // Cap image blocks too: a hostile page can otherwise repeat one <img> tens of
                // thousands of times and trigger an unbounded number of entry reads.
                if (size >= MAX_CHAPTER_BLOCKS) throw EpubDomLimitExceeded(xhtmlPath)
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

    private fun readXhtmlWithFallback(
        zip: ZipFile,
        entry: java.util.zip.ZipEntry,
        xhtmlPath: String,
        manifest: Collection<ManifestItem>,
    ): XhtmlContent = try {
        zip.openBoundedEntry(entry, MAX_EPUB_XHTML_BYTES).use { input ->
            readXhtml(input, zip, xhtmlPath, manifest)
        }
    } catch (error: Exception) {
        if (error is EpubDomLimitExceeded) {
            DiagnosticLog.record(
                Category.EPUB_PARSE,
                "xhtml_streaming_fallback",
                details = mapOf("entry" to entry.name.takeLast(96), "size" to entry.size),
            )
        }
        // Whether the DOM path hit the size budget (large XHTML) or the XML itself (DOCTYPE), the
        // streaming path must still retry leniently on a parse error.
        readXhtmlStreamingWithRetry(entry, zip, xhtmlPath, manifest)
    }

    private fun readXhtmlStreamingWithRetry(
        entry: java.util.zip.ZipEntry,
        zip: ZipFile,
        xhtmlPath: String,
        manifest: Collection<ManifestItem>,
    ): XhtmlContent = try {
        zip.openBoundedEntry(entry, MAX_STREAMED_EPUB_XHTML_BYTES).use { input ->
            readXhtmlStreaming(input, zip, xhtmlPath, manifest)
        }
    } catch (error: Exception) {
        // A size limit is not a parse error; retrying leniently would only hit the same budget.
        if (error is EpubDomLimitExceeded) throw error
        zip.openBoundedEntry(entry, MAX_STREAMED_EPUB_XHTML_BYTES).use { input ->
            readXhtmlStreaming(input, zip, xhtmlPath, manifest, lenient = true)
        }
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
                        val imported = runCatching {
                            zip.openBoundedEntry(entry, MAX_CSS_BYTES).use {
                                it.readBytes().toString(StandardCharsets.UTF_8)
                            }
                        }.getOrNull() ?: return@forEach
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
                    val source = runCatching {
                        zip.openBoundedEntry(entry, MAX_CSS_BYTES).use { input ->
                            input.readBytes().toString(StandardCharsets.UTF_8)
                        }
                    }.getOrNull() ?: return
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
        const val MAX_MANIFEST_ITEMS = 20_000
        const val MAX_SPINE_ITEMS = 10_000
        const val MAX_XML_ELEMENTS = 100_000
        const val MAX_CHAPTER_BLOCKS = 50_000
        const val MAX_CHAPTER_TEXT_CHARS = 4_000_000
        /** Synopses are short; scanning more front matter can only misread chapter one. */
        const val MAX_INTRO_PAGE_DOCS = 2
        const val MAX_FRONT_MATTER_DOCS = 3
        const val OPF_NAMESPACE = "http://www.idpf.org/2007/opf"
    }

}

private data class SpineOutlineInspection(
    val title: String?,
    val isImageOnly: Boolean,
)

private fun Long.elapsedMilliseconds(): Long = (System.nanoTime() - this) / 1_000_000L

private val NOTE_FRAGMENT_PATTERN = Regex("(?i)^(?:fn|footnote|note|endnote)[-_]?\\d+")
