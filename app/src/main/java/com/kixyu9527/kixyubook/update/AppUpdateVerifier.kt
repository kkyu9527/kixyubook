package com.kixyu9527.kixyubook.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal sealed interface ApkVerificationResult {
    data object Valid : ApkVerificationResult

    data object Unreadable : ApkVerificationResult

    data object WrongPackage : ApkVerificationResult

    data object NotNewer : ApkVerificationResult

    data object WrongSignature : ApkVerificationResult
}

internal data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val certificateDigests: Set<String>,
)

internal fun verifyUpdateIdentity(
    installed: ApkIdentity,
    archive: ApkIdentity,
): ApkVerificationResult {
    if (archive.packageName != installed.packageName) return ApkVerificationResult.WrongPackage
    if (archive.versionCode <= installed.versionCode) return ApkVerificationResult.NotNewer
    if (
        installed.certificateDigests.isEmpty() ||
        archive.certificateDigests.isEmpty() ||
        installed.certificateDigests.intersect(archive.certificateDigests).isEmpty()
    ) {
        return ApkVerificationResult.WrongSignature
    }
    return ApkVerificationResult.Valid
}

/** Verifies the downloaded archive belongs to this installed app before Android sees it. */
internal fun verifyUpdateApk(
    context: Context,
    apk: File,
): ApkVerificationResult {
    if (!apk.isFile || apk.length() <= 0L) return ApkVerificationResult.Unreadable

    val packageManager = context.packageManager
    val archive = packageManager.archivePackageInfo(apk) ?: return ApkVerificationResult.Unreadable
    val installed = runCatching {
        packageManager.installedPackageInfo(context.packageName)
    }.getOrNull() ?: return ApkVerificationResult.Unreadable
    return verifyUpdateIdentity(installed.toIdentity(), archive.toIdentity())
}

private fun PackageInfo.toIdentity() = ApkIdentity(
    packageName = packageName,
    versionCode = compatLongVersionCode(),
    certificateDigests = signingCertificateDigests(),
)

private fun PackageManager.archivePackageInfo(apk: File): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        getPackageArchiveInfo(apk.absolutePath, signingFlags())
    }

private fun PackageManager.installedPackageInfo(packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, signingFlags())
    }

private fun PackageInfo.compatLongVersionCode(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

private fun PackageInfo.signingCertificateDigests(): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.run {
            if (hasMultipleSigners()) apkContentsSigners else signingCertificateHistory
        }.orEmpty()
    } else {
        @Suppress("DEPRECATION")
        signatures.orEmpty()
    }
    return signatures.mapTo(linkedSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

@Suppress("DEPRECATION")
private fun signingFlags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
