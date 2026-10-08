package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.*
import com.kixyu9527.kixyubook.core.database.*
import com.kixyu9527.kixyubook.core.database.entity.*
import org.json.JSONObject
import org.robolectric.RuntimeEnvironment
import java.lang.reflect.Proxy

internal inline fun <reified T> unusedSyncRepository(): T = Proxy.newProxyInstance(
    T::class.java.classLoader, arrayOf(T::class.java),
) { _, method, _ -> error("Unexpected call: ${method.name}") } as T

internal class RemoteApplyFixture : AutoCloseable {
    val context: Context = RuntimeEnvironment.getApplication()
    val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
    val books = database.bookDao()
    val sync = database.syncDao()
    val preferences = SyncPreferencesStore(context)
    val mutations = RoomSyncMutationRecorder(sync, preferences, CloudSyncScheduler(context))
    val drive = DriveAppDataClient(context)
    val payloads = mutableMapOf<String, JSONObject>()
    val bookRepository = object : BookRepository by unusedSyncRepository<BookRepository>() {
        override suspend fun applySyncedBookMetadata(bookUuid: String, title: String, author: String, description: String, ownership: BookMetadataOwnership?) {
            books.updateBookMetadata(bookUuid, title, author, description)
        }
        override suspend fun setCategory(bookUuid: String, category: String) { books.setCategory(bookUuid, category) }
        override suspend fun updateBookImportedMetadata(bookUuid: String, originalDisplayName: String, titleSort: String, seriesName: String, seriesIndex: Double?) = Unit
        override suspend fun saveProgress(progress: ReadingProgress) {
            books.saveProgress(ReadingProgressEntity(progress.bookUuid, progress.chapterId, progress.position,
                progress.offset, progress.updatedTime, progress.fraction, progress.chapterKey,
                progress.paragraphIndex, progress.charOffset, progress.quoteAnchor))
        }
    }
    val annotations = LocalReaderAnnotationRepository(context, database.readerAnnotationDao(), books, mutations, database)
    val corrections = LocalTextCorrectionRepository(context, database.textCorrectionDao(), books, mutations, database)
    val fontRepository = unusedSyncRepository<FontRepository>()
    val applier = CloudRemoteStateApplier(context, database, books, database.fontDao(), sync,
        bookRepository, fontRepository, unusedSyncRepository(), unusedSyncRepository(), corrections,
        annotations, ReadingReminderScheduler(context, NotificationPreferencesStore(context, mutations)),
        preferences, mutations, drive, jsonDownload = { _, info -> payloads.getValue(info.objectKey) })

    fun pipeline(remote: CloudRemoteObjectApplier = applier) = CloudSyncPullPipeline(context, database,
        books, sync, bookRepository, fontRepository, corrections, annotations, mutations, drive, remote)

    suspend fun addBook(uuid: String) {
        books.insertBook(BookEntity(uuid, "本地书名", "", "", null, "TXT", "", "", 1, "hash", ""))
        books.insertChapter(ChapterEntity(1, uuid, "第一章", 0, chapterKey = "first"))
    }
    override fun close() { database.close() }
}

internal fun remoteTestObject(key: String, version: Long = 1) = DriveObject(
    "id-$key", key.substringAfterLast('/'), key, "application/json", version, version, 0, null,
)
