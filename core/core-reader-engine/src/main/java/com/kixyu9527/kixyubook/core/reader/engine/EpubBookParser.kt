package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
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
        ZipFile(file).use { zip ->
            val pkg = readPackage(file, zip)
            pkg.spine.indices.forEach { index ->
                currentCoroutineContext().ensureActive()
                readSpineChapter(zip, pkg, index)?.let { emit(it) }
            }
        }
    }

    /** Reads one spine item so large EPUBs remain chapter-lazy in the reader. */
    suspend fun readChapter(
        file: File,
        chapterIndex: Int,
        expectedTitle: String? = null,
    ): DocumentChapter? = ZipFile(file).use { zip ->
        val pkg = readPackage(file, zip)
        val nearby = buildList {
            add(chapterIndex)
            for (distance in 1..MAX_SPINE_LOOKAROUND) {
                add(chapterIndex + distance)
                add(chapterIndex - distance)
            }
        }.filter { it in pkg.spine.indices }.distinct()
        var fallback: DocumentChapter? = null
        for (spineIndex in nearby) {
            currentCoroutineContext().ensureActive()
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
                block.value.text.normalizedHeading() == title.normalizedHeading()
            if (duplicate) removedHeading = true
            duplicate
        }
        val styledParagraphs = body.mapNotNull { (it as? XhtmlBlock.Text)?.value }
        val paragraphs = styledParagraphs.map(StyledText::text)
        val images = body.mapIndexedNotNull { contentIndex, block ->
            (block as? XhtmlBlock.Image)?.image?.copy(contentIndex = contentIndex)
        }
        return DocumentChapter(title, paragraphs, images, styledParagraphs.map(StyledText::spans))
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
        val cssRules = readCssRules(document, zip, xhtmlPath)
        val nodes = document.getElementsByTagNameNS("*", "*")
        var heading: String? = null
        val blocks = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val tag = element.localName.orEmpty().lowercase()
                val readableBlock = tag in CONTENT_TAGS ||
                    (tag in FALLBACK_CONTENT_TAGS && !element.hasDescendantReadableBlock())
                if (readableBlock && !element.hasContentAncestor()) {
                    val text = element.toStyledText(cssRules).takeIf { it.text.isNotBlank() } ?: continue
                    if (heading == null && tag in HEADING_TAGS) heading = text.text
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

    private fun readCssRules(
        document: org.w3c.dom.Document,
        zip: ZipFile,
        xhtmlPath: String,
    ): List<CssRule> = buildList {
        val visited = mutableSetOf<String>()
        fun appendCss(source: String, basePath: String, depth: Int = 0) {
            if (depth < MAX_CSS_IMPORT_DEPTH) {
                CSS_IMPORT.findAll(source).forEach { match ->
                    val path = resolveArchivePath(basePath, match.groupValues[1])
                    if (!visited.add(path.lowercase())) return@forEach
                    val entry = zip.findEntry(path)?.takeIf { it.size in 1..MAX_CSS_BYTES.toLong() } ?: return@forEach
                    val imported = zip.getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                    appendCss(imported, path, depth + 1)
                }
            }
            addAll(parseCss(source))
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
            val entry = zip.findEntry(path)?.takeIf { it.size in 1..MAX_CSS_BYTES.toLong() } ?: continue
            val css = zip.getInputStream(entry).use { input -> input.readBytes().toString(StandardCharsets.UTF_8) }
            visited += path.lowercase()
            appendCss(css, path)
        }
    }.mapIndexed { index, rule -> rule.copy(order = index) }

    private fun parseCss(source: String): List<CssRule> {
        val clean = source.replace(CSS_COMMENT, "").replace(CSS_IMPORT, "")
        return CSS_RULE.findAll(clean).flatMap { match ->
            val declarations = match.groupValues[2].split(';').mapNotNull { declaration ->
                val separator = declaration.indexOf(':')
                if (separator <= 0) null else {
                    declaration.substring(0, separator).trim().lowercase() to
                        declaration.substring(separator + 1).substringBefore("!important").trim().lowercase()
                }
            }.toMap()
            match.groupValues[1].split(',').asSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith('@') }
                .map { selector -> CssRule(selector, declarations, cssSpecificity(selector)) }
        }.toList()
    }

    private fun Element.toStyledText(cssRules: List<CssRule>): StyledText {
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
                    val styles = element.normalizedStyle(cssRules, inherited)
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
        val inherited = ancestors.fold(NormalizedInlineState()) { state, element ->
            element.normalizedStyle(cssRules, state)
        }
        val rootStyle = normalizedStyle(cssRules, inherited)
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
            .filter { it.selector.contains(":root") || it.selector.trim() in setOf("html", "body") }
            .flatMap { it.declarations.entries.asSequence() }
            .filter { it.key.startsWith("--") }
            .forEach { variables[it.key] = it.value }
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

    private fun Element.hasDescendantReadableBlock(): Boolean {
        val descendants = getElementsByTagNameNS("*", "*")
        for (index in 0 until descendants.length) {
            val descendant = descendants.item(index) as? Element ?: continue
            if (descendant === this) continue
            if (descendant.localName.orEmpty().lowercase() in CONTENT_TAGS + FALLBACK_CONTENT_TAGS) return true
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

    private data class PackageCacheKey(
        val path: String,
        val size: Long,
        val modifiedAt: Long,
    )

    private data class ManifestItem(
        val path: String,
        val mediaType: String,
        val properties: Set<String>,
    )

    private sealed interface XhtmlBlock {
        data class Text(val value: StyledText) : XhtmlBlock
        data class Image(val image: DocumentImage) : XhtmlBlock
    }

    private data class XhtmlContent(val heading: String?, val blocks: List<XhtmlBlock>)
    private data class StyledText(val text: String, val spans: List<ReaderTextSpan>)
    private data class CssRule(
        val selector: String,
        val declarations: Map<String, String>,
        val specificity: Int,
        val order: Int = 0,
    )

    private data class NormalizedInlineState(
        val styles: Set<ReaderInlineStyle> = emptySet(),
        val foreground: ReaderSemanticColor? = null,
        val background: ReaderSemanticColor? = null,
        val variables: Map<String, String> = emptyMap(),
        val preserveWhitespace: Boolean = false,
        val hidden: Boolean = false,
    )

    private class StyledTextBuilder {
        private val text = StringBuilder()
        private val spans = mutableListOf<ReaderTextSpan>()
        private var pendingSpace = false
        private var pendingStyle = NormalizedInlineState()

        fun append(raw: String, style: NormalizedInlineState) {
            if (style.preserveWhitespace) {
                pendingSpace = false
                raw.replace("\r\n", "\n").replace('\r', '\n').forEach { appendCharacter(it, style) }
                return
            }
            raw.forEach { character ->
                if (character.isWhitespace()) {
                    if (text.isNotEmpty()) {
                        pendingSpace = true
                        pendingStyle = style
                    }
                } else {
                    if (pendingSpace) appendCharacter(' ', pendingStyle.merge(style))
                    pendingSpace = false
                    appendCharacter(character, style)
                }
            }
        }

        fun appendBreak(style: NormalizedInlineState) {
            pendingSpace = false
            if (text.isNotEmpty() && text.last() != '\n') appendCharacter('\n', style)
        }

        private fun appendCharacter(character: Char, style: NormalizedInlineState) {
            val start = text.length
            text.append(character)
            if (style.styles.isEmpty() && style.foreground == null && style.background == null) return
            val previous = spans.lastOrNull()
            if (previous != null && previous.end == start && previous.matches(style)) {
                spans[spans.lastIndex] = previous.copy(end = start + 1)
            } else {
                spans += ReaderTextSpan(start, start + 1, style.styles, style.foreground, style.background)
            }
        }

        fun build() = StyledText(text.toString(), spans.toList())

        private fun NormalizedInlineState.merge(other: NormalizedInlineState) = NormalizedInlineState(
            styles = styles + other.styles,
            foreground = other.foreground ?: foreground,
            background = other.background ?: background,
            variables = variables + other.variables,
            preserveWhitespace = preserveWhitespace || other.preserveWhitespace,
            hidden = hidden || other.hidden,
        )

        private fun ReaderTextSpan.matches(style: NormalizedInlineState) =
            styles == style.styles && foreground == style.foreground && background == style.background
    }

    private companion object {
        const val PACKAGE_INDEX_CACHE_SIZE = 4
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        const val MAX_CSS_BYTES = 1024 * 1024
        const val MAX_CSS_IMPORT_DEPTH = 4
        const val MAX_SPINE_LOOKAROUND = 12
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
    }
}

private fun String.resolveCssVariables(variables: Map<String, String>): String {
    var resolved = this
    repeat(4) {
        var changed = false
        resolved = CSS_VARIABLE.replace(resolved) { match ->
            val replacement = variables[match.groupValues[1]]
                ?: match.groupValues.getOrNull(2).orEmpty().trim()
            if (replacement.isNotBlank()) {
                changed = true
                replacement
            } else {
                match.value
            }
        }
        if (!changed) return resolved
    }
    return resolved
}

/** Converts arbitrary publisher shades into a small, stable set of reader color roles. */
private fun String.toReaderSemanticColor(): ReaderSemanticColor? {
    val value = trim().lowercase().substringBefore("!important").trim()
    namedReaderColor(value)?.let { return it }
    parseHexColor(value)?.let { return classifyRgb(it.first, it.second, it.third) }
    parseRgbColor(value)?.let { return classifyRgb(it.first, it.second, it.third) }
    parseHueColor(value)?.let { (hue, chroma) ->
        return if (chroma < .12f) ReaderSemanticColor.NEUTRAL else classifyHue(hue)
    }
    return null
}

private fun namedReaderColor(value: String): ReaderSemanticColor? = when {
    value in setOf("black", "white", "silver", "gray", "grey", "dimgray", "dimgrey", "slategray", "slategrey", "darkslategray", "darkslategrey", "lightgray", "lightgrey", "gainsboro", "whitesmoke", "snow") -> ReaderSemanticColor.NEUTRAL
    value == "fuchsia" || value == "magenta" -> ReaderSemanticColor.MAGENTA
    value.contains("purple") || value.contains("violet") || value.contains("orchid") ||
        value in setOf("indigo", "plum", "thistle", "lavender") -> ReaderSemanticColor.PURPLE
    value.contains("cyan") || value.contains("turquoise") ||
        value in setOf("aqua", "aquamarine", "teal") -> ReaderSemanticColor.CYAN
    value.contains("blue") || value in setOf("navy") -> ReaderSemanticColor.BLUE
    value.contains("green") || value in setOf("lime", "chartreuse", "olive", "olivedrab", "lawngreen") -> ReaderSemanticColor.GREEN
    value.contains("yellow") || value.contains("gold") || value.contains("khaki") ||
        value in setOf("lemonchiffon", "papayawhip", "cornsilk", "moccasin", "beige", "ivory") -> ReaderSemanticColor.YELLOW
    value.contains("orange") || value in setOf(
        "coral", "chocolate", "peru", "sienna", "saddlebrown", "burlywood", "tan", "peachpuff", "bisque",
        "navajowhite", "wheat", "blanchedalmond",
    ) -> ReaderSemanticColor.ORANGE
    value.contains("red") || value.contains("pink") || value.contains("salmon") ||
        value in setOf("crimson", "maroon", "firebrick", "tomato", "brown") -> ReaderSemanticColor.RED
    else -> null
}

private fun parseHexColor(value: String): Triple<Float, Float, Float>? {
    if (!value.startsWith('#')) return null
    val hex = value.drop(1)
    val rgb = when (hex.length) {
        3, 4 -> hex.take(3).map { "$it$it" }
        6, 8 -> listOf(hex.substring(0, 2), hex.substring(2, 4), hex.substring(4, 6))
        else -> return null
    }
    return runCatching {
        Triple(rgb[0].toInt(16) / 255f, rgb[1].toInt(16) / 255f, rgb[2].toInt(16) / 255f)
    }.getOrNull()
}

private fun parseRgbColor(value: String): Triple<Float, Float, Float>? {
    val match = CSS_RGB.matchEntire(value) ?: CSS_COLOR.matchEntire(value) ?: return null
    val components = match.groupValues[1].trim().split(Regex("[,/\\s]+"))
    if (components.size < 3) return null
    return components.take(3).map { component ->
        if (component.endsWith('%')) component.dropLast(1).toFloatOrNull()?.div(100f)
        else component.toFloatOrNull()?.let { if (it > 1f) it / 255f else it }
    }.takeIf { it.all { component -> component != null } }
        ?.let { Triple(it[0]!!.coerceIn(0f, 1f), it[1]!!.coerceIn(0f, 1f), it[2]!!.coerceIn(0f, 1f)) }
}

/** Parses hsl()/hsla()/hwb()/lch()/oklch() far enough to retain the source hue family. */
private fun parseHueColor(value: String): Pair<Float, Float>? {
    val match = CSS_HUE_COLOR.matchEntire(value) ?: return null
    val function = match.groupValues[1]
    val components = match.groupValues[2].trim().split(Regex("[,/\\s]+"))
    if (components.size < 3) return null
    return when (function) {
        "hsl", "hsla", "hwb" -> {
            val hue = components[0].cssHueOrNull() ?: return null
            val chroma = components[1].removeSuffix("%").toFloatOrNull()?.div(100f) ?: return null
            hue to chroma
        }
        "lch", "oklch" -> {
            val hue = components[2].cssHueOrNull() ?: return null
            val rawChroma = components[1].removeSuffix("%").toFloatOrNull() ?: return null
            hue to if (function == "oklch") rawChroma else rawChroma / 100f
        }
        else -> null
    }
}

private fun String.cssHueOrNull(): Float? {
    val raw = trim()
    val degrees = when {
        raw.endsWith("turn") -> raw.removeSuffix("turn").toFloatOrNull()?.times(360f)
        raw.endsWith("grad") -> raw.removeSuffix("grad").toFloatOrNull()?.times(.9f)
        raw.endsWith("rad") -> raw.removeSuffix("rad").toFloatOrNull()?.times(57.29578f)
        raw.endsWith("deg") -> raw.removeSuffix("deg").toFloatOrNull()
        else -> raw.toFloatOrNull()
    } ?: return null
    return ((degrees % 360f) + 360f) % 360f
}

private fun classifyRgb(red: Float, green: Float, blue: Float): ReaderSemanticColor {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta < .08f || max <= 0f || delta / max < .14f) return ReaderSemanticColor.NEUTRAL
    val hue = when (max) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return classifyHue(hue)
}

private fun classifyHue(hue: Float): ReaderSemanticColor = when {
    hue < 20f || hue >= 330f -> ReaderSemanticColor.RED
    hue < 45f -> ReaderSemanticColor.ORANGE
    hue < 75f -> ReaderSemanticColor.YELLOW
    hue < 165f -> ReaderSemanticColor.GREEN
    hue < 200f -> ReaderSemanticColor.CYAN
    hue < 260f -> ReaderSemanticColor.BLUE
    hue < 300f -> ReaderSemanticColor.PURPLE
    else -> ReaderSemanticColor.MAGENTA
}

private val CSS_VARIABLE = Regex("var\\(\\s*(--[a-zA-Z0-9_-]+)\\s*(?:,\\s*([^)]*))?\\)", RegexOption.IGNORE_CASE)
private val CSS_RGB = Regex("rgba?\\((.*)\\)", RegexOption.IGNORE_CASE)
private val CSS_COLOR = Regex("color\\((?:srgb|display-p3)\\s+(.*)\\)", RegexOption.IGNORE_CASE)
private val CSS_HUE_COLOR = Regex("(hsl|hsla|hwb|lch|oklch)\\((.*)\\)", RegexOption.IGNORE_CASE)

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
