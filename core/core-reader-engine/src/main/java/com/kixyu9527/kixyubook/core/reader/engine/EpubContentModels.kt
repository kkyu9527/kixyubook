package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan

internal data class PackageDocument(
    val identifier: String,
    val title: String,
    val author: String,
    val description: String,
    val coverId: String?,
    val manifest: Map<String, ManifestItem>,
    val spine: List<String>,
)

internal data class PackageCacheKey(
    val path: String,
    val size: Long,
    val modifiedAt: Long,
)

internal data class ManifestItem(
    val path: String,
    val mediaType: String,
    val properties: Set<String>,
)

internal data class NavigationEntry(
    val title: String,
    val volumeTitle: String?,
    val volumeIndex: Int?,
)

internal sealed interface XhtmlBlock {
    data class Text(val value: StyledText) : XhtmlBlock
    data class Image(val image: DocumentImage) : XhtmlBlock
}

internal data class XhtmlContent(val heading: String?, val blocks: List<XhtmlBlock>)
internal data class StyledText(val text: String, val spans: List<ReaderTextSpan>)
internal data class CssRule(
    val selector: String,
    val declarations: Map<String, String>,
    val specificity: Int,
    val order: Int = 0,
    val sourcePath: String,
)
internal data class CssBackgroundImage(
    val reference: String,
    val basePath: String,
    val cropToFill: Boolean,
)
internal data class CssStylesheet(
    val rules: List<CssRule>,
    val rootVariables: Map<String, String>,
)
internal data class CssSourceCacheKey(
    val archivePath: String,
    val entryPath: String,
    val crc: Long,
    val size: Long,
)
internal data class ParsedCssSource(
    val imports: List<String>,
    val rules: List<CssRule>,
)

internal data class NormalizedInlineState(
    val styles: Set<ReaderInlineStyle> = emptySet(),
    val foreground: ReaderSemanticColor? = null,
    val background: ReaderSemanticColor? = null,
    val variables: Map<String, String> = emptyMap(),
    val preserveWhitespace: Boolean = false,
    val hidden: Boolean = false,
)

internal class StyledTextBuilder {
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
