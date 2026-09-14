package com.kixyu9527.kixyubook.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 6 is the first database shipped to users. Every published schema stays migratable. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `chapters` ADD COLUMN `chapterKey` TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE `reading_progress` ADD COLUMN `chapterKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `reading_progress` ADD COLUMN `paragraphIndex` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `reading_progress` ADD COLUMN `charOffset` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `reading_progress` ADD COLUMN `quoteAnchor` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `reading_progress` SET `paragraphIndex` = `position`, `charOffset` = `offset`")

        db.execSQL("ALTER TABLE `reading_sessions` ADD COLUMN `syncUuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `reading_sessions` SET `syncUuid` = 'legacy-' || `id`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_sessions_syncUuid` " +
                "ON `reading_sessions` (`syncUuid`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
                `uuid` TEXT NOT NULL,
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `operation` TEXT NOT NULL,
                `changedAt` INTEGER NOT NULL,
                `logicalCounter` INTEGER NOT NULL,
                `deviceId` TEXT NOT NULL,
                `attemptCount` INTEGER NOT NULL,
                PRIMARY KEY(`uuid`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityId` " +
                "ON `sync_outbox` (`entityType`, `entityId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_changedAt` ON `sync_outbox` (`changedAt`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_object_state` (
                `objectKey` TEXT NOT NULL,
                `driveFileId` TEXT,
                `localHash` TEXT,
                `localChangedAt` INTEGER NOT NULL,
                `remoteModifiedAt` INTEGER NOT NULL,
                `remoteVersion` INTEGER NOT NULL,
                PRIMARY KEY(`objectKey`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_tombstones` (
                `objectKey` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL,
                `deviceId` TEXT NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                PRIMARY KEY(`objectKey`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_tombstones_expiresAt` " +
                "ON `sync_tombstones` (`expiresAt`)",
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `books` ADD COLUMN `lastOpenedTime` INTEGER NOT NULL DEFAULT 0")
    }
}

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

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) = createReaderAnnotationsTable(db)
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) = createSearchAndImportTables(db)
}

/** Direct upgrade path from the last published schema; it must not depend on development builds. */
val MIGRATION_12_14 = object : Migration(12, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createReaderAnnotationsTable(db)
        createSearchAndImportTables(db)
    }
}

/**
 * Adds a stable chapter anchor to bookmarks so a bookmark can be re-resolved after a reparse
 * instead of depending only on the mutable chapter row id. Existing bookmarks are backfilled from
 * the chapter they currently point at, so no reading data is lost.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `chapterKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "UPDATE `bookmarks` SET `chapterKey` = COALESCE(" +
                "(SELECT `chapters`.`chapterKey` FROM `chapters` " +
                "WHERE `chapters`.`id` = `bookmarks`.`chapterId`), '')",
        )
    }
}

private fun createReaderAnnotationsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `reader_annotations` (
            `uuid` TEXT NOT NULL,
            `bookUuid` TEXT NOT NULL,
            `sourceContentHash` TEXT NOT NULL,
            `chapterKey` TEXT NOT NULL,
            `chapterIndex` INTEGER NOT NULL,
            `paragraphIndex` INTEGER NOT NULL,
            `startOffset` INTEGER NOT NULL,
            `endOffset` INTEGER NOT NULL,
            `exactText` TEXT NOT NULL,
            `style` TEXT NOT NULL,
            `note` TEXT NOT NULL,
            `createdTime` INTEGER NOT NULL,
            `updatedTime` INTEGER NOT NULL,
            `deviceId` TEXT NOT NULL,
            PRIMARY KEY(`uuid`),
            FOREIGN KEY(`bookUuid`) REFERENCES `books`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_reader_annotations_bookUuid` ON `reader_annotations` (`bookUuid`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_reader_annotations_bookUuid_chapterKey_paragraphIndex` ON `reader_annotations` (`bookUuid`, `chapterKey`, `paragraphIndex`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_reader_annotations_updatedTime` ON `reader_annotations` (`updatedTime`)")
}

private fun createSearchAndImportTables(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `paragraphs_fts` USING FTS4(`text` TEXT NOT NULL)")
    db.execSQL("DELETE FROM `paragraphs_fts`")
    db.execSQL("INSERT INTO `paragraphs_fts`(`rowid`, `text`) SELECT `id`, `text` FROM `paragraphs`")
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `import_items` (
            `runId` TEXT NOT NULL,
            `sourceId` TEXT NOT NULL,
            `displayName` TEXT NOT NULL,
            `itemOrder` INTEGER NOT NULL,
            `stage` TEXT NOT NULL,
            `progress` REAL NOT NULL,
            `status` TEXT NOT NULL,
            `bookUuid` TEXT,
            `message` TEXT,
            `startedTime` INTEGER NOT NULL,
            `updatedTime` INTEGER NOT NULL,
            PRIMARY KEY(`runId`, `sourceId`)
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_items_runId` ON `import_items` (`runId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_items_status` ON `import_items` (`status`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_items_bookUuid` ON `import_items` (`bookUuid`)")
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
