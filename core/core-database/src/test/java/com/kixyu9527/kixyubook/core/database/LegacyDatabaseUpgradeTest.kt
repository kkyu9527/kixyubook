package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

private const val PLACEHOLDER = "\${TABLE_NAME}"

private val ALL_MIGRATIONS = arrayOf(
    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_12, MIGRATION_10_12, MIGRATION_11_12,
    MIGRATION_12_13, MIGRATION_12_14, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
    MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
)

/**
 * Builds a real database from an exported schema and opens it with the current Room definition.
 * This is the host-side equivalent of the instrumented migration test and catches a version bump
 * whose migration is missing or leaves the schema in a state Room rejects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyDatabaseUpgradeTest {
    @get:Rule val folder = TemporaryFolder()

    private fun schemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.kixyu9527.kixyubook.core.database.KixyuDatabase/$version.json"),
            File("core/core-database/schemas/com.kixyu9527.kixyubook.core.database.KixyuDatabase/$version.json"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("schema $version.json not found in ${candidates.map { it.absolutePath }}")
    }

    private fun createLegacyDatabase(version: Int, target: File) {
        val schema = JSONObject(schemaFile(version).readText())
        val database = SQLiteDatabase.openOrCreateDatabase(target, null)
        database.beginTransaction()
        try {
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace("\$" + "{TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (indexIndex in 0 until indices.length()) {
                    database.execSQL(
                        indices.getJSONObject(indexIndex).getString("createSql").replace("\$" + "{TABLE_NAME}", table),
                    )
                }
            }
            val identity = schema.getJSONObject("database").getString("identityHash")
            database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$identity')")
            database.execSQL(
                "INSERT INTO books (uuid, title, author, description, coverPath, format, originalPath, " +
                    "storagePath, createdTime, contentHash, category) " +
                    "VALUES ('11111111-1111-4111-8111-111111111111', '旧书', '作者', '', NULL, 'TXT', " +
                    "'/tmp/a.txt', '/tmp/a.txt', 1, 'hash', '未分类')",
            )
            if (version == 22) database.execSQL(
                "INSERT INTO sync_outbox (uuid, entityType, entityId, operation, changedAt, logicalCounter, " +
                    "deviceId, attemptCount, lastAttemptAt) VALUES ('pending-delete', 'BOOK', " +
                    "'11111111-1111-4111-8111-111111111111', 'DELETE', 10, 10, 'device', 3, 25)",
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        database.setVersion(version)
        database.close()
    }

    @Test
    fun booksSurviveAnUpgradeFromEveryExportedLegacySchema() {
        val legacyVersions = listOf(6, 7, 8, 9, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        val failures = legacyVersions.mapNotNull { version ->
            runCatching { booksSurviveAnUpgradeFrom(version) }
                .exceptionOrNull()
                ?.let { error -> "v$version: ${error.message ?: error::class.java.simpleName}" }
        }
        assertEquals("every exported legacy schema must upgrade without losing books", emptyList<String>(), failures)
    }

    private fun booksSurviveAnUpgradeFrom(legacyVersion: Int) = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val file = folder.newFile("legacy-v$legacyVersion.db")
        createLegacyDatabase(legacyVersion, file)

        val database = Room.databaseBuilder(context, KixyuDatabase::class.java, file.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val books = database.bookDao().getAllBooks()
            assertEquals("an upgrade from $legacyVersion must never empty the library", 1, books.size)
            assertEquals("旧书", books.single().title)
            if (legacyVersion == 22) {
                val pending = database.syncDao().allPending().single()
                assertEquals("DELETE", pending.operation)
                assertEquals(3, pending.attemptCount)
                assertEquals(25L, pending.lastAttemptAt)
                assertEquals(emptyList<Any>(), database.syncDao().remoteInbox())
            }
        } finally {
            database.close()
        }
    }
}
