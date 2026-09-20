package com.kixyu9527.kixyubook.core.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class RemoteSyncUuidValidationTest {
    private val canonical = UUID.randomUUID().toString()

    @Test
    fun onlyCanonicalLowercaseUuidsAreAccepted() {
        assertEquals(canonical, requireCanonicalSyncUuid(canonical, "id"))
        assertNull(canonicalSyncUuidOrNull("../evil"))
        assertNull(canonicalSyncUuidOrNull("books/../evil"))
        assertNull(canonicalSyncUuidOrNull(""))
        assertNull(
            "uppercase variants would create a second identity for the same object",
            canonicalSyncUuidOrNull(canonical.uppercase()),
        )
        assertThrows(IllegalArgumentException::class.java) {
            requireCanonicalSyncUuid("../evil", "id")
        }
    }

    @Test
    fun aMaliciousBookPayloadIsRejectedBeforeItBecomesAFileName() {
        val json = JSONObject()
            .put("uuid", "../evil")
            .put("contentHash", "hash")
            .put("title", "书")

        assertThrows(IllegalArgumentException::class.java) { parseBook(json) }
    }

    @Test
    fun aMaliciousAnnotationPayloadIsRejected() {
        val json = JSONObject()
            .put("uuid", "../../evil")
            .put("bookUuid", canonical)

        assertThrows(IllegalArgumentException::class.java) { parseAnnotation(json) }
    }
}
