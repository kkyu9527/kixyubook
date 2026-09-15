package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.reader.engine.LocalMetadata.EpubCreator
import kotlinx.coroutines.runBlocking
import com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.model.FilenameSegmentRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MetadataExtractionTest {
    @get:Rule val folder = TemporaryFolder()

    // ------------------------------------------------------------------ filename sources

    @Test fun fileNamePatternsFeedTitleAndAuthorCandidates() {
        val decorated = LocalMetadata.parseFileName("《遮天》作者：辰东.txt")
        assertEquals("遮天", LocalMetadata.mergeTitles(decorated.titles, "fallback"))
        assertEquals("辰东", LocalMetadata.mergeAuthors(decorated.authors))

        val finished = LocalMetadata.parseFileName("《遮天》（完本）作者：辰东.txt")
        assertEquals("辰东", LocalMetadata.mergeAuthors(finished.authors))

        val latin = LocalMetadata.parseFileName("遮天 by 辰东.epub")
        assertEquals("遮天", LocalMetadata.mergeTitles(latin.titles, "fallback"))
        assertEquals("辰东", LocalMetadata.mergeAuthors(latin.authors))

        val loose = LocalMetadata.parseFileName("遮天-辰东.txt")
        assertEquals("遮天", LocalMetadata.mergeTitles(loose.titles, "fallback"))
        assertEquals("辰东", LocalMetadata.mergeAuthors(loose.authors))

        val plain = LocalMetadata.parseFileName("遮天.txt")
        assertEquals("遮天", LocalMetadata.mergeTitles(plain.titles, "fallback"))
        assertEquals("未知作者", LocalMetadata.mergeAuthors(plain.authors))
    }

    // ------------------------------------------------------------------ EPUB creators

    @Test fun explicitAuthorRolesWinAndKeepDocumentOrder() {
        val creators = listOf(
            EpubCreator("译者甲", "trl"),
            EpubCreator("作者甲", "aut"),
            EpubCreator("作者乙", null),
            EpubCreator("作者丙", "aut"),
            EpubCreator("编者丁", "edt"),
        )

        assertEquals(listOf("作者甲", "作者丙"), LocalMetadata.selectEpubAuthors(creators))
        assertEquals(
            listOf("作者乙"),
            LocalMetadata.selectEpubAuthors(listOf(EpubCreator("译者甲", "trl"), EpubCreator("作者乙", null))),
        )
        assertEquals(
            "a translator alone must not become the author",
            emptyList<String>(),
            LocalMetadata.selectEpubAuthors(listOf(EpubCreator("译者甲", "trl"))),
        )
        // Non-MARC roles such as 作者/main are unknown, not forbidden.
        assertEquals(
            listOf("作者甲"),
            LocalMetadata.selectEpubAuthors(listOf(EpubCreator("作者甲", "作者"))),
        )
        assertEquals(
            listOf("作者甲"),
            LocalMetadata.selectEpubAuthors(listOf(EpubCreator("作者甲", "main"))),
        )
    }

    @Test fun declaredFileAsSortsAuthorsLikeCalibre() {
        assertEquals(
            listOf("蔡磊", "张伟"),
            LocalMetadata.selectEpubAuthors(
                listOf(
                    EpubCreator("张伟", "aut", sortName = "zhang wei"),
                    EpubCreator("蔡磊", "aut", sortName = "cai lei"),
                ),
            ),
        )
    }

    @Test fun whitespaceSeparatedCjkNamesSplitButLatinNamesDoNot() {
        val cjk = LocalMetadata.extractFrontMatter(listOf("作者：刘慈欣 韩松", "第一章 开始", "正文。"))
        assertEquals("刘慈欣、韩松", LocalMetadata.mergeAuthors(cjk.authors))

        val latin = LocalMetadata.extractFrontMatter(listOf("Author: John Smith", "第一章 开始", "正文。"))
        assertEquals("John Smith", LocalMetadata.mergeAuthors(latin.authors))
    }

    @Test fun bracketedAuthorPatternsWorkWithoutMistakingTagsForAuthors() {
        val prefixed = LocalMetadata.parseFileName("[辰东]遮天.txt")
        assertEquals("遮天", LocalMetadata.mergeTitles(prefixed.titles, "fallback"))
        assertEquals("辰东", LocalMetadata.mergeAuthors(prefixed.authors))

        val suffixed = LocalMetadata.parseFileName("遮天（辰东）.txt")
        assertEquals("遮天", LocalMetadata.mergeTitles(suffixed.titles, "fallback"))
        assertEquals("辰东", LocalMetadata.mergeAuthors(suffixed.authors))

        val finishedTag = LocalMetadata.parseFileName("遮天（完本）.txt")
        assertEquals("未知作者", LocalMetadata.mergeAuthors(finishedTag.authors))
    }

    @Test fun userDefinedFilenameRulesTakePrecedence() {
        val rule = LocalMetadata.FilenameRule(
            pattern = Regex("(?<author>.+?)的(?<title>.+)$"),
            titleGroup = "title",
            authorGroup = "author",
        )

        val parsed = LocalMetadata.parseFileName("辰东的遮天.txt", listOf(rule))

        assertEquals("遮天", LocalMetadata.mergeTitles(parsed.titles, "fallback"))
        assertEquals("辰东", LocalMetadata.mergeAuthors(parsed.authors))
    }

    @Test fun aSampleBuildsARuleFromTheAuthorPosition() {
        val authorLast = LocalMetadata.buildFilenameRule("遮天-辰东.txt", authorIndex = 1)!!
        assertEquals("遮天", authorLast.title)
        assertEquals("辰东", authorLast.author)
        assertEquals(
            LocalMetadata.FilenameRuleValidation.Valid("遮天", "辰东"),
            LocalMetadata.validateFilenameRule(authorLast.pattern, "遮天-辰东.txt"),
        )
        val applied = LocalMetadata.parseFileName(
            "遮天-辰东.txt",
            listOf(LocalMetadata.FilenameRule(Regex(authorLast.pattern))),
        )
        assertEquals("辰东", LocalMetadata.mergeAuthors(applied.authors))
        assertEquals("遮天", LocalMetadata.mergeTitles(applied.titles, "fallback"))

        val authorFirst = LocalMetadata.buildFilenameRule("辰东·遮天.txt", authorIndex = 0)!!
        assertEquals("遮天", authorFirst.title)
        assertEquals("辰东", authorFirst.author)
        assertEquals(
            LocalMetadata.FilenameRuleValidation.Valid("遮天", "辰东"),
            LocalMetadata.validateFilenameRule(authorFirst.pattern, "辰东·遮天.txt"),
        )
    }

    @Test fun aSeparatorCanBeChosenForMixedNames() {
        val sample = "斗破苍穹·异火篇-辰东.txt"
        assertEquals(
            listOf("斗破苍穹", "异火篇-辰东"),
            LocalMetadata.splitFilenameSample(sample)!!.segments,
        )
        val rule = LocalMetadata.buildFilenameRule(sample, authorIndex = 1, separator = "-")!!
        assertEquals("斗破苍穹·异火篇", rule.title)
        assertEquals("辰东", rule.author)
        assertEquals(
            LocalMetadata.FilenameRuleValidation.Valid("斗破苍穹·异火篇", "辰东"),
            LocalMetadata.validateFilenameRule(rule.pattern, sample),
        )
    }

    @Test fun aMiddleAuthorIsRejectedAndValidationExplainsWhy() {
        assertNull(LocalMetadata.buildFilenameRule("甲-乙-丙.txt", authorIndex = 1))
        assertNull(LocalMetadata.buildFilenameRule("没有分隔符.txt", authorIndex = 0))
        assertEquals(
            LocalMetadata.FilenameRuleValidation.Invalid(LocalMetadata.FilenameRuleError.SYNTAX),
            LocalMetadata.validateFilenameRule("(", "遮天-辰东.txt"),
        )
        assertEquals(
            LocalMetadata.FilenameRuleValidation.Invalid(LocalMetadata.FilenameRuleError.MISSING_GROUPS),
            LocalMetadata.validateFilenameRule("^(.*)-(.*)$", "遮天-辰东.txt"),
        )
        assertEquals(
            LocalMetadata.FilenameRuleValidation.Invalid(LocalMetadata.FilenameRuleError.NO_MATCH),
            LocalMetadata.validateFilenameRule("(?<title>.+)-(?<author>.+)", "遮天.txt"),
        )
        assertEquals(
            LocalMetadata.FilenameRuleValidation.Invalid(LocalMetadata.FilenameRuleError.EMPTY_AUTHOR),
            LocalMetadata.validateFilenameRule("(?<title>.+)(?<author>z*)", "遮天-辰东.txt"),
        )
    }

    @Test fun aBracketedSampleIsRecognizedAndItsMarkersIgnored() {
        val sample = "[精校]《三体》 - 刘慈欣（完本）.txt"
        val tokens = LocalMetadata.tokenizeFilenameSample(sample)
        assertEquals(listOf("精校", "三体", "刘慈欣", "完本"), tokens.map { it.text })
        val roles = LocalMetadata.autoAssignRoles(tokens)
        assertEquals(
            listOf(
                FilenameSegmentRole.IGNORE,
                FilenameSegmentRole.TITLE,
                FilenameSegmentRole.AUTHOR,
                FilenameSegmentRole.IGNORE,
            ),
            roles,
        )
        val spec = FilenameRuleSpec("r1", sample, "-", roles.filterNotNull())
        val match = LocalMetadata.applyFilenameRuleSpec(spec, sample)!!
        assertEquals("三体", match.title)
        assertEquals("刘慈欣", match.author)
        // The preview and the importer run this exact function, so another file of the same shape
        // resolves the same way.
        val other = LocalMetadata.applyFilenameRuleSpec(spec, "[校对]《三体》 - 刘慈欣（全集）.txt")!!
        assertEquals("三体", other.title)
        assertEquals("刘慈欣", other.author)
    }

    @Test fun anAmbiguousTwoPartNameIsNotGuessed() {
        val tokens = LocalMetadata.tokenizeFilenameSample("三体 - 黑暗森林.txt")
        assertEquals(listOf("三体", "黑暗森林"), tokens.map { it.text })
        assertEquals(listOf(null, null), LocalMetadata.autoAssignRoles(tokens))
    }

    @Test fun aMiddleAuthorKeepsEveryTitlePart() {
        val sample = "斗破苍穹-辰东-异火篇.txt"
        val spec = FilenameRuleSpec(
            id = "r2",
            sample = sample,
            separator = "-",
            roles = listOf(
                FilenameSegmentRole.TITLE,
                FilenameSegmentRole.AUTHOR,
                FilenameSegmentRole.TITLE,
            ),
        )
        val match = LocalMetadata.applyFilenameRuleSpec(spec, sample)!!
        assertEquals("斗破苍穹-异火篇", match.title)
        assertEquals("辰东", match.author)
    }

    @Test fun aConfirmedSpecBeatsBuiltInGuessesButNotBodyLabels() = runBlocking {
        val file = folder.newFile("辰东-遮天.txt").apply { writeText("第一章 开始\n正文。") }
        val spec = FilenameRuleSpec(
            id = "reversed",
            sample = "辰东-遮天.txt",
            separator = "-",
            roles = listOf(FilenameSegmentRole.AUTHOR, FilenameSegmentRole.TITLE),
        )

        val metadata = TxtBookParser().readMetadata(file, file.name, file.name, emptyList(), listOf(spec))
        assertEquals("遮天", metadata.title)
        assertEquals("辰东", metadata.author)

        val labeled = folder.newFile("labeled-reversed.txt").apply {
            writeText("书名：正文书名\n作者：正文作者\n第一章 开始\n正文。")
        }
        val fromBody = TxtBookParser().readMetadata(labeled, labeled.name, labeled.name, emptyList(), listOf(spec))
        assertEquals("正文书名", fromBody.title)
        assertEquals("正文作者", fromBody.author)
    }

    @Test fun aSpaceSeparatorKeepsItsBoundary() {
        val spec = FilenameRuleSpec(
            id = "space",
            sample = "三体 刘慈欣.txt",
            separator = " ",
            roles = listOf(FilenameSegmentRole.TITLE, FilenameSegmentRole.AUTHOR),
        )

        val match = LocalMetadata.applyFilenameRuleSpec(spec, "三体 刘慈欣.txt")!!

        assertEquals("三体", match.title)
        assertEquals("刘慈欣", match.author)
    }

    @Test fun aBracketedStatusNeverStealsTheAuthor() {
        val sample = "《三体》-刘慈欣（完本）.txt"
        val spec = FilenameRuleSpec(
            id = "status",
            sample = sample,
            separator = "-",
            roles = listOf(
                FilenameSegmentRole.TITLE,
                FilenameSegmentRole.AUTHOR,
                FilenameSegmentRole.STATUS,
            ),
        )

        val match = LocalMetadata.applyFilenameRuleSpec(spec, sample)!!

        assertEquals("三体", match.title)
        assertEquals("刘慈欣", match.author)
        assertEquals("完本", match.status)
    }

    @Test fun everyPresetResolvesItsOwnSample() {
        LocalMetadata.FilenameRulePresets.forEach { preset ->
            val spec = FilenameRuleSpec(
                id = preset.id,
                sample = preset.sample,
                separator = preset.separator,
                roles = preset.roles,
            )
            val match = LocalMetadata.applyFilenameRuleSpec(spec, preset.sample)
            assertTrue("preset ${preset.id} must resolve its sample", match != null)
            assertFalse("preset ${preset.id} must extract a non-empty title", match!!.title.isBlank())
            assertFalse("preset ${preset.id} must extract a non-empty author", match.author.isBlank())
        }
    }

    @Test fun disabledSpecsAreIgnoredAndMatchCountsReportBothOutcomes() {
        val spec = FilenameRuleSpec(
            id = "s",
            sample = "三体-刘慈欣.txt",
            separator = "-",
            roles = listOf(FilenameSegmentRole.TITLE, FilenameSegmentRole.AUTHOR),
            enabled = false,
        )
        assertEquals(
            0,
            LocalMetadata.specCandidates(listOf(spec), "三体-刘慈欣.txt").authors.size,
        )
        val enabled = spec.copy(enabled = true)
        assertEquals(1, LocalMetadata.specCandidates(listOf(enabled), "三体-刘慈欣.txt").authors.size)
        assertEquals(2 to 1, LocalMetadata.countSpecMatches(enabled, listOf("三体-刘慈欣.txt", "遮天-辰东.txt", "无分隔符")))
    }

    @Test fun aPathSampleUsesTheFileNameNotTheDirectory() {
        val tokens = LocalMetadata.tokenizeFilenameSample("content://provider/文档/遮天-辰东.txt")
        assertEquals(listOf("遮天", "辰东"), tokens.map { it.text })
    }

    @Test fun aStoredRuleHasAReadableDescription() {
        val rule = LocalMetadata.buildFilenameRule("遮天-辰东.txt", authorIndex = 1)!!
        assertEquals("《书名》-作者", LocalMetadata.describeFilenameRule(rule.pattern))
    }

    @Test fun userRuleLinesAreValidatedWithLineNumbers() {
        val parsed = LocalMetadata.parseFilenameRules(
            listOf(
                "(?<author>.+?)的(?<title>.+)",
                "(",
                "没有命名分组",
                "    ",
                "(?<author>.+?)-(?<title>.+)",
            ),
        )

        assertEquals(2, parsed.rules.size)
        assertEquals(listOf(2, 3), parsed.invalidLines)
    }

    @Test fun aMetadataLabelCannotBecomeALooseAuthor() {
        assertEquals(
            "未知作者",
            LocalMetadata.mergeAuthors(LocalMetadata.parseFileName("斗破苍穹-作者.txt").authors),
        )
    }

    @Test fun aTrailingAuthorMarkerIsStripped() {
        val front = LocalMetadata.extractFrontMatter(
            listOf("作者：刘慈欣 著", "第一章 开始", "正文。"),
        )

        assertEquals("刘慈欣", LocalMetadata.mergeAuthors(front.authors))
    }

    @Test fun aSynopsisMentioningCopyrightMidSentenceDoesNotEndTheBlock() {
        val front = LocalMetadata.extractFrontMatter(
            listOf(
                "内容简介",
                "本书版权归作者所有，字数很多。",
                "第一章 开始",
                "正文。",
            ),
        )

        assertEquals("本书版权归作者所有，字数很多。", front.description)
    }

    @Test fun reparseWithoutTheOriginalFileNameDoesNotInventAnAuthor() = runBlocking {
        val source = folder.newFile("reparse.txt").apply { writeText("第一章 开始\n正文。") }

        val metadata = TxtBookParser().readMetadata(source, "斗破苍穹-异火篇", "")

        assertEquals("斗破苍穹-异火篇", metadata.title)
        assertEquals("未知作者", metadata.author)
    }

    // ------------------------------------------------------------------ descriptions

    @Test fun descriptionBlockAllowsParagraphsAndStopsAtChapterHeadings() {
        val lines = listOf(
            "内容简介",
            "第一段。",
            "",
            "第二段。",
            "",
            "",
            "第一章 开始",
            "正文。",
        )

        val front = LocalMetadata.extractFrontMatter(lines)

        assertEquals("第一段。\n第二段。", front.description)
        assertTrue(front.excludedLines.containsAll(listOf(0, 1, 3)))
        assertFalse("the chapter heading must not be excluded", 6 in front.excludedLines)
        assertFalse("body text must not be excluded", 7 in front.excludedLines)
    }

    @Test fun inlineDescriptionHeadingKeepsTheSameLineContent() {
        val front = LocalMetadata.extractFrontMatter(
            listOf(
                "内容简介：这是一行简介",
                "第二章 开始",
                "正文。",
            ),
        )

        assertEquals("这是一行简介", front.description)
        assertTrue(0 in front.excludedLines)
        assertFalse(1 in front.excludedLines)
    }

    @Test fun cleanDescriptionStripsMarkupAndKeepsParagraphs() {
        val html = "<p>第一段 &amp; 内容</p><p>第二段<br/>换行</p><script>bad()</script><style>.x{}</style>"

        assertEquals("第一段 & 内容\n第二段\n换行", LocalMetadata.cleanDescription(html))
    }

    @Test fun aPrefaceHeadingEndsTheDescriptionBlockInsteadOfBeingSwallowed() = runBlocking {
        val source = """内容简介

序章
序章正文。
第一章 开始
正文一。"""
        val file = folder.newFile("preface.txt").apply { writeText(source) }
        val parser = TxtBookParser()

        val metadata = parser.readMetadata(file, file.name)
        val chapters = mutableListOf<DocumentChapter>()
        parser.readChapters(file, chapters::add)

        assertEquals("", metadata.description)
        assertTrue(
            "the preface chapter and its body must stay readable",
            chapters.any { it.title == "序章" && it.paragraphs.any { text -> text.contains("序章正文。") } },
        )
    }

    @Test fun authorsMergeByConfidenceInsteadOfConcatenatingSources() {
        val candidates = listOf(
            LocalMetadata.MetadataCandidate("刘慈欣", LocalMetadata.MetadataSource.TXT_AUTHOR_LABEL),
            LocalMetadata.MetadataCandidate("黑暗森林", LocalMetadata.MetadataSource.FILENAME_AUTHOR_LOOSE),
        )

        assertEquals("刘慈欣", LocalMetadata.mergeAuthors(candidates))
        assertEquals(
            "the weaker source is the fallback only when nothing stronger exists",
            "黑暗森林",
            LocalMetadata.mergeAuthors(
                listOf(LocalMetadata.MetadataCandidate("黑暗森林", LocalMetadata.MetadataSource.FILENAME_AUTHOR_LOOSE)),
            ),
        )
    }

    @Test fun aFileNameAuthorDoesNotJoinABodyAuthor() = runBlocking {
        val source = """作者：刘慈欣
第一章 开始
正文。"""
        val file = folder.newFile("三体-黑暗森林.txt").apply { writeText(source) }

        val metadata = TxtBookParser().readMetadata(file, file.name)

        assertEquals("刘慈欣", metadata.author)
    }

    // ------------------------------------------------------------------ TXT end to end

    @Test fun txtMetadataMergesFileNameAndHeaderAuthors() = runBlocking {
        val source = """作者：甲、乙
内容简介
第一段简介。

第二段简介。

第一章 开始
正文一。
第二章 继续
正文二。"""
        val file = folder.newFile("《长夜》作者：丙.txt").apply { writeText(source) }
        val parser = TxtBookParser()

        val metadata = parser.readMetadata(file, file.name)
        val chapters = mutableListOf<DocumentChapter>()
        parser.readChapters(file, chapters::add)

        assertEquals("长夜", metadata.title)
        // The body's labeled author is more trustworthy than the file name; sources are not merged.
        assertEquals("甲、乙", metadata.author)
        assertEquals("第一段简介。\n第二段简介。", metadata.description)
        assertEquals("正文一。", chapters.first().paragraphs.single())
        assertFalse(chapters.flatMap { it.paragraphs }.any { it.contains("简介") })
    }

    @Test fun txtWithoutMetadataLabelsKeepsTheWholeBody() = runBlocking {
        val source = """第一章 开始
简介这个词出现在正文里。
第二章 继续
正文二。"""
        val file = folder.newFile("普通小说.txt").apply { writeText(source) }

        val metadata = TxtBookParser().readMetadata(file, file.name)
        val chapters = mutableListOf<DocumentChapter>()
        TxtBookParser().readChapters(file, chapters::add)

        assertEquals("", metadata.description)
        assertTrue(chapters.flatMap { it.paragraphs }.any { it.contains("简介这个词出现在正文里。") })
    }

    // ------------------------------------------------------------------ EPUB end to end

    @Test fun epubReadsEveryCreatorRoleAndEveryDescription() {
        val epub = folder.newFile("roles.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>角色测试</dc:title>
                    <dc:creator id="p1">译者甲</dc:creator>
                    <dc:creator id="p2">作者甲</dc:creator>
                    <dc:creator id="p3">作者乙</dc:creator>
                    <meta refines="#p1" property="role">trl</meta>
                    <meta refines="#p2" property="role">aut</meta>
                    <meta refines="#p3" property="role">aut</meta>
                    <dc:description>&lt;p&gt;简介一 &amp;amp; 内容&lt;/p&gt;</dc:description>
                    <dc:description>简介二</dc:description>
                </metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>正文。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("作者甲、作者乙", metadata.author)
        assertEquals("简介一 & 内容\n\n简介二", metadata.description)
    }

    @Test fun epubUsesTheMainTitleTypeInsteadOfTheFirstTitleElement() {
        val epub = folder.newFile("title-type.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title id="t1">一个副标题</dc:title>
                    <dc:title id="t2">真正的主标题</dc:title>
                    <meta refines="#t1" property="title-type">subtitle</meta>
                    <meta refines="#t2" property="title-type">main</meta>
                </metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>正文。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("真正的主标题", metadata.title)
    }

    @Test fun epubFallsBackToANavigationIntroPageNotTheFirstChapter() {
        val epub = folder.newFile("intro.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>简介页测试</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/nav.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="intro.xhtml">内容简介</a></li><li><a href="c1.xhtml">第一章</a></li></ol></nav></body></html>""",
            )
            zip.textEntry(
                "OPS/intro.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>内容简介</h1><p>这是简介页。</p><p>第二段。</p></body></html>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>第一章正文不能当简介。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("这是简介页。\n第二段。", metadata.description)
    }

    @Test fun epubIntroStopsBeforeAChapterInTheSameDocument() {
        val epub = folder.newFile("same-doc.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>同文件简介</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/nav.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">内容简介</a></li></ol></nav></body></html>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>内容简介</h1><p>简介第一段。</p><h1>第一章 开始</h1><p>第一章正文不能当简介。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("简介第一段。", metadata.description)
    }

    @Test fun epubIntroFragmentAnchorsTheDescriptionStart() {
        val epub = folder.newFile("fragment.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>锚点简介</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/nav.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="c1.xhtml#blurb">内容简介</a></li></ol></nav></body></html>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>作者的话</h1><p>锚点前的内容不应出现。</p><div id="blurb"><h2>内容简介</h2><p>锚点后的简介。</p></div><h1>第一章 开始</h1><p>第一章正文不能当简介。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("锚点后的简介。", metadata.description)
    }

    @Test fun epubWithoutAnyIntroPageLeavesTheDescriptionEmpty() {
        val epub = folder.newFile("no-intro.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>无简介</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>第一章正文不能当简介。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("", metadata.description)
    }

    @Test fun streamedPackageStillResolvesCreatorRoles() {
        val epub = folder.newFile("streamed-roles.epub")
        val padding = "p".repeat(MAX_EPUB_XML_BYTES + 1)
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title id="t1">流式副标题</dc:title>
                    <dc:title id="t2">流式角色</dc:title>
                    <dc:creator id="p1">译者甲</dc:creator>
                    <dc:creator id="p2">作者甲</dc:creator>
                    <meta refines="#t1" property="title-type">subtitle</meta>
                    <meta refines="#t2" property="title-type">main</meta>
                    <meta refines="#p1" property="role">trl</meta>
                    <meta refines="#p2" property="role">aut</meta>
                    <dc:description>$padding</dc:description>
                </metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>正文。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("流式角色", metadata.title)
        assertEquals("作者甲", metadata.author)
    }

    @Test fun epubCreatorFileAsOrdersAuthorsAndCalibreFieldsAreRead() {
        val epub = folder.newFile("calibre.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf" xmlns:opf="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>排序测试</dc:title>
                    <dc:creator opf:file-as="zhang wei" opf:role="aut">张伟</dc:creator>
                    <dc:creator opf:file-as="cai lei" opf:role="aut">蔡磊</dc:creator>
                    <meta name="calibre:title_sort" content="paixu ceshi"/>
                    <meta name="calibre:series" content="测试系列"/>
                    <meta name="calibre:series_index" content="3.5"/>
                </metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>正文。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("蔡磊、张伟", metadata.author)
        assertEquals("paixu ceshi", metadata.titleSort)
        assertEquals("测试系列", metadata.seriesName)
        assertEquals(3.5, metadata.seriesIndex!!, 0.0)
    }

    @Test fun aDoctypeIntroPageStillFeedsTheDescription() {
        val epub = folder.newFile("doctype-intro.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>DOCTYPE 简介</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/nav.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="intro.xhtml">内容简介</a></li></ol></nav></body></html>""",
            )
            zip.textEntry(
                "OPS/intro.xhtml",
                """<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><body><h1>内容简介</h1><p>DOCTYPE 页面里的简介。</p></body></html>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>正文。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("DOCTYPE 页面里的简介。", metadata.description)
    }

    @Test fun epubGuideTitlePageFeedsTheDescription() {
        val epub = folder.newFile("guide.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>guide 简介</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine><guide><reference type="title-page" href="intro.xhtml"/></guide></package>""",
            )
            zip.textEntry(
                "OPS/intro.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>内容简介</h1><p>guide 指向的简介。</p><h1>第一章 开始</h1><p>第一章正文不能当简介。</p></body></html>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>正文。</p></body></html>""",
            )
        }

        val metadata = EpubBookParser().readMetadata(epub, epub.name)

        assertEquals("guide 指向的简介。", metadata.description)
    }

    @Test fun aPathInputResolvesTheFileNameNotTheDirectory() {
        val parsed = LocalMetadata.parseFileName("content://provider/文档/遮天-辰东.txt")
        assertEquals("遮天", LocalMetadata.mergeTitles(parsed.titles, "fallback"))
        assertEquals("辰东", LocalMetadata.mergeAuthors(parsed.authors))
    }

    @Test fun fullWidthSpacesSplitAndMatchLikeAsciiSpaces() {
        val sample = "三体　刘慈欣.txt"
        assertEquals(
            listOf("三体", "刘慈欣"),
            LocalMetadata.splitFilenameSample(sample)!!.segments,
        )
        val spec = FilenameRuleSpec(
            id = "fw",
            sample = sample,
            separator = " ",
            roles = listOf(FilenameSegmentRole.TITLE, FilenameSegmentRole.AUTHOR),
        )
        val match = LocalMetadata.applyFilenameRuleSpec(spec, sample)!!
        assertEquals("三体", match.title)
        assertEquals("刘慈欣", match.author)
    }

    @Test fun duplicateAuthorOrStatusRolesCannotCompile() {
        val duplicateAuthor = FilenameRuleSpec(
            id = "dup-author",
            sample = "甲-乙-丙.txt",
            separator = "-",
            roles = listOf(
                FilenameSegmentRole.TITLE,
                FilenameSegmentRole.AUTHOR,
                FilenameSegmentRole.AUTHOR,
            ),
        )
        assertEquals("", LocalMetadata.buildPatternForSpec(duplicateAuthor))
        assertTrue(LocalMetadata.specCandidates(listOf(duplicateAuthor), "甲-乙-丙.txt").authors.isEmpty())

        val duplicateStatus = FilenameRuleSpec(
            id = "dup-status",
            sample = "甲-乙-丙-丁.txt",
            separator = "-",
            roles = listOf(
                FilenameSegmentRole.TITLE,
                FilenameSegmentRole.AUTHOR,
                FilenameSegmentRole.STATUS,
                FilenameSegmentRole.STATUS,
            ),
        )
        assertEquals("", LocalMetadata.buildPatternForSpec(duplicateStatus))
    }

    @Test fun cjkDottedTitlesKeepTheirSubtitle() {
        val parsed = LocalMetadata.parseFileName("三体.黑暗森林.txt")
        assertEquals("三体.黑暗森林", LocalMetadata.mergeTitles(parsed.titles, "fallback"))
        val epub = LocalMetadata.parseFileName("三体.黑暗森林.epub")
        assertEquals("三体.黑暗森林", LocalMetadata.mergeTitles(epub.titles, "fallback"))
    }

    private fun ZipOutputStream.textEntry(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray())
        closeEntry()
    }
}
