package com.kixyu9527.kixyubook.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveStorageQuotaTest {
    @Test
    fun `reports fraction and remaining storage`() {
        val quota = DriveStorageQuota(
            usageBytes = 6L * GIB,
            limitBytes = 10L * GIB,
            usageInDriveBytes = 4L * GIB,
            usageInDriveTrashBytes = GIB / 2,
        )

        assertEquals(4L * GIB, quota.remainingBytes)
        assertEquals(0.6f, quota.usedFraction ?: 0f, 0.001f)
        assertFalse(quota.isNearlyFull)
    }

    @Test
    fun `warns when remaining storage is low`() {
        val quota = DriveStorageQuota(
            usageBytes = 14L * GIB + 512L * MIB,
            limitBytes = 15L * GIB,
            usageInDriveBytes = 0L,
            usageInDriveTrashBytes = 0L,
        )

        assertTrue(quota.isNearlyFull)
    }

    @Test
    fun `supports accounts without a reported limit`() {
        val quota = DriveStorageQuota(
            usageBytes = 2L * GIB,
            limitBytes = null,
            usageInDriveBytes = GIB,
            usageInDriveTrashBytes = 0L,
        )

        assertNull(quota.remainingBytes)
        assertNull(quota.usedFraction)
        assertFalse(quota.isNearlyFull)
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
