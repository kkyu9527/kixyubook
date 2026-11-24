package com.kixyu9527.kixyubook.core.reader.engine

import org.junit.Assert.*
import org.junit.Test

class EpubImageThemeTest {
    @Test fun largeTransparentNearNeutralInkNeedsNoWhitePaperAndLightModeKeepsItsOriginalColor() {
        val pixels = IntArray(128 * 128) { if (it % 3 == 0) 0xff221b19.toInt() else 0 }
        val profile = readerImageProfile(pixels)
        assertTrue(profile.singleInk)
        val night = imageAdaptiveInk(profile, 0xff101010.toInt(), 0xffffffff.toInt())!!
        assertTrue(imagePixelLuminance(night) in .54f..56f)
        assertNull(imageAdaptiveInk(profile, 0xfffafafa.toInt(), 0xff202020.toInt()))
    }

    @Test fun coloredAndTexturedArtIsNeverTreatedAsOneInkOrInverted() {
        val colored = readerImageProfile(IntArray(100) { when { it < 40 -> 0; it < 70 -> 0xff151515.toInt(); else -> 0xffff7020.toInt() } })
        assertFalse(colored.singleInk)
        assertNull(imageAdaptiveInk(colored, 0xff101010.toInt(), 0xffffffff.toInt()))
        val paper = imagePaperBackdrop(colored.inkLuminance, 0f)!!
        assertTrue("Backing is a minimum-contrast gray, not white", imagePixelLuminance(paper) in .1f..25f)
        val textured = readerImageProfile(IntArray(100) { if (it < 30) 0 else (0xff000000.toInt() or ((it % 100) * 0x010101)) })
        assertFalse(textured.singleInk)
        assertNull(imageAdaptiveInk(textured, 0xff000000.toInt(), 0xffffffff.toInt()))
        assertEquals(ReaderImageProfile(null, false), readerImageProfile(IntArray(100) { 0xff202020.toInt() }))
    }

    @Test fun whiteInkOnLightPaperUsesReadableDarkInkWhileEmptyImagesStayUnchanged() {
        val profile = readerImageProfile(IntArray(100) { if (it < 60) 0 else 0xffeeeeee.toInt() })
        assertNotNull(imageAdaptiveInk(profile, 0xfffafafa.toInt(), 0xff202020.toInt()))
        assertNull(imageAdaptiveInk(profile, 0xff101010.toInt(), 0xffeeeeee.toInt()))
        assertEquals(ReaderImageProfile(null, false), readerImageProfile(IntArray(100)))
        assertEquals(ReaderImageProfile(null, false), readerImageProfile(intArrayOf()))
    }
}
