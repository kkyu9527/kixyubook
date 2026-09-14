package com.kixyu9527.kixyubook

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide messages shown by the app-level bottom snackbar. Used where the emitter has no
 * Compose scope (the update downloader and its completion receiver) so those prompts use the same
 * glass + logo surface as every other bottom notification instead of a system toast.
 */
@Singleton
class AppTransientMessages @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val store = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _pending = MutableStateFlow(readPending())
    val pending: StateFlow<List<String>> = _pending.asStateFlow()

    /** Ephemeral notice. Dropped when nothing is listening (e.g. the app is in the background). */
    fun send(message: String) {
        _messages.tryEmit(message)
    }

    /**
     * A result the user must see (for example an update verification failure reported by the
     * download receiver while no Activity is attached). Persisted so a process death between the
     * report and the next launch does not lose it; kept until [acknowledge].
     */
    fun sendDurable(message: String) {
        _pending.update { current ->
            if (message in current) current else (current + message).also(::persist)
        }
    }

    /** Called once a durable message has been shown and dismissed. */
    fun acknowledge(message: String) {
        _pending.update { current ->
            val next = current - message
            persist(next)
            next
        }
    }

    private fun readPending(): List<String> = runCatching {
        val array = JSONArray(store.getString(KEY_PENDING, "[]"))
        buildList { for (index in 0 until array.length()) add(array.getString(index)) }
    }.getOrDefault(emptyList())

    private fun persist(messages: List<String>) {
        store.edit { putString(KEY_PENDING, JSONArray(messages).toString()) }
    }

    private companion object {
        const val PREFS_NAME = "app_transient_messages"
        const val KEY_PENDING = "pending_durable"
    }
}
