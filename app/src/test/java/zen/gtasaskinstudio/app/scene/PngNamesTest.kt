package zen.gtasaskinstudio.app.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PngNamesTest {

    @Test fun primaryDocIdBecomesAbsolutePath() {
        assertEquals(
            "/storage/emulated/0/Download/Model/hinata.fbx",
            PngNames.pathFromDocId("primary:Download/Model/hinata.fbx"),
        )
    }

    @Test fun nonPrimaryOrMalformedDocIdIsRejected() {
        assertTrue(PngNames.pathFromDocId("7A3B-1C2D:Dir/x.fbx") == null)
        assertTrue(PngNames.pathFromDocId("1234") == null)
        assertTrue(PngNames.pathFromDocId("primary:") == null)
        assertTrue(PngNames.pathFromDocId("primary:../secret/x.dff") == null)
    }

    @Test fun pngStemIsCaseInsensitiveOnExtension() {
        assertEquals("Skin", PngNames.pngStem("Skin.PNG"))
        assertEquals("body", PngNames.pngStem("body.png"))
        assertTrue(PngNames.pngStem("body.jpg") == null)
    }

    @Test fun matchesWantedNamesCaseInsensitively() {
        val files = listOf("Hinata.FBX", "Skin.png", "body.PNG", "readme.txt")
        val got = PngNames.match(files, listOf("skin", "BODY", "missing"))
        assertEquals(mapOf("skin" to "Skin.png", "BODY" to "body.PNG"), got)
    }

    @Test fun firstFileWinsWhenTwoPngsShareAStem() {
        val got = PngNames.match(listOf("a.png", "A.PNG"), listOf("a"))
        assertEquals(mapOf("a" to "a.png"), got)
    }
}
