package com.kixyu9527.kixyubook.core.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG
import org.w3c.dom.Element
import java.io.StringWriter
import java.util.zip.ZipFile
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.min
import kotlin.math.sqrt

internal const val INLINE_SVG_FRAGMENT = "#kixyu-inline-svg-"
private val SVG_DRAWING_TAGS = setOf("path", "rect", "circle", "ellipse", "polygon", "polyline", "line", "text", "use")

/** Raster-only SVG wrappers already use the ordinary image path and must not be duplicated. */
internal fun Element.isVectorIllustration(): Boolean {
    val nodes = getElementsByTagNameNS("*", "*")
    return (0 until nodes.length).any { nodes.item(it).localName in SVG_DRAWING_TAGS } &&
        (0 until nodes.length).none { nodes.item(it).localName == "image" }
}

internal fun Element.svgXml(): String = StringWriter().also { writer ->
    TransformerFactory.newInstance().apply {
        runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "") }
    }.newTransformer().apply {
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
    }.transform(DOMSource(this), StreamResult(writer))
}.toString()

/** Sanitization happens before AndroidSVG sees any XML. No scripts, external assets or entities. */
internal fun sanitizedEpubSvg(zip: ZipFile, reference: String): String? {
    val path = reference.substringBefore(INLINE_SVG_FRAGMENT)
    val entry = zip.findEntry(path) ?: return null
    val document = zip.openBoundedEntry(entry, MAX_EPUB_XHTML_BYTES).use {
        newEpubDocumentBuilder(lenient = true).parse(it)
    }
    val root = if (INLINE_SVG_FRAGMENT in reference) {
        val index = reference.substringAfter(INLINE_SVG_FRAGMENT).toIntOrNull() ?: return null
        document.getElementsByTagNameNS("*", "svg").item(index) as? Element ?: return null
    } else document.documentElement
    if (root.localName != "svg") return null
    val nodes = root.getElementsByTagNameNS("*", "*")
    if (nodes.length > 20_000) return null
    val elements = listOf(root) + (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    if (elements.any { generateSequence(it.parentNode) { node -> node.parentNode }.take(66).count() > 64 }) return null
    val ids = elements.filter { it.hasAttribute("id") }.associateBy { it.getAttribute("id") }
    var referenceBudget = 20_000
    fun cyclic(element: Element, visiting: Set<Element>, depth: Int): Boolean {
        if (--referenceBudget < 0 || depth > 32 || element in visiting) return true
        val visited = visiting + element
        val children = element.childNodes
        if ((0 until children.length).mapNotNull { children.item(it) as? Element }
                .any { cyclic(it, visited, depth + 1) }) return true
        if (element.localName != "use") return false
        val href = element.getAttribute("href").ifBlank { element.getAttributeNS("http://www.w3.org/1999/xlink", "href") }
        return ids[href.removePrefix("#")]?.let { cyclic(it, visited, depth + 1) } == true
    }
    for (element in elements) {
        if (element.localName in setOf("script", "foreignObject", "animate", "animateTransform", "set")) {
            element.parentNode?.removeChild(element)
            continue
        }
        if (element.localName == "use" && cyclic(element, emptySet(), 0)) return null
        val attributes = element.attributes
        for (i in attributes.length - 1 downTo 0) {
            val attr = attributes.item(i)
            if (attr.nodeName.startsWith("on", true) ||
                attr.localName == "href" && !attr.nodeValue.startsWith('#')) {
                element.removeAttributeNode(attr as org.w3c.dom.Attr)
            } else if ("url(" in attr.nodeValue.lowercase()) {
                attr.nodeValue = attr.nodeValue.withoutExternalSvgUrls()
            }
        }
        if (element.localName == "style") {
            element.textContent = element.textContent.replace(CSS_IMPORT, "").withoutExternalSvgUrls()
        }
    }
    return root.svgXml()
}

private fun String.withoutExternalSvgUrls(): String = EPUB_CSS_URL.replace(this) { match ->
    if (match.value.cssUrlReference()?.startsWith('#') == true) match.value else "none"
}

internal fun renderEpubSvg(zip: ZipFile, reference: String, targetWidth: Int, targetHeight: Int): Bitmap? {
    val xml = sanitizedEpubSvg(zip, reference) ?: return null
    val svg = SVG.getFromString(xml)
    val ratio = svg.documentAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    var width = targetWidth.coerceIn(1, 8192).toFloat()
    var height = width / ratio
    if (height > targetHeight.coerceIn(1, 8192)) {
        height = targetHeight.coerceIn(1, 8192).toFloat()
        width = height * ratio
    }
    val scale = min(1f, sqrt((4f * 1024 * 1024) / (width * height)))
    val bitmap = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    svg.setDocumentWidth("100%")
    svg.setDocumentHeight("100%")
    svg.renderToCanvas(Canvas(bitmap))
    return bitmap
}
