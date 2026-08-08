package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
import com.kixyu9527.kixyubook.core.common.model.singleLineBookHeading
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal val EPUB_CSS_URL = Regex(
    "url\\(\\s*(?:\"([^\"]+)\"|'([^']+)'|([^)'\"\\s]+))\\s*\\)",
    RegexOption.IGNORE_CASE,
)

internal fun String.cssUrlReference(): String? = EPUB_CSS_URL.find(this)?.groupValues
    ?.drop(1)
    ?.firstOrNull(String::isNotBlank)
    ?.trim()

internal fun String.resolveCssVariables(variables: Map<String, String>): String {
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
internal fun String.toReaderSemanticColor(): ReaderSemanticColor? {
    val value = trim().lowercase().substringBefore("!important").trim()
    namedReaderColor(value)?.let { return it }
    parseHexColor(value)?.let { return classifyRgb(it.first, it.second, it.third) }
    parseRgbColor(value)?.let { return classifyRgb(it.first, it.second, it.third) }
    parseHueColor(value)?.let { (hue, chroma) ->
        return if (chroma < .12f) ReaderSemanticColor.NEUTRAL else classifyHue(hue)
    }
    return null
}

internal fun namedReaderColor(value: String): ReaderSemanticColor? = when {
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

internal fun parseHexColor(value: String): Triple<Float, Float, Float>? {
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

internal fun parseRgbColor(value: String): Triple<Float, Float, Float>? {
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
internal fun parseHueColor(value: String): Pair<Float, Float>? {
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

internal fun String.cssHueOrNull(): Float? {
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

internal fun classifyRgb(red: Float, green: Float, blue: Float): ReaderSemanticColor {
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

internal fun classifyHue(hue: Float): ReaderSemanticColor = when {
    hue < 20f || hue >= 330f -> ReaderSemanticColor.RED
    hue < 45f -> ReaderSemanticColor.ORANGE
    hue < 75f -> ReaderSemanticColor.YELLOW
    hue < 165f -> ReaderSemanticColor.GREEN
    hue < 200f -> ReaderSemanticColor.CYAN
    hue < 260f -> ReaderSemanticColor.BLUE
    hue < 300f -> ReaderSemanticColor.PURPLE
    else -> ReaderSemanticColor.MAGENTA
}

internal val CSS_VARIABLE = Regex("var\\(\\s*(--[a-zA-Z0-9_-]+)\\s*(?:,\\s*([^)]*))?\\)", RegexOption.IGNORE_CASE)
internal val CSS_RGB = Regex("rgba?\\((.*)\\)", RegexOption.IGNORE_CASE)
internal val CSS_COLOR = Regex("color\\((?:srgb|display-p3)\\s+(.*)\\)", RegexOption.IGNORE_CASE)
internal val CSS_HUE_COLOR = Regex("(hsl|hsla|hwb|lch|oklch)\\((.*)\\)", RegexOption.IGNORE_CASE)

internal const val MAX_IMAGE_HEADER_BYTES = 512 * 1024
internal const val MAX_NAVIGATION_TITLE_LENGTH = 160
internal const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"
internal val JPEG_START_OF_FRAME = setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)

internal fun resolveArchivePath(baseFile: String, rawReference: String): String {
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

internal fun String.normalizedArchivePath(): String = substringBefore('#').substringBefore('?')
    .replace('\\', '/')
    .trimStart('/')
    .lowercase()

internal fun String.normalizedNavigationTitle(): String = singleLineBookHeading()

internal fun String.fallbackChapterTitle(sourceIndex: Int): String {
    val stem = substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
    return stem.takeIf { it.length in 2..80 && it.any(Char::isLetter) } ?: "第 ${sourceIndex + 1} 章"
}

internal fun ZipFile.findEntry(path: String): ZipEntry? = getEntry(path) ?: entries().asSequence()
    .firstOrNull { it.name.equals(path, ignoreCase = true) }

internal fun mediaTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg", "svgz" -> "image/svg+xml"
    else -> "application/octet-stream"
}

internal fun ZipFile.readImageDimensions(entry: ZipEntry, mediaType: String): Pair<Int, Int> {
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

internal fun ByteArray.bigEndianInt(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 24) or ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or (this[offset + 3].toInt() and 0xFF)

internal fun ByteArray.littleEndianShort(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.jpegDimensions(): Pair<Int, Int> {
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

internal fun ByteArray.webpDimensions(): Pair<Int, Int> {
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

internal fun ByteArray.svgDimensions(): Pair<Int, Int> {
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

internal fun Int?.orZero() = this ?: 0

internal fun String.normalizedHeading(): String =
    trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')
