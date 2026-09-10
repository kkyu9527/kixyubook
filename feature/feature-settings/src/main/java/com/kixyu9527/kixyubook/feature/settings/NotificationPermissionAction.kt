package com.kixyu9527.kixyubook.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.launch

/** Requests notification permission only after a related user action, never at app launch. */
@Composable
internal fun rememberNotificationPermissionAction(): (Boolean, () -> Unit) -> Unit {
    val context = LocalContext.current
    val permissionPreferences = remember(context) {
        context.getSharedPreferences("notification_state", android.content.Context.MODE_PRIVATE)
    }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissionRequired by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted || !permissionRequired) action?.invoke()
        permissionRequired = false
    }
    return remember(context, launcher) {
        actionGate@{ requirePermission, action ->
            val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                action()
            } else {
                val now = System.currentTimeMillis()
                val lastRequest = permissionPreferences.getLong("last_permission_request", 0L)
                if (now - lastRequest < 48 * 60 * 60_000L) {
                    if (!requirePermission) action()
                    return@actionGate
                }
                pendingAction = action
                permissionRequired = requirePermission
                permissionPreferences.edit { putLong("last_permission_request", now) }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
