package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kixyu9527.kixyubook.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoProgressTest {
    private lateinit var context: Context
    private lateinit var database: KixyuDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO books (
                uuid, title, author, description, coverPath, format, originalPath,
                storagePath, createdTime, contentHash, category
            ) VALUES ('book-1', '测试书籍', '作者', '', NULL, 'TXT', '', '', 1, 'hash', '')
            """.trimIndent(),
        )
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO chapters (id, bookUuid, title, chapterIndex, volumeTitle, volumeIndex, indexed, chapterKey)
            VALUES (1, 'book-1', '第一章', 0, NULL, NULL, 1, 'chapter-1')
            """.trimIndent(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun delayedOlderWrite_cannotReplaceLatestProgress() = runBlocking {
        val dao = database.bookDao()
        assertTrue(dao.saveProgressIfNewer(progress(position = 20, updatedTime = 200)))
        assertFalse(dao.saveProgressIfNewer(progress(position = 5, updatedTime = 100)))
        assertFalse(dao.saveProgressIfNewer(progress(position = 6, updatedTime = 200)))

        val stored = dao.getProgress("book-1")
        assertEquals(20, stored?.position)
        assertEquals(200L, stored?.updatedTime)
    }

    private fun progress(position: Int, updatedTime: Long) = ReadingProgressEntity(
        bookUuid = "book-1",
        chapterId = 1,
        position = position,
        offset = 0,
        updatedTime = updatedTime,
        fraction = position / 100f,
        chapterKey = "chapter-1",
        paragraphIndex = position,
        charOffset = 0,
    )
}
