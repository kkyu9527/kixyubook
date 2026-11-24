package com.kixyu9527.kixyubook.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mirrors the bounded header + weighted collection contract used by the two-pane library. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w1280dp-h800dp")
class LibraryFiltersLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialFiltersLeaveRoomForTheCollection() = verify(AppUiStyle.MATERIAL)
    @Test fun miuixFiltersLeaveRoomForTheCollection() = verify(AppUiStyle.MIUIX)

    private fun verify(style: AppUiStyle) {
        var selected: String? = null
        compose.setContent {
            KixyuBookTheme(uiStyle = style) {
                Column(Modifier.fillMaxSize()) {
                    LibraryFilters(
                        state = LibraryUiState(categories = listOf(ALL_LIBRARY_CATEGORIES, "Fiction")),
                        onSearch = {},
                        onCategory = { selected = it },
                    )
                    Box(Modifier.weight(1f).fillMaxSize()) { Text("Book collection") }
                }
            }
        }
        val collection = compose.onNodeWithText("Book collection").assertIsDisplayed()
        assertTrue(collection.fetchSemanticsNode().boundsInRoot.top < 200f *
            ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithContentDescription(context.getString(R.string.library_choose_category)).performClick()
        compose.onNodeWithText("Fiction").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("Fiction", selected) }
        collection.assertIsDisplayed()
    }
}
