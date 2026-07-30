package com.kixyu9527.kixyubook.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.entity.*

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ParagraphEntity::class, ReadingProgressEntity::class,
        MetadataEditEntity::class, ReadingSessionEntity::class, UserFontEntity::class, BookmarkEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class KixyuDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun fontDao(): FontDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN volumeTitle TEXT")
                db.execSQL("ALTER TABLE chapters ADD COLUMN volumeIndex INTEGER")
                db.execSQL("ALTER TABLE chapters ADD COLUMN indexed INTEGER NOT NULL DEFAULT 1")
                // Rich parsing semantics changed in v5; EPUB chapters are safe to rebuild from
                // their immutable source files while TXT data remains immediately available.
                db.execSQL(
                    "UPDATE chapters SET indexed = 0 WHERE bookUuid IN " +
                        "(SELECT uuid FROM books WHERE format = 'EPUB')",
                )
            }
        }
    }
}
