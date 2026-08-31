package com.kixyu9527.kixyubook.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateVerifierTest {
    @Test
    fun acceptsNewerArchiveWithMatchingSigningHistory() {
        val result = verifyUpdateIdentity(
            installed = identity(version = 3000, certificates = setOf("old")),
            archive = identity(version = 3001, certificates = setOf("new", "old")),
        )

        assertEquals(ApkVerificationResult.Valid, result)
    }

    @Test
    fun rejectsArchiveFromAnotherPackage() {
        val result = verifyUpdateIdentity(
            installed = identity(version = 3000),
            archive = identity(packageName = "other.app", version = 3001),
        )

        assertEquals(ApkVerificationResult.WrongPackage, result)
    }

    @Test
    fun rejectsSameOrOlderVersion() {
        assertEquals(
            ApkVerificationResult.NotNewer,
            verifyUpdateIdentity(identity(version = 3000), identity(version = 3000)),
        )
        assertEquals(
            ApkVerificationResult.NotNewer,
            verifyUpdateIdentity(identity(version = 3000), identity(version = 2999)),
        )
    }

    @Test
    fun rejectsMissingOrDifferentCertificate() {
        assertEquals(
            ApkVerificationResult.WrongSignature,
            verifyUpdateIdentity(
                identity(version = 3000, certificates = emptySet()),
                identity(version = 3001),
            ),
        )
        assertEquals(
            ApkVerificationResult.WrongSignature,
            verifyUpdateIdentity(
                identity(version = 3000, certificates = setOf("release-a")),
                identity(version = 3001, certificates = setOf("release-b")),
            ),
        )
    }

    private fun identity(
        packageName: String = "com.kixyu9527.kixyubook",
        version: Long,
        certificates: Set<String> = setOf("release"),
    ) = ApkIdentity(packageName, version, certificates)
}
