package com.kixyu9527.kixyubook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureDispatchResult
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry

/**
 * Bridges Android and HyperOS memory-pressure signals into feature-owned caches.
 *
 * HyperOS delivers its fair-running-memory protocol through an exported dynamic receiver and
 * expects a Binder result within three seconds. Work stays on a dedicated HandlerThread so the
 * reader/render thread is never blocked by cache eviction or a final progress checkpoint.
 */
internal class HyperOsFairMemoryManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val handlerThread = HandlerThread("hyperos-fair-memory")
    private lateinit var handler: Handler
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != HYPER_OS_TRIM_ACTION) return
            handleHyperOsRequest(intent)
        }
    }

    fun start() {
        if (started) return
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        val filter = IntentFilter(HYPER_OS_TRIM_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                receiver,
                filter,
                null,
                handler,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter, null, handler)
        }
        started = true
    }

    fun stop() {
        if (!started) return
        runCatching { appContext.unregisterReceiver(receiver) }
        handlerThread.quitSafely()
        started = false
    }

    fun handleAndroidTrim(level: Int) {
        val pressure = androidPressureLevel(level) ?: return
        val result = MemoryPressureRegistry.dispatch(pressure)
        recordHandled(
            source = "android",
            pressure = pressure,
            result = result,
            details = mapOf("trimLevel" to level),
        )
    }

    fun handleAndroidLowMemory() {
        val result = MemoryPressureRegistry.dispatch(MemoryPressureLevel.CRITICAL)
        recordHandled(
            source = "android_low_memory",
            pressure = MemoryPressureLevel.CRITICAL,
            result = result,
        )
    }

    private fun handleHyperOsRequest(intent: Intent) {
        val common = intent.extras?.getBundle(COMMON_BUNDLE_KEY)
        if (common == null) {
            recordMalformed("缺少 common 数据")
            return
        }
        val notifyType = common.getInt(NOTIFY_TYPE_KEY, UNKNOWN_ID)
        val notifyId = common.getInt(NOTIFY_ID_KEY, UNKNOWN_ID)
        val requestedAction = common.getString(ACTION_KEY).orEmpty()
        val callback = common.getBinder(CALLBACK_KEY)
        val extra = intent.extras?.getBundle(EXTRA_BUNDLE_KEY)
        val pressure = if (requestedAction.equals(KILL_ACTION, ignoreCase = true)) {
            MemoryPressureLevel.CRITICAL
        } else {
            MemoryPressureLevel.MODERATE
        }
        val startedAt = System.nanoTime()
        val result = MemoryPressureRegistry.dispatch(pressure)
        val replied = callback != null && reply(
            callback = callback,
            notifyType = notifyType,
            notifyId = notifyId,
            success = result.failureCount == 0,
            pressure = pressure,
        )
        val outcome = when {
            result.failureCount > 0 -> "memory_release_failed"
            !replied -> "memory_callback_failed"
            else -> "success"
        }
        DiagnosticLog.record(
            DiagnosticLog.Category.READER,
            "memory_pressure_handled",
            elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L,
            outcome = outcome,
            details = buildMap {
                put("source", "hyperos")
                put("pressure", pressure.name)
                put("action", requestedAction.ifBlank { TRIM_ACTION })
                put("notifyType", notifyType)
                put("notifyId", notifyId)
                put("listeners", result.listenerCount)
                put("listenerFailures", result.failureCount)
                put("callbackReplied", replied)
                common.getString(REASON_KEY)?.takeIf(String::isNotBlank)?.let { put("reason", it) }
                extra?.putMemoryDetailsInto(this)
            },
        )
    }

    private fun reply(
        callback: IBinder,
        notifyType: Int,
        notifyId: Int,
        success: Boolean,
        pressure: MemoryPressureLevel,
    ): Boolean = runCatching {
        val data = Parcel.obtain()
        try {
            data.writeInt(notifyType)
            data.writeInt(notifyId)
            data.writeInt(if (success) RESULT_SUCCESS else RESULT_FAILURE)
            data.writeBundle(
                Bundle().apply {
                    putString(REPLY_KEY, "Kixyu Book ${pressure.name.lowercase()} checkpoint completed")
                },
            )
            callback.transact(
                IBinder.FIRST_CALL_TRANSACTION,
                data,
                null,
                IBinder.FLAG_ONEWAY,
            )
        } finally {
            data.recycle()
        }
    }.getOrDefault(false)

    private fun recordHandled(
        source: String,
        pressure: MemoryPressureLevel,
        result: MemoryPressureDispatchResult,
        details: Map<String, Any?> = emptyMap(),
    ) {
        DiagnosticLog.record(
            DiagnosticLog.Category.READER,
            "memory_pressure_handled",
            outcome = if (result.failureCount == 0) "success" else "memory_release_failed",
            details = buildMap {
                put("source", source)
                put("pressure", pressure.name)
                put("listeners", result.listenerCount)
                put("listenerFailures", result.failureCount)
                putAll(details)
            },
        )
    }

    private fun recordMalformed(reason: String) {
        DiagnosticLog.record(
            DiagnosticLog.Category.READER,
            "memory_pressure_handled",
            outcome = "invalid_data",
            details = mapOf(
                "source" to "hyperos",
                "reason" to reason,
            ),
        )
    }

    private fun Bundle.putMemoryDetailsInto(target: MutableMap<String, Any?>) {
        if (containsKey(HEAP_ALLOC_KEY)) target[HEAP_ALLOC_KEY] = getInt(HEAP_ALLOC_KEY)
        if (containsKey(HEAP_CAPACITY_KEY)) target[HEAP_CAPACITY_KEY] = getInt(HEAP_CAPACITY_KEY)
        if (containsKey(PSS_KEY)) target[PSS_KEY] = getInt(PSS_KEY)
        if (containsKey(PSS_LIMIT_KEY)) target[PSS_LIMIT_KEY] = getInt(PSS_LIMIT_KEY)
    }

    companion object {
        private const val HYPER_OS_TRIM_ACTION = "itgsa.intent.action.TRIM"
        private const val COMMON_BUNDLE_KEY = "common"
        private const val EXTRA_BUNDLE_KEY = "extra"
        private const val NOTIFY_TYPE_KEY = "notifyType"
        private const val NOTIFY_ID_KEY = "notifyId"
        private const val REASON_KEY = "reason"
        private const val ACTION_KEY = "action"
        private const val CALLBACK_KEY = "callback"
        private const val HEAP_ALLOC_KEY = "heapAlloc"
        private const val HEAP_CAPACITY_KEY = "heapCapacity"
        private const val PSS_KEY = "pss"
        private const val PSS_LIMIT_KEY = "pssLimit"
        private const val REPLY_KEY = "reply"
        private const val TRIM_ACTION = "TRIM"
        private const val KILL_ACTION = "KILL"
        private const val RESULT_SUCCESS = 0
        private const val RESULT_FAILURE = 1
        private const val UNKNOWN_ID = -1

        @Suppress("DEPRECATION")
        private fun androidPressureLevel(level: Int): MemoryPressureLevel? = when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE ->
                MemoryPressureLevel.CRITICAL
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ->
                MemoryPressureLevel.MODERATE
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                MemoryPressureLevel.BACKGROUND
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
                MemoryPressureLevel.CRITICAL
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ->
                MemoryPressureLevel.MODERATE
            else -> null
        }
    }
}
