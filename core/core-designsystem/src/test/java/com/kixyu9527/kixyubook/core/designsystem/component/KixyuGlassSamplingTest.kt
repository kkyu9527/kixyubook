package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KixyuGlassSamplingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun siblingSurfacesCanSampleButNestedSurfacesCannotSampleThemselves() {
        lateinit var source: LayoutCoordinates
        lateinit var nested: LayoutCoordinates
        lateinit var sibling: LayoutCoordinates
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(200.dp).onGloballyPositioned { source = it }) {
                    Box(Modifier.size(80.dp).onGloballyPositioned { nested = it })
                }
                Box(Modifier.size(80.dp).onGloballyPositioned { sibling = it })
            }
        }
        compose.runOnIdle {
            assertFalse(canSampleKixyuGlassBackdrop(source, source))
            assertFalse(canSampleKixyuGlassBackdrop(source, nested))
            assertTrue(canSampleKixyuGlassBackdrop(source, sibling))
        }
    }

    @Test fun independentDialogWindowsCanUseThePageBackdrop() {
        lateinit var page: LayoutCoordinates
        lateinit var dialog: LayoutCoordinates
        compose.setContent {
            Box(Modifier.size(200.dp).onGloballyPositioned { page = it })
            Dialog(onDismissRequest = {}, properties = KixyuEdgeToEdgeDialogProperties) {
                Box(Modifier.size(80.dp).onGloballyPositioned { dialog = it })
            }
        }
        compose.runOnIdle { assertTrue(canSampleKixyuGlassBackdrop(page, dialog)) }
    }

    @Test fun missingOrDetachedSourcesNeverEnterTheRenderer() {
        val visible = mutableStateOf(true)
        lateinit var source: LayoutCoordinates
        lateinit var consumer: LayoutCoordinates
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                if (visible.value) Box(Modifier.size(200.dp).onGloballyPositioned { source = it })
                Box(Modifier.size(80.dp).onGloballyPositioned { consumer = it })
            }
        }
        compose.runOnIdle {
            assertFalse(canSampleKixyuGlassBackdrop(null, consumer))
            assertFalse(canSampleKixyuGlassBackdrop(source, null))
            visible.value = false
        }
        compose.runOnIdle { assertFalse(canSampleKixyuGlassBackdrop(source, consumer)) }
    }
}
