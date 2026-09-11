package com.kixyu9527.kixyubook.core.common.operation

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UserOperationControllerTest {
    @Test fun dismissedFailureCannotRetryAnObsoletePayload() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            var calls = 0
            controller.submit { calls++; throw IOException() }
            val failed = controller.state.value.attempt
            controller.dismissFailure(failed)
            controller.retry(failed)
            assertEquals(1, calls)
            controller.submit { calls++ }
            assertTrue(controller.state.value.succeeded)
            assertEquals(2, calls)
        } finally { scope.cancel() }
    }
    @Test fun deletionRequiresConfirmationAndCancellationPreservesData() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            var deleted = false
            controller.confirmDelete { deleted = true }
            assertFalse(deleted)
            controller.cancelConfirmation()
            controller.acceptConfirmation()
            assertFalse(deleted)
            controller.confirmDelete { deleted = true }
            val request = controller.state.value.attempt
            controller.acceptConfirmation()
            assertTrue(deleted)
            assertEquals(request, controller.state.value.attempt)
            assertTrue(controller.state.value.succeeded)
        } finally { scope.cancel() }
    }
    @Test fun failedPayloadCanRetryWithoutChangingRequestIdentity() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            var calls = 0
            controller.submit { if (++calls == 1) throw IOException("disk full") }
            assertTrue(controller.state.value.failed)
            val request = controller.state.value.attempt
            controller.retry(request)
            assertEquals(2, calls)
            assertEquals(request, controller.state.value.attempt)
            assertTrue(controller.state.value.succeeded)
            controller.retry(request)
            assertEquals(2, calls)
        } finally { scope.cancel() }
    }

    @Test fun deleteKindIsExposedThroughFailureRetryAndSuccess() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            var calls = 0
            controller.submit(kind = UserOperationKind.DELETE) {
                calls++
                if (calls == 1) throw IOException()
            }
            assertEquals(UserOperationKind.DELETE, controller.state.value.kind)
            assertTrue(controller.state.value.failed)
            controller.retry(controller.state.value.attempt)
            assertTrue(controller.state.value.succeeded)
            assertEquals(UserOperationKind.DELETE, controller.state.value.kind)
        } finally { scope.cancel() }
    }

    @Test fun submitDefaultsToGenericKind() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            controller.submit { }
            assertEquals(UserOperationKind.GENERIC, controller.state.value.kind)
        } finally { scope.cancel() }
    }

    @Test fun repeatedTapsAreRejectedAndCancellationIsNotReportedAsFailure() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var errors = 0
        val controller = UserOperationController(scope) { errors++ }
        controller.submit { awaitCancellation() }
        var duplicate = false
        controller.submit { duplicate = true }
        assertFalse(duplicate)
        assertTrue(controller.state.value.running)
        scope.cancel()
        assertFalse(controller.state.value.failed)
        assertFalse(controller.state.value.running)
        assertEquals(0, errors)
    }
}
