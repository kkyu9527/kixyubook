package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.width
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w1280dp-h800dp-land")
class ReaderDirectorySidePanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun landscapeTabletDirectoryPanelStaysAnchoredToTheStartEdge() {
        compose.setContent {
            KixyuBookTheme {
                ReaderDirectorySidePanel(
                    backdrop = rememberKixyuNavigationBackdrop(Color.Black),
                    panelProgress = 1f,
                    backProgress = { 0f },
                    visible = true,
                    onDismiss = {},
                ) {
                    Text("panel", Modifier.testTag("panel"))
                }
            }
        }

        val panel = compose.onNodeWithTag("panel").getUnclippedBoundsInRoot()
        val window = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "landscape tablets must keep the directory on the start half: left=${panel.left}, width=${window.width}",
            panel.left < window.width / 2f,
        )
        assertTrue(
            "the directory must stay a side panel instead of a full-width sheet: right=${panel.right}",
            panel.right < window.width,
        )
    }
}
