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
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["uuid"], childColumns = ["bookUuid"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookUuid"), Index(value = ["bookUuid", "chapterIndex"], unique = true)],
)
data class ChapterEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val bookUuid: String, val title: String, val chapterIndex: Int)

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

@Entity(tableName = "reading_sessions", indices = [Index("bookUuid"), Index("epochDay")])
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUuid: String,
    val startedTime: Long,
    val durationMillis: Long,
    val charactersRead: Long,
    val epochDay: Long,
)

@Entity(tableName = "user_fonts")
data class UserFontEntity(@PrimaryKey val uuid: String, val name: String, val filePath: String, val createdTime: Long)
