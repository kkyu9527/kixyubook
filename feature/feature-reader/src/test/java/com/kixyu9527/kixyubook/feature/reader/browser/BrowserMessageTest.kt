package com.kixyu9527.kixyubook.feature.reader.browser

import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserMessageTest {
    private val chapter = ReaderChapter(1, "book", "Chapter", 5, listOf(Paragraph(1,1,7,"Original text.")))

    @Test fun onlyCurrentTokenAndActualParagraphRangesCanReachTheReader() {
        assertNull(parseBrowserMessage("""{"type":"ready","token":"old"}""", "current", chapter))
        assertNull(parseBrowserMessage("""{"type":"boundary","token":"current","delta":999}""", "current", chapter))
        assertNull(parseBrowserMessage("""{"type":"position","token":"current","p":99,"c":0,"end":99}""", "current", chapter))
        assertNull(parseBrowserMessage("""{"type":"position","token":"current","p":7,"c":99,"end":7}""", "current", chapter))
        assertNull(parseBrowserMessage("""{"type":"selection","token":"current","p":7,"start":0,"end":99,"x":0,"y":0,"w":10,"h":10}""", "current", chapter))
        assertEquals(BrowserMessage.Position(7,3,7,false), parseBrowserMessage("""{"type":"position","token":"current","p":7,"c":3,"end":7}""", "current", chapter))
        val event = """{"type":"position","token":"current","navigationVersion":3,"layoutVersion":2,"p":7,"c":3,"end":7}"""
        assertNull(parseBrowserMessage(event, "current", chapter, 4, 2))
        assertNull(parseBrowserMessage(event, "current", chapter, 3, 3))
        assertEquals(BrowserMessage.Position(7,3,7,false), parseBrowserMessage(event, "current", chapter, 3, 2))
    }

    @Test fun correctedTextCanBeProjectedBackToItsOriginalDomWithoutGuessing() {
        val first = TextCorrection("a","book","hash","key",5,7,0,5,"Alpha","","","A",createdTime=1,updatedTime=1)
        val second = first.copy(uuid="b",startOffset=6,endOffset=10,exactText="beta",replacementText="BETA-LONG")
        assertEquals("Alpha beta end", browserOriginalText("A BETA-LONG end",listOf(first,second)))
        assertEquals("different text", browserOriginalText("different text",listOf(first,second)))
    }

    @Test fun crossParagraphSelectionReconstructsCopyFromTheNativeSourceInsteadOfTrustingBrowserText() {
        val next = chapter.copy(paragraphs = chapter.paragraphs + Paragraph(2,1,8,"Second paragraph."))
        val message = parseBrowserMessage("""{"type":"selection","token":"current","p":7,"start":9,"endP":8,"end":6,"text":"spoofed","x":1,"y":2,"w":10,"h":20}""", "current", next)
        assertTrue(message is BrowserMessage.Selection)
        message as BrowserMessage.Selection
        assertEquals("text.\nSecond", message.copyText)
        assertEquals(8, message.endParagraph)
        assertNull(parseBrowserMessage("""{"type":"selection","token":"current","p":8,"start":0,"endP":7,"end":3,"x":1,"y":2,"w":10,"h":20}""", "current", next))
    }

    @Test fun illustrationsSharingATextAnchorCannotInterceptProgressOrSelectionOffsets() {
        val withArt = chapter.copy(paragraphs = listOf(Paragraph(99,1,7,"",kind = ParagraphKind.IMAGE)) + chapter.paragraphs)
        assertEquals(BrowserMessage.Position(7,10,7,false), parseBrowserMessage(
            """{"type":"position","token":"current","p":7,"c":10,"end":7}""", "current", withArt))
        val selection = parseBrowserMessage("""{"type":"selection","token":"current","p":7,"start":9,"end":13,"x":1,"y":2,"w":10,"h":20}""", "current", withArt)
        assertEquals("text", (selection as BrowserMessage.Selection).copyText)
    }
}
