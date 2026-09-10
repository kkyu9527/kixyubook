package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.provider.Settings
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotation
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotationStyle
import com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.ReaderAnnotationDao
import com.kixyu9527.kixyubook.core.database.entity.ReaderAnnotationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalReaderAnnotationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val annotations: ReaderAnnotationDao,
    private val books: BookDao,
    private val syncMutations: SyncMutationRecorder,
    private val database: KixyuDatabase,
) : ReaderAnnotationRepository {
    override fun observeBookAnnotations(bookUuid: String): Flow<List<ReaderAnnotation>> =
        annotations.observeForBook(bookUuid).map { values -> values.map(ReaderAnnotationEntity::toModel) }

    override suspend fun getBookAnnotations(bookUuid: String) = withContext(Dispatchers.IO) {
        annotations.getForBook(bookUuid).map(ReaderAnnotationEntity::toModel)
    }

    override suspend fun createAnnotation(
        bookUuid: String,
        chapterKey: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        originalText: String,
        startOffset: Int,
        endOffset: Int,
        style: ReaderAnnotationStyle,
        note: String,
    ): ReaderAnnotation = database.withTransaction {
        require(originalText.isNotBlank()) { context.getString(R.string.db_empty_annotation) }
        val safeStart = startOffset.coerceIn(0, originalText.length)
        val safeEnd = endOffset.coerceIn(safeStart, originalText.length)
        require(safeStart < safeEnd) { context.getString(R.string.db_no_annotation_selection) }
        val book = books.getBook(bookUuid) ?: error(context.getString(R.string.db_book_removed))
        val now = System.currentTimeMillis()
        val existing = annotations.getForBook(bookUuid).firstOrNull {
            it.chapterKey == chapterKey && it.paragraphIndex == paragraphIndex &&
                it.startOffset == safeStart && it.endOffset == safeEnd
        }
        val value = ReaderAnnotation(
            uuid = existing?.uuid ?: UUID.randomUUID().toString(),
            bookUuid = bookUuid,
            sourceContentHash = book.contentHash,
            chapterKey = chapterKey,
            chapterIndex = chapterIndex,
            paragraphIndex = paragraphIndex,
            startOffset = safeStart,
            endOffset = safeEnd,
            exactText = originalText.substring(safeStart, safeEnd),
            style = style,
            note = note.ifBlank { existing?.note.orEmpty() },
            createdTime = existing?.createdTime ?: now,
            updatedTime = now,
            deviceId = deviceId(),
        )
        annotations.upsert(value.toEntity())
        syncMutations.record(SyncEntityType.ANNOTATION, value.uuid)
        value
    }

    override suspend fun updateNote(uuid: String, note: String): ReaderAnnotation? = database.withTransaction {
        val current = annotations.get(uuid)?.toModel() ?: return@withTransaction null
        val updated = current.copy(note = note.trim(), updatedTime = System.currentTimeMillis(), deviceId = deviceId())
        annotations.upsert(updated.toEntity())
        syncMutations.record(SyncEntityType.ANNOTATION, uuid)
        updated
    }

    override suspend fun deleteAnnotation(uuid: String) = database.withTransaction {
        annotations.delete(uuid)
        syncMutations.record(SyncEntityType.ANNOTATION, uuid, SyncMutationOperation.DELETE)
    }

    override suspend fun applyRemote(annotation: ReaderAnnotation) = database.withTransaction {
        if (books.getBook(annotation.bookUuid) == null) return@withTransaction
        val local = annotations.get(annotation.uuid)?.toModel()
        if (local != null && local.updatedTime > annotation.updatedTime) return@withTransaction
        annotations.upsert(annotation.toEntity())
    }

    override suspend fun deleteRemote(uuid: String) = database.withTransaction {
        annotations.delete(uuid)
    }

    private fun deviceId(): String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID,
    ).orEmpty()
}

internal fun ReaderAnnotationEntity.toModel() = ReaderAnnotation(
    uuid, bookUuid, sourceContentHash, chapterKey, chapterIndex, paragraphIndex,
    startOffset, endOffset, exactText,
    runCatching { ReaderAnnotationStyle.valueOf(style) }.getOrDefault(ReaderAnnotationStyle.HIGHLIGHT),
    note, createdTime, updatedTime, deviceId,
)

internal fun ReaderAnnotation.toEntity() = ReaderAnnotationEntity(
    uuid, bookUuid, sourceContentHash, chapterKey, chapterIndex, paragraphIndex,
    startOffset, endOffset, exactText, style.name, note, createdTime, updatedTime, deviceId,
)
