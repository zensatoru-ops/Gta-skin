package zen.gtasaskinstudio.app.scene

import zen.gtasaskinstudio.app.rw.Limits

/** Hasil decode TGA: piksel BGRA dalam urutan memori, baris atas ke bawah. / Decoded TGA: BGRA bytes, top row first. */
class TgaImage(val width: Int, val height: Int, val bgra: ByteArray)

/**
 * Decoder TGA murni (tanpa Android), supaya bisa diuji di JVM.
 * Mendukung tipe 2 (truecolor), 3 (grayscale), 10 dan 11 (RLE), dengan 8/16/24/32 bit.
 * Tipe color-mapped (1, 9) tidak didukung dan mengembalikan null.
 *
 * Pure TGA decoder (no Android), testable on the JVM. Supports types 2 (truecolor),
 * 3 (grayscale), 10 and 11 (RLE), at 8/16/24/32 bits. Color-mapped types (1, 9) are
 * not supported and return null.
 */
object TgaDecoder {

    private val OPAQUE: Byte = -1

    fun decode(src: ByteArray): TgaImage? {
        if (src.size < 18) return null
        val idLen = ub(src, 0)
        val colorMapType = ub(src, 1)
        val imageType = ub(src, 2)
        val w = ub(src, 12) or (ub(src, 13) shl 8)
        val h = ub(src, 14) or (ub(src, 15) shl 8)
        val bpp = ub(src, 16)
        val desc = ub(src, 17)

        if (colorMapType != 0) return null
        if (w <= 0 || h <= 0 || w > Limits.MAX_TEXTURE_DIM || h > Limits.MAX_TEXTURE_DIM) return null
        val gray = imageType == 3 || imageType == 11
        val truecolor = imageType == 2 || imageType == 10
        if (!gray && !truecolor) return null
        if (gray && bpp != 8) return null
        if (truecolor && bpp != 16 && bpp != 24 && bpp != 32) return null

        val rle = imageType >= 10
        val topOrigin = (desc and 0x20) != 0
        val alphaBits = desc and 0x0F
        val bytesPerPixel = if (bpp == 16) 2 else bpp / 8
        val total = w * h
        val out = ByteArray(total * 4)

        fun writePixel(index: Int, at: Int) {
            val x = index % w
            val y = index / w
            val row = if (topOrigin) y else h - 1 - y
            val o = (row * w + x) * 4
            when (bpp) {
                8 -> {
                    val v = src[at]
                    out[o] = v; out[o + 1] = v; out[o + 2] = v; out[o + 3] = OPAQUE
                }
                16 -> {
                    val v = ub(src, at) or (ub(src, at + 1) shl 8)
                    out[o] = expand5(v and 31)
                    out[o + 1] = expand5((v shr 5) and 31)
                    out[o + 2] = expand5((v shr 10) and 31)
                    out[o + 3] = if (alphaBits == 0 || (v shr 15) != 0) OPAQUE else 0.toByte()
                }
                24 -> {
                    out[o] = src[at]; out[o + 1] = src[at + 1]; out[o + 2] = src[at + 2]; out[o + 3] = OPAQUE
                }
                else -> {
                    out[o] = src[at]; out[o + 1] = src[at + 1]; out[o + 2] = src[at + 2]
                    out[o + 3] = if (alphaBits == 0) OPAQUE else src[at + 3]
                }
            }
        }

        var pos = 18 + idLen
        var i = 0
        if (!rle) {
            if (src.size < pos + total * bytesPerPixel) return null
            while (i < total) {
                writePixel(i, pos)
                pos += bytesPerPixel
                i++
            }
            return TgaImage(w, h, out)
        }

        while (i < total) {
            if (pos >= src.size) return null
            val header = ub(src, pos++)
            val count = (header and 0x7F) + 1
            if ((header and 0x80) != 0) {
                // Paket run: satu piksel diulang. / Run packet: one pixel repeated.
                if (pos + bytesPerPixel > src.size) return null
                for (k in 0 until count) {
                    if (i >= total) break
                    writePixel(i++, pos)
                }
                pos += bytesPerPixel
            } else {
                // Paket raw: count piksel berurutan. / Raw packet: count pixels in sequence.
                if (pos + count * bytesPerPixel > src.size) return null
                for (k in 0 until count) {
                    if (i >= total) break
                    writePixel(i++, pos)
                    pos += bytesPerPixel
                }
            }
        }
        return TgaImage(w, h, out)
    }

    private fun ub(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF

    /** Ekspansi 5-bit ke 8-bit. / Expand 5-bit to 8-bit. */
    private fun expand5(x: Int): Byte {
        val v = (x shl 3) or (x shr 2)
        return v.toByte()
    }
}
