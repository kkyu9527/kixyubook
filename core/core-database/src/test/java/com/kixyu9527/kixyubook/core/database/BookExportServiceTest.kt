package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookExportServiceTest {
    @get:Rule val folder = TemporaryFolder()

    private fun annotations(): ReaderAnnotationRepository =
        Proxy.newProxyInstance(
            ReaderAnnotationRepository::class.java.classLoader,
            arrayOf(ReaderAnnotationRepository::class.java),
        ) { _, _, _ -> error("annotations are not used by these exports") } as ReaderAnnotationRepository

    private fun database(context: Context): KixyuDatabase =
        Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()

    @Test
    fun anEpubExportCopiesTheSourceBytesInsteadOfConvertingThem() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database(context)
        try {
            val source = folder.newFile("source.epub").apply {
                writeBytes(byteArrayOf(0x50, 0x4B, 3, 4, 9, 8, 7))
            }
            database.bookDao().insertBook(
                BookEntity("book", "书名", "作者", "", null, "EPUB", "", source.absolutePath, 0, "hash", ""),
            )
            val destination = folder.newFile("exported.epub")
            val service = BookExportService(context, database.bookDao(), annotations()) { _, _ -> null }

            val result = service.exportOriginalFile("book", destination.toURI().toString())

            assertTrue(result.isSuccess)
            assertArrayEquals(source.readBytes(), destination.readBytes())
        } finally {
            database.close()
        }
    }

    @Test
    fun aFailedBodyExportDoesNotLeaveAZeroByteFile() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database(context)
        try {
            val source = folder.newFile("book.txt").apply { writeText("正文") }
            database.bookDao().insertBook(
                BookEntity("book", "书名", "作者", "", null, "TXT", "", source.absolutePath, 0, "hash", ""),
            )
            database.bookDao().insertChapter(ChapterEntity(1, "book", "第一章", 0))
            val destination = folder.newFile("body.txt").apply { writeText("") }
            // A chapter that cannot be loaded must fail the export and clean the destination.
            val service = BookExportService(context, database.bookDao(), annotations()) { _, _ -> null }

            val result = service.exportBook("book", destination.toURI().toString())

            assertTrue(result.isFailure)
            assertFalse("a failed export must not leave a 0-byte document", destination.exists())
        } finally {
            database.close()
        }
    }
}
