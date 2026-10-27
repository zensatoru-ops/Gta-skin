package zen.gtasaskinstudio.app.rw

/**
 * Dekode mip 0 ke ARGB (0xAARRGGBB) di CPU. Tidak memakai ekstensi GPU, sehingga
 * berjalan di semua perangkat GLES. Murni JVM agar bisa diuji.
 *
 * Decodes mip 0 to ARGB (0xAARRGGBB) on the CPU. No GPU texture-compression
 * extension is needed, so it runs on any GLES device. Pure JVM so it is testable.
 */
object TxdDecoder {

    fun toArgb(t: TxdTexture): IntArray {
        val w = t.width
        val h = t.height
        val out = IntArray(w * h)
        when (t.d3dFormat) {
            TxdFormat.A8R8G8B8 -> decodeA8R8G8B8(t.data, w, h, out)
            TxdFormat.DXT1 -> decodeBlocks(t.data, w, h, out, Dxt.DXT1)
            TxdFormat.DXT3 -> decodeBlocks(t.data, w, h, out, Dxt.DXT3)
            TxdFormat.DXT5 -> decodeBlocks(t.data, w, h, out, Dxt.DXT5)
            else -> throw IllegalArgumentException("format 0x${Integer.toHexString(t.d3dFormat)}")
        }
        return out
    }

    private fun decodeA8R8G8B8(src: ByteArray, w: Int, h: Int, out: IntArray) {
        // Memori BGRA little-endian = u32 0xAARRGGBB. / BGRA in memory = u32 0xAARRGGBB.
        for (i in 0 until w * h) {
            val o = i * 4
            out[i] = (src[o + 3].toInt() shl 24) or
                ((src[o + 2].toInt() and 0xFF) shl 16) or
                ((src[o + 1].toInt() and 0xFF) shl 8) or
                (src[o].toInt() and 0xFF)
        }
    }

    private enum class Dxt { DXT1, DXT3, DXT5 }

    private fun decodeBlocks(src: ByteArray, w: Int, h: Int, out: IntArray, kind: Dxt) {
        val bw = maxOf(1, (w + 3) / 4)
        val bh = maxOf(1, (h + 3) / 4)
        val blockBytes = if (kind == Dxt.DXT1) 8 else 16
        val colors = IntArray(4)
        val px = IntArray(16)
        for (by in 0 until bh) for (bx in 0 until bw) {
            val o = (by * bw + bx) * blockBytes
            var colorOff = o
            val alphas = IntArray(16) { 255 }
            if (kind == Dxt.DXT3) {
                for (i in 0 until 16) {
                    val nib = (src[o + i / 2].toInt() ushr ((i and 1) * 4)) and 0xF
                    alphas[i] = nib * 17
                }
                colorOff = o + 8
            } else if (kind == Dxt.DXT5) {
                decodeDxt5Alpha(src, o, alphas)
                colorOff = o + 8
            }
            val c0 = u16(src, colorOff)
            val c1 = u16(src, colorOff + 2)
            val rgb0 = expand565(c0)
            val rgb1 = expand565(c1)
            colors[0] = rgb0
            colors[1] = rgb1
            // DXT1 bisa punya mode alpha 1-bit saat c0 <= c1. DXT3/DXT5 selalu 4 warna.
            // DXT1 may use 1-bit alpha when c0 <= c1; DXT3/DXT5 always use 4 colors.
            val threeColor = kind == Dxt.DXT1 && c0 <= c1
            if (threeColor) {
                colors[2] = avgHalf(rgb0, rgb1)
                colors[3] = 0 // transparan / transparent
            } else {
                colors[2] = mix(rgb0, rgb1, 2, 1)
                colors[3] = mix(rgb0, rgb1, 1, 2)
            }
            val bits = u32(src, colorOff + 4)
            for (y in 0 until 4) for (x in 0 until 4) {
                val idx = (bits ushr (2 * (y * 4 + x))) and 3
                val alphaFromDxt1 = if (threeColor && idx == 3) 0 else 255
                val a = if (kind == Dxt.DXT1) alphaFromDxt1 else alphas[y * 4 + x]
                px[y * 4 + x] = (a shl 24) or (colors[idx] and 0xFFFFFF)
            }
            for (y in 0 until 4) for (x in 0 until 4) {
                val gx = bx * 4 + x
                val gy = by * 4 + y
                if (gx < w && gy < h) out[gy * w + gx] = px[y * 4 + x]
            }
        }
    }

    /** Alpha DXT5: dua nilai 8-bit dan 16 indeks 3-bit dalam 48 bit. / DXT5 alpha: two 8-bit endpoints and 16 3-bit indices. */
    private fun decodeDxt5Alpha(src: ByteArray, o: Int, alphas: IntArray) {
        val a0 = src[o].toInt() and 0xFF
        val a1 = src[o + 1].toInt() and 0xFF
        val table = IntArray(8)
        table[0] = a0
        table[1] = a1
        if (a0 > a1) {
            for (i in 1..6) table[i + 1] = ((6 - i) * a0 + i * a1) / 7
        } else {
            for (i in 1..4) table[i + 1] = ((4 - i) * a0 + i * a1) / 5
            table[6] = 0
            table[7] = 255
        }
        var bits = 0L
        for (i in 0 until 6) bits = bits or ((src[o + 2 + i].toLong() and 0xFF) shl (8 * i))
        for (i in 0 until 16) alphas[i] = table[((bits ushr (3 * i)) and 7).toInt()]
    }

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    /** RGB565 -> 0x00RRGGBB dengan pelebaran bit standar. / RGB565 to 0x00RRGGBB with standard bit replication. */
    private fun expand565(c: Int): Int {
        val r5 = (c ushr 11) and 0x1F
        val g6 = (c ushr 5) and 0x3F
        val b5 = c and 0x1F
        val r = (r5 shl 3) or (r5 ushr 2)
        val g = (g6 shl 2) or (g6 ushr 4)
        val b = (b5 shl 3) or (b5 ushr 2)
        return (r shl 16) or (g shl 8) or b
    }

    /** (a*wa + b*wb)/3 per kanal, untuk interpolasi 1/3 dan 2/3. / Per-channel (a*wa + b*wb)/3 interpolation. */
    private fun mix(a: Int, b: Int, wa: Int, wb: Int): Int {
        val r = (((a ushr 16) and 0xFF) * wa + ((b ushr 16) and 0xFF) * wb) / 3
        val g = (((a ushr 8) and 0xFF) * wa + ((b ushr 8) and 0xFF) * wb) / 3
        val bl = ((a and 0xFF) * wa + (b and 0xFF) * wb) / 3
        return (r shl 16) or (g shl 8) or bl
    }

    private fun avgHalf(a: Int, b: Int): Int {
        val r = (((a ushr 16) and 0xFF) + ((b ushr 16) and 0xFF)) / 2
        val g = (((a ushr 8) and 0xFF) + ((b ushr 8) and 0xFF)) / 2
        val bl = ((a and 0xFF) + (b and 0xFF)) / 2
        return (r shl 16) or (g shl 8) or bl
    }
}
