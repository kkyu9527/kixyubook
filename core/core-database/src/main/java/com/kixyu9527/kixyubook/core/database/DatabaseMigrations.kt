package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.security.MessageDigest
import java.util.UUID

fun migration1To2(context: Context) = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create the V2 graph with its final table names. Referencing temporary names and
        // relying on ALTER TABLE to rewrite foreign keys is not portable across Android's
        // SQLite versions (some devices retained `books_new` and failed Room validation).
        db.execSQL("ALTER TABLE `reading_progress` RENAME TO `reading_progress_legacy`")
        db.execSQL("ALTER TABLE `paragraphs` RENAME TO `paragraphs_legacy`")
        db.execSQL("ALTER TABLE `chapters` RENAME TO `chapters_legacy`")
        db.execSQL("ALTER TABLE `books` RENAME TO `books_legacy`")
        listOf("reading_progress_legacy", "paragraphs_legacy", "chapters_legacy", "books_legacy")
            .forEach { db.dropUserIndexes(it) }

        db.execSQL("CREATE TABLE IF NOT EXISTS `books` (`uuid` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `description` TEXT NOT NULL, `coverPath` TEXT, `format` TEXT NOT NULL, `originalPath` TEXT NOT NULL, `storagePath` TEXT NOT NULL, `createdTime` INTEGER NOT NULL, `contentHash` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`uuid`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookUuid` TEXT NOT NULL, `title` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, FOREIGN KEY(`bookUuid`) REFERENCES `books`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `paragraphs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chapterId` INTEGER NOT NULL, `paragraphIndex` INTEGER NOT NULL, `text` TEXT NOT NULL, FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `reading_progress` (`bookUuid` TEXT NOT NULL, `chapterId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `offset` INTEGER NOT NULL, `updatedTime` INTEGER NOT NULL, `fraction` REAL NOT NULL, PRIMARY KEY(`bookUuid`), FOREIGN KEY(`bookUuid`) REFERENCES `books`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE)")

        val bookIds = mutableMapOf<Long, String>()
        db.query("SELECT * FROM books_legacy").use { cursor ->
            while (cursor.moveToNext()) {
                val oldId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val uuid = UUID.randomUUID().toString()
                bookIds[oldId] = uuid
                val oldPath = cursor.getString(cursor.getColumnIndexOrThrow("filePath"))
                val format = cursor.getString(cursor.getColumnIndexOrThrow("format"))
                val source = File(oldPath)
                val booksDir = File(context.filesDir, "books").apply { mkdirs() }
                val target = File(booksDir, "$uuid.${format.lowercase()}")
                if (source.exists()) source.copyTo(target, overwrite = true)
                val oldCover = cursor.getString(cursor.getColumnIndexOrThrow("coverPath"))
                val newCover = oldCover?.let { path ->
                    File(path).takeIf(File::exists)?.let { cover ->
                        File(context.filesDir, "covers").apply { mkdirs() }
                        File(context.filesDir, "covers/$uuid.${cover.extension.ifBlank { "jpg" }}").also { cover.copyTo(it, true) }.absolutePath
                    }
                }
                val hash = target.takeIf(File::exists)?.sha256() ?: "legacy-$uuid"
                db.execSQL(
                    "INSERT INTO books VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(uuid, cursor.getString(cursor.getColumnIndexOrThrow("title")), cursor.getString(cursor.getColumnIndexOrThrow("author")), "", newCover, format, oldPath, target.absolutePath, cursor.getLong(cursor.getColumnIndexOrThrow("addedTime")), hash, "未分类"),
                )
            }
        }
        db.query("SELECT * FROM chapters_legacy").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = bookIds[cursor.getLong(cursor.getColumnIndexOrThrow("bookId"))] ?: continue
                db.execSQL("INSERT INTO chapters VALUES(?, ?, ?, ?)", arrayOf<Any?>(cursor.getLong(0), uuid, cursor.getString(cursor.getColumnIndexOrThrow("title")), cursor.getInt(cursor.getColumnIndexOrThrow("chapterIndex"))))
            }
        }
        db.execSQL("INSERT INTO paragraphs SELECT id, chapterId, paragraphIndex, text FROM paragraphs_legacy")
        db.query("SELECT * FROM reading_progress_legacy").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = bookIds[cursor.getLong(cursor.getColumnIndexOrThrow("bookId"))] ?: continue
                db.execSQL("INSERT INTO reading_progress VALUES(?, ?, ?, 0, ?, ?)", arrayOf<Any?>(uuid, cursor.getLong(cursor.getColumnIndexOrThrow("chapterId")), cursor.getInt(cursor.getColumnIndexOrThrow("position")), cursor.getLong(cursor.getColumnIndexOrThrow("updatedTime")), cursor.getFloat(cursor.getColumnIndexOrThrow("fraction"))))
            }
        }

        db.execSQL("DROP TABLE reading_progress_legacy")
        db.execSQL("DROP TABLE paragraphs_legacy")
        db.execSQL("DROP TABLE chapters_legacy")
        db.execSQL("DROP TABLE books_legacy")

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_contentHash` ON `books` (`contentHash`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bookUuid` ON `chapters` (`bookUuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_bookUuid_chapterIndex` ON `chapters` (`bookUuid`, `chapterIndex`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_paragraphs_chapterId` ON `paragraphs` (`chapterId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_paragraphs_chapterId_paragraphIndex` ON `paragraphs` (`chapterId`, `paragraphIndex`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_progress_chapterId` ON `reading_progress` (`chapterId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `text_edit_patches` (`uuid` TEXT NOT NULL, `bookUuid` TEXT NOT NULL, `chapterId` INTEGER NOT NULL, `paragraphIndex` INTEGER NOT NULL, `originalText` TEXT NOT NULL, `replacementText` TEXT NOT NULL, `createdTime` INTEGER NOT NULL, `undone` INTEGER NOT NULL, PRIMARY KEY(`uuid`), FOREIGN KEY(`bookUuid`) REFERENCES `books`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_edit_patches_bookUuid` ON `text_edit_patches` (`bookUuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_edit_patches_chapterId` ON `text_edit_patches` (`chapterId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_edit_patches_chapterId_paragraphIndex_createdTime` ON `text_edit_patches` (`chapterId`, `paragraphIndex`, `createdTime`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `metadata_edits` (`uuid` TEXT NOT NULL, `bookUuid` TEXT NOT NULL, `previousTitle` TEXT NOT NULL, `previousAuthor` TEXT NOT NULL, `previousDescription` TEXT NOT NULL, `newTitle` TEXT NOT NULL, `newAuthor` TEXT NOT NULL, `newDescription` TEXT NOT NULL, `createdTime` INTEGER NOT NULL, PRIMARY KEY(`uuid`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_edits_bookUuid` ON `metadata_edits` (`bookUuid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `reading_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookUuid` TEXT NOT NULL, `startedTime` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, `charactersRead` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookUuid` ON `reading_sessions` (`bookUuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_epochDay` ON `reading_sessions` (`epochDay`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_fonts` (`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `filePath` TEXT NOT NULL, `createdTime` INTEGER NOT NULL, PRIMARY KEY(`uuid`))")
    }
}

private fun SupportSQLiteDatabase.dropUserIndexes(table: String) {
    val names = buildList {
        query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                cursor.getString(nameColumn).takeUnless { it.startsWith("sqlite_autoindex_") }?.let(::add)
            }
        }
    }
    names.forEach { name -> execSQL("DROP INDEX IF EXISTS `${name.replace("`", "``")}`") }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
