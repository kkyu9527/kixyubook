package com.kixyu9527.kixyubook

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kixyu9527.kixyubook.core.common.repository.CloudSyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KixyuBookApplication : Application(), DefaultLifecycleObserver {
    @Inject lateinit var cloudSync: CloudSyncCoordinator

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        cloudSync.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        cloudSync.onAppBackground()
    }
}
