package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises real Pager measure/scroll, which pure list-window tests cannot cover. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderPagerHandoffTest {
    @get:Rule val rule = createComposeRule()

    @Test fun recenteringKeepsDisplayedLeafAndSecondTurnReachesNextLeaf() {
        var keys by mutableStateOf(listOf("old:0", "old:1", "current:0", "next:0", "next:1"))
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        val events = mutableListOf<String>()
        rule.setContent {
            pager = rememberPagerState(initialPage = 2, pageCount = { keys.size })
            scope = rememberCoroutineScope()
            LaunchedEffect(pager) {
                pager.settledReaderLeaves({ 0 }, { 0 }).collect { it?.let { events += it.first } }
            }
            HorizontalPager(pager, Modifier.size(300.dp, 500.dp), key = { keys[it] }) {
                Box(Modifier.size(300.dp, 500.dp))
            }
        }
        rule.runOnIdle { scope.launch { pager.scrollToPage(3) } }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("next:0", events.last())
            events.clear()
            keys = listOf("current:0", "next:0", "next:1", "future:0")
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertTrue(events.all { it == "next:0" })
            assertEquals(1, pager.currentPage)
            scope.launch { pager.animateScrollToPage(2) }
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("next:1", events.last()) }
    }

    @Test fun explicitBookmarkOrSearchJumpDoesNotCommitOldLeafDuringRestore() {
        var version by mutableIntStateOf(0)
        var applied by mutableIntStateOf(0)
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        val events = mutableListOf<Pair<String, Int>>()
        val keys = listOf("chapter:0", "chapter:1", "bookmark:0", "search:0")
        rule.setContent {
            pager = rememberPagerState(pageCount = { keys.size })
            scope = rememberCoroutineScope()
            LaunchedEffect(pager) {
                pager.settledReaderLeaves({ version }, { applied }).collect { it?.let(events::add) }
            }
            HorizontalPager(pager, Modifier.size(300.dp, 500.dp), key = { keys[it] }) {
                Box(Modifier.size(300.dp, 500.dp))
            }
        }
        for (target in 2..3) {
            rule.runOnIdle { events.clear(); version++ }
            rule.waitForIdle()
            rule.runOnIdle {
                assertTrue(events.isEmpty())
                scope.launch { pager.scrollToPage(target); applied = version }
            }
            rule.waitForIdle()
            rule.runOnIdle {
                assertEquals(listOf(keys[target] to version), events)
            }
        }
    }
}
