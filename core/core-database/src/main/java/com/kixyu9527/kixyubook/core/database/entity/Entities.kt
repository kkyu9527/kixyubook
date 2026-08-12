package com.kixyu9527.kixyubook.core.database.entity

import androidx.room.*

@Entity(tableName = "books", indices = [Index(value = ["contentHash"], unique = true)])
data class BookEntity(
    @PrimaryKey val uuid: String,
    val title: String,
    val author: String,
    val description: String,
    val coverPath: String?,
    val format: String,
    val originalPath: String,
    val storagePath: String,
    val createdTime: Long,
    val contentHash: String,
    val category: String,
    @ColumnInfo(defaultValue = "0") val lastOpenedTime: Long = 0,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["uuid"], childColumns = ["bookUuid"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookUuid"), Index(value = ["bookUuid", "chapterIndex"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUuid: String,
    val title: String,
    val chapterIndex: Int,
    val volumeTitle: String? = null,
    val volumeIndex: Int? = null,
    @ColumnInfo(defaultValue = "1") val indexed: Boolean = true,
    @ColumnInfo(defaultValue = "''") val chapterKey: String = "",
)

@Entity(
    tableName = "paragraphs",
    foreignKeys = [ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chapterId"), Index(value = ["chapterId", "paragraphIndex"], unique = true)],
)
data class ParagraphEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val chapterId: Long, val paragraphIndex: Int, val text: String)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["uuid"], childColumns = ["bookUuid"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chapterId")],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookUuid: String,
    val chapterId: Long,
    val position: Int,
    val offset: Int,
    val updatedTime: Long,
    val fraction: Float,
    @ColumnInfo(defaultValue = "''") val chapterKey: String = "",
    @ColumnInfo(defaultValue = "0") val paragraphIndex: Int = position,
    @ColumnInfo(defaultValue = "0") val charOffset: Int = offset,
    @ColumnInfo(defaultValue = "''") val quoteAnchor: String = "",
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["uuid"], childColumns = ["bookUuid"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookUuid"), Index("chapterId"), Index(value = ["bookUuid", "chapterId", "position"], unique = true)],
)
data class BookmarkEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val chapterId: Long,
    val position: Int,
    val preview: String,
    val createdTime: Long,
)

data class BookmarkRow(
    val uuid: String,
    val bookUuid: String,
    val chapterId: Long,
    val chapterTitle: String,
    val chapterIndex: Int,
    val position: Int,
    val preview: String,
    val createdTime: Long,
)

data class BookSearchResultRow(
    val chapterId: Long,
    val chapterTitle: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
)

@Entity(tableName = "metadata_edits", indices = [Index("bookUuid")])
data class MetadataEditEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val previousTitle: String,
    val previousAuthor: String,
    val previousDescription: String,
    val newTitle: String,
    val newAuthor: String,
    val newDescription: String,
    val createdTime: Long,
)

@Entity(tableName = "reading_sessions", indices = [Index("bookUuid"), Index("epochDay"), Index(value = ["syncUuid"], unique = true)])
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUuid: String,
    val startedTime: Long,
    val durationMillis: Long,
    val epochDay: Long,
    @ColumnInfo(defaultValue = "''") val syncUuid: String = "",
)

@Entity(tableName = "user_fonts")
data class UserFontEntity(@PrimaryKey val uuid: String, val name: String, val filePath: String, val createdTime: Long)

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["entityType", "entityId"], unique = true), Index("changedAt")],
)
data class SyncOutboxEntity(
    @PrimaryKey val uuid: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val changedAt: Long,
    val logicalCounter: Long,
    val deviceId: String,
    val attemptCount: Int = 0,
)

@Entity(tableName = "sync_object_state")
data class SyncObjectStateEntity(
    @PrimaryKey val objectKey: String,
    val driveFileId: String?,
    val localHash: String?,
    val localChangedAt: Long,
    val remoteModifiedAt: Long,
    val remoteVersion: Long,
)

@Entity(tableName = "sync_tombstones", indices = [Index("expiresAt")])
data class SyncTombstoneEntity(
    @PrimaryKey val objectKey: String,
    val deletedAt: Long,
    val deviceId: String,
    val expiresAt: Long,
)

@Entity(
    tableName = "text_corrections",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["bookUuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("bookUuid"),
        Index(value = ["bookUuid", "chapterKey", "paragraphIndex"]),
        Index("updatedTime"),
    ],
)
data class TextCorrectionEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val sourceContentHash: String,
    val chapterKey: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val exactText: String,
    val prefixText: String,
    val suffixText: String,
    val replacementText: String,
    val status: String,
    val createdTime: Long,
    val updatedTime: Long,
    val deviceId: String,
)
