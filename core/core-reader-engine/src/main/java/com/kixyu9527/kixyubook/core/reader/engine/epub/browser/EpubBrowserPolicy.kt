package com.kixyu9527.kixyubook.core.reader.engine.epub.browser

import com.kixyu9527.kixyubook.core.reader.engine.PackageDocument
import com.kixyu9527.kixyubook.core.reader.engine.normalizedReaderCssSource
import com.kixyu9527.kixyubook.core.reader.engine.findEntry
import com.kixyu9527.kixyubook.core.reader.engine.newEpubDocumentBuilder
import com.kixyu9527.kixyubook.core.reader.engine.openBoundedEntry
import java.util.zip.ZipFile

/** Conservative book-level routing: ordinary styled novels keep their native pagination path. */
internal fun epubBrowserReason(zip: ZipFile, pkg: PackageDocument): String? {
    if (pkg.fixedLayout) return "fixed-layout"
    if (pkg.manifest.values.any { "mathml" in it.properties }) return "mathml"
    var remaining = 4 * 1024 * 1024
    for (item in pkg.manifest.values) {
        if (!item.mediaType.equals("text/css", true)) continue
        val entry = zip.findEntry(item.path) ?: continue
        if (entry.size !in 1..minOf(remaining, 1024 * 1024).toLong()) continue
        remaining -= entry.size.toInt()
        val css = runCatching {
            zip.openBoundedEntry(entry, 1024 * 1024).bufferedReader().use { it.readText() }
        }.getOrNull() ?: continue
        cssBrowserReason(css)?.let { return it }
    }
    // Some publishers keep their layout in XHTML rather than a CSS manifest item. Sample a
    // bounded front-of-spine window once per package revision, never scan a novel on every turn.
    for (id in pkg.spine.take(3)) {
        val entry = pkg.manifest[id]?.path?.let(zip::findEntry) ?: continue
        if (entry.size !in 1..262_144) continue
        val document = runCatching {
            zip.openBoundedEntry(entry, 262_144).use { newEpubDocumentBuilder(true).parse(it) }
        }.getOrNull() ?: continue
        if (document.getElementsByTagNameNS("*", "math").length > 0) return "mathml"
        val styles = document.getElementsByTagNameNS("*", "style")
        for (i in 0 until styles.length) cssBrowserReason(styles.item(i).textContent)?.let { return it }
        for (tag in listOf("html", "body", "main", "article", "section")) {
            val elements = document.getElementsByTagNameNS("*", tag)
            for (i in 0 until elements.length) {
                val element = elements.item(i) as? org.w3c.dom.Element ?: continue
                cssBrowserReason("$tag {${element.getAttribute("style")}}")?.let { return it }
            }
        }
    }
    return null
}

private fun cssBrowserReason(css: String): String? {
    val base = normalizedReaderCssSource(css)
    if (Regex("writing-mode\\s*:\\s*vertical-(?:rl|lr)", RegexOption.IGNORE_CASE).containsMatchIn(base)) return "vertical-writing"
    if (Regex("display\\s*:\\s*(?:inline-)?grid\\b", RegexOption.IGNORE_CASE).containsMatchIn(base)) return "complex-layout"
    if (Regex("(?is)(?:body|main|article|section)[^{}]*\\{[^{}]*display\\s*:\\s*(?:inline-)?flex\\b").containsMatchIn(base)) return "complex-layout"
    return null
}

internal fun hasLegacyFixedLayout(zip: ZipFile): Boolean = listOf(
    "META-INF/com.apple.ibooks.display-options.xml", "META-INF/com.kobobooks.display-options.xml",
).any { path ->
    runCatching {
        val entry = zip.findEntry(path) ?: return@runCatching false
        val document = zip.openBoundedEntry(entry, 128 * 1024).use { newEpubDocumentBuilder(true).parse(it) }
        val nodes = document.getElementsByTagName("option")
        (0 until nodes.length).any { index ->
            val node = nodes.item(index) as? org.w3c.dom.Element
            node?.getAttribute("name") == "fixed-layout" && node.textContent.trim().equals("true", true)
        }
    }.getOrDefault(false)
}
