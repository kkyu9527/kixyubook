package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
import org.w3c.dom.Element
import org.w3c.dom.Node

internal fun parseCss(source: String, sourcePath: String): List<CssRule> {
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

internal fun Element.backgroundImage(cssRules: List<CssRule>, xhtmlPath: String): CssBackgroundImage? {
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

internal fun Element.toStyledText(stylesheet: CssStylesheet): StyledText {
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

internal fun Element.listMarker(): String? {
    if (!localName.orEmpty().equals("li", true)) return null
    val list = parentNode as? Element ?: return "• "
    if (!list.localName.orEmpty().equals("ol", true)) return "• "
    val position = generateSequence(previousSibling) { it.previousSibling }
        .count { it is Element && it.localName.orEmpty().equals("li", true) }
    val start = list.getAttribute("start").toIntOrNull() ?: 1
    return "${start + position}. "
}

internal fun Element.normalizedStyle(
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

internal fun Element.matchesCssSelector(rawSelector: String): Boolean {
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

internal fun Element.matchesCssCompound(selector: String): Boolean {
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

internal fun cssSpecificity(selector: String): Int {
    val withoutPseudo = selector.replace(CSS_PSEUDO, "")
    val idCount = CSS_ID.findAll(withoutPseudo).count()
    val classLikeCount = CSS_CLASS.findAll(withoutPseudo).count() + CSS_ATTRIBUTE.findAll(withoutPseudo).count()
    val tagCount = withoutPseudo.split(CSS_COMBINATOR).count { compound ->
        CSS_TAG.find(compound.trim())?.value?.let { it != "*" } == true
    }
    return idCount * 100 + classLikeCount * 10 + tagCount
}

internal fun Element.imageReference(tag: String): String? {
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

internal fun Element.hasContentAncestor(): Boolean {
    var ancestor: Node? = parentNode
    while (ancestor is Element) {
        if (ancestor.localName.orEmpty().lowercase() in CONTENT_TAGS) return true
        ancestor = ancestor.parentNode
    }
    return false
}

internal fun Element.hasTextBearingContentAncestor(): Boolean {
    var ancestor: Node? = parentNode
    while (ancestor is Element) {
        if (ancestor.localName.orEmpty().lowercase() in CONTENT_TAGS) {
            return !ancestor.textContent.isNullOrBlank()
        }
        ancestor = ancestor.parentNode
    }
    return false
}

internal fun Element.hasDescendantReadableBlock(): Boolean {
    val descendants = getElementsByTagNameNS("*", "*")
    for (index in 0 until descendants.length) {
        val descendant = descendants.item(index) as? Element ?: continue
        if (descendant === this) continue
        if (descendant.localName.orEmpty().lowercase() in CONTENT_TAGS + FALLBACK_CONTENT_TAGS) return true
    }
    return false
}



internal const val MAX_CSS_BYTES = 1024 * 1024
internal const val MAX_CSS_IMPORT_DEPTH = 4
internal val CONTENT_TAGS = setOf(
    "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "dt", "dd", "figcaption",
    "caption", "td", "th", "hr",
)
internal val FALLBACK_CONTENT_TAGS = setOf("div", "section", "article", "main", "aside")
internal val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val NON_CONTENT_TAGS = setOf("style", "script", "noscript", "rp")
private val INLINE_COLOR_TAGS = setOf(
    "a", "span", "font", "em", "i", "strong", "b", "mark", "u", "ins", "s", "strike", "del", "code",
    "kbd", "samp", "tt", "sup", "sub", "ruby", "rt", "abbr", "cite", "dfn", "var", "small", "big", "label",
)
private val SUPPORTED_CSS_PROPERTIES = setOf(
    "font", "font-family", "font-style", "font-variant", "font-variant-caps", "font-weight",
    "text-decoration", "text-decoration-line", "border-bottom", "border-bottom-style",
    "white-space", "display", "visibility", "vertical-align", "color",
    "-webkit-text-fill-color", "background", "background-color", "background-image", "background-size",
)
private val CASE_SENSITIVE_CSS_PROPERTIES = setOf("background", "background-image")
private val CSS_COMMENT = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
private val CSS_RULE = Regex("([^{}]+)\\{([^{}]*)\\}")
private val CSS_PSEUDO = Regex("::?[a-zA-Z-]+(?:\\([^)]*\\))?")
private val CSS_COMBINATOR = Regex("\\s*[>+~]\\s*|\\s+")
private val CSS_TAG = Regex("^[a-zA-Z][a-zA-Z0-9_-]*|^\\*")
private val CSS_ID = Regex("#([a-zA-Z0-9_-]+)")
private val CSS_CLASS = Regex("\\.([a-zA-Z0-9_-]+)")
private val CSS_ATTRIBUTE = Regex("\\[\\s*([a-zA-Z0-9_|:-]+)\\s*(?:([~|^$*]?=)\\s*([^\\]]+?))?\\s*]")
internal val CSS_IMPORT = Regex(
    "@import\\s+(?:url\\(\\s*)?[\"']?([^\"')\\s;]+)[\"']?\\s*\\)?[^;]*;",
    RegexOption.IGNORE_CASE,
)
private val CSS_COVER_VALUE = Regex("(?:^|\\s|/)cover(?:$|\\s)", RegexOption.IGNORE_CASE)
