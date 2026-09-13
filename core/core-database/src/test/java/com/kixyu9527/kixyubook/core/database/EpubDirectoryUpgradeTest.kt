package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.database.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubDirectoryUpgradeTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun enrichingOldDirectoryPreservesStoredReadingDataAndSearchIndex() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("old-book.epub")
        ZipOutputStream(source.outputStream()).use { zip ->
            fun entry(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""")
            entry("book.opf", """<package xmlns="http://www.idpf.org/2007/opf"><manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>""")
            entry("a.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>原先遗漏的序章</h1><p>序章正文。</p></body></html>""")
            entry("b.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>正文</h1><p>保留书签。</p><p>searchable 这段用于批注和笔记。</p></body></html>""")
        }
        val context = RuntimeEnvironment.getApplication() as Context
        val preferences = context.getSharedPreferences("directory-upgrade-test", Context.MODE_PRIVATE)
        preferences.edit().clear().putInt("epub_directory_version", 2).commit()
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(BookEntity("book", "测试", "", "", null, "EPUB", "", source.path, 1, "hash", ""))
            val chapter = ChapterEntity(22, "book", "旧正文名", 1, chapterKey = "permanent-key")
            dao.insertChapter(chapter)
            dao.insertParagraphsChunked(chapter.id, listOf("保留书签。", "searchable 这段用于批注和笔记。"))
            val bookmark = BookmarkEntity("bookmark", "book", chapter.id, 1, "searchable", 1)
            dao.insertBookmark(bookmark)
            val progress = ReadingProgressEntity("book", chapter.id, 1, 3, 100, .5f, chapter.chapterKey)
            dao.saveProgress(progress)
            val highlight = ReaderAnnotationEntity(
                "highlight", "book", "hash", chapter.chapterKey, 1, 1, 0, 10,
                "searchable", "HIGHLIGHT", "", 1, 1, "device",
            )
            val note = highlight.copy(uuid = "note", note = "原有笔记", startOffset = 11, endOffset = 13, exactText = "这段")
            database.readerAnnotationDao().upsert(highlight)
            database.readerAnnotationDao().upsert(note)
            val paragraphs = dao.getParagraphs(chapter.id)
            val annotations = database.readerAnnotationDao().getForBook("book")
            assertEquals(1, dao.searchBook("book", "searchable").size)

            var scheduled = 0
            val coordinator = EpubIndexCoordinator(
                database, dao, EpubParseCoordinator(), EpubChapterCache(folder.newFolder("cache")),
                Mutex(), Mutex(), preferences, { scheduled++ },
            )
            coordinator.upgradeDirectoryDataIfNeeded()
            coordinator.upgradeDirectoryDataIfNeeded() // Reopening must be idempotent.

            assertEquals(listOf(0, 1), dao.getChapters("book").map { it.chapterIndex })
            assertEquals(chapter.copy(title = "正文"), dao.getChapter("book", 1))
            assertEquals(paragraphs, dao.getParagraphs(chapter.id))
            assertEquals(listOf(bookmark), dao.getAllBookmarkEntities())
            assertEquals(progress, dao.getProgress("book"))
            assertEquals(annotations, database.readerAnnotationDao().getForBook("book"))
            val result = dao.searchBook("book", "searchable").single()
            assertEquals(chapter.id, result.chapterId)
            assertEquals(1, result.paragraphIndex)
            assertEquals(result, dao.searchBookLiteralChapters("book", "这段", listOf(1), 10).single())
            assertEquals(1, scheduled)
            assertFalse(dao.getChapter("book", 0)!!.indexed)
        } finally {
            database.close()
            preferences.edit().clear().commit()
        }
    }

    @Test fun directoryUpgradeRestoresAdoptedVolumeTitleAndKeepsReadingData() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("foreword-upgrade.epub")
        ZipOutputStream(source.outputStream()).use { zip ->
            fun entry(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""")
            entry("book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>卷首升级</dc:title></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="fw2" href="foreword2.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="fw2"/><itemref idref="c2"/></spine></package>""")
            entry("nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><nav><ol><li><span>第一卷</span><ol><li><a href="c1.xhtml">第一章</a></li></ol></li><li><span>第二卷</span><ol><li><a href="c2.xhtml">第二章</a></li></ol></li></ol></nav></body></html>""")
            entry("c1.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>第一章</h1></body></html>")
            entry("foreword2.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>致读者</h1><p>卷首正文。</p></body></html>")
            entry("c2.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>第二章</h1></body></html>")
        }
        val context = RuntimeEnvironment.getApplication() as Context
        val preferences = context.getSharedPreferences("foreword-upgrade-test", Context.MODE_PRIVATE)
        // Previous directory version: the adopted title was already overwritten by the body heading.
        preferences.edit().clear().putInt("epub_directory_version", 4).commit()
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(BookEntity("book", "卷首升级", "", "", null, "EPUB", "", source.path, 1, "hash", ""))
            val chapter = ChapterEntity(7, "book", "致读者", 1, chapterKey = "stable-key")
            dao.insertChapter(chapter)
            dao.insertParagraphsChunked(chapter.id, listOf("卷首正文。"))
            val bookmark = BookmarkEntity("bookmark", "book", chapter.id, 0, "卷首正文。", 1)
            dao.insertBookmark(bookmark)
            val progress = ReadingProgressEntity("book", chapter.id, 0, 0, 10, .1f, chapter.chapterKey)
            dao.saveProgress(progress)
            val note = ReaderAnnotationEntity(
                "note", "book", "hash", chapter.chapterKey, 0, 0, 0, 4,
                "卷首正文。", "HIGHLIGHT", "原有笔记", 1, 1, "device",
            )
            database.readerAnnotationDao().upsert(note)

            var scheduled = 0
            val coordinator = EpubIndexCoordinator(
                database, dao, EpubParseCoordinator(), EpubChapterCache(folder.newFolder("foreword-cache")),
                Mutex(), Mutex(), preferences, { scheduled++ },
            )
            coordinator.upgradeDirectoryDataIfNeeded()

            val repaired = dao.getChapter("book", 1)!!
            assertEquals("第二卷", repaired.title)
            assertEquals(7L, repaired.id)
            assertEquals(listOf(bookmark), dao.getAllBookmarkEntities())
            assertEquals(progress, dao.getProgress("book"))
            assertEquals(listOf(note), database.readerAnnotationDao().getForBook("book"))
        } finally {
            database.close()
            preferences.edit().clear().commit()
        }
    }
}
