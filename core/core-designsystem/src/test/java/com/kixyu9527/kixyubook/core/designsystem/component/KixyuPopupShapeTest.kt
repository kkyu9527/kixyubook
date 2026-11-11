package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuPopupShapeTest {
    @Test
    fun popupShapeIsAlwaysRounded() {
        assertTrue(KixyuPopupShape is RoundedCornerShape)
    }
}
