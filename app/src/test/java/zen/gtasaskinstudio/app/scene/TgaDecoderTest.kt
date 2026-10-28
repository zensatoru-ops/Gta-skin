package zen.gtasaskinstudio.app.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TgaDecoderTest {

    /** Header TGA 18 byte tanpa color map. / 18-byte TGA header with no color map. */
    private fun header(type: Int, w: Int, h: Int, bpp: Int, desc: Int): ByteArray {
        val b = ByteArray(18)
        b[2] = type.toByte()
        b[12] = (w and 0xFF).toByte(); b[13] = (w shr 8).toByte()
        b[14] = (h and 0xFF).toByte(); b[15] = (h shr 8).toByte()
        b[16] = bpp.toByte()
        b[17] = desc.toByte()
        return b
    }

    private fun u(v: Byte): Int = v.toInt() and 0xFF

    @Test fun uncompressedBgrWithBottomLeftOriginIsFlippedToTopFirst() {
        // 2x2, origin kiri-bawah: baris pertama di data = baris bawah. / Bottom-left origin: first stored row is the bottom row.
        val pixels = byteArrayOf(
            10, 0, 0, 20, 0, 0,   // baris bawah / bottom row
            30, 0, 0, 40, 0, 0,   // baris atas / top row
        )
        val img = TgaDecoder.decode(header(2, 2, 2, 24, 0) + pixels)!!
        assertEquals(2, img.width)
        assertEquals(2, img.height)
        // Keluaran dari baris atas: baris atas (30, 40) lalu baris bawah (10, 20). / Output top row first.
        assertEquals(30, u(img.bgra[0]))
        assertEquals(40, u(img.bgra[4]))
        assertEquals(10, u(img.bgra[8]))
        assertEquals(20, u(img.bgra[12]))
        assertEquals(255, u(img.bgra[3]))
    }

    @Test fun topLeftOriginIsKeptAsIs() {
        val pixels = byteArrayOf(1, 2, 3, 4, 5, 6)
        val img = TgaDecoder.decode(header(2, 2, 1, 24, 0x20) + pixels)!!
        assertEquals(1, u(img.bgra[0])); assertEquals(2, u(img.bgra[1])); assertEquals(3, u(img.bgra[2]))
        assertEquals(4, u(img.bgra[4])); assertEquals(5, u(img.bgra[5])); assertEquals(6, u(img.bgra[6]))
    }

    @Test fun rleRunPacketRepeatsOnePixel() {
        // Paket run dengan count 3 (header 0x80 | 2), lalu piksel (7, 8, 9). / Run packet count 3, then pixel (7, 8, 9).
        val pixels = byteArrayOf((0x80 or 2).toByte(), 7, 8, 9)
        val img = TgaDecoder.decode(header(10, 3, 1, 24, 0x20) + pixels)!!
        for (x in 0 until 3) {
            assertEquals(7, u(img.bgra[x * 4]))
            assertEquals(8, u(img.bgra[x * 4 + 1]))
            assertEquals(9, u(img.bgra[x * 4 + 2]))
        }
    }

    @Test fun rleRawPacketCopiesPixels() {
        // Paket raw count 2 (header 1), lalu dua piksel berurutan. / Raw packet count 2, then two pixels.
        val pixels = byteArrayOf(1, 1, 2, 3, 4, 5, 6)
        val img = TgaDecoder.decode(header(10, 2, 1, 24, 0x20) + pixels)!!
        assertEquals(1, u(img.bgra[0])); assertEquals(3, u(img.bgra[2]))
        assertEquals(4, u(img.bgra[4])); assertEquals(6, u(img.bgra[6]))
    }

    @Test fun thirtyTwoBitKeepsAlpha() {
        val pixels = byteArrayOf(1, 2, 3, 128.toByte())
        val img = TgaDecoder.decode(header(2, 1, 1, 32, 0x28) + pixels)!!
        assertEquals(128, u(img.bgra[3]))
    }

    @Test fun grayscaleIsExpandedToBgr() {
        val img = TgaDecoder.decode(header(3, 1, 1, 8, 0x20) + byteArrayOf(77))!!
        assertEquals(77, u(img.bgra[0])); assertEquals(77, u(img.bgra[1])); assertEquals(77, u(img.bgra[2]))
    }

    @Test fun colorMappedAndTruncatedAreRejected() {
        val mapped = header(1, 1, 1, 8, 0x20).also { it[1] = 1 } + byteArrayOf(0)
        assertTrue(TgaDecoder.decode(mapped) == null)
        assertTrue(TgaDecoder.decode(header(2, 4, 4, 24, 0x20) + byteArrayOf(1, 2, 3)) == null)
        assertTrue(TgaDecoder.decode(ByteArray(10)) == null)
    }
}
