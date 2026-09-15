package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.model.FilenameSegmentRole

/**
 * Shared local metadata extraction. Format parsers only collect sources; the rules for which
 * candidate wins, how multiple authors are ordered, and how a description is cleaned live here so
 * TXT, EPUB DOM and EPUB streaming all agree.
 *
 * Nothing here tries to be clever with the body text: a candidate is only used when a source
 * explicitly marks it, and the caller decides separately which source lines may be excluded from
 * the body.
 */
object LocalMetadata {
    const val UNKNOWN_AUTHOR = "未知作者"

    // ---------------------------------------------------------------- candidates

    /**
     * Multiple sources are not multiple authors. The most trustworthy source tier wins and all of
     * its authors are kept in order; weaker tiers only fill in when the stronger one is empty.
     * A body `作者：刘慈欣` must never become `刘慈欣、黑暗森林` from a `三体-黑暗森林.txt` guess.
     */
    fun mergeAuthors(candidates: List<MetadataCandidate>): String {
        val cleaned = candidates.map { it.copy(value = it.value.trim()) }
            .filter { it.value.isNotEmpty() && !it.value.equals(UNKNOWN_AUTHOR, ignoreCase = true) }
        val best = cleaned.maxOfOrNull { it.source.confidence } ?: return UNKNOWN_AUTHOR
        return cleaned.asSequence()
            .filter { it.source.confidence == best }
            .map(MetadataCandidate::value)
            .distinct()
            .take(MAX_AUTHORS)
            .joinToString("、")
            .ifBlank { UNKNOWN_AUTHOR }
    }

    fun mergeTitles(candidates: List<MetadataCandidate>, fallback: String): String =
        candidates.sortedByDescending { it.source.confidence }
            .firstOrNull { it.value.isNotBlank() }
            ?.value
            ?.trim()
            .orEmpty()
            .ifBlank { fallback }

    // ---------------------------------------------------------------- filename

    /**
     * Filenames are an independent source (Legado does this too): 《书名》作者：某某.txt,
     * 《书名》（完本）作者：某某.txt, Author - 书名 patterns. Loose `书名-某某` is kept as a
     * low-confidence author only; it must never beat an explicit marker.
     */
    data class FilenameRule(
        val pattern: Regex,
        val titleGroup: String = "title",
        val authorGroup: String = "author",
    )

    data class FilenameRulesParse(val rules: List<FilenameRule>, val invalidLines: List<Int>)

    /** A sample file name split into its non-empty parts so the user can point at the author. */
    data class FilenameSampleSplit(val segments: List<String>, val separator: String)

    /** A rule generated from a sample: the regex plus the values the sample actually yields. */
    data class GeneratedFilenameRule(val pattern: String, val title: String, val author: String)

    enum class FilenameRuleError { SYNTAX, MISSING_GROUPS, NO_MATCH, EMPTY_TITLE, EMPTY_AUTHOR }

    sealed interface FilenameRuleValidation {
        data class Valid(val title: String, val author: String) : FilenameRuleValidation
        data class Invalid(val reason: FilenameRuleError) : FilenameRuleValidation
    }

    /** Common separators tried in order; whitespace is only a fallback to avoid splitting names. */
    private val SAMPLE_SEPARATORS = listOf("·", "—", "–", "-", "_", "|", "｜", "／", "/", "、", "，", ",")

    /** Separators that are actually usable for this sample; the UI offers them as a choice. */
    fun availableSampleSeparators(sample: String): List<String> {
        val withoutTags = sampleBaseName(sample) ?: return emptyList()
        return buildList {
            SAMPLE_SEPARATORS.forEach { if (it in withoutTags) add(it) }
            if (WHITESPACE.containsMatchIn(withoutTags)) add(" ")
        }
    }

    fun splitFilenameSample(sample: String, separator: String? = null): FilenameSampleSplit? {
        val withoutTags = sampleBaseName(sample) ?: return null
        val chosen = separator?.takeIf { it.isNotBlank() && it != " " && it in withoutTags }
            ?: separator?.takeIf { it == " " && WHITESPACE.containsMatchIn(withoutTags) }
            ?: SAMPLE_SEPARATORS.firstOrNull { it in withoutTags }
            ?: if (WHITESPACE.containsMatchIn(withoutTags)) " " else return null
        val segments = (if (chosen == " ") withoutTags.split(WHITESPACE) else withoutTags.split(chosen))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (segments.size < 2) return null
        return FilenameSampleSplit(segments, chosen)
    }

    private fun rawSampleBaseName(sample: String): String? {
        val base = sample.substringAfterLast('/').ifBlank { sample }.let(::stripKnownExtension)
        return base.trim().takeIf(String::isNotBlank)
    }

    private fun sampleBaseName(sample: String): String? {
        // A path (or SAF document id) must contribute its last segment, never the directory.
        val base = sample.substringAfterLast('/').ifBlank { sample }.let(::stripKnownExtension)
        val withoutTags = TAG_PATTERN.replace(base, " ").replace(Regex("\\s+"), " ").trim()
        return withoutTags.takeIf(String::isNotBlank)
    }

    /**
     * Turns "which part is the author" into a named-group regex. The author must be the first or
     * last part: a middle author would need a second title group and is intentionally unsupported
     * by the simple flow.
     */
    fun buildFilenameRule(
        sample: String,
        authorIndex: Int,
        separator: String? = null,
    ): GeneratedFilenameRule? {
        val split = splitFilenameSample(sample, separator) ?: return null
        if (authorIndex !in split.segments.indices) return null
        if (authorIndex != 0 && authorIndex != split.segments.lastIndex) return null
        val author = split.segments[authorIndex]
        val title = split.segments.filterIndexed { index, _ -> index != authorIndex }
            .joinToString(split.separator)
        if (title.isBlank() || author.isBlank()) return null
        val separator = if (split.separator == " ") WHITESPACE_PATTERN else escapeRegexLiteral(split.separator)
        val authorPart = if (split.separator == " " || split.separator.length > 1) {
            ".+?"
        } else {
            "[^${escapeRegexClass(split.separator)}]+?"
        }
        val pattern = if (authorIndex == 0) {
            "^\\s*(?<author>$authorPart)\\s*$separator\\s*(?<title>.+?)\\s*$"
        } else {
            "^\\s*(?<title>.+)\\s*$separator\\s*(?<author>$authorPart)\\s*$"
        }
        return GeneratedFilenameRule(pattern = pattern, title = title, author = author)
    }

    /** Runs one rule against a sample and reports what it extracts, or why it cannot be used. */
    fun validateFilenameRule(pattern: String, sample: String): FilenameRuleValidation {
        val regex = runCatching { Regex(pattern) }.getOrNull()
            ?: return FilenameRuleValidation.Invalid(FilenameRuleError.SYNTAX)
        val hasGroups = NAMED_TITLE_GROUP.containsMatchIn(pattern) || NAMED_AUTHOR_GROUP.containsMatchIn(pattern)
        if (!hasGroups) return FilenameRuleValidation.Invalid(FilenameRuleError.MISSING_GROUPS)
        // Advanced rules run against the same raw name the importer applies them to.
        val base = rawSampleBaseName(sample) ?: return FilenameRuleValidation.Invalid(FilenameRuleError.NO_MATCH)
        val match = regex.find(base) ?: return FilenameRuleValidation.Invalid(FilenameRuleError.NO_MATCH)
        val title = match.groupOrNull("title").orEmpty()
        val author = match.groupOrNull("author").orEmpty()
        if (title.isEmpty()) return FilenameRuleValidation.Invalid(FilenameRuleError.EMPTY_TITLE)
        if (author.isEmpty()) return FilenameRuleValidation.Invalid(FilenameRuleError.EMPTY_AUTHOR)
        return FilenameRuleValidation.Valid(title, author)
    }

    // ---------------------------------------------------------------- structured rules

    /** One token of the sample: bracketed markers, 《》 titles, or bare text between separators. */
    data class FilenameToken(
        val text: String,
        val bracketed: Boolean,
        val decorated: Boolean,
        val precededBySeparator: Boolean,
    )


    private val BRACKET_GROUP = Regex("([\\[【（(])(.*?)([\\]】）)])")

    fun tokenizeFilenameSample(sample: String, separator: String? = null): List<FilenameToken> {
        // Markers ([精校]、（完本）) are meaningful segments for a structured rule, so tokenization
        // must not run the legacy tag stripper.
        val base = rawSampleBaseName(sample) ?: return emptyList()
        val chosen = separator?.takeIf { it.isNotBlank() && it != " " && it in base }
            ?: separator?.takeIf { it == " " && WHITESPACE.containsMatchIn(base) }
            ?: SAMPLE_SEPARATORS.firstOrNull { it in base }
            ?: if (WHITESPACE.containsMatchIn(base)) " " else null
        val tokens = mutableListOf<FilenameToken>()
        var cursor = 0
        var afterSeparator = false
        while (cursor < base.length) {
            if (chosen != null) {
                val skips = when {
                    chosen == " " -> base[cursor].isWhitespace()
                    base.startsWith(chosen, cursor) -> true
                    else -> false
                }
                if (skips) {
                    cursor += if (chosen == " ") 1 else chosen.length
                    afterSeparator = true
                    continue
                }
            }
            val bracket = BRACKET_GROUP.find(base, cursor)?.takeIf { it.range.first == cursor }
            if (bracket != null) {
                val inner = bracket.groupValues[2].trim()
                if (inner.isNotEmpty()) {
                    tokens += FilenameToken(inner, bracketed = true, decorated = false, precededBySeparator = afterSeparator)
                }
                afterSeparator = false
                cursor = bracket.range.last + 1
                continue
            }
            val decorated = DECORATED_PATTERN.find(base, cursor)?.takeIf { it.range.first == cursor }
            if (decorated != null) {
                val inner = decorated.groupValues[1].trim()
                if (inner.isNotEmpty()) {
                    tokens += FilenameToken(inner, bracketed = false, decorated = true, precededBySeparator = afterSeparator)
                }
                afterSeparator = false
                cursor = decorated.range.last + 1
                continue
            }
            val nextStop = listOfNotNull(
                if (chosen != null && chosen != " ") base.indexOf(chosen, cursor + 1).takeIf { it >= 0 } else null,
                BRACKET_GROUP.find(base, cursor + 1)?.range?.first,
                DECORATED_PATTERN.find(base, cursor + 1)?.range?.first,
            ).minOrNull() ?: base.length
            var end = nextStop
            if (chosen == " ") {
                while (end > cursor && !base[end - 1].isWhitespace()) {
                    // keep the run bounded by the separator
                    break
                }
                var index = cursor
                while (index < end && !base[index].isWhitespace()) index++
                end = index
            }
            val raw = base.substring(cursor, end).trim()
            if (raw.isNotEmpty()) {
                tokens += FilenameToken(raw, bracketed = false, decorated = false, precededBySeparator = afterSeparator)
            }
            afterSeparator = false
            cursor = if (end > cursor) end else cursor + 1
        }
        return tokens
    }

    /** Confident automatic assignment; ambiguous samples leave every role unassigned. */
    fun autoAssignRoles(tokens: List<FilenameToken>): List<FilenameSegmentRole?> {
        if (tokens.isEmpty()) return emptyList()
        val roles = MutableList<FilenameSegmentRole?>(tokens.size) { null }
        tokens.forEachIndexed { index, token ->
            when {
                token.bracketed -> roles[index] = FilenameSegmentRole.IGNORE
                token.decorated -> roles[index] = FilenameSegmentRole.TITLE
            }
        }
        val bare = tokens.indices.filter { roles[it] == null }
        if (roles.contains(FilenameSegmentRole.TITLE) && bare.size == 1) {
            roles[bare.single()] = FilenameSegmentRole.AUTHOR
        }
        return roles
    }

    /** Generates the matching regex from a confirmed spec (markers optional, groups named). */
    fun buildPatternForSpec(spec: FilenameRuleSpec): String {
        val tokens = tokenizeFilenameSample(spec.sample, spec.separator)
        if (tokens.size != spec.roles.size || tokens.isEmpty()) return ""
        // Named groups must be unique: two authors or two status markers cannot compile.
        if (spec.roles.count { it == FilenameSegmentRole.AUTHOR } > 1) return ""
        if (spec.roles.count { it == FilenameSegmentRole.STATUS } > 1) return ""
        // null means "no separator between adjacent segments"; " " is a real separator and must
        // not be filtered out here.
        val separator = spec.separator?.takeIf { it.isNotEmpty() }
        val separatorPattern = when {
            separator == null -> null
            separator == " " -> WHITESPACE_PATTERN
            else -> escapeRegexLiteral(separator)
        }
        var titleCounter = 0
        return buildString {
            append("^\\s*")
            tokens.forEachIndexed { index, token ->
                if (index > 0 && token.precededBySeparator && separatorPattern != null) {
                    append("\\s*").append(separatorPattern).append("\\s*")
                } else if (index > 0) {
                    append("\\s*")
                }
                when (spec.roles[index]) {
                    FilenameSegmentRole.TITLE -> {
                        // A middle author leaves title parts on both sides; each gets its own group
                        // and they are joined again by [applyFilenameRuleSpec].
                        val group = if (titleCounter == 0) "title" else "title${titleCounter + 1}"
                        titleCounter++
                        append(
                            if (token.decorated) "《(?<$group>[^》]+)》" else "(?<$group>[^\\n]+?)",
                        )
                    }
                    FilenameSegmentRole.AUTHOR -> append(
                        if (token.bracketed) {
                            "(?:[\\[【（(](?<author>[^\\]】）)]*)[\\]】）)])?"
                        } else {
                            "(?<author>[^\\n]+?)"
                        },
                    )
                    // Bracketed status markers capture their inner text only, so the author
                    // before them is never truncated.
                    FilenameSegmentRole.STATUS -> append(
                        if (token.bracketed) {
                            "(?:[\\[【（(](?<status>[^\\]】）)]*)[\\]】）)])?"
                        } else {
                            "(?<status>[^\\n]+?)"
                        },
                    )
                    FilenameSegmentRole.IGNORE -> append(
                        if (token.bracketed) {
                            "(?:\\[[^\\]]*]|【[^】]*】|（[^）]*）|\\([^)]*\\))?"
                        } else {
                            "(?:.*?)"
                        },
                    )
                }
            }
            append("\\s*$")
        }
    }

    data class FilenameSpecMatch(val title: String, val author: String, val status: String)

    /** Built-in formats offered as one-tap presets; the sample is still fully editable. */
    data class FilenameRulePreset(
        val id: String,
        val sample: String,
        val separator: String?,
        val roles: List<FilenameSegmentRole>,
    )

    val FilenameRulePresets = listOf(
        FilenameRulePreset(
            "title_dash_author", "三体 - 刘慈欣.txt", "-",
            listOf(FilenameSegmentRole.TITLE, FilenameSegmentRole.AUTHOR),
        ),
        FilenameRulePreset(
            "author_dash_title", "刘慈欣 - 三体.txt", "-",
            listOf(FilenameSegmentRole.AUTHOR, FilenameSegmentRole.TITLE),
        ),
        FilenameRulePreset(
            "by", "三体 by 刘慈欣.txt", " ",
            listOf(FilenameSegmentRole.TITLE, FilenameSegmentRole.IGNORE, FilenameSegmentRole.AUTHOR),
        ),
        FilenameRulePreset(
            "bracketed_author", "三体（刘慈欣）.txt", null,
            listOf(FilenameSegmentRole.TITLE, FilenameSegmentRole.AUTHOR),
        ),
        FilenameRulePreset(
            "marker_title_author", "[精校]《三体》-刘慈欣（完本）.txt", "-",
            listOf(
                FilenameSegmentRole.IGNORE,
                FilenameSegmentRole.TITLE,
                FilenameSegmentRole.AUTHOR,
                FilenameSegmentRole.IGNORE,
            ),
        ),
    )

    /** Counts how many extra samples a final rule resolves, for the "N matched / M missed" line. */
    fun countSpecMatches(spec: FilenameRuleSpec, samples: List<String>): Pair<Int, Int> {
        val matched = samples.count { sample ->
            runCatching { applyFilenameRuleSpec(spec, sample) }.getOrNull() != null
        }
        return matched to (samples.size - matched)
    }

    /** Shared by the preview and the importer: one path, one result. */
    fun applyFilenameRuleSpec(spec: FilenameRuleSpec, fileName: String): FilenameSpecMatch? {
        val pattern = buildPatternForSpec(spec).takeIf { it.isNotBlank() } ?: return null
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return null
        // Marker segments are part of the structural rule, so the match must see them.
        val base = rawSampleBaseName(fileName) ?: return null
        val match = regex.find(base) ?: return null
        val titleCount = spec.roles.count { it == FilenameSegmentRole.TITLE }
        val titleParts = (1..titleCount).map { index ->
            val name = if (index == 1) "title" else "title$index"
            match.groupOrNull(name).orEmpty()
        }.filter(String::isNotEmpty)
        val title = titleParts.joinToString(spec.separator ?: " ")
        val author = match.groupOrNull("author").orEmpty()
        if (title.isEmpty() || author.isEmpty()) return null
        return FilenameSpecMatch(
            title = title,
            author = author,
            status = match.groupOrNull("status").orEmpty(),
        )
    }

    /**
     * Candidates from the first confirmed rule that matches the file name. They sit above the
     * built-in filename guesses (60) but below explicit body labels (95), so a user-confirmed
     * format beats heuristic file-name parsing without overriding `书名：`/`作者：`.
     */
    fun specCandidates(specs: List<FilenameRuleSpec>, sourceName: String): ExtractedMetadata {
        val active = specs.filter { it.enabled }
        if (active.isEmpty() || sourceName.isBlank()) {
            return ExtractedMetadata(emptyList(), emptyList(), emptyList(), emptySet())
        }
        val match = active.firstNotNullOfOrNull { spec ->
            runCatching { applyFilenameRuleSpec(spec, sourceName) }.getOrNull()
        } ?: return ExtractedMetadata(emptyList(), emptyList(), emptyList(), emptySet())
        return ExtractedMetadata(
            titles = listOf(MetadataCandidate(match.title, MetadataSource.FILENAME_STRUCTURED)),
            authors = listOf(MetadataCandidate(match.author, MetadataSource.FILENAME_STRUCTURED)),
            descriptions = emptyList(),
            excludedLines = emptySet(),
        )
    }

    /** Human-readable form of a stored rule, e.g. `《书名》-作者` for the rules list. */
    fun describeFilenameRule(pattern: String): String = pattern
        .replace(Regex("\\(\\?<title>[^)]*\\)"), "《书名》")
        .replace(Regex("\\(\\?<author>[^)]*\\)"), "作者")
        .replace(Regex("\\\\s\\+"), " ")
        .replace(Regex("\\\\s\\*"), "")
        .replace(Regex("[\\^$]"), "")
        .replace(Regex("\\\\."), ".")
        .replace("\\Q", "")
        .replace("\\E", "")
        .replace("\\", "")
        .trim()

    private fun MatchResult.groupOrNull(name: String): String? =
        runCatching { groups[name]?.value?.trim() }.getOrNull()

    private fun escapeRegexLiteral(value: String): String = buildString {
        value.forEach { ch ->
            if (ch in "\\.^$|?*+()[]{}") append('\\')
            append(ch)
        }
    }

    private fun escapeRegexClass(value: String): String = buildString {
        value.forEach { ch ->
            if (ch in "\\^]-") append('\\')
            append(ch)
        }
    }

    /**
     * Compiles user rule lines (1-based indices reported back for errors). A rule must compile and
     * contain a named `title` or `author` group, otherwise it would silently match nothing.
     */
    fun parseFilenameRules(lines: List<String>): FilenameRulesParse {
        val rules = mutableListOf<FilenameRule>()
        val invalid = mutableListOf<Int>()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            val pattern = runCatching { Regex(line) }.getOrNull()
            val hasNamedGroup = NAMED_TITLE_GROUP.containsMatchIn(line) || NAMED_AUTHOR_GROUP.containsMatchIn(line)
            if (pattern == null || !hasNamedGroup) {
                invalid += index + 1
            } else {
                rules += FilenameRule(pattern)
            }
        }
        return FilenameRulesParse(rules, invalid)
    }

    fun parseFileName(fileName: String, userRules: List<FilenameRule> = emptyList()): ExtractedMetadata {
        val base = fileName.substringAfterLast('/')
            .ifBlank { fileName }
            .let(::stripKnownExtension)
        val withoutTags = TAG_PATTERN.replace(base, " ").replace(Regex("\\s+"), " ").trim()
        val decorated = DECORATED_PATTERN.find(withoutTags)?.groupValues?.get(1)?.trim()
        val remainder = withoutTags
            .replace(DECORATED_PATTERN, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val titleCandidates = mutableListOf<MetadataCandidate>()
        decorated?.takeIf { it.isNotBlank() }?.let {
            titleCandidates += MetadataCandidate(it, MetadataSource.FILENAME_TITLE)
        }

        val authors = mutableListOf<MetadataCandidate>()
        // User-defined rules win over the built-ins; they are the only fully explicit source.
        userRules.forEach { rule ->
            rule.pattern.find(base)?.let { match ->
                match.groups[rule.authorGroup]?.value?.trim()?.takeIf(String::isNotBlank)?.let {
                    authors += MetadataCandidate(it, MetadataSource.FILENAME_AUTHOR_LABELED)
                }
                match.groups[rule.titleGroup]?.value?.trim()?.takeIf(String::isNotBlank)?.let {
                    titleCandidates += MetadataCandidate(it, MetadataSource.FILENAME_TITLE)
                }
            }
        }
        if (authors.isEmpty()) {
            BRACKET_AUTHOR_PREFIX.find(base)?.groupValues?.get(1)?.trim()
                ?.takeIf(::isPlausibleLooseAuthor)
                ?.let { authors += MetadataCandidate(it, MetadataSource.FILENAME_AUTHOR_LABELED) }
        }
        if (authors.isEmpty()) {
            BRACKETED_AUTHOR_SUFFIX.find(base)?.groupValues?.get(1)?.trim()
                ?.takeIf { isPlausibleLooseAuthor(it) && !TAG_WORD.matches(it) }
                ?.let { authors += MetadataCandidate(it, MetadataSource.FILENAME_AUTHOR_LABELED) }
        }
        LABELED_AUTHOR_PATTERN.findAll(remainder).forEach { match ->
            splitAuthorList(match.groupValues[1]).forEach {
                authors += MetadataCandidate(it, MetadataSource.FILENAME_AUTHOR_LABELED)
            }
        }
        if (authors.isEmpty()) {
            BY_AUTHOR_PATTERN.findAll(remainder).forEach { match ->
                splitAuthorList(match.groupValues[1]).firstOrNull()?.let {
                    authors += MetadataCandidate(it, MetadataSource.FILENAME_AUTHOR_LABELED)
                }
            }
        }
        if (authors.isEmpty()) {
            LOOSE_AUTHOR_PATTERN.find(remainder)?.groupValues?.get(1)?.trim()
                ?.takeIf(::isPlausibleLooseAuthor)
                ?.let { authors += MetadataCandidate(it, MetadataSource.FILENAME_AUTHOR_LOOSE) }
        }

        if (titleCandidates.isEmpty()) {
            // Remove the author markers before deriving a title; `书名 by 作者` must not become
            // the title. A loose `书名-某某` still falls back to its first segment.
            val titleBase = remainder
                .replace(LABELED_AUTHOR_PATTERN, " ")
                .replace(BY_AUTHOR_PATTERN, " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val cleaned = titleBase.split(LOOSE_AUTHOR_SEPARATOR).first()
                .trim('-', '_', '—')
                .trim()
            if (cleaned.isNotBlank()) {
                titleCandidates += MetadataCandidate(cleaned, MetadataSource.FILENAME_TITLE)
            }
        }
        return ExtractedMetadata(
            titles = titleCandidates,
            authors = authors,
            descriptions = emptyList(),
            excludedLines = emptySet(),
        )
    }

    private fun stripKnownExtension(name: String): String {
        val extension = name.substringAfterLast('.', "")
        val isAsciiExtension = extension.isNotEmpty() && extension.length <= 5 &&
            extension.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        return if (isAsciiExtension) name.substringBeforeLast('.') else name
    }

    private fun splitAuthorList(raw: String): List<String> {
        val tokens = mutableListOf<String>()
        raw.split(AUTHOR_LIST_SEPARATOR).forEach { part ->
            val cleaned = part.trim().trim('-', '—', '_').trim().replace(TRAILING_AUTHOR_MARKER, "").trim()
            if (cleaned.isEmpty()) return@forEach
            val whitespaceParts = cleaned.split(WHITESPACE).filter(String::isNotBlank)
            // `刘慈欣 韩松` is a list of CJK names; `John Smith` is one Latin name.
            if (whitespaceParts.size > 1 && whitespaceParts.all(::looksLikeCjkName)) {
                tokens += whitespaceParts
            } else {
                tokens += cleaned
            }
        }
        return tokens
            .filter { it.isNotEmpty() && !isNonAuthorRoleText(it) && !AUTHOR_LABEL_ONLY.matches(it) }
            .take(MAX_AUTHORS)
    }

    private fun looksLikeCjkName(value: String): Boolean =
        value.length in 2..4 &&
            value.none(Character::isLowerCase) &&
            value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    private fun isNonAuthorRoleText(value: String): Boolean =
        NON_AUTHOR_TEXT_PATTERN.containsMatchIn(value)

    /** A bare `书名-某某` segment is a weak author only when it cannot be a subtitle or chapter. */
    private fun isPlausibleLooseAuthor(value: String): Boolean =
        value.length in 2..20 &&
            !isNonAuthorRoleText(value) &&
            !AUTHOR_LABEL_ONLY.matches(value) &&
            !isChapterHeading(value) &&
            value.none { it in "。！？!?，,；;：:" }

    // ---------------------------------------------------------------- front matter lines

    /**
     * Collects author candidates and a paragraph description block from the front lines. Only lines
     * that are actually used are reported as excluded, so a failed recognition never removes body.
     */
    fun extractFrontMatter(
        lines: List<String>,
        scanLimit: Int = MAX_FRONT_MATTER_SCAN_LINES,
    ): FrontMatterMetadata {
        val authors = mutableListOf<MetadataCandidate>()
        val titles = mutableListOf<MetadataCandidate>()
        val excluded = linkedSetOf<Int>()
        var description = ""
        var descriptionHeadingIndex = -1
        var index = 0
        while (index < lines.size && index < scanLimit) {
            val line = lines[index]
            if (isChapterHeading(line)) break
            val authorMatch = AUTHOR_LINE_PATTERN.matchEntire(line)
            if (authorMatch != null) {
                splitAuthorList(authorMatch.groupValues[1]).forEach {
                    authors += MetadataCandidate(it, MetadataSource.TXT_AUTHOR_LABEL)
                }
                excluded += index
                index++
                continue
            }
            val titleMatch = TITLE_LINE_PATTERN.matchEntire(line)
            if (titleMatch != null) {
                titleMatch.groupValues[1].trim().takeIf(String::isNotBlank)?.let {
                    titles += MetadataCandidate(it, MetadataSource.TXT_TITLE_LABEL)
                }
                excluded += index
                index++
                continue
            }
            if (GENERIC_AUTHOR_ROLE_PATTERN.matches(line)) {
                excluded += index
                index++
                continue
            }
            if (descriptionHeadingIndex < 0 && DESCRIPTION_HEADING_PATTERN.matchEntire(line) != null) {
                descriptionHeadingIndex = index
                excluded += index
                index++
                continue
            }
            index++
        }

        if (descriptionHeadingIndex >= 0) {
            val inline = DESCRIPTION_HEADING_PATTERN.matchEntire(lines[descriptionHeadingIndex])
                ?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val collected = mutableListOf<Pair<Int, String>>()
            var blankRun = 0
            var cursor = descriptionHeadingIndex + 1
            if (inline.isNotEmpty()) collected += descriptionHeadingIndex to inline
            while (cursor < lines.size && cursor < scanLimit && collected.size < MAX_DESCRIPTION_LINES) {
                val candidate = lines[cursor].trim()
                if (candidate.isEmpty()) {
                    blankRun++
                    if (blankRun >= 2) break
                    cursor++
                    continue
                }
                blankRun = 0
                // Named headings (序章/楔子/前言/…) are chapters exactly like 第一章; the block
                // must end before them and their body must never be excluded.
                if (isDescriptionBoundary(candidate)) break
                collected += cursor to candidate
                cursor++
            }
            // Only confirm the block once content was actually collected; otherwise the heading
            // line stays in the body instead of being removed for nothing.
            if (collected.isNotEmpty()) {
                description = cleanDescription(collected.joinToString("\n") { it.second })
                collected.forEach { (index, _) -> excluded += index }
                excluded += descriptionHeadingIndex
            }
        }

        val decorated = lines.take(DECORATED_TITLE_SCAN_LINES).mapIndexedNotNull { lineIndex, line ->
            DECORATED_PATTERN.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.let {
                MetadataCandidate(it, MetadataSource.TXT_DECORATED_TITLE) to lineIndex
            }
        }.firstOrNull()
        if (decorated != null) {
            titles += decorated.first
            // A 《title》 line alone is only a title when the file also looks like a book header;
            // removing a decorated chapter heading from the body would be a real content loss.
            if (lines.take(DECORATED_TITLE_SCAN_LINES).any(AUTHOR_LINE_PATTERN::matches)) {
                excluded += decorated.second
            }
        }
        return FrontMatterMetadata(authors, titles, description, excluded)
    }

    private fun isDescriptionBoundary(line: String): Boolean =
        isChapterHeading(line) ||
            DESCRIPTION_HEADING_PATTERN.matchEntire(line) != null ||
            AUTHOR_LINE_PATTERN.matches(line) ||
            TITLE_LINE_PATTERN.matches(line) ||
            BOUNDARY_LINE_PATTERN.containsMatchIn(line)

    fun isDescriptionHeading(line: String): Boolean =
        DESCRIPTION_HEADING_PATTERN.matchEntire(line.trim()) != null

    /** Shared chapter/volume judgment so metadata boundaries match the body parser exactly. */
    fun isChapterHeading(line: String): Boolean = chapterHeading(line) != null || volumeHeading(line) != null

    fun chapterHeading(raw: String): String? {
        if (raw.length !in 2..MAX_HEADING_LENGTH) return null
        val line = raw.trim().trim(*HEADING_DECORATIONS).trim()
        if (line.length !in 2..MAX_HEADING_LENGTH) return null
        return line.takeIf {
            CHAPTER_PATTERN.matches(it) || NAMED_CHAPTER_PATTERN.matches(it) || LATIN_CHAPTER_PATTERN.matches(it)
        }
    }

    fun volumeHeading(raw: String): String? {
        if (raw.length !in 2..MAX_HEADING_LENGTH) return null
        val line = raw.trim().trim(*HEADING_DECORATIONS).trim()
        if (line.length !in 2..MAX_HEADING_LENGTH || SENTENCE_END.containsMatchIn(line)) return null
        return line.takeIf {
            VOLUME_PATTERN.matches(it) || REVERSED_VOLUME_PATTERN.matches(it) || LATIN_VOLUME_PATTERN.matches(it)
        }
    }

    // ---------------------------------------------------------------- descriptions

    /**
     * Strips markup while keeping paragraph breaks. Entity decoding covers named and numeric
     * references so host-side tests (and users) do not see `&amp;#233;` leftovers.
     */
    fun cleanDescription(raw: String): String {
        if (raw.isBlank()) return ""
        val withBreaks = raw
            .replace(Regex("(?is)<\\s*(script|style)[^>]*>.*?</\\s*\\1\\s*>"), "\n")
            .replace(Regex("(?i)<\\s*br\\s*/?\\s*>"), "\n")
            .replace(Regex("(?i)</\\s*(p|div|li|h[1-6]|tr|section|blockquote)\\s*>"), "\n")
            .replace(Regex("(?i)<\\s*(p|div|li|h[1-6]|tr|section|blockquote)[^>]*>"), "\n")
        val withoutTags = withBreaks.replace(Regex("<[^>]*>"), " ")
        val decoded = decodeEntities(withoutTags)
        val paragraphs = decoded
            .lineSequence()
            .map { it.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .fold(mutableListOf<String>()) { result, line ->
                if (result.lastOrNull() == line) result else result.also { it += line }
            }
        return paragraphs.joinToString("\n")
            .take(MAX_DESCRIPTION_CHARS)
            .trim()
    }

    fun mergeDescriptions(values: List<String>): String =
        values.asSequence()
            .map(::cleanDescription)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n\n")
            .take(MAX_DESCRIPTION_CHARS)
            .trim()

    private fun decodeEntities(value: String): String {
        if ('&' !in value) return value
        return ENTITY_PATTERN.replace(value) { match ->
            when (val body = match.groupValues[1]) {
                "nbsp", "ensp", "emsp", "thinsp" -> " "
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos", "#39" -> "'"
                "ldquo" -> "“"
                "rdquo" -> "”"
                "lsquo" -> "‘"
                "rsquo" -> "’"
                "hellip" -> "…"
                "mdash" -> "—"
                "ndash" -> "–"
                else -> when {
                    body.startsWith("#x", true) -> body.drop(2).toIntOrNull(16)?.let(::codePointToString) ?: match.value
                    body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePointToString) ?: match.value
                    else -> match.value
                }
            }
        }
    }

    private fun codePointToString(code: Int): String = runCatching {
        String(Character.toChars(code))
    }.getOrDefault(" ")

    // ---------------------------------------------------------------- EPUB creators

    data class EpubCreator(val name: String, val role: String?, val sortName: String = "")

    /**
     * Explicit `aut` wins and keeps document order; untagged creators are the next best source;
     * translators/editors/illustrators alone never become the author.
     */
    fun selectEpubAuthors(creators: List<EpubCreator>): List<String> {
        val named = creators.map { it.copy(name = it.name.trim()) }
            .filter { it.name.isNotEmpty() }
            .distinctBy(EpubCreator::name)
        val explicitAuthors = named.filter { explicitAuthorRole(it.role) }
        val untagged = named.filter { it.role.isNullOrBlank() }
        // Some publishers use non-MARC roles such as 作者/main; those are unknown, not forbidden.
        // Only roles that clearly describe a different contributor are excluded.
        val unknownRoles = named.filter { !explicitAuthorRole(it.role) && !isNonAuthorRole(it.role) }
        val chosen = when {
            explicitAuthors.isNotEmpty() -> explicitAuthors
            untagged.isNotEmpty() -> untagged
            unknownRoles.isNotEmpty() -> unknownRoles
            else -> emptyList()
        }
        // Calibre sorts contributor lists by file-as. Only reorder when the publisher declared it;
        // otherwise document order is the only meaningful one.
        val ordered = if (chosen.any { it.sortName.isNotBlank() }) {
            chosen.sortedBy { it.sortName.ifBlank { it.name }.lowercase() }
        } else {
            chosen
        }
        return ordered.map(EpubCreator::name).take(MAX_AUTHORS)
    }

    fun selectEpubDescriptions(descriptions: List<String>): List<String> =
        descriptions.map(::cleanDescription).filter { it.isNotEmpty() }.distinct()

    private fun explicitAuthorRole(role: String?): Boolean {
        val normalized = role.orEmpty().trim().lowercase()
        if (normalized.isEmpty()) return false
        return normalized.split(Regex("[\\s,;]+")).any { it in AUTHOR_ROLES }
    }

    fun isNonAuthorRole(role: String?): Boolean {
        val normalized = role.orEmpty().trim().lowercase()
        if (normalized.isEmpty()) return false
        return normalized.split(Regex("[\\s,;]+")).any { it in NON_AUTHOR_ROLES }
    }

    data class ExtractedMetadata(
        val titles: List<MetadataCandidate>,
        val authors: List<MetadataCandidate>,
        val descriptions: List<String>,
        val excludedLines: Set<Int>,
    )

    data class FrontMatterMetadata(
        val authors: List<MetadataCandidate>,
        val titles: List<MetadataCandidate>,
        val description: String,
        val excludedLines: Set<Int>,
    )

    data class MetadataCandidate(val value: String, val source: MetadataSource)

    enum class MetadataSource(val confidence: Int) {
        EPUB_ROLE(100),
        TXT_TITLE_LABEL(95),
        TXT_AUTHOR_LABEL(95),
        TXT_DECORATED_TITLE(80),
        EPUB_DEFAULT(70),
        FILENAME_STRUCTURED(85),
        FILENAME_TITLE(60),
        FILENAME_AUTHOR_LABELED(60),
        FILENAME_AUTHOR_LOOSE(20),
    }

    private val AUTHOR_ROLES = setOf("aut", "author")
    private val NON_AUTHOR_ROLES = setOf(
        "edt", "editor", "trl", "trans", "translator", "ill", "illustrator", "pht", "photographer",
        "aui", "pbl", "publisher", "prt", "printer", "ctb", "contributor", "ed", "comp", "composer",
        "nrt", "narrator", "red", "redactor", "rev", "reviewer",
    )

    private const val MAX_AUTHORS = 8
    private const val MAX_DESCRIPTION_LINES = 40
    private const val MAX_DESCRIPTION_CHARS = 4_000
    private const val MAX_FRONT_MATTER_SCAN_LINES = 256
    private const val MAX_HEADING_LENGTH = 88
    private const val DECORATED_TITLE_SCAN_LINES = 16

    private val TAG_PATTERN = Regex("[（(【\\[][^）)】\\]]{1,20}[）)】\\]]")
    private val DECORATED_PATTERN = Regex("[《〈]([^》〉]{1,120})[》〉]")
    private val LABELED_AUTHOR_PATTERN = Regex(
        "(?:作者|著者|作\\s*者|著)\\s*[：:]\\s*([^\\n]{1,80})",
        RegexOption.IGNORE_CASE,
    )
    private val BY_AUTHOR_PATTERN = Regex("(?:\\bby\\b|author)\\s*[：: ]\\s*([^\\n]{1,80})", RegexOption.IGNORE_CASE)
    private val LOOSE_AUTHOR_PATTERN = Regex("^[^\\n]{1,80}?\\s*[-—_]\\s*([^\\n-—_]{1,40})$")
    private val LOOSE_AUTHOR_SEPARATOR = Regex("\\s*[-—_]\\s*")
    private val AUTHOR_LIST_SEPARATOR = Regex("[、,，/／|｜]+")
    private val NON_AUTHOR_TEXT_PATTERN = Regex(
        "译者|譯者|编者|編者|绘者|繪者|校对|校對|插图|插圖|主编|主編|责任编辑|責任編輯|" +
            "翻译|翻譯|编译|編譯|\\btranslator\\b|\\beditor\\b|\\billustrator\\b",
        RegexOption.IGNORE_CASE,
    )

    private val AUTHOR_LINE_PATTERN = Regex(
        "^\\s*(?:(?:作者|著者|作\\s*者|著)\\s*[：:]|\\b(?:author|by)\\b\\s*[：:]?)\\s*(.+?)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val TITLE_LINE_PATTERN = Regex(
        "^\\s*(?:书名|書名|作品名|作品名称|作品名稱|小说名|小說名|小说名称|小說名稱)\\s*[：:]\\s*(.+?)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val GENERIC_AUTHOR_ROLE_PATTERN = Regex(
        "^\\s*(?:(?:作者|著者|作\\s*者|著)\\s*[：:]?|\\b(?:author|by)\\b\\s*[：:]?)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val DESCRIPTION_HEADING_PATTERN = Regex(
        "^\\s*(?:内容简介|內容簡介|作品简介|作品簡介|小说简介|小說簡介|故事简介|故事簡介|图书简介|圖書簡介|" +
            "内容介绍|內容介紹|内容提要|內容提要|故事梗概|简介|簡介|文案|导读|導讀|" +
            "synopsis|summary|description|あらすじ|前書き)(?:\\s*[：:]\\s*(.*))?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    /** Anchored to the line start: a synopsis may legitimately mention 版权 or 字数 mid-sentence. */
    private val BOUNDARY_LINE_PATTERN = Regex(
        "^\\s*(?:目录|目錄|contents|作者简介|作者簡介|关于作者|關於作者|版权|版權|copyright|" +
            "出版|出版社|责任编辑|責任編輯|ISBN|字数|字數|更新时间|更新時間|标签|標籤|分类|分類)" +
            "\\s*[：:]?.*$",
        RegexOption.IGNORE_CASE,
    )
    private val AUTHOR_LABEL_ONLY = Regex("^(?:作者|著者|作\\s*者|书名|書名|简介|簡介|文案|目录|目錄)$")
    private val NAMED_TITLE_GROUP = Regex("\\(\\?<title>")
    private val NAMED_AUTHOR_GROUP = Regex("\\(\\?<author>")
    private val TAG_WORD = Regex("^(?:完本|完结|全本|全集|精校|校对|连载|出版|番外|重置|补番|合集|套装|全\\d+[卷册部]?|第?\\d+[卷册部]?)$")
    private val WHITESPACE = Regex("[\\s　]+")
    private const val WHITESPACE_PATTERN = "[\\s　]+"
    private val BRACKET_AUTHOR_PREFIX = Regex("^\\s*[\\[【]([^\\]】]{2,20})[\\]】]")
    private val BRACKETED_AUTHOR_SUFFIX = Regex("[（(]([^）)]{2,20})[）)]\\s*$")
    private val TRAILING_AUTHOR_MARKER = Regex("\\s+(?:著者|编著|編著|著)$")
    private val ENTITY_PATTERN = Regex("&(#?[A-Za-z0-9]+);")
    private val CHAPTER_PATTERN = Regex(
        "^(?:正文\\s+)?第\\s*[0-9０-９零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟IVXLCDMivxlcdm]+\\s*[章节回话集幕](?:(?:\\s+|\\s*[-—:：、.．]\\s*).{1,48})?$",
        RegexOption.IGNORE_CASE,
    )
    private val VOLUME_PATTERN = Regex(
        "^第\\s*[0-9０-９零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟IVXLCDMivxlcdm]+\\s*[卷部篇](?:(?:\\s+|\\s*[-—:：、.．]\\s*)[^。！？!?]{1,48})?$",
        RegexOption.IGNORE_CASE,
    )
    private val REVERSED_VOLUME_PATTERN = Regex(
        "^[卷部篇]\\s*[0-9０-９零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟IVXLCDMivxlcdm]+(?:(?:\\s+|\\s*[-—:：、.．]\\s*)[^。！？!?]{1,48})?$",
        RegexOption.IGNORE_CASE,
    )
    private val NAMED_CHAPTER_PATTERN = Regex(
        "^(?:序章|楔子|引子|前言|序言|后记|尾声|终章|大结局|番外(?:篇)?)(?:(?:\\s+|\\s*[-—:：、.．]\\s*).{1,48})?$",
        RegexOption.IGNORE_CASE,
    )
    private val LATIN_VOLUME_PATTERN = Regex(
        "^(?:part|volume|book)\\s+(?:[0-9]+|[ivxlcdm]+)(?:(?:\\s+|\\s*[-—:：.]\\s*)[^.!?]{1,48})?$",
        RegexOption.IGNORE_CASE,
    )
    private val SENTENCE_END = Regex("[。！？!?]$")
    private val LATIN_CHAPTER_PATTERN = Regex(
        "^(?:chapter|part|volume|book)\\s+(?:[0-9]+|[ivxlcdm]+)(?:(?:\\s+|\\s*[-—:：.]\\s*).{1,48})?$",
        RegexOption.IGNORE_CASE,
    )
    private val HEADING_DECORATIONS = charArrayOf('=', '-', '*', '#', '_', '~', '—', '－', '【', '】', '[', ']', '「', '」', '『', '』', '　')
}
