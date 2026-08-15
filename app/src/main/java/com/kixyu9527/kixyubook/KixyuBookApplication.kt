package com.kixyu9527.kixyubook

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kixyu9527.kixyubook.core.common.repository.CloudSyncCoordinator
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KixyuBookApplication : Application(), DefaultLifecycleObserver {
    @Inject lateinit var cloudSync: CloudSyncCoordinator
    private lateinit var fairMemoryManager: HyperOsFairMemoryManager

    override fun onCreate() {
        super<Application>.onCreate()
        DiagnosticLog.initialize(this)
        fairMemoryManager = HyperOsFairMemoryManager(this).also(HyperOsFairMemoryManager::start)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        fairMemoryManager.handleAndroidTrim(level)
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        fairMemoryManager.handleAndroidLowMemory()
    }

    override fun onTerminate() {
        fairMemoryManager.stop()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        super.onTerminate()
    }

    override fun onStart(owner: LifecycleOwner) {
        cloudSync.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        cloudSync.onAppBackground()
    }
}
