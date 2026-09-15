package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.MetadataEditEntity
import com.kixyu9527.kixyubook.core.reader.engine.BookParser
import com.kixyu9527.kixyubook.core.reader.engine.LocalMetadata
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import com.kixyu9527.kixyubook.core.reader.engine.DocumentMetadata
import com.kixyu9527.kixyubook.core.reader.engine.TxtBookParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class MetadataRefreshTest {
    @get:Rule val folder = TemporaryFolder()

    private class RecordingRecorder : SyncMutationRecorder {
        val recorded = mutableListOf<Pair<SyncEntityType, String>>()

        override suspend fun record(
            type: SyncEntityType,
            entityId: String,
            operation: SyncMutationOperation,
        ) {
            recorded += type to entityId
        }
    }

    private fun book(storagePath: String) = BookEntity(
        uuid = "book",
        title = "旧书名",
        author = "旧作者",
        description = "旧简介",
        coverPath = null,
        format = "TXT",
        originalPath = "/import/《新书》作者：解析作者.txt",
        storagePath = storagePath,
        createdTime = 0,
        contentHash = "hash",
        category = "未分类",
    )

    @Test
    fun recognitionNeverDowngradesAKnownAuthorToThePlaceholder() {
        assertEquals("辰东", recognizedAuthor("未知作者", "辰东"))
        assertEquals("未知作者", recognizedAuthor("未知作者", ""))
        assertEquals("新作者", recognizedAuthor("新作者", "旧作者"))
    }

    @Test
    fun importedAndSortMetadataMergeKeepsExistingValues() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(book(storagePath = folder.newFile("merge.txt").absolutePath))
            dao.updateBookImportedMetadata("book", "原标题", "原排序", "原系列", 3.0)

            dao.updateBookImportedMetadata("book", "", "", "", null)
            dao.updateBookSortMetadata("book", "", "", null)
            val preserved = dao.getBook("book")!!
            assertEquals("原标题", preserved.originalDisplayName)
            assertEquals("原排序", preserved.titleSort)
            assertEquals("原系列", preserved.seriesName)
            assertEquals(3.0, preserved.seriesIndex!!, 0.0)

            dao.updateBookSortMetadata("book", "新排序", "", 0.0)
            val updated = dao.getBook("book")!!
            assertEquals("新排序", updated.titleSort)
            assertEquals("原系列", updated.seriesName)
            assertEquals(0.0, updated.seriesIndex!!, 0.0)
        } finally {
            database.close()
        }
    }

    @Test
    fun refreshReReadsAllFieldsWhenTheUserNeverEditedThem() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("source.txt").apply {
            writeText("作者：解析作者\n内容简介：解析简介。\n第一章 开始\n正文。")
        }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(book(source.path))
            val recorder = RecordingRecorder()

            val result = refreshBookMetadata(
                context, database, dao, TxtBookParser(), recorder, "book",
            )

            assertFalse(result.preservedAuthor)
            assertEquals("解析作者", result.author)
            assertEquals("解析简介。", result.description)
            assertEquals("解析作者", dao.getBook("book")!!.author)
            assertEquals(listOf(SyncEntityType.BOOK to "book"), recorder.recorded)
        } finally {
            database.close()
        }
    }

    @Test
    fun refreshKeepsTheFolderAuthorWhenTheFileNameCarriesNone() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("01.txt").apply { writeText("第一章 开始\n正文。") }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(
                book(source.path).copy(
                    title = "01",
                    author = LocalMetadata.UNKNOWN_AUTHOR,
                    originalDisplayName = "01.txt",
                    originalFolderName = "《遮天》作者：辰东",
                ),
            )

            val result = refreshBookMetadata(
                context, database, dao, TxtBookParser(), RecordingRecorder(), "book",
            )

            assertEquals("辰东", result.author)
            assertEquals("遮天", result.title)
            assertEquals("辰东", dao.getBook("book")!!.author)
            assertEquals("遮天", dao.getBook("book")!!.title)
        } finally {
            database.close()
        }
    }

    @Test
    fun refreshKeepsAFieldTheUserEditedByHand() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("source.txt").apply {
            writeText("作者：解析作者\n内容简介：解析简介。\n第一章 开始\n正文。")
        }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            // The user already renamed the author; ownership is persisted on the book itself.
            dao.insertBook(book(source.path).copy(author = "我的作者", userEditedAuthor = true))
            dao.insertMetadataEdit(
                MetadataEditEntity(
                    uuid = "edit",
                    bookUuid = "book",
                    previousTitle = "旧书名",
                    previousAuthor = "旧作者",
                    previousDescription = "旧简介",
                    newTitle = "旧书名",
                    newAuthor = "我的作者",
                    newDescription = "旧简介",
                    createdTime = 1,
                ),
            )

            val result = refreshBookMetadata(
                context, database, dao, TxtBookParser(), RecordingRecorder(), "book",
            )

            assertTrue(result.preservedAuthor)
            assertEquals("我的作者", result.author)
            assertEquals("解析简介。", result.description)
            assertEquals("我的作者", dao.getBook("book")!!.author)
        } finally {
            database.close()
        }
    }

    @Test
    fun aManualEditDuringParsingWinsOverTheParsedValue() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("source.txt").apply { writeText("第一章 开始\n正文。") }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(book(source.path).copy(author = "原作者"))
            // The editor commits while refreshMetadata is still reading the file.
            val parser = object : BookParser {
                override val format = BookFormat.TXT

                override fun readMetadata(
                    file: File,
                    fallbackTitle: String,
                    sourceName: String,
                    rules: List<LocalMetadata.FilenameRule>,
                    specs: List<com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec>,
                ): DocumentMetadata {
                    runBlocking {
                        database.withTransaction {
                            dao.updateBookMetadata("book", "旧书名", "用户刚修改的作者", "旧简介")
                            dao.markMetadataEdited("book", title = false, author = true, description = false)
                            dao.insertMetadataEdit(
                                MetadataEditEntity(
                                    uuid = "edit",
                                    bookUuid = "book",
                                    previousTitle = "旧书名",
                                    previousAuthor = "原作者",
                                    previousDescription = "旧简介",
                                    newTitle = "旧书名",
                                    newAuthor = "用户刚修改的作者",
                                    newDescription = "旧简介",
                                    createdTime = 1,
                                ),
                            )
                        }
                    }
                    return DocumentMetadata(title = "解析书名", author = "解析作者")
                }

                override suspend fun readChapters(
                    file: File,
                    emit: suspend (DocumentChapter) -> Unit,
                ) = Unit
            }

            val result = refreshBookMetadata(context, database, dao, parser, RecordingRecorder(), "book")

            assertTrue(result.preservedAuthor)
            assertEquals("用户刚修改的作者", result.author)
            assertEquals("用户刚修改的作者", dao.getBook("book")!!.author)
            assertEquals("解析书名", dao.getBook("book")!!.title)
        } finally {
            database.close()
        }
    }

    @Test
    fun aDocumentUriOriginalPathDoesNotBecomeTheTitle() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("uri-source.txt").apply {
            writeText("作者：解析作者\n第一章 开始\n正文。")
        }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(
                book(source.path).copy(
                    title = "原本正确的书名",
                    author = "旧作者",
                    originalPath = "content://com.android.providers.downloads.documents/document/157",
                ),
            )

            val result = refreshBookMetadata(
                context, database, dao, TxtBookParser(), RecordingRecorder(), "book",
            )

            assertEquals("原本正确的书名", result.title)
            assertEquals("原本正确的书名", dao.getBook("book")!!.title)
        } finally {
            database.close()
        }
    }

    @Test
    fun fieldOwnershipSurvivesMoreThanFiftyLaterEdits() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("ownership.txt").apply { writeText("第一章 开始\n正文。") }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            val original = book(source.path)
            dao.insertBook(original)
            val store = BookMutationStore(context, database, dao, RecordingRecorder())
            // The author edit is old; later description edits must not erase its ownership.
            store.updateBookMetadata("book", original.title, "我的作者", original.description)
            repeat(60) { index -> store.updateBookMetadata("book", original.title, "我的作者", "简介$index") }
            assertEquals(50, dao.getMetadataEdits("book").size)

            val parser = object : BookParser {
                override val format = BookFormat.TXT

                override fun readMetadata(
                    file: File,
                    fallbackTitle: String,
                    sourceName: String,
                    rules: List<LocalMetadata.FilenameRule>,
                    specs: List<com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec>,
                ): DocumentMetadata = DocumentMetadata(title = "解析书名", author = "解析作者")

                override suspend fun readChapters(
                    file: File,
                    emit: suspend (DocumentChapter) -> Unit,
                ) = Unit
            }

            val result = refreshBookMetadata(context, database, dao, parser, RecordingRecorder(), "book")

            assertTrue(result.preservedAuthor)
            assertEquals("我的作者", result.author)
            assertEquals("我的作者", dao.getBook("book")!!.author)
        } finally {
            database.close()
        }
    }

    @Test
    fun refreshReplacesTheCoverWhenTheSourceStillHasOne() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("cover-source.txt").apply { writeText("第一章 开始\n正文。") }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(book(source.path))
            val parser = object : BookParser {
                override val format = BookFormat.TXT

                override fun readMetadata(
                    file: File,
                    fallbackTitle: String,
                    sourceName: String,
                    rules: List<LocalMetadata.FilenameRule>,
                    specs: List<com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec>,
                ): DocumentMetadata = DocumentMetadata(
                    title = "新封面",
                    author = "作者",
                    coverBytes = byteArrayOf(1, 2, 3),
                    coverExtension = "png",
                )

                override suspend fun readChapters(
                    file: File,
                    emit: suspend (DocumentChapter) -> Unit,
                ) = Unit
            }

            refreshBookMetadata(context, database, dao, parser, RecordingRecorder(), "book")

            val coverPath = requireNotNull(dao.getBook("book")!!.coverPath)
            assertEquals(true, coverPath.endsWith("book.png"))
            assertEquals(listOf<Byte>(1, 2, 3), File(coverPath).readBytes().toList())
        } finally {
            database.close()
        }
    }

    @Test
    fun theEditJournalIsPrunedButStillKnowsTheFieldWasEdited() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("prune-source.txt").apply { writeText("第一章 开始\n正文。") }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(book(source.path))
            val store = BookMutationStore(context, database, dao, RecordingRecorder())

            repeat(60) { index -> store.updateBookMetadata("book", "标题$index", "作者$index", "简介$index") }

            val edits = dao.getMetadataEdits("book")
            assertEquals(50, edits.size)
            assertEquals(true, edits.any { it.newAuthor == "作者59" })
            // The oldest surviving edit still differs from its previous value, so the ownership
            // question keeps a correct answer.
            assertEquals(true, edits.any { it.newAuthor != it.previousAuthor })
        } finally {
            database.close()
        }
    }

    @Test
    fun folderNameIsExtractedFromSafDocumentIds() {
        assertEquals(
            "《遮天》作者：辰东",
            folderNameFromDocumentId("primary:Books/合集/《遮天》作者：辰东/01.txt"),
        )
        assertEquals(null, folderNameFromDocumentId("primary:single.txt"))
    }

    @Test
    fun folderMetadataOnlyFillsFieldsTheFileLeftUnknown() {
        val missing = DocumentMetadata(title = "01", author = "未知作者")
            .withFolderFallback("《遮天》作者：辰东", fallbackTitle = "01")
        assertEquals("遮天", missing.title)
        assertEquals("辰东", missing.author)

        val explicit = DocumentMetadata(title = "我的标题", author = "我的作者")
        assertEquals(explicit, explicit.withFolderFallback("《遮天》作者：辰东", fallbackTitle = "01"))
    }

    @Test
    fun refreshUsesTheStoredOriginalDisplayNameForFilenameMetadata() = runBlocking(Dispatchers.IO) {
        val source = folder.newFile("display-name-source.txt").apply {
            writeText("第一章 开始\n正文。")
        }
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(
                book(source.path).copy(
                    author = "未知作者",
                    // Import URIs are opaque; the display name is the only usable file name.
                    originalPath = "content://com.android.providers.downloads.documents/document/157",
                    originalDisplayName = "《遮天》作者：辰东.txt",
                ),
            )

            val result = refreshBookMetadata(
                context, database, dao, TxtBookParser(), RecordingRecorder(), "book",
            )

            assertEquals("遮天", result.title)
            assertEquals("辰东", result.author)
            assertEquals("遮天", dao.getBook("book")!!.title)
        } finally {
            database.close()
        }
    }
}
