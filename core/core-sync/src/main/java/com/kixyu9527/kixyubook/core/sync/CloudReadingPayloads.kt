package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import org.json.JSONObject

/** Receive stable reading anchors before the file/directory exists; apply them afterwards. */
internal class CloudReadingPayloads(private val dao: SyncDao) {
    suspend fun read(info: DriveObject, fetch: suspend () -> JSONObject): JSONObject {
        val readingData = info.objectKey.startsWith("progress/") || info.objectKey.startsWith("bookmarks/")
        if (!readingData) return fetch()
        val previous = dao.receivedObject(info.objectKey)
        if (previous?.matches(info) == true) previous.payloadJson?.let { return JSONObject(it) }
        val json = fetch()
        dao.cacheReceivedPayload(info.objectKey, info.id, info.version, info.modifiedAt, json.toString())
        return json
    }
}
