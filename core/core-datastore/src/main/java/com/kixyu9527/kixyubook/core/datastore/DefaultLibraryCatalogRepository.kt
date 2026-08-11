package com.kixyu9527.kixyubook.core.datastore

import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.repository.CompleteLibraryRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryCatalog
import com.kixyu9527.kixyubook.core.common.repository.LibraryCatalogRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.partitionLibraryCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLibraryCatalogRepository @Inject constructor(
    books: CompleteLibraryRepository,
    preferences: LibraryPreferencesRepository,
) : LibraryCatalogRepository {
    override val catalog: Flow<LibraryCatalog> = combine(
        books.observeCompleteLibrary(),
        preferences.preferences,
        ::partitionLibraryCatalog,
    ).distinctUntilChanged()

    override fun observeVisibleLibrary(): Flow<List<LibraryBook>> = catalog
        .map { it.visibleBooks }
        .distinctUntilChanged()

    override fun observeHiddenLibrary(): Flow<List<LibraryBook>> = catalog
        .map { it.hiddenBooks }
        .distinctUntilChanged()
}
