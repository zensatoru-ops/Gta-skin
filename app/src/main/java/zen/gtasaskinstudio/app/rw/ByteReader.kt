package zen.gtasaskinstudio.app.rw

/** ID chunk RenderWare yang dipakai parser. / RenderWare chunk IDs used by the parser. */
object Rw {
    const val STRUCT = 1
    const val STRING = 2
    const val EXTENSION = 3
    const val TEXTURE = 6
    const val MATERIAL = 7
    const val MATERIAL_LIST = 8
    const val FRAME_LIST = 14
    const val GEOMETRY = 15
    const val CLUMP = 16
    const val ATOMIC = 20
    const val GEOMETRY_LIST = 26
    const val RIGHT_TO_RENDER = 31
    const val SKIN = 278
    const val HANIM = 286
    const val MATFX = 288
    const val BIN_MESH = 1294
    const val SKY_GFX = 60909
    const val PIPELINE_SET = 39056115
    const val FRAME_NAME = 39056126

    // flag geometri / geometry flags
    const val GEO_TRISTRIP = 0x00000001
    const val GEO_TEXTURED = 0x00000004
    const val GEO_PRELIT = 0x00000008
    const val GEO_TEXTURED2 = 0x00000080
    const val GEO_NATIVE = 0x01000000

    /** libraryID -> versi RW, mengikuti rumus yang terdokumentasi di gtamods wiki. */
    fun rwVersion(libraryId: Int): Int =
        if ((libraryId and 0xFFFF0000.toInt()) != 0) {
            (((libraryId ushr 14) and 0x3FF00) + 0x30000) or ((libraryId ushr 16) and 0x3F)
        } else {
            libraryId shl 8
        }
}

/** Header chunk 12 byte. [contentEnd] sudah dipastikan berada di dalam induknya. */
class ChunkHeader(
    val type: Int,
    val size: Int,
    val libraryId: Int,
    /** Offset header chunk di berkas. */
    val start: Int,
) {
    val contentStart: Int get() = start + 12
    val contentEnd: Int get() = contentStart + size
    val version: Int get() = Rw.rwVersion(libraryId)
}

/**
 * Pembaca little-endian dengan batas eksplisit. Setiap pembacaan di luar
 * [end] melempar [ParseFailure] (bukan IndexOutOfBounds).
 */
class ByteReader(private val data: ByteArray, start: Int, val end: Int) {
    var pos: Int = start

    init {
        require(start in 0..end && end <= data.size) { "invalid reader window" }
    }

    val remaining: Int get() = end - pos

    private fun need(n: Int) {
        if (n < 0 || n > end - pos) {
            throw ParseFailure(
                Issue(Severity.ERROR, IssueCode.TRUNCATED, pos, "need=$n left=${end - pos}")
            )
        }
    }

    fun skip(n: Int) {
        need(n)
        pos += n
    }

    fun u8(): Int {
        need(1)
        return data[pos++].toInt() and 0xFF
    }

    fun u16(): Int {
        need(2)
        val v = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        return v
    }

    fun i32(): Int {
        need(4)
        val v = (data[pos].toInt() and 0xFF) or
            ((data[pos + 1].toInt() and 0xFF) shl 8) or
            ((data[pos + 2].toInt() and 0xFF) shl 16) or
            ((data[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return v
    }

    fun f32(): Float = Float.fromBits(i32())

    fun floats(count: Int): FloatArray {
        need(multiplyOrFail(count, 4))
        val out = FloatArray(count)
        for (i in 0 until count) out[i] = f32()
        return out
    }

    fun bytes(count: Int): ByteArray {
        need(count)
        val out = data.copyOfRange(pos, pos + count)
        pos += count
        return out
    }

    /**
     * Baca header chunk berikutnya dan pastikan isinya muat di dalam jendela
     * pembaca ini. Posisi pembaca berakhir di awal isi chunk.
     */
    fun chunk(): ChunkHeader {
        val headerStart = pos
        need(12)
        val type = i32()
        val size = i32()
        val lib = i32()
        if (size < 0 || size > end - pos) {
            throw ParseFailure(
                Issue(
                    Severity.ERROR, IssueCode.CHUNK_EXCEEDS_PARENT, headerStart,
                    "type=$type size=${size.toLong() and 0xFFFFFFFFL} left=${end - pos} header=" +
                        "%08x %08x %08x".format(type, size, lib)
                )
            )
        }
        return ChunkHeader(type, size, lib, headerStart)
    }

    /** Pembaca baru untuk isi sebuah chunk. */
    fun contentOf(h: ChunkHeader): ByteReader = ByteReader(data, h.contentStart, h.contentEnd)

    /** String ASCII dari isi chunk (berhenti di NUL atau akhir isi; karakter non-ASCII jadi '?'). */
    fun asciiOf(h: ChunkHeader): String {
        val limit = minOf(h.size, Limits.MAX_STRING)
        val sb = StringBuilder()
        for (i in 0 until limit) {
            val b = data[h.contentStart + i].toInt() and 0xFF
            if (b == 0) break
            sb.append(if (b in 32..126) b.toChar() else '?')
        }
        return sb.toString()
    }

    companion object {
        /** a*b, atau [ParseFailure] bila meluap di Int. */
        fun multiplyOrFail(a: Int, b: Int): Int {
            val r = a.toLong() * b.toLong()
            if (a < 0 || b < 0 || r > Int.MAX_VALUE) {
                throw ParseFailure(Issue(Severity.ERROR, IssueCode.TOO_MANY_ELEMENTS, -1, "$a*$b"))
            }
            return r.toInt()
        }
    }
}
