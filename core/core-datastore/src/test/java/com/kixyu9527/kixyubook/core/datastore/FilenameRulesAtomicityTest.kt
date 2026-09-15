package com.kixyu9527.kixyubook.core.datastore

import android.content.Context
import com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.model.FilenameSegmentRole
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FilenameRulesAtomicityTest {
    private val recorder = object : SyncMutationRecorder {
        override suspend fun record(
            type: SyncEntityType,
            entityId: String,
            operation: SyncMutationOperation,
        ) = Unit
    }

    private fun repository(context: Context) = DataStoreLibraryPreferencesRepository(context, recorder)

    @Test
    fun concurrentRemovalsDoNotResurrectARule() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val repository = repository(context)
        val specs = (1..4).map { index ->
            FilenameRuleSpec(
                id = "id-$index",
                sample = "示例$index-作者$index.txt",
                separator = "-",
                roles = listOf(FilenameSegmentRole.TITLE, FilenameSegmentRole.AUTHOR),
            )
        }
        repository.setFilenameRuleSpecs(specs)

        // The removals really run on different threads; a read-modify-write outside the store would
        // let one of them write back the other's stale list.
        joinAll(
            launch(Dispatchers.IO) { repository.removeFilenameRuleSpec("id-1") },
            launch(Dispatchers.IO) { repository.removeFilenameRuleSpec("id-2") },
        )

        assertEquals(
            setOf("id-3", "id-4"),
            repository.preferences.first().filenameRuleSpecs.map { it.id }.toSet(),
        )
    }

    @Test
    fun editingARuleKeepsItsMatchPriority() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val repository = repository(context)
        val first = FilenameRuleSpec(
            id = "first",
            sample = "三体-刘慈欣.txt",
            separator = "-",
            roles = listOf(FilenameSegmentRole.TITLE, FilenameSegmentRole.AUTHOR),
        )
        val second = FilenameRuleSpec(
            id = "second",
            sample = "刘慈欣-三体.txt",
            separator = "-",
            roles = listOf(FilenameSegmentRole.AUTHOR, FilenameSegmentRole.TITLE),
        )
        repository.setFilenameRuleSpecs(listOf(first, second))

        // Saving without changes must not move the rule to the end of the list.
        repository.upsertFilenameRuleSpec(first.copy(roles = first.roles))

        assertEquals(
            listOf("first", "second"),
            repository.preferences.first().filenameRuleSpecs.map { it.id },
        )
    }
}
