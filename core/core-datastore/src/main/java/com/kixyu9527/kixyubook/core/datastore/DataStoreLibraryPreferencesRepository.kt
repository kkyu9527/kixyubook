package com.kixyu9527.kixyubook.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kixyu9527.kixyubook.core.common.model.LibraryPreferences
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.configuration.filenameRuleSpecToJson
import com.kixyu9527.kixyubook.core.common.configuration.jsonToFilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import org.json.JSONObject
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
    private val mutations = SettingsMutationJournal(context.libraryPreferencesDataStore, syncMutations)
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
            filenameRules = values[FILENAME_RULES].orEmpty().split('\n').filter(String::isNotBlank),
            filenameRuleSpecs = values[FILENAME_RULE_SPECS].orEmpty().split('\n')
                .filter(String::isNotBlank)
                .mapNotNull { line ->
                    runCatching { JSONObject(line) }.getOrNull()?.let(::jsonToFilenameRuleSpec)
                },
        )
    }

    override suspend fun setSortMode(mode: LibrarySortMode) {
        mutations.edit { it[SORT_MODE] = mode.name }
    }

    override suspend fun setLayoutMode(mode: LibraryLayoutMode) {
        mutations.edit { it[LAYOUT_MODE] = mode.name }
    }

    override suspend fun setCustomOrder(bookUuids: List<String>) {
        mutations.edit { values ->
            values[CUSTOM_ORDER] = bookUuids.distinct().joinToString(",")
        }
    }

    override suspend fun setFilenameRules(rules: List<String>) {
        mutations.edit { values ->
            values[FILENAME_RULES] = rules.map(String::trim).filter(String::isNotBlank).joinToString("\n")
        }
    }

    override suspend fun setFilenameRuleSpecs(specs: List<FilenameRuleSpec>) {
        mutations.edit { values ->
            values[FILENAME_RULE_SPECS] = specs.joinToString("\n") { filenameRuleSpecToJson(it).toString() }
        }
    }

    override suspend fun setCategoryHidden(category: String, hidden: Boolean) {
        mutations.edit { values ->
            val updated = values[HIDDEN_CATEGORIES].orEmpty().toMutableSet()
            if (hidden) updated += category else updated -= category
            values[HIDDEN_CATEGORIES] = updated
        }
    }

    override suspend fun addFilenameRule(rule: String) {
        val trimmed = rule.trim()
        if (trimmed.isEmpty()) return
        mutations.edit { values ->
            val existing = values[FILENAME_RULES].orEmpty().split('\n').filter(String::isNotBlank)
            if (trimmed !in existing) values[FILENAME_RULES] = (existing + trimmed).joinToString("\n")
        }
    }

    override suspend fun removeFilenameRule(rule: String) {
        mutations.edit { values ->
            values[FILENAME_RULES] = values[FILENAME_RULES].orEmpty().split('\n')
                .filter { it.isNotBlank() && it != rule }
                .joinToString("\n")
        }
    }

    override suspend fun addFilenameRuleSpec(spec: FilenameRuleSpec) {
        mutations.edit { values ->
            val existing = values[FILENAME_RULE_SPECS].orEmpty().split('\n')
                .filter(String::isNotBlank)
                .mapNotNull { runCatching { JSONObject(it) }.getOrNull()?.let(::jsonToFilenameRuleSpec) }
            if (existing.none { it.id == spec.id || (it.sample == spec.sample && it.roles == spec.roles) }) {
                values[FILENAME_RULE_SPECS] = (existing + spec)
                    .joinToString("\n") { filenameRuleSpecToJson(it).toString() }
            }
        }
    }

    override suspend fun upsertFilenameRuleSpec(spec: FilenameRuleSpec) {
        mutations.edit { values ->
            val existing = values[FILENAME_RULE_SPECS].orEmpty().split('\n')
                .filter(String::isNotBlank)
                .mapNotNull { runCatching { JSONObject(it) }.getOrNull()?.let(::jsonToFilenameRuleSpec) }
            // Replace in place: editing a rule must not silently change its match priority.
            val index = existing.indexOfFirst { it.id == spec.id }
            val updated = if (index >= 0) {
                existing.toMutableList().also { it[index] = spec }
            } else {
                existing + spec
            }
            values[FILENAME_RULE_SPECS] = updated.joinToString("\n") { filenameRuleSpecToJson(it).toString() }
        }
    }

    override suspend fun setFilenameRuleSpecEnabled(id: String, enabled: Boolean) {
        mutations.edit { values ->
            val updated = values[FILENAME_RULE_SPECS].orEmpty().split('\n')
                .filter(String::isNotBlank)
                .mapNotNull { runCatching { JSONObject(it) }.getOrNull()?.let(::jsonToFilenameRuleSpec) }
                .map { if (it.id == id) it.copy(enabled = enabled) else it }
            values[FILENAME_RULE_SPECS] = updated.joinToString("\n") { filenameRuleSpecToJson(it).toString() }
        }
    }

    override suspend fun removeFilenameRuleSpec(id: String) {
        mutations.edit { values ->
            val remaining = values[FILENAME_RULE_SPECS].orEmpty().split('\n')
                .filter(String::isNotBlank)
                .mapNotNull { runCatching { JSONObject(it) }.getOrNull()?.let(::jsonToFilenameRuleSpec) }
                .filterNot { it.id == id }
            values[FILENAME_RULE_SPECS] = remaining.joinToString("\n") { filenameRuleSpecToJson(it).toString() }
        }
    }

    override suspend fun replace(preferences: LibraryPreferences) {
        mutations.edit { values ->
            values[SORT_MODE] = preferences.sortMode.name
            values[LAYOUT_MODE] = preferences.layoutMode.name
            values[CUSTOM_ORDER] = preferences.customOrder.distinct().joinToString(",")
            values[HIDDEN_CATEGORIES] = preferences.hiddenCategories
            values[FILENAME_RULES] = preferences.filenameRules
                .map(String::trim).filter(String::isNotBlank).joinToString("\n")
            values[FILENAME_RULE_SPECS] = preferences.filenameRuleSpecs
                .joinToString("\n") { filenameRuleSpecToJson(it).toString() }
        }
    }

    private companion object {
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val CUSTOM_ORDER = stringPreferencesKey("custom_order")
        val HIDDEN_CATEGORIES = stringSetPreferencesKey("hidden_categories")
        val FILENAME_RULES = stringPreferencesKey("filename_rules")
        val FILENAME_RULE_SPECS = stringPreferencesKey("filename_rule_specs")
    }
}
