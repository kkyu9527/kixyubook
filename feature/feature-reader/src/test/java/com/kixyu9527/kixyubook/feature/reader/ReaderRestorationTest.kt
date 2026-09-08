package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderRestorationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun processRecreationUsesDurableAnchorNotOldWindowIndex() = restorePager(newProcess = true)

    @Test fun sameSessionRecreationStillPreservesPagerPosition() = restorePager(newProcess = false)

    @Test fun coldScrollRestoreUsesDatabaseItem() = restoreScroll(newProcess = true)

    @Test fun warmScrollRestoreKeepsPixelOffset() = restoreScroll(newProcess = false)

    private fun restoreScroll(newProcess: Boolean) {
        val restoration = StateRestorationTester(rule)
        var recreated = false
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        restoration.setContent {
            val cold = recreated && newProcess
            list = rememberReaderListState(if (cold) "new-vm" else "old-vm", if (cold) 3 else 0)
            scope = rememberCoroutineScope()
            LazyColumn(Modifier.size(300.dp, 300.dp), state = list) {
                items(20) { Box(Modifier.size(300.dp, 100.dp)) }
            }
        }
        rule.runOnIdle { scope.launch { list.scrollToItem(6, 24) } }
        rule.runOnIdle { recreated = true }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(if (newProcess) 3 else 6, list.firstVisibleItemIndex)
            assertEquals(if (newProcess) 0 else 24, list.firstVisibleItemScrollOffset)
        }
    }

    private fun restorePager(newProcess: Boolean) {
        val restoration = StateRestorationTester(rule)
        // Non-snapshot variable: change the new process's inputs only when composition restarts,
        // after the old pager's saved-state provider has captured its actual old numeric index.
        var recreated = false
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        val savedLeaves = mutableListOf<String>()
        restoration.setContent {
            val cold = recreated && newProcess
            val keys = if (cold) List(4) { "current:$it" } else
                List(4) { "previous:$it" } + List(4) { "current:$it" }
            pager = rememberReaderPagerState(if (cold) "new-vm" else "old-vm", if (cold) 2 else 4) { keys.size }
            scope = rememberCoroutineScope()
            LaunchedEffect(pager) {
                pager.settledReaderLeaves({ 0 }, { 0 }).collect { it?.let { savedLeaves += it.first } }
            }
            HorizontalPager(pager, Modifier.size(300.dp, 500.dp), key = { keys[it] }) {
                Box(Modifier.size(300.dp, 500.dp))
            }
        }
        rule.runOnIdle { scope.launch { pager.scrollToPage(6) } }
        rule.runOnIdle {
            assertEquals("current:2", savedLeaves.last())
            savedLeaves.clear()
            recreated = true
        }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(if (newProcess) 2 else 6, pager.currentPage)
            assertEquals(listOf("current:2"), savedLeaves)
        }
    }

    @Test fun reindexedChapterRestoresByStableKeyBeforeRowId() {
        val chapters = listOf(
            Chapter(12, "book", "第一章", 0, chapterKey = "first"),
            Chapter(33, "book", "第二章", 1, chapterKey = "second"),
        )
        val progress = ReadingProgress("book", 12, 3, 7, 100, chapterKey = "second")
        assertEquals(1, readerProgressChapterIndex(chapters, progress))
        assertEquals(0, readerProgressChapterIndex(chapters, progress.copy(chapterKey = "")))
        assertEquals(-1, readerProgressChapterIndex(chapters, progress.copy(chapterId = 99, chapterKey = "missing")))
    }
}
