package com.kixyu9527.kixyubook.core.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class DriveObject(
    val id: String,
    val name: String,
    val objectKey: String,
    val mimeType: String,
    val modifiedAt: Long,
    val version: Long,
    val size: Long,
    val md5: String?,
)

data class DriveChange(val fileId: String, val removed: Boolean, val file: DriveObject?)
data class DriveChangePage(val changes: List<DriveChange>, val nextPageToken: String?, val newStartPageToken: String?)

class DriveHttpException(val statusCode: Int, message: String) : Exception(message)

@Singleton
class DriveAppDataClient @Inject constructor() {
    suspend fun listAll(token: String): List<DriveObject> {
        val result = mutableListOf<DriveObject>()
        var pageToken: String? = null
        do {
            val query = buildList {
                add("spaces=appDataFolder")
                add("pageSize=1000")
                add("fields=${encode("nextPageToken,files(id,name,mimeType,modifiedTime,version,size,md5Checksum,appProperties)")}")
                pageToken?.let { add("pageToken=${encode(it)}") }
            }.joinToString("&")
            val json = requestJson("GET", "$DRIVE_API/files?$query", token)
            json.optJSONArray("files")?.forEachObject { result += it.toDriveObject() }
            pageToken = json.optString("nextPageToken").takeIf(String::isNotBlank)
        } while (pageToken != null)
        return result
    }

    suspend fun startPageToken(token: String): String = requestJson(
        "GET",
        "$DRIVE_API/changes/startPageToken?spaces=appDataFolder",
        token,
    ).getString("startPageToken")

    suspend fun listChanges(token: String, initialPageToken: String): DriveChangePage {
        val changes = mutableListOf<DriveChange>()
        var pageToken: String? = initialPageToken
        var newStart: String? = null
        do {
            val fields = "nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,mimeType,modifiedTime,version,size,md5Checksum,appProperties))"
            val url = "$DRIVE_API/changes?pageSize=1000&spaces=appDataFolder&includeRemoved=true" +
                "&fields=${encode(fields)}&pageToken=${encode(requireNotNull(pageToken))}"
            val json = requestJson("GET", url, token)
            json.optJSONArray("changes")?.forEachObject { change ->
                changes += DriveChange(
                    fileId = change.getString("fileId"),
                    removed = change.optBoolean("removed"),
                    file = change.optJSONObject("file")?.toDriveObject(),
                )
            }
            pageToken = json.optString("nextPageToken").takeIf(String::isNotBlank)
            newStart = json.optString("newStartPageToken").takeIf(String::isNotBlank) ?: newStart
        } while (pageToken != null)
        return DriveChangePage(changes, pageToken, newStart)
    }

    suspend fun download(token: String, fileId: String, destination: File) {
        destination.parentFile?.mkdirs()
        val connection = open("$DRIVE_API/files/${encode(fileId)}?alt=media", "GET", token)
        try {
            connection.connect()
            ensureSuccess(connection)
            connection.inputStream.use { input -> destination.outputStream().buffered().use(input::copyTo) }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun upload(
        token: String,
        name: String,
        objectKey: String,
        mimeType: String,
        source: File,
        existingFileId: String? = null,
    ): DriveObject {
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", mimeType)
            .put("appProperties", JSONObject().put("objectKey", objectKey).put("schema", "1"))
        if (existingFileId == null) metadata.put("parents", JSONArray().put("appDataFolder"))
        val method = if (existingFileId == null) "POST" else "PATCH"
        val endpoint = if (existingFileId == null) {
            "$DRIVE_UPLOAD/files?uploadType=resumable&fields=${encode(FILE_FIELDS)}"
        } else {
            "$DRIVE_UPLOAD/files/${encode(existingFileId)}?uploadType=resumable&fields=${encode(FILE_FIELDS)}"
        }
        val session = open(endpoint, method, token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", mimeType)
            setRequestProperty("X-Upload-Content-Length", source.length().toString())
        }
        val location = try {
            session.outputStream.use { it.write(metadata.toString().toByteArray()) }
            ensureSuccess(session)
            session.getHeaderField("Location") ?: error("Google Drive 未返回上传地址")
        } finally {
            session.disconnect()
        }
        val upload = open(location, "PUT", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", mimeType)
            setFixedLengthStreamingMode(source.length())
        }
        return try {
            source.inputStream().buffered().use { input -> upload.outputStream.buffered().use(input::copyTo) }
            ensureSuccess(upload)
            JSONObject(upload.inputStream.bufferedReader().use { it.readText() }).toDriveObject()
        } finally {
            upload.disconnect()
        }
    }

    suspend fun delete(token: String, fileId: String) {
        val connection = open("$DRIVE_API/files/${encode(fileId)}", "DELETE", token)
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_NOT_FOUND) ensureSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestJson(method: String, url: String, token: String): JSONObject {
        val connection = open(url, method, token)
        return try {
            connection.connect()
            ensureSuccess(connection)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String, token: String) =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

    private fun ensureSuccess(connection: HttpURLConnection) {
        if (connection.responseCode in 200..299) return
        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw DriveHttpException(connection.responseCode, body.ifBlank { "Google Drive 请求失败 (${connection.responseCode})" })
    }

    private fun JSONObject.toDriveObject(): DriveObject {
        val properties = optJSONObject("appProperties")
        return DriveObject(
            id = getString("id"),
            name = optString("name"),
            objectKey = properties?.optString("objectKey").orEmpty().ifBlank { optString("name") },
            mimeType = optString("mimeType", "application/octet-stream"),
            modifiedAt = optString("modifiedTime").takeIf(String::isNotBlank)?.let { Instant.parse(it).toEpochMilli() } ?: 0,
            version = optLong("version"),
            size = optLong("size"),
            md5 = optString("md5Checksum").takeIf(String::isNotBlank),
        )
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) block(getJSONObject(index))
    }

    private companion object {
        const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_FIELDS = "id,name,mimeType,modifiedTime,version,size,md5Checksum,appProperties"
        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
