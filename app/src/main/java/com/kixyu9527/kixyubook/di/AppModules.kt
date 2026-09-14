package com.kixyu9527.kixyubook.di

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.CompleteLibraryRepository
import com.kixyu9527.kixyubook.core.common.repository.BackupRepository
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.ReadingStatsRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryCatalogRepository
import com.kixyu9527.kixyubook.core.common.repository.AppUpdateRepository
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.LocalBookRepository
import com.kixyu9527.kixyubook.core.database.LocalBackupRepository
import com.kixyu9527.kixyubook.core.database.LocalFontRepository
import com.kixyu9527.kixyubook.core.database.LocalReadingStatsRepository
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.dao.TextCorrectionDao
import com.kixyu9527.kixyubook.core.database.dao.ReaderAnnotationDao
import com.kixyu9527.kixyubook.core.database.dao.ImportDao
import com.kixyu9527.kixyubook.core.database.MIGRATION_6_7
import com.kixyu9527.kixyubook.core.database.MIGRATION_7_8
import com.kixyu9527.kixyubook.core.database.MIGRATION_8_9
import com.kixyu9527.kixyubook.core.database.MIGRATION_9_12
import com.kixyu9527.kixyubook.core.database.MIGRATION_10_12
import com.kixyu9527.kixyubook.core.database.MIGRATION_11_12
import com.kixyu9527.kixyubook.core.database.MIGRATION_12_13
import com.kixyu9527.kixyubook.core.database.MIGRATION_12_14
import com.kixyu9527.kixyubook.core.database.MIGRATION_13_14
import com.kixyu9527.kixyubook.core.database.MIGRATION_14_15
import com.kixyu9527.kixyubook.core.database.LocalTextCorrectionRepository
import com.kixyu9527.kixyubook.core.database.LocalReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.common.repository.TextCorrectionRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.datastore.DataStoreReaderSettingsRepository
import com.kixyu9527.kixyubook.core.datastore.DataStoreLibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.datastore.DefaultLibraryCatalogRepository
import com.kixyu9527.kixyubook.update.GitHubUpdateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KixyuDatabase {
        com.kixyu9527.kixyubook.core.common.repository.StartupRecoveryState.requireReady()
        return Room.databaseBuilder(context, KixyuDatabase::class.java, "kixyu-books.db")
            .addMigrations(
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_12,
                MIGRATION_10_12,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_12_14,
                MIGRATION_13_14,
                MIGRATION_14_15,
            )
            .build()
    }

    @Provides
    fun provideBookDao(database: KixyuDatabase): BookDao = database.bookDao()

    @Provides fun provideFontDao(database: KixyuDatabase): FontDao = database.fontDao()

    @Provides fun provideSyncDao(database: KixyuDatabase): SyncDao = database.syncDao()

    @Provides fun provideTextCorrectionDao(database: KixyuDatabase): TextCorrectionDao = database.textCorrectionDao()

    @Provides fun provideReaderAnnotationDao(database: KixyuDatabase): ReaderAnnotationDao = database.readerAnnotationDao()

    @Provides fun provideImportDao(database: KixyuDatabase): ImportDao = database.importDao()

}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindBookSettings(implementation: DataStoreReaderSettingsRepository): com.kixyu9527.kixyubook.core.common.repository.BookSettingsRepository
    @Binds abstract fun bindReadingReminders(implementation: com.kixyu9527.kixyubook.core.sync.NotificationPreferencesStore): com.kixyu9527.kixyubook.core.common.repository.ReadingReminderRepository
    @Binds abstract fun bindBookRepository(implementation: LocalBookRepository): BookRepository
    @Binds abstract fun bindCompleteLibraryRepository(implementation: LocalBookRepository): CompleteLibraryRepository
    @Binds abstract fun bindBackupRepository(implementation: LocalBackupRepository): BackupRepository
    @Binds abstract fun bindSettingsRepository(implementation: DataStoreReaderSettingsRepository): ReaderSettingsRepository
    @Binds abstract fun bindLibraryPreferencesRepository(implementation: DataStoreLibraryPreferencesRepository): LibraryPreferencesRepository
    @Binds abstract fun bindLibraryCatalogRepository(implementation: DefaultLibraryCatalogRepository): LibraryCatalogRepository
    @Binds abstract fun bindStatsRepository(implementation: LocalReadingStatsRepository): ReadingStatsRepository
    @Binds abstract fun bindFontRepository(implementation: LocalFontRepository): FontRepository
    @Binds abstract fun bindAppUpdateRepository(implementation: GitHubUpdateRepository): AppUpdateRepository
    @Binds abstract fun bindTextCorrectionRepository(implementation: LocalTextCorrectionRepository): TextCorrectionRepository
    @Binds abstract fun bindReaderAnnotationRepository(implementation: LocalReaderAnnotationRepository): ReaderAnnotationRepository
}
