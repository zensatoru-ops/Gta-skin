package zen.gtasaskinstudio.app.rw

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxdTest {

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun chunk(type: Int, content: ByteArray): ByteArray =
        le32(type) + le32(content.size) + le32(0x1803FFFF) + content

    private fun cat(vararg parts: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        for (p in parts) o.write(p)
        return o.toByteArray()
    }

    private fun name32(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII).copyOf(32)

    /** Satu tekstur D3D9 dalam struct native. / One D3D9 texture native struct. */
    private fun textureChunk(name: String, format: Int, w: Int, h: Int, levels: Int, pixels: ByteArray): ByteArray {
        val structBody = cat(
            le32(9), le32(0x1102), name32(name), name32(""),
            le32(0x0800), le32(format), le32((h shl 16) or w), le32(levels shl 8), le32(pixels.size), pixels,
        )
        return chunk(0x15, cat(chunk(1, structBody), chunk(3, ByteArray(0))))
    }

    private fun txd(vararg textures: ByteArray): ByteArray {
        val dictStruct = chunk(1, cat(byteArrayOf(textures.size.toByte(), 0, 0, 0)))
        return chunk(0x16, cat(dictStruct, *textures))
    }

    private fun argb(t: TxdTexture) = TxdDecoder.toArgb(t)

    @Test fun parsesSingleDxt1Texture() {
        // blok 4x4 tunggal: c0 = merah 565, c1 = biru 565, semua indeks 0 -> semua merah
        // single 4x4 block: c0 = red (565), c1 = blue (565), all indices 0 -> all red
        val block = byteArrayOf(0x00, 0xF8.toByte(), 0x1F, 0x00, 0, 0, 0, 0)
        val r = TxdParser.parse(txd(textureChunk("tex_red", TxdFormat.DXT1, 4, 4, 1, block)))
        assertTrue(r is TxdParseResult.Success)
        val t = (r as TxdParseResult.Success).textures.single()
        assertEquals("tex_red", t.name)
        assertEquals(4, t.width); assertEquals(4, t.height)
        assertEquals("DXT1", t.formatName)
        val px = argb(t)
        assertTrue(px.all { it == 0xFFFF0000.toInt() })
    }

    @Test fun dxt1ThreeColorModeHasTransparentIndex() {
        // c0 <= c1 -> mode 3 warna; indeks 3 = transparan
        // c0 <= c1 selects 3-color mode; index 3 is transparent
        val block = byteArrayOf(0x1F, 0x00, 0x00, 0xF8.toByte(), 0xC0.toByte(), 0, 0, 0)
        val r = TxdParser.parse(txd(textureChunk("t", TxdFormat.DXT1, 4, 4, 1, block))) as TxdParseResult.Success
        val px = argb(r.textures.single())
        assertEquals(0xFF0000FF.toInt(), px[0]) // indeks 0 = biru / index 0 = blue
        assertEquals(0x00000000, px[3])          // indeks 3 = transparan (bit 6..7 = 3) / index 3 transparent
    }

    @Test fun dxt5AlphaInterpolationUsesEightValueTable() {
        // a0 = 255, a1 = 0, indeks 0 -> alpha 255; warna dibiarkan hitam
        // a0 = 255, a1 = 0, index 0 -> alpha 255; color left black
        val block = byteArrayOf(0xFF.toByte(), 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val r = TxdParser.parse(txd(textureChunk("t5", TxdFormat.DXT5, 4, 4, 1, block))) as TxdParseResult.Success
        val px = argb(r.textures.single())
        assertTrue(px.all { (it ushr 24) == 0xFF })
    }

    @Test fun a8r8g8b8DecodesBgraMemoryOrder() {
        // piksel: memori B=0x10 G=0x20 R=0x30 A=0x40 -> 0x40302010 (AARRGGBB)
        val pixels = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val r = TxdParser.parse(txd(textureChunk("p", TxdFormat.A8R8G8B8, 1, 1, 1, pixels))) as TxdParseResult.Success
        assertEquals(0x40302010, argb(r.textures.single())[0])
    }

    @Test fun unsupportedFormatIsSkippedWithWarning() {
        val r = TxdParser.parse(txd(textureChunk("x", 0x12345678, 4, 4, 1, ByteArray(16))))
        assertTrue(r is TxdParseResult.Success)
        val s = r as TxdParseResult.Success
        assertTrue(s.textures.isEmpty())
        assertTrue(s.issues.any { it.code == IssueCode.TXD_UNSUPPORTED_FORMAT })
    }

    @Test fun wrongDataSizeIsSkippedWithWarning() {
        val r = TxdParser.parse(txd(textureChunk("x", TxdFormat.DXT1, 4, 4, 1, ByteArray(4)))) as TxdParseResult.Success
        assertTrue(r.textures.isEmpty())
        assertTrue(r.issues.any { it.code == IssueCode.TXD_DATA_SIZE_MISMATCH })
    }

    @Test fun nonD3d9PlatformIsSkipped() {
        val structBody = cat(le32(8), le32(0x1102), name32("q"), name32(""), le32(0), le32(TxdFormat.DXT1), le32(0x40004), le32(0x100), le32(8), ByteArray(8))
        val tex = chunk(0x15, cat(chunk(1, structBody), chunk(3, ByteArray(0))))
        val r = TxdParser.parse(txd(tex)) as TxdParseResult.Success
        assertTrue(r.textures.isEmpty())
        assertTrue(r.issues.any { it.code == IssueCode.TXD_UNSUPPORTED_PLATFORM })
    }

    @Test fun truncatedFileFailsWithoutCrash() {
        val full = txd(textureChunk("t", TxdFormat.DXT1, 4, 4, 1, ByteArray(8)))
        for (cut in 0 until full.size) {
            val r = TxdParser.parse(full.copyOf(cut))
            // tidak boleh melempar / must never throw
            assertTrue(r is TxdParseResult.Failure || r is TxdParseResult.Success)
        }
    }

    @Test fun textureSizeLimitIsEnforced() {
        val big = textureChunk("big", TxdFormat.A8R8G8B8, Limits.MAX_TEXTURE_DIM + 1, 1, 1, ByteArray(4))
        val r = TxdParser.parse(txd(big)) as TxdParseResult.Success
        assertTrue(r.textures.isEmpty())
        assertEquals(true, r.issues.any { it.code == IssueCode.TXD_TEXTURE_INVALID })
    }
}
