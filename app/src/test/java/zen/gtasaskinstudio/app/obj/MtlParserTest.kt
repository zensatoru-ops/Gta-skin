package zen.gtasaskinstudio.app.obj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MtlParserTest {

    private fun parse(text: String) = MtlParser.parse(text.toByteArray(Charsets.ISO_8859_1))

    @Test fun mapsMaterialToTextureStem() {
        val m = parse("newmtl skin\nKd 1 1 1\nmap_Kd textures/Alan74.bmp\n")
        assertEquals("Alan74", m["skin"])
    }

    @Test fun pathWithBackslashesAndOptionsUsesLastToken() {
        val m = parse("newmtl hair\nmap_Kd -s 1 1 1 C:\\art\\hair_front.png\n")
        assertEquals("hair_front", m["hair"])
    }

    @Test fun materialWithoutMapHasNoEntry() {
        val m = parse("newmtl plain\nKd 0.5 0.5 0.5\n")
        assertTrue(m.isEmpty())
    }

    @Test fun mapBeforeAnyNewmtlIsIgnored() {
        assertTrue(parse("map_Kd orphan.png\n").isEmpty())
    }

    @Test fun malformedInputNeverThrows() {
        val rnd = java.util.Random(3)
        for (k in 0 until 30) {
            val b = ByteArray(500) { rnd.nextInt(256).toByte() }
            MtlParser.parse(b)
        }
        parse("newmtl\nmap_Kd\n")
    }
}
