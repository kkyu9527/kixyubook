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

/**
 * Version 10/11 existed only in local development builds. Version 12 removes their abandoned
 * per-book reader columns while keeping the user's books and reading data intact.
 */
val MIGRATION_9_12 = object : Migration(9, 12) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

val MIGRATION_10_12 = object : Migration(10, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        dropBookReaderSettingsColumns(db, BOOK_READER_COLUMNS_V10)
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        dropBookReaderSettingsColumns(db, BOOK_READER_COLUMNS_V11)
    }
}

private val BOOK_READER_COLUMNS_V10 = listOf(
    "readerSettingsEnabled",
    "readerFontSize",
    "readerLineHeight",
    "readerLetterSpacing",
    "readerMargin",
    "readerTheme",
    "readerPageMode",
    "readerFontUuid",
)

private val BOOK_READER_COLUMNS_V11 = BOOK_READER_COLUMNS_V10 + listOf(
    "readerCustomThemeEnabled",
    "readerCustomDayTheme",
    "readerCustomNightTheme",
    "readerEpubLayoutMode",
)

private fun dropBookReaderSettingsColumns(
    db: SupportSQLiteDatabase,
    columns: List<String>,
) {
    columns.forEach { column -> db.execSQL("ALTER TABLE `books` DROP COLUMN `$column`") }
}
