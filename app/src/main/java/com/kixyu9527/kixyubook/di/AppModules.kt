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
    fun provideDatabase(@ApplicationContext context: Context): KixyuDatabase =
        Room.databaseBuilder(context, KixyuDatabase::class.java, "kixyu-books.db")
            .build()

    @Provides
    fun provideBookDao(database: KixyuDatabase): BookDao = database.bookDao()

    @Provides fun provideFontDao(database: KixyuDatabase): FontDao = database.fontDao()

    @Provides fun provideSyncDao(database: KixyuDatabase): SyncDao = database.syncDao()

}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindBookRepository(implementation: LocalBookRepository): BookRepository
    @Binds abstract fun bindCompleteLibraryRepository(implementation: LocalBookRepository): CompleteLibraryRepository
    @Binds abstract fun bindBackupRepository(implementation: LocalBackupRepository): BackupRepository
    @Binds abstract fun bindSettingsRepository(implementation: DataStoreReaderSettingsRepository): ReaderSettingsRepository
    @Binds abstract fun bindLibraryPreferencesRepository(implementation: DataStoreLibraryPreferencesRepository): LibraryPreferencesRepository
    @Binds abstract fun bindLibraryCatalogRepository(implementation: DefaultLibraryCatalogRepository): LibraryCatalogRepository
    @Binds abstract fun bindStatsRepository(implementation: LocalReadingStatsRepository): ReadingStatsRepository
    @Binds abstract fun bindFontRepository(implementation: LocalFontRepository): FontRepository
    @Binds abstract fun bindAppUpdateRepository(implementation: GitHubUpdateRepository): AppUpdateRepository
}
