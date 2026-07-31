package com.kixyu9527.kixyubook.update

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing

internal sealed interface ReleaseNoteBlock {
    data class Heading(val level: Int, val text: String) : ReleaseNoteBlock
    data class ListItem(val marker: String, val text: String) : ReleaseNoteBlock
    data class Quote(val text: String) : ReleaseNoteBlock
    data class Code(val text: String) : ReleaseNoteBlock
    data class Paragraph(val text: String) : ReleaseNoteBlock
    data object Divider : ReleaseNoteBlock
}

@Composable
internal fun ReleaseNotesMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth()
            .heightIn(max = KixyuSize.updateNotesMaxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNoteBlock.Heading -> Text(
                    text = renderInlineMarkdown(block.text, colors.primary, colors.surfaceVariant),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = colors.onSurface,
                )
                is ReleaseNoteBlock.ListItem -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = block.marker,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                    )
                    Text(
                        text = renderInlineMarkdown(block.text, colors.primary, colors.surfaceVariant),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                }
                is ReleaseNoteBlock.Quote -> Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                ) {
                    Box(
                        Modifier.width(3.dp).fillMaxHeight()
                            .background(colors.primary, RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = renderInlineMarkdown(block.text, colors.primary, colors.surfaceVariant),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = colors.onSurfaceVariant,
                    )
                }
                is ReleaseNoteBlock.Code -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surfaceContainerHigh,
                ) {
                    Text(
                        text = block.text,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                            .padding(KixyuSpacing.medium),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.onSurface,
                        softWrap = false,
                    )
                }
                is ReleaseNoteBlock.Paragraph -> Text(
                    text = renderInlineMarkdown(block.text, colors.primary, colors.surfaceVariant),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
                ReleaseNoteBlock.Divider -> HorizontalDivider(color = colors.outlineVariant)
            }
        }
    }
}

internal fun parseReleaseNotes(markdown: String): List<ReleaseNoteBlock> {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    val blocks = mutableListOf<ReleaseNoteBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.isBlank()) {
            index++
            continue
        }

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val fence = trimmed.take(3)
            val code = buildList {
                index++
                while (index < lines.size && !lines[index].trim().startsWith(fence)) {
                    add(lines[index])
                    index++
                }
                if (index < lines.size) index++
            }.joinToString("\n")
            blocks += ReleaseNoteBlock.Code(code)
            continue
        }

        val heading = HEADING_REGEX.matchEntire(trimmed)
        if (heading != null) {
            blocks += ReleaseNoteBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            index++
            continue
        }

        if (DIVIDER_REGEX.matches(trimmed)) {
            blocks += ReleaseNoteBlock.Divider
            index++
            continue
        }

        val unorderedItem = UNORDERED_LIST_REGEX.matchEntire(trimmed)
        if (unorderedItem != null) {
            val (marker, text) = taskMarker(unorderedItem.groupValues[1])
            blocks += ReleaseNoteBlock.ListItem(marker, text)
            index++
            continue
        }

        val orderedItem = ORDERED_LIST_REGEX.matchEntire(trimmed)
        if (orderedItem != null) {
            blocks += ReleaseNoteBlock.ListItem("${orderedItem.groupValues[1]}.", orderedItem.groupValues[2])
            index++
            continue
        }

        if (trimmed.startsWith('>')) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trim().startsWith('>')) {
                quoteLines += lines[index].trim().removePrefix(">").trimStart()
                index++
            }
            blocks += ReleaseNoteBlock.Quote(quoteLines.joinToString(" "))
            continue
        }

        val paragraph = mutableListOf<String>()
        while (index < lines.size) {
            val candidate = lines[index].trim()
            if (candidate.isBlank() || isBlockStart(candidate)) break
            paragraph += candidate
            index++
        }
        if (paragraph.isNotEmpty()) {
            blocks += ReleaseNoteBlock.Paragraph(paragraph.joinToString(" "))
        } else {
            index++
        }
    }
    return blocks
}

private fun isBlockStart(line: String): Boolean =
    line.startsWith("```") ||
        line.startsWith("~~~") ||
        line.startsWith('>') ||
        HEADING_REGEX.matches(line) ||
        DIVIDER_REGEX.matches(line) ||
        UNORDERED_LIST_REGEX.matches(line) ||
        ORDERED_LIST_REGEX.matches(line)

private fun taskMarker(text: String): Pair<String, String> = when {
    text.startsWith("[x] ", ignoreCase = true) -> "✓" to text.drop(4)
    text.startsWith("[ ] ") -> "○" to text.drop(4)
    else -> "•" to text
}

private fun renderInlineMarkdown(source: String, linkColor: Color, codeBackground: Color): AnnotatedString {
    val normalized = source
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
    return buildAnnotatedString {
        var cursor = 0
        INLINE_MARKDOWN_REGEX.findAll(normalized).forEach { match ->
            append(normalized.substring(cursor, match.range.first))
            val groups = match.groups
            when {
                groups[1] != null -> withStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ) { append(groups[1]!!.value) }
                groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(groups[3]!!.value)
                }
                groups[4] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(groups[4]!!.value)
                }
                groups[5] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(groups[5]!!.value)
                }
                groups[6] != null -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                ) { append(groups[6]!!.value) }
                groups[7] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(groups[7]!!.value)
                }
                groups[8] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(groups[8]!!.value)
                }
            }
            cursor = match.range.last + 1
        }
        append(normalized.substring(cursor))
    }
}

private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+?)\\s*#*$")
private val DIVIDER_REGEX = Regex("^(?:-{3,}|\\*{3,}|_{3,})$")
private val UNORDERED_LIST_REGEX = Regex("^[-+*]\\s+(.+)$")
private val ORDERED_LIST_REGEX = Regex("^(\\d+)[.)]\\s+(.+)$")
private val INLINE_MARKDOWN_REGEX = Regex(
    """!?(?:\[([^]]+)]\(([^)]+)\))|\*\*([^*]+)\*\*|__([^_]+)__|~~([^~]+)~~|`([^`]+)`|(?<!\*)\*([^*]+)\*(?!\*)|(?<!_)_([^_]+)_(?!_)""",
)
