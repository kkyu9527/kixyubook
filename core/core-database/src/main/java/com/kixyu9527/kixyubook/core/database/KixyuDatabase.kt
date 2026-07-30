package com.kixyu9527.kixyubook.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.entity.*

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ParagraphEntity::class, ReadingProgressEntity::class,
        MetadataEditEntity::class, ReadingSessionEntity::class, UserFontEntity::class, BookmarkEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class KixyuDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun fontDao(): FontDao
}
