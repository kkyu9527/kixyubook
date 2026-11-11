package com.kixyu9527.kixyubook.feature.cloud

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.operation.LatestOperationWriter
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.sync.CloudSyncManager
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.GoogleConnectResult
import com.kixyu9527.kixyubook.core.sync.InitialSyncChoice
import com.kixyu9527.kixyubook.feature.cloud.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Cohesive state holder for Drive sync, account management and body downloads. The general
 * settings page only reads the cloud summary; it no longer carries cloud mutation methods.
 */
@HiltViewModel
class CloudSettingsViewModel @Inject constructor(
    private val cloudSync: CloudSyncManager,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val operations = UserOperationController(viewModelScope)
    private val settingWrites = LatestOperationWriter(viewModelScope, operations)

    val state: StateFlow<CloudSyncState> = cloudSync.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudSyncState())

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val _authorizationRequests = Channel<PendingIntent>(Channel.BUFFERED)
    val authorizationRequests = _authorizationRequests.receiveAsFlow()

    fun connectGoogle(activity: Activity) = viewModelScope.launch {
        handleConnectResult(cloudSync.connect(activity))
    }

    fun switchGoogleAccount(activity: Activity) = viewModelScope.launch {
        handleConnectResult(cloudSync.switchAccount(activity))
    }

    fun finishGoogleAuthorization(activity: Activity, resultData: Intent?) = viewModelScope.launch {
        handleConnectResult(cloudSync.finishAuthorization(activity, resultData))
    }

    fun setCloudSyncEnabled(enabled: Boolean) = settingWrites.submit("syncEnabled") {
        cloudSync.setEnabled(enabled)
    }

    fun setSyncOriginalFiles(enabled: Boolean) = settingWrites.submit("syncOriginals") {
        cloudSync.setSyncOriginalFiles(enabled)
    }

    fun setSyncFonts(enabled: Boolean) = settingWrites.submit("syncFonts") {
        cloudSync.setSyncFonts(enabled)
    }

    fun setWifiOnlyForLargeFiles(enabled: Boolean) = settingWrites.submit("syncWifi") {
        cloudSync.setWifiOnlyForLargeFiles(enabled)
    }

    fun resolveInitialSync(choice: InitialSyncChoice) = viewModelScope.launch {
        cloudSync.resolveInitialSync(choice)
            .onFailure { _messages.emit(it.message ?: context.getString(R.string.settings_conflict_failed)) }
    }

    fun syncNow() = cloudSync.syncNow()
    fun retryBookDownload(bookUuid: String) = cloudSync.retryBookDownload(bookUuid)
    fun refreshGoogleDriveStorage(force: Boolean = false) = cloudSync.refreshStorageQuota(force)
    fun disconnectGoogle() = viewModelScope.launch { cloudSync.disconnect() }

    fun deleteCloudData(activity: Activity) = viewModelScope.launch {
        cloudSync.deleteCloudData(activity)
            .onSuccess { _messages.emit(context.getString(R.string.settings_cloud_deleted)) }
            .onFailure { _messages.emit(it.message ?: context.getString(R.string.settings_cloud_delete_failed)) }
    }

    private suspend fun handleConnectResult(result: GoogleConnectResult) {
        when (result) {
            GoogleConnectResult.Connected -> _messages.emit(context.getString(R.string.settings_account_connected))
            is GoogleConnectResult.NeedsAuthorization -> _authorizationRequests.send(result.pendingIntent)
            is GoogleConnectResult.Failed -> _messages.emit(result.message)
        }
    }
}
