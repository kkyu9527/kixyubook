package com.kixyu9527.kixyubook

import android.app.Application
import android.content.Context
import com.kixyu9527.kixyubook.core.database.recoverInterruptedBackupRestore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kixyu9527.kixyubook.core.common.repository.CloudSyncCoordinator
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import com.kixyu9527.kixyubook.core.common.repository.StartupRecoveryState
import androidx.work.WorkManager
import androidx.work.Configuration

@HiltAndroidApp
class KixyuBookApplication : Application(), DefaultLifecycleObserver {
    @Inject lateinit var cloudSync: Provider<CloudSyncCoordinator>
    private lateinit var fairMemoryManager: HyperOsFairMemoryManager

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupRecoveryState.recover { recoverInterruptedBackupRestore(base) }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        DiagnosticLog.initialize(this)
        if (StartupRecoveryState.failure == null) WorkManager.initialize(this, Configuration.Builder().build())
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
        if (StartupRecoveryState.failure == null) cloudSync.get().onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (StartupRecoveryState.failure == null) cloudSync.get().onAppBackground()
    }
}
