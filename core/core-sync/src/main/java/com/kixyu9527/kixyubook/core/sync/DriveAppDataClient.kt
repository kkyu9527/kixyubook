package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

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

class DriveHttpException(
    val statusCode: Int,
    message: String,
    val retryAfterMillis: Long? = null,
) : Exception(message)

private data class ChunkUploadResult(
    val nextOffset: Long,
    val completed: DriveObject? = null,
)

@Singleton
class DriveAppDataClient @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val uploadSessions = context.getSharedPreferences("drive_resumable_uploads", Context.MODE_PRIVATE)

    suspend fun storageQuota(token: String): DriveStorageQuota {
        val fields = "storageQuota(limit,usage,usageInDrive,usageInDriveTrash)"
        val quota = requestJson(
            method = "GET",
            url = "$DRIVE_API/about?fields=${encode(fields)}",
            token = token,
        ).getJSONObject("storageQuota")
        return DriveStorageQuota(
            usageBytes = quota.longString("usage"),
            limitBytes = quota.optString("limit").takeIf(String::isNotBlank)?.toLongOrNull(),
            usageInDriveBytes = quota.longString("usageInDrive"),
            usageInDriveTrashBytes = quota.longString("usageInDriveTrash"),
        )
    }

    suspend fun findByObjectKey(token: String, objectKey: String): DriveObject? {
        val escapedKey = objectKey.replace("\\", "\\\\").replace("'", "\\'")
        val query = buildList {
            add("spaces=appDataFolder")
            add("pageSize=2")
            add("q=${encode("appProperties has { key='objectKey' and value='$escapedKey' } and trashed=false")}")
            add("fields=${encode("files($FILE_FIELDS)")}")
        }.joinToString("&")
        return requestJson("GET", "$DRIVE_API/files?$query", token)
            .optJSONArray("files")
            ?.takeIf { it.length() > 0 }
            ?.getJSONObject(0)
            ?.toDriveObject()
    }

    suspend fun listAll(token: String): List<DriveObject> {
        val result = mutableListOf<DriveObject>()
        var pageToken: String? = null
        do {
            currentCoroutineContext().ensureActive()
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
            currentCoroutineContext().ensureActive()
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
        val downloadKey = fileId.hashCode().toUInt().toString(16)
        val partial = File(destination.parentFile, ".drive-$downloadKey.part")
        withBackoff {
            val offset = partial.length()
            val connection = open("$DRIVE_API/files/${encode(fileId)}?alt=media", "GET", token).apply {
                if (offset > 0L) {
                    setRequestProperty("Range", "bytes=$offset-")
                    uploadSessions.getString("download.$downloadKey.etag", null)?.let {
                        setRequestProperty("If-Range", it)
                    }
                }
            }
            try {
                connection.connect()
                if (connection.responseCode == HTTP_RANGE_NOT_SATISFIABLE) {
                    partial.delete()
                    uploadSessions.edit { remove("download.$downloadKey.etag") }
                    // Retry from byte zero. Reuse the normal backoff loop so a broken server does
                    // not turn an invalid range response into unbounded recursion.
                    throw DriveHttpException(
                        statusCode = HttpURLConnection.HTTP_INTERNAL_ERROR,
                        message = "Google Drive 下载断点已失效，正在重试",
                    )
                }
                ensureSuccess(connection)
                connection.getHeaderField("ETag")?.let { etag ->
                    uploadSessions.edit { putString("download.$downloadKey.etag", etag) }
                }
                val append = offset > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
                connection.inputStream.use { input ->
                    FileOutputStream(partial, append).buffered().use { output ->
                        copyCancellable(input, output)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        partial.copyTo(destination, overwrite = true)
        partial.delete()
        uploadSessions.edit { remove("download.$downloadKey.etag") }
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
        if (source.length() <= MULTIPART_UPLOAD_MAX_BYTES) {
            val sessionKey = objectKey.hashCode().toUInt().toString(16)
            uploadSessions.edit {
                remove("$sessionKey.url")
                remove("$sessionKey.length")
            }
            return uploadMultipart(token, method, existingFileId, mimeType, source, metadata)
        }
        val endpoint = if (existingFileId == null) {
            "$DRIVE_UPLOAD/files?uploadType=resumable&fields=${encode(FILE_FIELDS)}"
        } else {
            "$DRIVE_UPLOAD/files/${encode(existingFileId)}?uploadType=resumable&fields=${encode(FILE_FIELDS)}"
        }
        val sessionKey = objectKey.hashCode().toUInt().toString(16)
        val storedLength = uploadSessions.getLong("$sessionKey.length", -1L)
        var location = uploadSessions.getString("$sessionKey.url", null)
            ?.takeIf { storedLength == source.length() }
        val resumedSession = location != null
        if (location == null) {
            uploadSessions.edit {
                remove("$sessionKey.url")
                remove("$sessionKey.length")
            }
            location = createUploadSession(endpoint, method, token, mimeType, source.length(), metadata)
            uploadSessions.edit {
                putString("$sessionKey.url", location)
                putLong("$sessionKey.length", source.length())
            }
        }
        return try {
            uploadChunks(location, token, mimeType, source, objectKey, initialOffset = if (resumedSession) null else 0L)
        } catch (error: DriveHttpException) {
            if (error.statusCode == 404 || error.statusCode == 410) {
                uploadSessions.edit {
                    remove("$sessionKey.url")
                    remove("$sessionKey.length")
                }
                val replacement = createUploadSession(endpoint, method, token, mimeType, source.length(), metadata)
                uploadSessions.edit {
                    putString("$sessionKey.url", replacement)
                    putLong("$sessionKey.length", source.length())
                }
                uploadChunks(replacement, token, mimeType, source, objectKey, initialOffset = 0L)
            } else {
                throw error
            }
        }.also {
            uploadSessions.edit {
                remove("$sessionKey.url")
                remove("$sessionKey.length")
            }
        }
    }

    private suspend fun uploadMultipart(
        token: String,
        method: String,
        existingFileId: String?,
        mimeType: String,
        source: File,
        metadata: JSONObject,
    ): DriveObject = withBackoff {
        val endpoint = if (existingFileId == null) {
            "$DRIVE_UPLOAD/files?uploadType=multipart&fields=${encode(FILE_FIELDS)}"
        } else {
            "$DRIVE_UPLOAD/files/${encode(existingFileId)}?uploadType=multipart&fields=${encode(FILE_FIELDS)}"
        }
        val prefix = buildString {
            append("--$MULTIPART_BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$MULTIPART_BOUNDARY\r\n")
            append("Content-Type: $mimeType\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        val suffix = "\r\n--$MULTIPART_BOUNDARY--\r\n".toByteArray(StandardCharsets.UTF_8)
        val connection = open(endpoint, method, token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/related; boundary=$MULTIPART_BOUNDARY")
            setFixedLengthStreamingMode(prefix.size.toLong() + source.length() + suffix.size)
        }
        try {
            connection.outputStream.buffered().use { output ->
                output.write(prefix)
                source.inputStream().buffered().use { input -> copyCancellable(input, output) }
                output.write(suffix)
            }
            ensureSuccess(connection)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).toDriveObject()
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun createUploadSession(
        endpoint: String,
        method: String,
        token: String,
        mimeType: String,
        length: Long,
        metadata: JSONObject,
    ): String = withBackoff {
        val session = open(endpoint, method, token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", mimeType)
            setRequestProperty("X-Upload-Content-Length", length.toString())
        }
        try {
            session.outputStream.use { it.write(metadata.toString().toByteArray()) }
            ensureSuccess(session)
            session.getHeaderField("Location") ?: error("Google Drive 未返回上传地址")
        } finally {
            session.disconnect()
        }
    }

    private suspend fun uploadChunks(
        location: String,
        token: String,
        mimeType: String,
        source: File,
        objectKey: String,
        initialOffset: Long? = null,
    ): DriveObject {
        var offset = initialOffset ?: queryUploadOffset(location, token, source.length())
        if (offset >= source.length()) {
            return driveObjectAfterCompletedUpload(token, objectKey)
        }
        RandomAccessFile(source, "r").use { input ->
            while (offset < source.length()) {
                currentCoroutineContext().ensureActive()
                val endExclusive = minOf(offset + UPLOAD_CHUNK_BYTES, source.length())
                val count = (endExclusive - offset).toInt()
                val result = withBackoff {
                    val upload = open(location, "PUT", token).apply {
                        doOutput = true
                        setRequestProperty("Content-Type", mimeType)
                        setRequestProperty("Content-Range", "bytes $offset-${endExclusive - 1}/${source.length()}")
                        setFixedLengthStreamingMode(count)
                    }
                    try {
                        input.seek(offset)
                        upload.outputStream.buffered().use { output ->
                            var remaining = count
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (remaining > 0) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                                if (read < 0) error("上传源文件提前结束")
                                output.write(buffer, 0, read)
                                remaining -= read
                            }
                        }
                        when (val code = upload.responseCode) {
                            308 -> ChunkUploadResult(uploadedOffset(upload) ?: endExclusive)
                            in 200..299 -> ChunkUploadResult(
                                nextOffset = source.length(),
                                completed = JSONObject(
                                    upload.inputStream.bufferedReader().use { it.readText() },
                                ).toDriveObject(),
                            )
                            else -> throw driveError(upload, code)
                        }
                    } finally {
                        upload.disconnect()
                    }
                }
                result.completed?.let { return it }
                offset = result.nextOffset
            }
        }
        error("Google Drive 上传未返回文件信息")
    }

    private suspend fun driveObjectAfterCompletedUpload(token: String, objectKey: String): DriveObject =
        findByObjectKey(token, objectKey) ?: error("Google Drive 已完成上传，但未返回文件信息")

    private suspend fun queryUploadOffset(location: String, token: String, total: Long): Long = withBackoff {
        val query = open(location, "PUT", token).apply {
            doOutput = true
            setFixedLengthStreamingMode(0)
            setRequestProperty("Content-Range", "bytes */$total")
        }
        try {
            query.connect()
            when (val code = query.responseCode) {
                308 -> uploadedOffset(query) ?: 0L
                in 200..299 -> total
                404, 410 -> throw DriveHttpException(code, "Google Drive 上传会话已过期")
                else -> throw driveError(query, code)
            }
        } finally {
            query.disconnect()
        }
    }

    private fun uploadedOffset(connection: HttpURLConnection): Long? = connection
        .getHeaderField("Range")
        ?.substringAfterLast('-')
        ?.toLongOrNull()
        ?.plus(1)

    private fun driveError(connection: HttpURLConnection, code: Int): DriveHttpException {
        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return DriveHttpException(
            statusCode = code,
            message = body.ifBlank { "Google Drive 请求失败 ($code)" },
            retryAfterMillis = parseRetryAfterMillis(connection.getHeaderField("Retry-After")),
        )
    }

    suspend fun delete(token: String, fileId: String) = withBackoff {
        val connection = open("$DRIVE_API/files/${encode(fileId)}", "DELETE", token)
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_NOT_FOUND) ensureSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestJson(method: String, url: String, token: String): JSONObject = withBackoff {
        val connection = open(url, method, token)
        try {
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
        throw DriveHttpException(
            statusCode = connection.responseCode,
            message = body.ifBlank { "Google Drive 请求失败 (${connection.responseCode})" },
            retryAfterMillis = parseRetryAfterMillis(connection.getHeaderField("Retry-After")),
        )
    }

    private suspend fun <T> withBackoff(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return block()
            } catch (error: DriveHttpException) {
                val transient = error.statusCode == 429 || error.statusCode >= 500
                if (!transient || attempt >= MAX_BACKOFF_RETRIES) throw error
                delay(error.retryAfterMillis ?: BASE_BACKOFF_MILLIS * (1L shl attempt))
            } catch (error: IOException) {
                if (attempt >= MAX_BACKOFF_RETRIES) throw error
                delay(BASE_BACKOFF_MILLIS * (1L shl attempt))
            }
            attempt += 1
        }
    }

    private suspend fun copyCancellable(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
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

    private fun JSONObject.longString(name: String): Long =
        optString(name).toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) block(getJSONObject(index))
    }

    private companion object {
        const val MULTIPART_UPLOAD_MAX_BYTES = 5L * 1024 * 1024
        const val MULTIPART_BOUNDARY = "kixyu-drive-multipart-7f5d21c9"
        const val UPLOAD_CHUNK_BYTES = 8L * 1024 * 1024
        const val MAX_BACKOFF_RETRIES = 3
        const val BASE_BACKOFF_MILLIS = 1_000L
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_FIELDS = "id,name,mimeType,modifiedTime,version,size,md5Checksum,appProperties"
        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}

internal fun parseRetryAfterMillis(
    value: String?,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    raw.toLongOrNull()?.let { seconds -> return (seconds * 1_000L).coerceAtLeast(0L) }
    return runCatching {
        val retryAt = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
        (retryAt - nowMillis).coerceAtLeast(0L)
    }.getOrNull()
}
