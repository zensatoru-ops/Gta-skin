package zen.gtasaskinstudio.app.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zen.gtasaskinstudio.app.rw.TxdFormat
import zen.gtasaskinstudio.app.rw.TxdTexture

class TextureSetTest {

    private fun tex(name: String) = TxdTexture(name, "", 1, 1, 1, TxdFormat.A8R8G8B8, byteArrayOf(0x10, 0x20, 0x30, 0x40))

    @Test fun rgbaOrderIsRedGreenBlueAlpha() {
        // ARGB 0x40302010 -> byte R=0x30, G=0x20, B=0x10, A=0x40
        val out = TextureSet.rgba(tex("t"))
        assertEquals(0x30, out[0].toInt() and 0xFF)
        assertEquals(0x20, out[1].toInt() and 0xFF)
        assertEquals(0x10, out[2].toInt() and 0xFF)
        assertEquals(0x40, out[3].toInt() and 0xFF)
    }

    @Test fun findIsCaseInsensitiveAndNullSafe() {
        val list = listOf(tex("Nikke_Alice"), tex("skin_skin"))
        assertEquals("Nikke_Alice", TextureSet.find(list, "nikke_alice")?.name)
        assertNull(TextureSet.find(list, "missing"))
        assertNull(TextureSet.find(list, null))
        assertNull(TextureSet.find(list, ""))
    }
}
