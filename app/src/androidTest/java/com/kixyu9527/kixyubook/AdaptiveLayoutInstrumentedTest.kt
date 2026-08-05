package com.kixyu9527.kixyubook

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.designsystem.component.classifyKixyuWindowSize
import org.junit.Rule
import org.junit.Test

class AdaptiveLayoutInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun adaptiveDecisionCanRecomposeFromCompactToExpanded() {
        var width by mutableStateOf(412.dp)
        var height by mutableStateOf(915.dp)
        composeRule.setContent {
            MaterialTheme {
                val window = classifyKixyuWindowSize(width, height)
                Text(if (window.supportsTwoPane) "双栏" else "单栏")
            }
        }

        composeRule.onNodeWithText("单栏").assertIsDisplayed()
        composeRule.runOnIdle {
            width = 1280.dp
            height = 800.dp
        }
        composeRule.onNodeWithText("双栏").assertIsDisplayed()
    }
}
