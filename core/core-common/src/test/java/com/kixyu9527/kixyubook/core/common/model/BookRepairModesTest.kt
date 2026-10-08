package com.kixyu9527.kixyubook.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BookRepairModesTest {
    @Test fun theRepairMenuOnlyOffersOperationsBackedByRealWork() {
        // LibraryBookRepairDialog renders this shared list. FTS no longer exists, so a third
        // "search index repair" action would promise an operation the database cannot perform.
        assertEquals(listOf("CACHE", "REPARSE"), BookRepairMode.entries.map { it.name })
    }
}
