package com.kixyu9527.kixyubook.core.common.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface BookSettingsRepository {
    /** Presence (including an empty patch) means enabled; absent means inherit global settings. */
    val overrides: Flow<Map<String, String>>
    suspend fun setEnabled(bookUuid: String, enabled: Boolean)
    suspend fun updateField(bookUuid: String, field: String, encodedValue: String)
    suspend fun replaceAll(values: Map<String, String>)
    suspend fun clearFontReferences(fontUuid: String)
}

object NoBookSettings : BookSettingsRepository {
    override val overrides = flowOf(emptyMap<String, String>())
    override suspend fun setEnabled(bookUuid: String, enabled: Boolean) = Unit
    override suspend fun updateField(bookUuid: String, field: String, encodedValue: String) = Unit
    override suspend fun replaceAll(values: Map<String, String>) = Unit
    override suspend fun clearFontReferences(fontUuid: String) = Unit
}
