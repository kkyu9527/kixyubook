package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.provider.Settings
import com.kixyu9527.kixyubook.core.common.model.ChapterContent
import com.kixyu9527.kixyubook.core.common.model.Paragraph
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.model.TextCorrection
import com.kixyu9527.kixyubook.core.common.model.TextCorrectionStatus
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.common.repository.TextCorrectionRepository
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.TextCorrectionDao
import com.kixyu9527.kixyubook.core.database.entity.TextCorrectionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTextCorrectionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val corrections: TextCorrectionDao,
    private val books: BookDao,
    private val syncMutations: SyncMutationRecorder,
) : TextCorrectionRepository {
    override fun observeBookCorrections(bookUuid: String): Flow<List<TextCorrection>> =
        corrections.observeForBook(bookUuid).map { values -> values.map(TextCorrectionEntity::toModel) }

    override suspend fun getCorrection(uuid: String): TextCorrection? = withContext(Dispatchers.IO) {
        corrections.get(uuid)?.toModel()
    }

    override suspend fun getBookCorrections(bookUuid: String): List<TextCorrection> = withContext(Dispatchers.IO) {
        corrections.getForBook(bookUuid).map(TextCorrectionEntity::toModel)
    }

    override suspend fun createParagraphCorrection(
        bookUuid: String,
        chapterKey: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        originalText: String,
        replacementText: String,
    ): TextCorrection = withContext(Dispatchers.IO) {
        require(originalText.isNotEmpty()) { "空段落不能纠错" }
        require(replacementText != originalText) { "纠正后的内容没有变化" }
        val book = books.getBook(bookUuid) ?: error("书籍不存在或已被删除")
        val now = System.currentTimeMillis()
        var value = TextCorrection(
            uuid = UUID.randomUUID().toString(),
            bookUuid = bookUuid,
            sourceContentHash = book.contentHash,
            chapterKey = chapterKey,
            chapterIndex = chapterIndex,
            paragraphIndex = paragraphIndex,
            startOffset = 0,
            endOffset = originalText.length,
            exactText = originalText,
            prefixText = "",
            suffixText = "",
            replacementText = replacementText,
            createdTime = now,
            updatedTime = now,
            deviceId = deviceId(),
        )
        if (markOverlappingConflicts(value)) value = value.copy(status = TextCorrectionStatus.CONFLICT)
        corrections.upsert(value.toEntity())
        syncMutations.record(SyncEntityType.CORRECTION, value.uuid)
        value
    }

    override suspend fun updateCorrection(uuid: String, replacementText: String): TextCorrection? =
        withContext(Dispatchers.IO) {
            val current = corrections.get(uuid)?.toModel() ?: return@withContext null
            if (replacementText == current.exactText) {
                deleteCorrection(uuid)
                return@withContext null
            }
            var updated = current.copy(
                replacementText = replacementText,
                status = TextCorrectionStatus.ACTIVE,
                updatedTime = System.currentTimeMillis(),
                deviceId = deviceId(),
            )
            if (markOverlappingConflicts(updated)) updated = updated.copy(status = TextCorrectionStatus.CONFLICT)
            corrections.upsert(updated.toEntity())
            syncMutations.record(SyncEntityType.CORRECTION, uuid)
            updated
        }

    override suspend fun deleteCorrection(uuid: String) = withContext(Dispatchers.IO) {
        corrections.delete(uuid)
        syncMutations.record(SyncEntityType.CORRECTION, uuid, SyncMutationOperation.DELETE)
    }

    override suspend fun resolveConflict(uuid: String) = withContext(Dispatchers.IO) {
        val selected = corrections.get(uuid)?.toModel() ?: return@withContext
        val overlapping = corrections.getForBook(selected.bookUuid).map(TextCorrectionEntity::toModel)
            .filter { it.uuid != uuid && it.overlaps(selected) }
        overlapping.forEach { deleteCorrection(it.uuid) }
        val resolved = selected.copy(
            status = TextCorrectionStatus.ACTIVE,
            updatedTime = System.currentTimeMillis(),
            deviceId = deviceId(),
        )
        corrections.upsert(resolved.toEntity())
        syncMutations.record(SyncEntityType.CORRECTION, uuid)
    }

    override suspend fun applyToChapter(content: ChapterContent): ChapterContent = withContext(Dispatchers.IO) {
        val all = corrections.getForBook(content.chapter.bookUuid).map(TextCorrectionEntity::toModel)
        if (all.isEmpty()) return@withContext content
        val candidates = all.filter {
            it.chapterKey == content.chapter.chapterKey || it.chapterIndex == content.chapter.index
        }
        if (candidates.isEmpty()) return@withContext content

        val relocated = candidates.map { correction -> relocate(correction, content) }
        val active = relocated.filter { it.status == TextCorrectionStatus.ACTIVE }
            .groupBy(TextCorrection::paragraphIndex)
        content.copy(
            paragraphs = content.paragraphs.map { paragraph ->
                val paragraphCorrections = active[paragraph.index].orEmpty()
                    .filter { it.endOffset <= paragraph.text.length }
                    .sortedByDescending(TextCorrection::startOffset)
                if (paragraph.kind != ParagraphKind.TEXT || paragraphCorrections.isEmpty()) paragraph
                else paragraph.copy(
                    text = applyCorrections(paragraph.text, paragraphCorrections),
                    // Publisher spans are source offsets. Once text changes, retaining them could
                    // style the wrong characters; correctness wins over decorative EPUB styling.
                    spans = emptyList(),
                )
            },
        )
    }

    override suspend fun applyRemote(correction: TextCorrection) = withContext(Dispatchers.IO) {
        if (books.getBook(correction.bookUuid) == null) return@withContext
        val local = corrections.get(correction.uuid)?.toModel()
        if (local != null && local.updatedTime > correction.updatedTime) return@withContext
        val incoming = if (markOverlappingConflicts(correction)) {
            correction.copy(status = TextCorrectionStatus.CONFLICT)
        } else correction
        corrections.upsert(incoming.toEntity())
    }

    override suspend fun deleteRemote(uuid: String) = withContext(Dispatchers.IO) {
        corrections.delete(uuid)
    }

    private suspend fun relocate(value: TextCorrection, content: ChapterContent): TextCorrection {
        val direct = content.paragraphs.firstOrNull { it.index == value.paragraphIndex }
        if (direct?.text?.matchesAnchor(value) == true) {
            if (value.status == TextCorrectionStatus.UNRESOLVED) {
                val active = value.copy(status = TextCorrectionStatus.ACTIVE, updatedTime = System.currentTimeMillis())
                corrections.upsert(active.toEntity())
                return active
            }
            return value
        }
        val match = content.paragraphs.firstOrNull { it.text.matchesAnchor(value) }
        if (match != null) {
            val start = match.text.indexOf(value.exactText)
            val relocated = value.copy(
                paragraphIndex = match.index,
                startOffset = start,
                endOffset = start + value.exactText.length,
                status = TextCorrectionStatus.ACTIVE,
                updatedTime = System.currentTimeMillis(),
            )
            corrections.upsert(relocated.toEntity())
            return relocated
        }
        if (value.status != TextCorrectionStatus.UNRESOLVED) {
            val unresolved = value.copy(status = TextCorrectionStatus.UNRESOLVED, updatedTime = System.currentTimeMillis())
            corrections.upsert(unresolved.toEntity())
            return unresolved
        }
        return value
    }

    private suspend fun markOverlappingConflicts(incoming: TextCorrection): Boolean {
        val overlapping = corrections.getForBook(incoming.bookUuid).map(TextCorrectionEntity::toModel)
            .filter {
                it.uuid != incoming.uuid && it.status != TextCorrectionStatus.UNRESOLVED &&
                    it.overlaps(incoming) && it.replacementText != incoming.replacementText
            }
        overlapping.forEach { existing ->
                corrections.upsert(
                    existing.copy(
                        status = TextCorrectionStatus.CONFLICT,
                        updatedTime = maxOf(existing.updatedTime, incoming.updatedTime),
                    ).toEntity(),
                )
            }
        return overlapping.isNotEmpty()
    }

    private fun deviceId(): String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID,
    ).orEmpty()
}

private fun String.matchesAnchor(value: TextCorrection): Boolean {
    if (value.startOffset >= 0 && value.endOffset <= length &&
        substring(value.startOffset, value.endOffset) == value.exactText
    ) return true
    val index = indexOf(value.exactText)
    if (index < 0) return false
    val prefixMatches = value.prefixText.isEmpty() ||
        substring(0, index).endsWith(value.prefixText)
    val suffixStart = index + value.exactText.length
    val suffixMatches = value.suffixText.isEmpty() ||
        substring(suffixStart).startsWith(value.suffixText)
    return prefixMatches && suffixMatches
}

private fun TextCorrection.overlaps(other: TextCorrection): Boolean =
    bookUuid == other.bookUuid && (chapterKey == other.chapterKey || chapterIndex == other.chapterIndex) &&
        paragraphIndex == other.paragraphIndex && startOffset < other.endOffset && other.startOffset < endOffset

internal fun applyCorrections(source: String, values: List<TextCorrection>): String {
    var result = source
    values.sortedByDescending(TextCorrection::startOffset).forEach { correction ->
        if (correction.status == TextCorrectionStatus.ACTIVE &&
            correction.startOffset >= 0 && correction.endOffset <= result.length &&
            source.substring(correction.startOffset, correction.endOffset) == correction.exactText
        ) {
            result = result.replaceRange(correction.startOffset, correction.endOffset, correction.replacementText)
        }
    }
    return result
}

internal fun TextCorrectionEntity.toModel() = TextCorrection(
    uuid, bookUuid, sourceContentHash, chapterKey, chapterIndex, paragraphIndex,
    startOffset, endOffset, exactText, prefixText, suffixText, replacementText,
    runCatching { TextCorrectionStatus.valueOf(status) }.getOrDefault(TextCorrectionStatus.UNRESOLVED),
    createdTime, updatedTime, deviceId,
)

internal fun TextCorrection.toEntity() = TextCorrectionEntity(
    uuid, bookUuid, sourceContentHash, chapterKey, chapterIndex, paragraphIndex,
    startOffset, endOffset, exactText, prefixText, suffixText, replacementText,
    status.name, createdTime, updatedTime, deviceId,
)
