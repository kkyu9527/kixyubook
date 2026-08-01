package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindCloudSyncManager(value: GoogleDriveCloudSyncManager): CloudSyncManager

    @Binds
    @Singleton
    abstract fun bindSyncMutationRecorder(value: RoomSyncMutationRecorder): SyncMutationRecorder
}
