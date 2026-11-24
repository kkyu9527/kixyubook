package com.kixyu9527.kixyubook.core.datastore

import android.content.Context
import com.kixyu9527.kixyubook.core.common.model.ReaderColorScheme
import com.kixyu9527.kixyubook.core.common.model.CustomReaderTheme
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderPalettePersistenceTest {
    @Test fun allPalettesAndCustomColoursArePersistedAndRegisteredForSync() = runBlocking {
        val mutations = mutableListOf<SyncEntityType>()
        val recorder = object : SyncMutationRecorder {
            override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
                mutations += type
            }
        }
        val context = RuntimeEnvironment.getApplication() as Context
        val repository = DataStoreReaderSettingsRepository(context, recorder)
        val custom = CustomReaderTheme(backgroundHex = "#123456")
        ReaderColorScheme.entries.forEach { scheme ->
            repository.update { it.copy(readerColorScheme = scheme, customDayTheme = custom) }
            val restored = DataStoreReaderSettingsRepository(context, recorder).settings.first()
            assertEquals(scheme, restored.readerColorScheme)
            assertEquals(custom, restored.customDayTheme)
        }
        assertTrue("Palette edits must enter the settings sync journal", mutations.isNotEmpty())
    }
}
