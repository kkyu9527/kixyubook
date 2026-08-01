package com.kixyu9527.kixyubook.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.*

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ParagraphEntity::class, ReadingProgressEntity::class,
        MetadataEditEntity::class, ReadingSessionEntity::class, UserFontEntity::class, BookmarkEntity::class,
        SyncOutboxEntity::class, SyncObjectStateEntity::class, SyncTombstoneEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class KixyuDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun fontDao(): FontDao
    abstract fun syncDao(): SyncDao
}
