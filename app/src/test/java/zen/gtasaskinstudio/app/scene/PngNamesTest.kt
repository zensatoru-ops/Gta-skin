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

    @Test fun imageStemIsCaseInsensitiveOnExtension() {
        assertEquals("Skin", PngNames.imageStem("Skin.PNG"))
        assertEquals("body", PngNames.imageStem("body.png"))
        assertEquals("top hhh", PngNames.imageStem("top hhh.tga"))
        assertEquals("photo", PngNames.imageStem("photo.JPEG"))
        assertTrue(PngNames.imageStem("body.fbx") == null)
        assertTrue(PngNames.imageStem(".png") == null)
    }

    @Test fun matchesWantedNamesCaseInsensitively() {
        val files = listOf("Hinata.FBX", "Skin.png", "body.PNG", "readme.txt", "head hhhhh.tga")
        val got = PngNames.match(files, listOf("skin", "BODY", "Head HHHHH", "missing"))
        assertEquals(mapOf("skin" to "Skin.png", "BODY" to "body.PNG", "Head HHHHH" to "head hhhhh.tga"), got)
    }

    @Test fun firstFileWinsWhenTwoPngsShareAStem() {
        val got = PngNames.match(listOf("a.png", "A.PNG"), listOf("a"))
        assertEquals(mapOf("a" to "a.png"), got)
    }
}
