package com.kixyu9527.kixyubook.core.reader.engine

import javax.xml.parsers.DocumentBuilderFactory

/** One XML security contract for packages, XHTML, navigation and vector illustrations. */
internal fun newEpubDocumentBuilder(lenient: Boolean = false) = DocumentBuilderFactory.newInstance().run {
    isNamespaceAware = true
    isExpandEntityReferences = false
    if (!lenient) runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
    runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
    newDocumentBuilder().also { builder ->
        builder.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
    }
}
