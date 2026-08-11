package com.kixyu9527.kixyubook.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kixyu9527.kixyubook.core.common.model.LibraryPreferences
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryPreferencesDataStore by preferencesDataStore(name = "library_preferences")

@Singleton
class DataStoreLibraryPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val syncMutations: SyncMutationRecorder,
) : LibraryPreferencesRepository {
    override val preferences: Flow<LibraryPreferences> = context.libraryPreferencesDataStore.data.map { values ->
        LibraryPreferences(
            sortMode = values[SORT_MODE]
                ?.let { runCatching { LibrarySortMode.valueOf(it) }.getOrNull() }
                ?: LibrarySortMode.RECENT,
            layoutMode = values[LAYOUT_MODE]
                ?.let { runCatching { LibraryLayoutMode.valueOf(it) }.getOrNull() }
                ?: LibraryLayoutMode.LIST,
            customOrder = values[CUSTOM_ORDER]
                .orEmpty()
                .split(',')
                .filter(String::isNotBlank),
            hiddenCategories = values[HIDDEN_CATEGORIES].orEmpty(),
        )
    }

    override suspend fun setSortMode(mode: LibrarySortMode) {
        context.libraryPreferencesDataStore.edit { it[SORT_MODE] = mode.name }
        recordChange()
    }

    override suspend fun setLayoutMode(mode: LibraryLayoutMode) {
        context.libraryPreferencesDataStore.edit { it[LAYOUT_MODE] = mode.name }
        recordChange()
    }

    override suspend fun setCustomOrder(bookUuids: List<String>) {
        context.libraryPreferencesDataStore.edit { values ->
            values[CUSTOM_ORDER] = bookUuids.distinct().joinToString(",")
        }
        recordChange()
    }

    override suspend fun setCategoryHidden(category: String, hidden: Boolean) {
        context.libraryPreferencesDataStore.edit { values ->
            val updated = values[HIDDEN_CATEGORIES].orEmpty().toMutableSet()
            if (hidden) updated += category else updated -= category
            values[HIDDEN_CATEGORIES] = updated
        }
        recordChange()
    }

    override suspend fun replace(preferences: LibraryPreferences) {
        context.libraryPreferencesDataStore.edit { values ->
            values[SORT_MODE] = preferences.sortMode.name
            values[LAYOUT_MODE] = preferences.layoutMode.name
            values[CUSTOM_ORDER] = preferences.customOrder.distinct().joinToString(",")
            values[HIDDEN_CATEGORIES] = preferences.hiddenCategories
        }
        recordChange()
    }

    private suspend fun recordChange() = syncMutations.record(SyncEntityType.SETTINGS, "global")

    private companion object {
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val CUSTOM_ORDER = stringPreferencesKey("custom_order")
        val HIDDEN_CATEGORIES = stringSetPreferencesKey("hidden_categories")
    }
}
