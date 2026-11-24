package com.kixyu9527.kixyubook.core.reader.engine.epub.browser

import com.kixyu9527.kixyubook.core.reader.engine.findEntry
import com.kixyu9527.kixyubook.core.reader.engine.newEpubDocumentBuilder
import com.kixyu9527.kixyubook.core.reader.engine.openBoundedEntry
import org.w3c.dom.Element
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.StringWriter
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

const val EPUB_BROWSER_ORIGIN = "https://epub.kixyu.invalid"

data class EpubBrowserDocument(val html: String, val viewportWidth: Int, val viewportHeight: Int)
data class EpubBrowserResource(val mimeType: String, val stream: InputStream)

/** Keep browser-resource security and archive parsing independent of WebView/UI lifecycle. */
object EpubBrowserDocuments {
    fun url(path: String): String = "$EPUB_BROWSER_ORIGIN/" + path.split('/').joinToString("/") {
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    fun archivePath(url: String): String? = runCatching {
        val uri = java.net.URI(url)
        if (uri.scheme != "https" || uri.host != "epub.kixyu.invalid" || uri.port !in listOf(-1, 443) || uri.userInfo != null) return null
        val path = URLDecoder.decode(uri.rawPath.replace("+", "%2B"), "UTF-8").trimStart('/')
        if (path.split('/').any { it == ".." || it == "." } || '\u0000' in path || '\\' in path || ':' in path) return null
        path
    }.getOrNull()

    fun prepare(file: File, path: String): EpubBrowserDocument = ZipFile(file).use { zip ->
        val entry = zip.findEntry(path) ?: error("Missing EPUB document")
        val source = zip.openBoundedEntry(entry, 8 * 1024 * 1024).use { newEpubDocumentBuilder(true).parse(it) }
        val document = if (source.documentElement.localName == "svg") {
            newEpubDocumentBuilder().newDocument().apply {
                val html = createElement("html"); appendChild(html)
                html.appendChild(createElement("head"))
                html.appendChild(createElement("body")).appendChild(importNode(source.documentElement, true))
            }
        } else source
        val nodes = document.getElementsByTagName("*")
        require(nodes.length <= 100_000) { "EPUB browser document is too complex" }
        val elements = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        require(elements.none { generateSequence(it.parentNode) { node -> node.parentNode }.take(258).count() > 256 })
        var viewportWidth = 0
        var viewportHeight = 0
        for (element in elements) {
            val tag = element.localName?.lowercase() ?: element.tagName.lowercase()
            if (tag == "meta" && element.getAttribute("name").equals("viewport", true)) {
                val value = element.getAttribute("content")
                viewportWidth = Regex("width\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                viewportHeight = Regex("height\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                element.setAttribute("content", "width=device-width,initial-scale=1,maximum-scale=5")
            }
            if (tag in setOf("script", "iframe", "frame", "frameset", "object", "embed", "base", "foreignobject") ||
                tag == "meta" && element.hasAttribute("http-equiv") ||
                tag == "link" && !element.getAttribute("rel").equals("stylesheet", true)) {
                element.parentNode?.removeChild(element)
                continue
            }
            val attrs = element.attributes
            for (i in attrs.length - 1 downTo 0) {
                val attr = attrs.item(i)
                if (attr.nodeName.startsWith("on", true) || attr.nodeName.lowercase() in setOf("srcdoc", "formaction", "action") ||
                    attr.nodeValue.trim().startsWith("javascript:", true) || attr.nodeValue.trim().startsWith("vbscript:", true)) {
                    element.removeAttributeNode(attr as org.w3c.dom.Attr)
                }
            }
        }
        val html = document.documentElement
        val head = (document.getElementsByTagNameNS("*", "head").item(0) as? Element)
            ?: document.createElement("head").also { html.insertBefore(it, html.firstChild) }
        // Browser CSS is enabled, publisher JavaScript/frames/forms/network access is not.
        val csp = document.createElement("meta").apply {
            setAttribute("http-equiv", "Content-Security-Policy")
            setAttribute("content", "default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; script-src 'none'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'")
        }
        head.insertBefore(csp, head.firstChild)
        head.insertBefore(document.createElement("meta").apply {
            setAttribute("charset", "utf-8")
        }, head.firstChild)
        if (elements.none { it.localName == "meta" && it.getAttribute("name").equals("viewport", true) }) {
            head.appendChild(document.createElement("meta").apply {
                setAttribute("name", "viewport"); setAttribute("content", "width=device-width,initial-scale=1,maximum-scale=5")
            })
        }
        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.METHOD, "html")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        }.transform(DOMSource(document), StreamResult(writer))
        EpubBrowserDocument("<!doctype html>" + writer, viewportWidth.coerceIn(0, 100_000), viewportHeight.coerceIn(0, 100_000))
    }

    /** Every response is archive-scoped and bounded. Never delegate a blocked URL to the network. */
    fun resource(file: File, path: String): EpubBrowserResource? {
        val mime = mimeType(path) ?: return null
        val zip = ZipFile(file)
        try {
            val entry = zip.findEntry(path) ?: run { zip.close(); return null }
            val limit = if (mime == "text/css") 1024 * 1024 else 32 * 1024 * 1024
            val input = zip.openBoundedEntry(entry, limit)
            return EpubBrowserResource(mime, object : FilterInputStream(input) {
                override fun close() { try { super.close() } finally { zip.close() } }
            })
        } catch (error: Exception) { zip.close(); throw error }
    }

    private fun mimeType(path: String): String? = when (path.substringAfterLast('.').lowercase()) {
        "css" -> "text/css"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "svg" -> "image/svg+xml"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "ttc" -> "font/collection"
        "otf" -> "font/otf"
        else -> null // HTML navigation, scripts and arbitrary ZIP entries never become subresources.
    }
}
