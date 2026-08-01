package com.kixyu9527.kixyubook.core.sync

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class GoogleAccountClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: SyncPreferencesStore,
) {
    suspend fun connect(activity: Activity): GoogleConnectResult = authorize(activity)

    suspend fun authorize(activity: Activity): GoogleConnectResult = runCatching {
        val result = Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest())
            .await()
        consumeAuthorizationResult(result)
    }.getOrElse { GoogleConnectResult.Failed(it.authorizationMessage()) }

    suspend fun finishAuthorization(activity: Activity, resultData: Intent?): GoogleConnectResult = runCatching {
        requireNotNull(resultData) { "已取消 Google 账号授权" }
        val result = Identity.getAuthorizationClient(activity)
            .getAuthorizationResultFromIntent(resultData)
        consumeAuthorizationResult(result)
    }.getOrElse { GoogleConnectResult.Failed(it.authorizationMessage()) }

    suspend fun accessToken(): String? {
        val result = Identity.getAuthorizationClient(context)
            .authorize(authorizationRequest())
            .await()
        return if (result.hasResolution()) null else result.accessToken
    }

    suspend fun disconnect() {
        val email = preferences.current().account?.email.orEmpty()
        if (email.isNotBlank()) {
            runCatching {
                Identity.getAuthorizationClient(context)
                    .revokeAccess(
                        RevokeAccessRequest.builder()
                            .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
                            .setScopes(requestedScopes())
                            .build(),
                    )
                    .await()
            }
        }
        preferences.clearAccount()
    }

    private fun authorizationRequest() = AuthorizationRequest.builder()
        .setRequestedScopes(requestedScopes())
        .build()

    private fun requestedScopes() = listOf(
        Scope(DRIVE_APPDATA_SCOPE),
        Scope(OPEN_ID_SCOPE),
        Scope(EMAIL_SCOPE),
        Scope(PROFILE_SCOPE),
    )

    private fun AuthorizationResult.toConnectResult(): GoogleConnectResult? = if (hasResolution()) {
        pendingIntent?.let(GoogleConnectResult::NeedsAuthorization)
            ?: GoogleConnectResult.Failed("Google 账号选择界面不可用")
    } else if (accessToken.isNullOrBlank()) {
        GoogleConnectResult.Failed("Google Drive 未返回访问令牌")
    } else {
        GoogleConnectResult.Connected
    }

    private suspend fun consumeAuthorizationResult(result: AuthorizationResult): GoogleConnectResult {
        val connectResult = result.toConnectResult()
            ?: return GoogleConnectResult.Failed("Google Drive 授权流程不可用")
        if (connectResult is GoogleConnectResult.Connected) {
            saveAuthorizedAccount(result.accessToken.orEmpty())
        }
        return connectResult
    }

    private fun Throwable.authorizationMessage(): String = when {
        this is ApiException && statusCode == 16 -> "已取消 Google 账号授权"
        this is ApiException && statusCode in setOf(8, 10) ->
            "应用尚未在 Google Cloud 注册：请为正式版包名和签名 SHA-1 创建 Android OAuth Client"
        message?.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true) == true ->
            "应用尚未在 Google Cloud 注册：请为正式版包名和签名 SHA-1 创建 Android OAuth Client"
        else -> message ?: "Google Drive 授权失败"
    }

    private suspend fun saveAuthorizedAccount(accessToken: String) = withContext(Dispatchers.IO) {
        val connection = (URL(USER_INFO_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            check(connection.responseCode in 200..299) { "无法读取 Google 账号信息" }
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val subject = json.optString("sub").ifBlank { json.optString("email") }
            val email = json.optString("email")
            check(subject.isNotBlank()) { "Google 账号信息不完整" }
            preferences.saveAccount(
                SyncAccount(
                    subject = subject,
                    email = email,
                    displayName = json.optString("name").ifBlank {
                        email.substringBefore('@').ifBlank { "Google 账号" }
                    },
                    avatarUrl = json.optString("picture").takeIf(String::isNotBlank),
                ),
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val OPEN_ID_SCOPE = "openid"
        private const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
        private const val PROFILE_SCOPE = "https://www.googleapis.com/auth/userinfo.profile"
        private const val USER_INFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
