package com.kixyu9527.kixyubook.feature.reader

import androidx.lifecycle.ViewModelStore
import com.kixyu9527.kixyubook.core.common.configuration.mergeBookSetting
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/** Actual ViewModels, including init, local/cloud gates and onCleared; I/O boundaries are fake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelRecoveryTest {
    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun txtReopensAtLastCheckpointAfterReaderIsCleared() = recreateReader(BookFormat.TXT)

    @Test fun epubReopensAtLastCheckpointAfterReaderIsCleared() = recreateReader(BookFormat.EPUB)

    @Test fun txtLocationChainKeepsAnchorsAndCanStillRestore() = recreateReader(BookFormat.TXT, exerciseJumps = true)
    @Test fun epubLocationChainKeepsAnchorsAndCanStillRestore() = recreateReader(BookFormat.EPUB, exerciseJumps = true)
    @Test fun failedBookmarkRetryAfterChapterJumpUsesOriginalPayload() = recreateReader(BookFormat.EPUB, exerciseBookmark = true)

    private fun recreateReader(format: BookFormat, exerciseJumps: Boolean = false, exerciseBookmark: Boolean = false) = runBlocking { withTimeout(30_000) {
        val bookmarkAttempts = mutableListOf<Bookmark>()
        val chapters = MutableStateFlow(listOf(
            Chapter(12, "book", "第一章", 0, chapterKey = "first"),
            Chapter(33, "book", "第二章", 1, chapterKey = "second"),
        ))
        val durable = MutableStateFlow<ReadingProgress?>(ReadingProgress(
            "book", 33, 2, 4, 1, chapterKey = "second", paragraphIndex = 2, charOffset = 4,
        ))
        val diskGate = CompletableDeferred<Unit>()
        val enteredDisk = CompletableDeferred<Unit>()
        val repository = object : BookRepository by fake<BookRepository>({ method, _ ->
            when (method) {
                "observeChapters" -> chapters
                "observeProgress" -> durable
                "observeBookmarks" -> flowOf(emptyList<Bookmark>())
                "getBook" -> Book("book", "测试", "", "", null, format, "", "", 0, "hash")
                "readEpubNavigation" -> emptyList<EpubNavigationEntry>()
                "setReaderSessionActive", "setReaderInteractionActive", "releaseReaderMemory" -> Unit
                else -> error("Unexpected book call: $method")
            }
        }) {
            override suspend fun addBookmark(bookmark: Bookmark) {
                bookmarkAttempts += bookmark
                if (bookmarkAttempts.size == 1) throw java.io.IOException("temporary write error")
            }
            override suspend fun getChapter(bookUuid: String, chapterIndex: Int, priority: ChapterLoadPriority): ChapterContent {
                val chapter = chapters.value[chapterIndex]
                return ChapterContent(chapter, List(10) { index ->
                    Paragraph(index.toLong(), chapter.id, index, "正文段落 $index，用于保存字符位置")
                })
            }

            override suspend fun saveProgress(progress: ReadingProgress) {
                enteredDisk.complete(Unit)
                diskGate.await()
                if (progress.updatedTime > (durable.value?.updatedTime ?: Long.MIN_VALUE)) durable.value = progress
            }
        }
        val settings = fake<ReaderSettingsRepository> { method, _ ->
            when (method) {
                "getSettings" -> flowOf(ReaderSettings())
                "getSearchHistory" -> flowOf(emptyList<String>())
                else -> error("Unexpected settings call: $method")
            }
        }
        val fonts = fake<FontRepository> { method, _ ->
            check(method == "observeFonts"); flowOf(emptyList<UserFont>())
        }
        val stats = fake<ReadingStatsRepository> { method, _ -> error("Unexpected stats call: $method") }
        // Keep remote sync pending throughout: local checkpoint must not depend on its completion.
        val sync = fake<CloudSyncCoordinator> { method, _ ->
            when (method) {
                "getPriorityBookSync" -> MutableStateFlow(PriorityBookSyncState("book", PriorityBookSyncPhase.PULLING))
                "prioritizeBook", "releaseBook" -> Unit
                else -> error("Unexpected sync call: $method")
            }
        }
        val corrections = fake<TextCorrectionRepository> { method, _ ->
            check(method == "observeBookCorrections"); flowOf(emptyList<TextCorrection>())
        }
        val annotations = fake<ReaderAnnotationRepository> { method, _ ->
            check(method == "observeBookAnnotations"); flowOf(emptyList<ReaderAnnotation>())
        }
        fun newReader() = ReaderViewModel("book", repository, settings, fonts, stats, sync, corrections, annotations,
            androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val firstStore = ViewModelStore()
        val secondStore = ViewModelStore()
        lateinit var first: ReaderViewModel
        lateinit var second: ReaderViewModel
        try {
            first = newReader()
            firstStore.put("reader", first)
            first.uiState.first { !it.loading }
            if (exerciseBookmark) {
                first.savePosition(7, 5)
                first.addBookmark()
                first.operations.state.first { it.failed }
                val attempt = first.operations.state.value.attempt
                first.jumpToChapter(0)
                first.uiState.first { it.chapterIndex == 0 }
                first.operations.retry(attempt)
                first.operations.state.first { it.succeeded }
                assertEquals(2, bookmarkAttempts.size)
                assertEquals(bookmarkAttempts.first(), bookmarkAttempts.last())
                assertEquals(33L, bookmarkAttempts.last().chapterId)
                assertEquals(7, bookmarkAttempts.last().position)
                first.requestLocation(ReaderLocationRequest(1, 2, 4, ReaderLocationSource.BOOKMARK))
                first.uiState.first { it.chapterIndex == 1 && it.restorePosition == 2 }
            }
            if (exerciseJumps) {
                first.requestLocation(ReaderLocationRequest(0, 4, 6, ReaderLocationSource.ANNOTATION))
                first.uiState.first { it.chapterIndex == 0 && it.restorePosition == 4 }
                assertEquals(6, first.uiState.value.restoreCharOffset)
                first.jumpToChapter(1)
                first.uiState.first { it.chapterIndex == 1 && it.restorePosition == 0 }
                first.navigateHistoryBack()
                first.uiState.first { it.chapterIndex == 0 && it.restorePosition == 4 }
                assertEquals(6, first.uiState.value.restoreCharOffset)
                first.requestLocation(ReaderLocationRequest(1, 3, source = ReaderLocationSource.BOOKMARK))
                first.uiState.first { it.chapterIndex == 1 && it.restorePosition == 3 }
                first.requestLocation(ReaderLocationRequest(1, 2, 4, ReaderLocationSource.SEARCH))
                assertEquals(4, first.uiState.value.restoreCharOffset)
            }
            run {
                assertEquals(2, first.uiState.value.restorePosition)
                first.savePosition(7, 5)
                first.checkpointReadingProgress()
            }
            enteredDisk.await()
            firstStore.clear()
            diskGate.complete(Unit)
            durable.first { it?.paragraphIndex == 7 }
            run {
                // Simulate rebuilding the chapter table as well as losing all decoded caches.
                chapters.value = chapters.value.map { it.copy(id = it.id + 100) }
                second = newReader()
                secondStore.put("reader", second)
            }
            second.uiState.first { !it.loading }
            run {
                assertNotEquals(first.uiState.value.sessionId, second.uiState.value.sessionId)
                assertEquals(133L, second.uiState.value.chapter?.id)
                assertEquals(1, second.uiState.value.chapterIndex)
                assertEquals(7, second.uiState.value.restorePosition)
                assertEquals(5, second.uiState.value.restoreCharOffset)
                assertEquals(7, durable.value?.paragraphIndex)
            }
        } finally {
            diskGate.complete(Unit)
            firstStore.clear()
            secondStore.clear()
        }
    } }

    @Test fun perBookProfileRoutesAnEditQueuedRightAfterEnablingToThisBook() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val harness = perBookHarness()
            advanceUntilIdle()
            assertFalse(harness.viewModel.uiState.value.bookSettingsEnabled)

            // Toggle and edit within the same frame: the edit must follow the new per-book mode.
            harness.viewModel.setBookSettingsEnabled(true)
            harness.viewModel.updateSettings { it.copy(fontSize = 25f) }
            advanceUntilIdle()

            assertEquals(listOf(true), harness.enabledCalls)
            assertTrue(
                "fontSize must be written to the per-book profile",
                harness.fieldWrites.any { it.first == "fontSize" && it.second.contains("25") },
            )
            assertTrue("global fontSize must not change", harness.globalFontWrites.none { it == 25f })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun disablingPerBookProfileBeforeTheDebouncedWriteKeepsGlobalUntouched() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val harness = perBookHarness()
            advanceUntilIdle()
            harness.viewModel.setBookSettingsEnabled(true)
            advanceUntilIdle()
            // The edit is queued for the debounced writer, then the mode is switched off first.
            harness.viewModel.updateSettings { it.copy(fontSize = 26f) }
            harness.viewModel.setBookSettingsEnabled(false)
            advanceUntilIdle()

            assertTrue(
                "a per-book edit must not be reinterpreted as a global write",
                harness.globalFontWrites.none { it == 26f },
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun failedChapterNavigationStartsAFreshLoadWhenRetried() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var failSecondChapter = true
            val chapters = listOf(
                Chapter(1, "book", "第一章", 0, chapterKey = "first"),
                Chapter(2, "book", "第二章", 1, chapterKey = "second"),
            )
            val repository = object : BookRepository by fake<BookRepository>({ method, _ ->
                when (method) {
                    "observeChapters" -> MutableStateFlow(chapters)
                    "observeProgress" -> MutableStateFlow<ReadingProgress?>(null)
                    "observeBookmarks" -> flowOf(emptyList<Bookmark>())
                    "getBook" -> Book("book", "测试", "", "", null, BookFormat.EPUB, "", "", 0, "hash")
                    "readEpubNavigation" -> emptyList<EpubNavigationEntry>()
                    "setReaderSessionActive", "setReaderInteractionActive", "releaseReaderMemory" -> Unit
                    else -> error("Unexpected book call: $method")
                }
            }) {
                override suspend fun getChapter(
                    bookUuid: String,
                    chapterIndex: Int,
                    priority: ChapterLoadPriority,
                ): ChapterContent? {
                    if (chapterIndex == 1) {
                        // Speculative loads return nothing so only the user jump exercises the
                        // failure path; the first user jump fails, the retry must reload.
                        if (priority != ChapterLoadPriority.USER) return null
                        if (failSecondChapter) throw java.io.IOException("temporary parse error")
                    }
                    val chapter = chapters[chapterIndex]
                    return ChapterContent(chapter, listOf(Paragraph(0, chapter.id, 0, "正文")))
                }
                override suspend fun saveProgress(progress: ReadingProgress) = Unit
            }
            val settings = fake<ReaderSettingsRepository> { method, _ ->
                when (method) {
                    "getSettings" -> flowOf(ReaderSettings())
                    "getSearchHistory" -> flowOf(emptyList<String>())
                    else -> error("Unexpected settings call: $method")
                }
            }
            val fonts = fake<FontRepository> { method, _ ->
                check(method == "observeFonts"); flowOf(emptyList<UserFont>())
            }
            val stats = fake<ReadingStatsRepository> { method, _ -> error("Unexpected stats call: $method") }
            val sync = fake<CloudSyncCoordinator> { method, _ ->
                when (method) {
                    "getPriorityBookSync" ->
                        MutableStateFlow(PriorityBookSyncState("book", PriorityBookSyncPhase.PULLING))
                    "prioritizeBook", "releaseBook" -> Unit
                    else -> error("Unexpected sync call: $method")
                }
            }
            val corrections = fake<TextCorrectionRepository> { method, _ ->
                check(method == "observeBookCorrections"); flowOf(emptyList<TextCorrection>())
            }
            val annotations = fake<ReaderAnnotationRepository> { method, _ ->
                check(method == "observeBookAnnotations"); flowOf(emptyList<ReaderAnnotation>())
            }
            val viewModel = ReaderViewModel(
                "book", repository, settings, fonts, stats, sync, corrections, annotations,
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            )
            advanceUntilIdle()
            assertEquals(0, viewModel.uiState.value.chapterIndex)

            viewModel.jumpToChapter(1)
            advanceUntilIdle()
            assertNotNull("the first navigation must surface the failure", viewModel.uiState.value.error)

            failSecondChapter = false
            viewModel.jumpToChapter(1)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.chapterIndex)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class PerBookHarness(
    val viewModel: ReaderViewModel,
    val enabledCalls: MutableList<Boolean>,
    val fieldWrites: MutableList<Pair<String, String>>,
    val globalFontWrites: MutableList<Float>,
)

private fun perBookHarness(): PerBookHarness {
    val globalSettings = MutableStateFlow(ReaderSettings())
    val globalFontWrites = mutableListOf<Float>()
    val settingsRepository = object : ReaderSettingsRepository {
        override val settings: Flow<ReaderSettings> = globalSettings
        override val readingGoalMinutes: Flow<Int> = flowOf(30)
        override val searchHistory: Flow<List<String>> = flowOf(emptyList())
        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
            globalSettings.value = transform(globalSettings.value)
            globalFontWrites += globalSettings.value.fontSize
        }
        override suspend fun setReadingGoalMinutes(minutes: Int) = Unit
        override suspend fun addSearchHistory(query: String) = Unit
        override suspend fun clearSearchHistory() = Unit
    }
    val overrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val enabledCalls = mutableListOf<Boolean>()
    val fieldWrites = mutableListOf<Pair<String, String>>()
    val bookSettings = object : BookSettingsRepository {
        override val overrides: Flow<Map<String, String>> = overrides
        override suspend fun setEnabled(bookUuid: String, enabled: Boolean) {
            enabledCalls += enabled
            overrides.value = if (enabled) overrides.value + (bookUuid to "{}") else overrides.value - bookUuid
        }
        override suspend fun updateField(bookUuid: String, field: String, encodedValue: String) {
            fieldWrites += field to encodedValue
            overrides.value = overrides.value + (bookUuid to mergeBookSetting(
                globalSettings.value, overrides.value[bookUuid] ?: "{}", field, encodedValue,
            ))
        }
        override suspend fun clearFontReferences(fontUuid: String) = Unit
        override suspend fun replaceAll(values: Map<String, String>) { overrides.value = values }
    }
    val chapters = MutableStateFlow(listOf(Chapter(12, "book", "第一章", 0, chapterKey = "first")))
    val repository = object : BookRepository by fake<BookRepository>({ method, _ ->
        when (method) {
            "observeChapters" -> chapters
            "observeProgress" -> MutableStateFlow<ReadingProgress?>(null)
            "observeBookmarks" -> flowOf(emptyList<Bookmark>())
            "getBook" -> Book("book", "测试", "", "", null, BookFormat.EPUB, "", "", 0, "hash")
            "readEpubNavigation" -> emptyList<EpubNavigationEntry>()
            "setReaderSessionActive", "setReaderInteractionActive", "releaseReaderMemory" -> Unit
            else -> error("Unexpected book call: $method")
        }
    }) {
        override suspend fun getChapter(
            bookUuid: String,
            chapterIndex: Int,
            priority: ChapterLoadPriority,
        ): ChapterContent {
            val chapter = chapters.value[chapterIndex]
            return ChapterContent(
                chapter,
                List(5) { index -> Paragraph(index.toLong(), chapter.id, index, "正文 $index") },
            )
        }
        override suspend fun saveProgress(progress: ReadingProgress) = Unit
    }
    val fonts = fake<FontRepository> { method, _ ->
        check(method == "observeFonts"); flowOf(emptyList<UserFont>())
    }
    val stats = fake<ReadingStatsRepository> { method, _ -> error("Unexpected stats call: $method") }
    val sync = fake<CloudSyncCoordinator> { method, _ ->
        when (method) {
            "getPriorityBookSync" -> MutableStateFlow(PriorityBookSyncState("book", PriorityBookSyncPhase.PULLING))
            "prioritizeBook", "releaseBook" -> Unit
            else -> error("Unexpected sync call: $method")
        }
    }
    val corrections = fake<TextCorrectionRepository> { method, _ ->
        check(method == "observeBookCorrections"); flowOf(emptyList<TextCorrection>())
    }
    val annotations = fake<ReaderAnnotationRepository> { method, _ ->
        check(method == "observeBookAnnotations"); flowOf(emptyList<ReaderAnnotation>())
    }
    val viewModel = ReaderViewModel(
        "book", repository, settingsRepository, fonts, stats, sync, corrections, annotations,
        androidx.test.core.app.ApplicationProvider.getApplicationContext(), bookSettings,
    )
    return PerBookHarness(viewModel, enabledCalls, fieldWrites, globalFontWrites)
}

private inline fun <reified T> fake(crossinline call: (String, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        call(method.name, args)
    } as T
