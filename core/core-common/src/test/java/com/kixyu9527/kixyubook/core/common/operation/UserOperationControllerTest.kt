package com.kixyu9527.kixyubook.core.common.operation

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

private fun UserOperationState.kindOrNull(): UserOperationKind? = when (this) {
    is UserOperationState.Running -> kind
    is UserOperationState.Succeeded -> kind
    is UserOperationState.Failed -> kind
    else -> null
}

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
            assertTrue(controller.state.value is UserOperationState.Succeeded)
            assertEquals(2, calls)
        } finally { scope.cancel() }
    }
    @Test fun deletionRequiresConfirmationAndCancellationPreservesData() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            var deleted = false
            controller.confirmDelete { deleted = true }
            assertTrue(controller.state.value is UserOperationState.Confirming)
            assertFalse(deleted)
            controller.cancelConfirmation()
            controller.acceptConfirmation()
            assertFalse(deleted)
            controller.confirmDelete { deleted = true }
            val request = controller.state.value.attempt
            controller.acceptConfirmation()
            assertTrue(deleted)
            assertEquals(request, controller.state.value.attempt)
            assertTrue(controller.state.value is UserOperationState.Succeeded)
        } finally { scope.cancel() }
    }
    @Test fun failedPayloadCanRetryWithoutChangingRequestIdentity() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            var calls = 0
            controller.submit { if (++calls == 1) throw IOException("disk full") }
            assertTrue(controller.state.value is UserOperationState.Failed)
            val request = controller.state.value.attempt
            controller.retry(request)
            assertEquals(2, calls)
            assertEquals(request, controller.state.value.attempt)
            assertTrue(controller.state.value is UserOperationState.Succeeded)
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
            assertEquals(UserOperationKind.DELETE, controller.state.value.kindOrNull())
            assertTrue(controller.state.value is UserOperationState.Failed)
            controller.retry(controller.state.value.attempt)
            assertTrue(controller.state.value is UserOperationState.Succeeded)
            assertEquals(UserOperationKind.DELETE, controller.state.value.kindOrNull())
        } finally { scope.cancel() }
    }

    @Test fun submitDefaultsToGenericKind() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            controller.submit { }
            assertEquals(UserOperationKind.GENERIC, controller.state.value.kindOrNull())
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
        assertTrue(controller.state.value is UserOperationState.Running)
        scope.cancel()
        assertFalse(controller.state.value is UserOperationState.Failed)
        assertFalse(controller.state.value is UserOperationState.Running)
        assertEquals(0, errors)
    }

    @Test fun confirmationAndRunningAreMutuallyExclusive() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = UserOperationController(scope)
            controller.confirmDelete { }
            // A normal submit cannot start while a confirmation is pending.
            var started = false
            controller.submit { started = true }
            assertFalse(started)
            assertTrue(controller.state.value is UserOperationState.Confirming)
        } finally { scope.cancel() }
    }
}
