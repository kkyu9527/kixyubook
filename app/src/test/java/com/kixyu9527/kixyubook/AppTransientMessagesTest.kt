package com.kixyu9527.kixyubook

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppTransientMessagesTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearStore() {
        context.getSharedPreferences("app_transient_messages", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun durableMessageSurvivesManagerRecreation() {
        // The receiver can report a failure and the process can die before the next launch.
        AppTransientMessages(context).sendDurable("update verification failed")

        val recreated = AppTransientMessages(context)
        assertEquals(listOf("update verification failed"), recreated.pending.value)

        recreated.acknowledge("update verification failed")
        assertEquals(emptyList<String>(), AppTransientMessages(context).pending.value)
    }

    @Test
    fun transientMessageReachesAnActiveSubscriber() = runBlocking {
        val messages = AppTransientMessages(context)
        // UNDISPATCHED subscribes before `async` returns, so the emission cannot be dropped.
        val received = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { messages.messages.first() }
        }
        messages.send("download started")
        assertEquals("download started", received.await())
    }
}
