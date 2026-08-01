package com.kixyu9527.kixyubook

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.kixyu9527.kixyubook.core.sync.CloudSyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class KixyuBookApplication : Application() {
    @Inject lateinit var cloudSyncScheduler: CloudSyncScheduler

    override fun onCreate() {
        super.onCreate()
        cloudSyncScheduler.ensurePeriodic()
        cloudSyncScheduler.requestImmediate()
    }
}
