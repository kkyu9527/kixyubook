package com.kixyu9527.kixyubook.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Production database migrations.
 *
 * Versions before 6 were only used during private development and intentionally remain outside
 * the supported upgrade path. Version 6 is the first schema whose user data must be preserved.
 */
object DatabaseMigrations {
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chapters ADD COLUMN chapterKey TEXT NOT NULL DEFAULT ''")

            db.execSQL("ALTER TABLE reading_progress ADD COLUMN chapterKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE reading_progress ADD COLUMN paragraphIndex INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE reading_progress ADD COLUMN charOffset INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE reading_progress ADD COLUMN quoteAnchor TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE reading_progress SET paragraphIndex = position, charOffset = offset")

            db.execSQL("ALTER TABLE reading_sessions ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE reading_sessions SET syncUuid = 'legacy-' || id")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_reading_sessions_syncUuid " +
                    "ON reading_sessions (syncUuid)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_outbox (
                    uuid TEXT NOT NULL,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    changedAt INTEGER NOT NULL,
                    logicalCounter INTEGER NOT NULL,
                    deviceId TEXT NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    PRIMARY KEY(uuid)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_entityType_entityId " +
                    "ON sync_outbox (entityType, entityId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sync_outbox_changedAt ON sync_outbox (changedAt)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_object_state (
                    objectKey TEXT NOT NULL,
                    driveFileId TEXT,
                    localHash TEXT,
                    localChangedAt INTEGER NOT NULL,
                    remoteModifiedAt INTEGER NOT NULL,
                    remoteVersion INTEGER NOT NULL,
                    PRIMARY KEY(objectKey)
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_tombstones (
                    objectKey TEXT NOT NULL,
                    deletedAt INTEGER NOT NULL,
                    deviceId TEXT NOT NULL,
                    expiresAt INTEGER NOT NULL,
                    PRIMARY KEY(objectKey)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sync_tombstones_expiresAt " +
                    "ON sync_tombstones (expiresAt)",
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN lastOpenedTime INTEGER NOT NULL DEFAULT 0")
        }
    }

    val supported = arrayOf(MIGRATION_6_7, MIGRATION_7_8)
}
