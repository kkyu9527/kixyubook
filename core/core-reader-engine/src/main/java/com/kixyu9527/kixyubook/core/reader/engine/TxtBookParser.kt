package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.BookFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class TxtBookParser : BookParser {
    override val format = BookFormat.TXT

    override fun readMetadata(file: File, fallbackTitle: String): DocumentMetadata =
        inspectFrontMatter(file, fallbackTitle).metadata

    override suspend fun readChapters(file: File, emit: suspend (DocumentChapter) -> Unit) {
        val frontMatter = inspectFrontMatter(file, file.name)
        file.bufferedReader(frontMatter.charset).use { reader ->
            var title = "正文"
            var volumeTitle: String? = null
            var volumeIndex = -1
            var emitted = false
            var lineIndex = 0
            val paragraphs = mutableListOf<String>()
            suspend fun flush() {
                if (paragraphs.isEmpty()) return
                emit(
                    DocumentChapter(
                        title = title,
                        paragraphs = paragraphs.toList(),
                        volumeTitle = volumeTitle,
                        volumeIndex = volumeIndex.takeIf { it >= 0 },
                    ),
                )
                paragraphs.clear()
                emitted = true
            }
            while (true) {
                val rawLine = reader.readLine() ?: break
                val currentIndex = lineIndex++
                if (currentIndex in frontMatter.excludedLines) continue
                val line = rawLine.trim().removePrefix("\uFEFF").trim()
                if (line.isBlank()) continue
                when {
                    volumeTitleOrNull(line) != null -> {
                        flush()
                        volumeTitle = checkNotNull(volumeTitleOrNull(line))
                        volumeIndex++
                        title = volumeTitle
                    }
                    chapterTitleOrNull(line) != null -> {
                        flush()
                        // Volume boundaries still split content, but the volume name
                        // is not repeated in every chapter title shown to the reader.
                        title = checkNotNull(chapterTitleOrNull(line))
                    }
                    else -> paragraphs += line
                }
            }
            flush()
            if (!emitted) emit(DocumentChapter("正文", listOf("这本书没有可显示的文本。")))
        }
    }

    private fun inspectFrontMatter(file: File, fallbackTitle: String): TxtFrontMatter {
        val charset = detectCharset(file)
        val lines = file.bufferedReader(charset).use { reader ->
            buildList {
                repeat(MAX_FRONT_MATTER_LINES) {
                    val line = reader.readLine() ?: return@repeat
                    add(line.removePrefix("\uFEFF").trim())
                }
            }
        }
        val excluded = mutableSetOf<Int>()
        var title = fallbackTitle.substringBeforeLast('.').ifBlank { "未命名书籍" }
        var author = "未知作者"
        var description = ""
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (headingTitleOrNull(line) != null) break
            val titleMatch = TITLE_PATTERN.matchEntire(line)
            if (titleMatch != null) {
                title = titleMatch.groupValues[1].trim().ifBlank { title }
                excluded += index
                index++
                continue
            }
            val authorMatch = AUTHOR_PATTERN.matchEntire(line)
            if (authorMatch != null) {
                author = authorMatch.groupValues[1].trim().ifBlank { author }
                excluded += index
                index++
                continue
            }
            val descriptionMatch = DESCRIPTION_PATTERN.matchEntire(line)
            if (descriptionMatch != null) {
                excluded += index
                val inline = descriptionMatch.groupValues[1].trim()
                if (inline.isNotEmpty()) {
                    description = inline
                    index++
                } else {
                    val descriptionLines = mutableListOf<String>()
                    var cursor = index + 1
                    while (cursor < lines.size && descriptionLines.size < MAX_DESCRIPTION_LINES) {
                        val candidate = lines[cursor].trim()
                        if (candidate.isBlank() || headingTitleOrNull(candidate) != null ||
                            TITLE_PATTERN.matches(candidate) || AUTHOR_PATTERN.matches(candidate) || DESCRIPTION_PATTERN.matches(candidate)
                        ) break
                        descriptionLines += candidate
                        excluded += cursor
                        cursor++
                    }
                    description = descriptionLines.joinToString("\n")
                    index = cursor
                }
                continue
            }
            index++
        }

        val decoratedTitle = lines.take(12).mapIndexedNotNull { lineIndex, line ->
            DECORATED_TITLE_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()?.let { lineIndex to it }
        }.firstOrNull()
        if (decoratedTitle != null && lines.take(16).any(AUTHOR_PATTERN::matches)) {
            title = decoratedTitle.second.ifBlank { title }
            excluded += decoratedTitle.first
        }
        return TxtFrontMatter(
            charset = charset,
            metadata = DocumentMetadata(title = title, author = author, description = description),
            excludedLines = excluded,
        )
    }

    private fun chapterTitleOrNull(raw: String): String? {
        if (raw.length !in 2..MAX_CHAPTER_TITLE_LENGTH) return null
        val line = raw.trim().trim(*CHAPTER_DECORATIONS).trim()
        if (line.length !in 2..MAX_CHAPTER_TITLE_LENGTH) return null
        return line.takeIf {
            NUMBERED_CHAPTER.matches(it) || NAMED_CHAPTER.matches(it) || LATIN_CHAPTER.matches(it)
        }
    }

    private fun volumeTitleOrNull(raw: String): String? {
        if (raw.length !in 2..MAX_CHAPTER_TITLE_LENGTH) return null
        val line = raw.trim().trim(*CHAPTER_DECORATIONS).trim()
        if (line.length !in 2..MAX_CHAPTER_TITLE_LENGTH || SENTENCE_END.containsMatchIn(line)) return null
        return line.takeIf { NUMBERED_VOLUME.matches(it) || REVERSED_VOLUME.matches(it) || LATIN_VOLUME.matches(it) }
    }

    private fun headingTitleOrNull(raw: String): String? = volumeTitleOrNull(raw) ?: chapterTitleOrNull(raw)

    private fun detectCharset(file: File): Charset {
        val sample = file.inputStream().use { input ->
            val buffer = ByteArray(CHARSET_SAMPLE_BYTES)
            buffer.copyOf(input.read(buffer).coerceAtLeast(0))
        }
        if (sample.startsWith(0x00, 0x00, 0xFE, 0xFF)) return charset("UTF-32BE")
        if (sample.startsWith(0xFF, 0xFE, 0x00, 0x00)) return charset("UTF-32LE")
        if (sample.startsWith(0xEF, 0xBB, 0xBF)) return StandardCharsets.UTF_8
        if (sample.startsWith(0xFE, 0xFF)) return StandardCharsets.UTF_16BE
        if (sample.startsWith(0xFF, 0xFE)) return StandardCharsets.UTF_16LE

        val evenZeros = sample.indices.count { it % 2 == 0 && sample[it] == 0.toByte() }
        val oddZeros = sample.indices.count { it % 2 == 1 && sample[it] == 0.toByte() }
        if (oddZeros > sample.size / 6 && evenZeros * 3 < oddZeros) return StandardCharsets.UTF_16LE
        if (evenZeros > sample.size / 6 && oddZeros * 3 < evenZeros) return StandardCharsets.UTF_16BE
        // A fixed-size sample can end halfway through a UTF-8 character. Decode
        // it as a stream fragment so a valid UTF-8 book is not sent through the
        // much heavier legacy-encoding scorer just because its last sample byte
        // is an incomplete code point.
        decodeSample(sample, StandardCharsets.UTF_8)?.let { return StandardCharsets.UTF_8 }

        return LEGACY_CHARSETS.mapNotNull { candidate ->
            decodeSample(sample, candidate)?.let { decoded ->
                val chinesePreference = if (candidate.name().equals("GB18030", true)) GB18030_TIE_BREAKER else 0
                candidate to (readabilityScore(decoded) + chinesePreference)
            }
        }.maxByOrNull { it.second }?.first ?: charset("GB18030")
    }

    private fun readabilityScore(text: String): Int {
        var score = 0
        text.forEach { character ->
            score += when {
                character == '\uFFFD' || character == '\u0000' -> -120
                Character.isISOControl(character) && character !in "\r\n\t" -> -40
                character in COMMON_CJK -> 5
                Character.UnicodeScript.of(character.code) == Character.UnicodeScript.HAN -> 2
                character.isLetterOrDigit() -> 1
                character in "，。！？；：、“”‘’《》（）【】…—,.!?;:'\"()-" -> 1
                else -> 0
            }
        }
        text.lineSequence().take(160).forEach { line ->
            if (headingTitleOrNull(line.trim()) != null) score += 160
            if (TITLE_PATTERN.matches(line.trim()) || AUTHOR_PATTERN.matches(line.trim())) score += 80
        }
        COMMON_CHINESE_PHRASES.forEach { phrase -> score += text.countOccurrences(phrase) * 24 }
        if (MOJIBAKE_MARKERS.any(text::contains)) score -= 500
        return score
    }

    private fun decodeSample(bytes: ByteArray, charset: Charset): String? = runCatching {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate((bytes.size * decoder.maxCharsPerByte()).toInt() + 1)
        val result = decoder.decode(input, output, false)
        if (result.isError) result.throwException()
        output.flip()
        output.toString()
    }.getOrNull()

    private data class TxtFrontMatter(
        val charset: Charset,
        val metadata: DocumentMetadata,
        val excludedLines: Set<Int>,
    )


    private companion object {
        const val MAX_FRONT_MATTER_LINES = 256
        const val MAX_DESCRIPTION_LINES = 16
        const val MAX_CHAPTER_TITLE_LENGTH = 88
        const val CHARSET_SAMPLE_BYTES = 128 * 1024
        val TITLE_PATTERN = Regex("^\\s*(?:书名|書名|作品名|作品名称|作品名稱|小说名|小說名|小说名称|小說名稱)\\s*[：:]\\s*(.+?)\\s*$", RegexOption.IGNORE_CASE)
        val AUTHOR_PATTERN = Regex("^\\s*(?:作者|著者|作\\s*者)\\s*[：:]\\s*(.+?)\\s*$", RegexOption.IGNORE_CASE)
        val DESCRIPTION_PATTERN = Regex("^\\s*(?:内容简介|內容簡介|作品简介|作品簡介|小说简介|小說簡介|简介|簡介|文案)\\s*[：:]?\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE)
        val DECORATED_TITLE_PATTERN = Regex("^\\s*[《〈]([^》〉]{1,80})[》〉]\\s*$")
        val CHINESE_NUMBER = "0-9０-９零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟IVXLCDMivxlcdm"
        val NUMBERED_CHAPTER = Regex("^(?:正文\\s+)?第\\s*[$CHINESE_NUMBER]+\\s*[章节回话集幕](?:(?:\\s+|\\s*[-—:：、.．]\\s*).{1,48})?$", RegexOption.IGNORE_CASE)
        val NUMBERED_VOLUME = Regex("^第\\s*[$CHINESE_NUMBER]+\\s*[卷部篇](?:(?:\\s+|\\s*[-—:：、.．]\\s*)[^。！？!?]{1,48})?$", RegexOption.IGNORE_CASE)
        val REVERSED_VOLUME = Regex("^[卷部篇]\\s*[$CHINESE_NUMBER]+(?:(?:\\s+|\\s*[-—:：、.．]\\s*)[^。！？!?]{1,48})?$", RegexOption.IGNORE_CASE)
        val NAMED_CHAPTER = Regex("^(?:序章|楔子|引子|前言|序言|后记|尾声|终章|大结局|番外(?:篇)?)(?:(?:\\s+|\\s*[-—:：、.．]\\s*).{1,48})?$", RegexOption.IGNORE_CASE)
        val LATIN_CHAPTER = Regex("^chapter\\s+(?:[0-9]+|[ivxlcdm]+)(?:(?:\\s+|\\s*[-—:：.]\\s*).{1,48})?$", RegexOption.IGNORE_CASE)
        val LATIN_VOLUME = Regex("^(?:part|volume|book)\\s+(?:[0-9]+|[ivxlcdm]+)(?:(?:\\s+|\\s*[-—:：.]\\s*)[^.!?]{1,48})?$", RegexOption.IGNORE_CASE)
        val SENTENCE_END = Regex("[。！？!?]$")
        val CHAPTER_DECORATIONS = charArrayOf('=', '-', '*', '#', '_', '~', '—', '－', '【', '】', '[', ']', '「', '」', '『', '』', '　')
        val LEGACY_CHARSETS = listOf(
            // Simplified and Traditional Chinese
            "GB18030", "Big5", "Big5-HKSCS", "HZ-GB-2312", "ISO-2022-CN",
            // Japanese and Korean
            "Shift_JIS", "EUC-JP", "ISO-2022-JP", "EUC-KR", "x-windows-949", "ISO-2022-KR",
            // Cyrillic, Western European, Vietnamese and Thai
            "windows-1251", "KOI8-R", "ISO-8859-5", "windows-1252", "ISO-8859-1",
            "windows-1258", "windows-874",
        ).mapNotNull { name -> runCatching { Charset.forName(name) }.getOrNull() }
        val COMMON_CJK = "的一是不了人我在有他这为之大来以个中上们到说国和地也子时道出而要于就下得可你年生自会那后能对着事其里所去行过家十用发天如然作方成者多日都三小军二无同么经法当起与好看学进种将还分此心前面又定见只主没公从想实"
        val MOJIBAKE_MARKERS = listOf("锟斤拷", "烫烫烫", "Ã", "Â", "Ð", "�")
        val COMMON_CHINESE_PHRASES = listOf(
            "我们", "他们", "一个", "这个", "没有", "什么", "说道", "自己", "时候", "知道",
            "起来", "可以", "已经", "但是", "因为", "所以", "如果", "不是", "就是", "还有", "看着",
            "我們", "他們", "一個", "這個", "沒有", "什麼", "說道", "時候", "知道", "已經", "但是",
        )
        const val GB18030_TIE_BREAKER = 120
    }
}

private fun ByteArray.startsWith(vararg values: Int): Boolean =
    size >= values.size && values.indices.all { this[it] == values[it].toByte() }

private fun String.countOccurrences(needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var start = 0
    while (start <= length - needle.length) {
        val match = indexOf(needle, start)
        if (match < 0) break
        count++
        start = match + needle.length
    }
    return count
}
