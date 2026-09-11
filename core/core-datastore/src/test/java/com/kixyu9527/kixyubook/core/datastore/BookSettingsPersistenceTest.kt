package com.kixyu9527.kixyubook.core.datastore

import android.content.Context
import com.kixyu9527.kixyubook.core.common.configuration.applySettingsPatch
import com.kixyu9527.kixyubook.core.common.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookSettingsPersistenceTest {
    @Test fun independentBooksInheritGlobalResetAndCleanDeletedFontReferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val recorder = object : SyncMutationRecorder {
            override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) = Unit
        }
        val repository = DataStoreReaderSettingsRepository(context, recorder)
        repository.update { it.copy(fontSize = 19f, fontUuid = "font") }
        repository.setEnabled("book", true)
        repository.updateField("book", "fontSize", "{\"fontSize\":25}")
        repository.updateField("book", "fontUuid", "{\"fontUuid\":\"other-font\"}")
        repository.update { it.copy(lineHeight = 2f) }
        val stored = repository.overrides.first()
        val effective = applySettingsPatch(repository.settings.first(), stored["book"])
        assertEquals(25f, effective.fontSize); assertEquals(2f, effective.lineHeight)
        assertEquals("other-font", effective.fontUuid)
        assertEquals(19f, applySettingsPatch(repository.settings.first(), stored["unconfigured"]).fontSize)
        repository.clearFontReferences("other-font")
        assertEquals("font", applySettingsPatch(repository.settings.first(), repository.overrides.first()["book"]).fontUuid)
        repository.clearFontReferences("font")
        assertNull(repository.settings.first().fontUuid)
        repository.setEnabled("book", false)
        assertFalse(repository.overrides.first().containsKey("book"))
        repository.replaceAll(stored)
        assertEquals(stored, repository.overrides.first())
    }
}
