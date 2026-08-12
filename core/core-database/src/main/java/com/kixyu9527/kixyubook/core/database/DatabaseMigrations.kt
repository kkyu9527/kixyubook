package com.kixyu9527.kixyubook.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `text_corrections` (
                `uuid` TEXT NOT NULL,
                `bookUuid` TEXT NOT NULL,
                `sourceContentHash` TEXT NOT NULL,
                `chapterKey` TEXT NOT NULL,
                `chapterIndex` INTEGER NOT NULL,
                `paragraphIndex` INTEGER NOT NULL,
                `startOffset` INTEGER NOT NULL,
                `endOffset` INTEGER NOT NULL,
                `exactText` TEXT NOT NULL,
                `prefixText` TEXT NOT NULL,
                `suffixText` TEXT NOT NULL,
                `replacementText` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdTime` INTEGER NOT NULL,
                `updatedTime` INTEGER NOT NULL,
                `deviceId` TEXT NOT NULL,
                PRIMARY KEY(`uuid`),
                FOREIGN KEY(`bookUuid`) REFERENCES `books`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_corrections_bookUuid` ON `text_corrections` (`bookUuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_corrections_bookUuid_chapterKey_paragraphIndex` ON `text_corrections` (`bookUuid`, `chapterKey`, `paragraphIndex`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_corrections_updatedTime` ON `text_corrections` (`updatedTime`)")
    }
}
