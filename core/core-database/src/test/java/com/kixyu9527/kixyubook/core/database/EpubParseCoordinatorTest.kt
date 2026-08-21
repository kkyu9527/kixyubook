package com.kixyu9527.kixyubook.core.database

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubParseCoordinatorTest {
    @Test
    fun interactiveChapterPreemptsSilentIndexing() = runBlocking {
        val coordinator = EpubParseCoordinator()
        val backgroundStarted = CompletableDeferred<Unit>()
        val background = async {
            coordinator.background {
                backgroundStarted.complete(Unit)
                awaitCancellation()
            }
        }
        backgroundStarted.await()

        val selectedChapter = withTimeout(1_000) {
            coordinator.interactive { "用户目标章节" }
        }

        assertEquals("用户目标章节", selectedChapter)
        assertFalse(background.await())
    }

    @Test
    fun readerGesturePausesAndThenResumesSilentIndexing() = runBlocking {
        val coordinator = EpubParseCoordinator()
        val backgroundStarted = CompletableDeferred<Unit>()
        val activeBackground = async {
            coordinator.background {
                backgroundStarted.complete(Unit)
                awaitCancellation()
            }
        }
        backgroundStarted.await()

        coordinator.setReaderInteractionActive(true)
        assertFalse(withTimeout(1_000) { activeBackground.await() })

        val queuedBackground = async { coordinator.background { } }
        delay(30)
        assertFalse(queuedBackground.isCompleted)

        coordinator.setReaderInteractionActive(false)
        assertTrue(withTimeout(1_000) { queuedBackground.await() })
    }

    @Test
    fun appAnimationAndReaderInteractionHoldIndependentPauseReasons() = runBlocking {
        val coordinator = EpubParseCoordinator()
        coordinator.setAppAnimationActive(true)
        coordinator.setReaderInteractionActive(true)

        val queuedBackground = async { coordinator.background { } }
        delay(30)
        assertFalse(queuedBackground.isCompleted)

        coordinator.setAppAnimationActive(false)
        delay(30)
        assertFalse(queuedBackground.isCompleted)

        coordinator.setReaderInteractionActive(false)
        assertTrue(withTimeout(1_000) { queuedBackground.await() })
    }

    @Test
    fun visibleReaderFullyPausesSilentIndexingUntilReaderLeaves() = runBlocking {
        val coordinator = EpubParseCoordinator()
        val backgroundStarted = CompletableDeferred<Unit>()
        val activeBackground = async {
            coordinator.background {
                backgroundStarted.complete(Unit)
                awaitCancellation()
            }
        }
        backgroundStarted.await()
        coordinator.setReaderSessionActive(true)
        assertFalse(withTimeout(1_000) { activeBackground.await() })

        val queuedBackground = async { coordinator.background { } }
        delay(30)
        assertFalse(queuedBackground.isCompleted)

        coordinator.setReaderSessionActive(false)
        assertTrue(withTimeout(1_000) { queuedBackground.await() })
    }

    @Test
    fun animationKeepsRequestedChapterRunningButHoldsNeighbourPrefetch() = runBlocking {
        val coordinator = EpubParseCoordinator()
        coordinator.setAppAnimationActive(true)

        val requestedChapter = async { coordinator.interactive { "当前正文" } }
        val neighbour = async { coordinator.prefetch { "下一章" } }

        assertEquals("当前正文", withTimeout(1_000) { requestedChapter.await() })
        delay(30)
        assertFalse(neighbour.isCompleted)

        coordinator.setAppAnimationActive(false)
        assertEquals("下一章", withTimeout(1_000) { neighbour.await() })
    }

    @Test
    fun animationPreemptsAnActiveNeighbourPrefetch() = runBlocking {
        val coordinator = EpubParseCoordinator()
        val prefetchStarted = CompletableDeferred<Unit>()
        val neighbour = async {
            runCatching {
                coordinator.prefetch {
                    prefetchStarted.complete(Unit)
                    awaitCancellation()
                }
            }
        }
        prefetchStarted.await()

        coordinator.setAppAnimationActive(true)

        assertTrue(withTimeout(1_000) { neighbour.await().isFailure })
    }

    @Test
    fun requestedChapterPreemptsNeighbourAndStartsImmediately() = runBlocking {
        val coordinator = EpubParseCoordinator()
        val prefetchStarted = CompletableDeferred<Unit>()
        val neighbour = async {
            runCatching {
                coordinator.prefetch {
                    prefetchStarted.complete(Unit)
                    awaitCancellation()
                }
            }
        }
        prefetchStarted.await()

        val requestedChapter = coordinator.interactive { "用户刚刚翻到的正文" }

        assertEquals("用户刚刚翻到的正文", requestedChapter)
        assertTrue(withTimeout(1_000) { neighbour.await().isFailure })
    }
}
