package com.kixyu9527.kixyubook

import com.kixyu9527.kixyubook.core.common.model.BookPreparationProgress
import com.kixyu9527.kixyubook.core.common.model.BookPreparationStage
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BookOpeningControllerTest {
    @Test fun aBookThatIsAlreadyAvailableNavigatesWithoutShowingLoadingFeedback() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var navigations = 0
            val controller = BookOpeningController(scope, { _, _ -> Result.success(Unit) })
            controller.open("local") { navigations++ }
            yield()
            assertEquals(1, navigations)
            assertNull(controller.state.value)
        } finally { scope.cancel() }
    }

    @Test fun cloudBooksStayOnTheShelfUntilTheFileDirectoryAndPositionAreReady() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val ready = CompletableDeferred<Unit>()
        try {
            var navigations = 0
            var calls = 0
            val controller = BookOpeningController(scope, { _, progress ->
                calls++
                progress(BookPreparationProgress(BookPreparationStage.DOWNLOADING, 20, 100))
                ready.await()
                progress(BookPreparationProgress(BookPreparationStage.RESTORING_PROGRESS))
                Result.success(Unit)
            }, feedbackDelayMillis = 0)
            controller.open("cloud") { navigations++ }
            controller.open("cloud") { fail("duplicate tap must not open twice") }
            yield()
            assertEquals(0, navigations)
            assertEquals(1, calls)
            assertEquals(BookPreparationStage.DOWNLOADING, controller.state.value!!.progress.stage)
            ready.complete(Unit)
            yield()
            assertEquals(1, navigations)
            assertNull(controller.state.value)
        } finally { ready.complete(Unit); scope.cancel() }
    }

    @Test fun failuresStayOnTheShelfAndTheSameRequestCanBeRetried() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var attempts = 0
            var navigations = 0
            val controller = BookOpeningController(scope, { _, _ ->
                if (++attempts == 1) Result.failure(IOException("offline")) else Result.success(Unit)
            })
            controller.open("cloud") { navigations++ }
            yield()
            assertEquals(0, navigations)
            assertTrue(controller.state.value!!.error is IOException)
            assertTrue(controller.state.value!!.feedbackVisible)
            controller.retry()
            yield()
            assertEquals(2, attempts)
            assertEquals(1, navigations)
            assertNull(controller.state.value)
        } finally { scope.cancel() }
    }

    @Test fun providerCancellationDoesNotStrandTheBookInAnInvisibleOpeningState() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var calls = 0
            var opened = 0
            val controller = BookOpeningController(scope, { _, _ ->
                if (++calls == 1) throw CancellationException("account changed")
                Result.success(Unit)
            })
            controller.open("book") { opened++ }
            yield()
            assertNull(controller.state.value)
            controller.open("book") { opened++ }
            yield()
            assertEquals(2, calls)
            assertEquals(1, opened)
        } finally { scope.cancel() }
    }

    @Test fun cancellingWaitIgnoresLateProgressAndCompletionFromAnUncooperativeSource() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        try {
            var navigations = 0
            val controller = BookOpeningController(scope, { _, progress ->
                withContext(NonCancellable) {
                    release.await()
                    progress(BookPreparationProgress(BookPreparationStage.READY))
                    finished.complete(Unit)
                }
                Result.success(Unit)
            })
            controller.open("cloud") { navigations++ }
            yield()
            controller.cancel()
            release.complete(Unit)
            withTimeout(2_000) { finished.await() }
            yield()
            assertNull(controller.state.value)
            assertEquals(0, navigations)
        } finally { release.complete(Unit); scope.cancel() }
    }

    @Test fun anOlderOpenCannotReplaceOrNavigateOverANewerRequest() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val old = CompletableDeferred<Unit>()
        val latest = CompletableDeferred<Unit>()
        try {
            val navigated = mutableListOf<String>()
            val controller = BookOpeningController(scope, { uuid, progress ->
                if (uuid == "old") withContext(NonCancellable) {
                    old.await(); progress(BookPreparationProgress(BookPreparationStage.READY))
                } else latest.await()
                Result.success(Unit)
            })
            controller.open("old") { navigated += "old" }
            yield()
            controller.open("new") { navigated += "new" }
            yield()
            old.complete(Unit)
            yield()
            assertEquals("new", controller.state.value?.bookUuid)
            latest.complete(Unit)
            yield()
            assertEquals(listOf("new"), navigated)
        } finally { old.complete(Unit); latest.complete(Unit); scope.cancel() }
    }
}
