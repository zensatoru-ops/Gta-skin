package zen.gtasaskinstudio.app.rw

/**
 * Parser TXD (Texture Dictionary) defensif. Hanya mip 0 dan hanya platform D3D9
 * (platform 9) dengan format DXT1 / DXT3 / DXT5 / A8R8G8B8, sesuai yang terbukti
 * pada sampel A74_Alice.txd. Format lain dilaporkan dan dilewati, tidak dipaksa.
 *
 * Defensive TXD parser. Only mip 0 and only D3D9 (platform 9) textures in DXT1 /
 * DXT3 / DXT5 / A8R8G8B8, as proven on A74_Alice.txd. Other formats are reported
 * and skipped, never forced.
 *
 * Layout texture native D3D9 (dari byte sampel / from sample bytes):
 *   struct: platform u32, filter u32, name[32], mask[32],
 *           raster u32, d3dFormat u32 (FourCC atau D3DFMT),
 *           dims u32 (width = low16, height = high16),
 *           levels u32 (byte1 = jumlah level), dataSize u32 (ukuran mip 0), data...
 */
class TxdTexture(
    val name: String,
    val maskName: String,
    val width: Int,
    val height: Int,
    val levels: Int,
    val d3dFormat: Int,
    /** Isi mip 0 mentah sesuai [d3dFormat]. / Raw mip 0 bytes in [d3dFormat]. */
    val data: ByteArray,
) {
    val formatName: String
        get() = when (d3dFormat) {
            TxdFormat.DXT1 -> "DXT1"
            TxdFormat.DXT3 -> "DXT3"
            TxdFormat.DXT5 -> "DXT5"
            TxdFormat.A8R8G8B8 -> "A8R8G8B8"
            else -> "?"
        }
}

sealed class TxdParseResult {
    data class Success(val textures: List<TxdTexture>, val issues: List<Issue>) : TxdParseResult()
    data class Failure(val issues: List<Issue>) : TxdParseResult()
}

object TxdFormat {
    /** FourCC 'DXT1' little-endian. */
    const val DXT1 = 0x31545844
    const val DXT3 = 0x33545844
    const val DXT5 = 0x35545844
    /** D3DFMT_A8R8G8B8 = 21. */
    const val A8R8G8B8 = 21
    const val PLATFORM_D3D9 = 9

    /** Ukuran byte mip 0 yang diharapkan, atau -1 bila format tidak didukung. */
    fun expectedSize(format: Int, w: Int, h: Int): Long = when (format) {
        DXT1 -> blocks(w) * blocks(h) * 8L
        DXT3, DXT5 -> blocks(w) * blocks(h) * 16L
        A8R8G8B8 -> w.toLong() * h * 4L
        else -> -1L
    }

    private fun blocks(n: Int): Long = maxOf(1, (n + 3) / 4).toLong()
}

class TxdParser private constructor(private val data: ByteArray) {

    private val issues = ArrayList<Issue>()

    companion object {
        private const val TXD_DICTIONARY = 0x16
        private const val TXD_TEXTURE = 0x15

        fun parse(bytes: ByteArray): TxdParseResult = TxdParser(bytes).run()
    }

    private fun run(): TxdParseResult {
        if (data.size > Limits.MAX_FILE_BYTES) {
            return TxdParseResult.Failure(listOf(Issue(Severity.ERROR, IssueCode.FILE_TOO_LARGE, -1, data.size.toString())))
        }
        return try {
            val root = ByteReader(data, 0, data.size)
            val dict = root.chunk()
            if (dict.type != TXD_DICTIONARY) {
                fail(IssueCode.UNEXPECTED_STRUCTURE, dict.start, "txd type=${dict.type}")
            }
            val r = root.contentOf(dict)
            val s = r.chunk()
            if (s.type != Rw.STRUCT) fail(IssueCode.UNEXPECTED_STRUCTURE, s.start, "txd struct")
            val count = r.contentOf(s).u16()
            r.pos = s.contentEnd

            val textures = ArrayList<TxdTexture>()
            for (i in 0 until count) {
                if (r.remaining < 12) {
                    issues.add(Issue(Severity.WARNING, IssueCode.TRUNCATED, r.pos, "textures=$count read=$i"))
                    break
                }
                val c = r.chunk()
                if (c.type == TXD_TEXTURE) {
                    val t = parseTexture(c)
                    if (t != null) textures.add(t)
                } else {
                    issues.add(Issue(Severity.WARNING, IssueCode.UNSUPPORTED_CHUNKS, c.start, "0x${Integer.toHexString(c.type)}"))
                }
                r.pos = c.contentEnd
            }
            TxdParseResult.Success(textures, issues)
        } catch (e: ParseFailure) {
            issues.add(e.issue)
            TxdParseResult.Failure(issues)
        } catch (e: RuntimeException) {
            issues.add(Issue(Severity.ERROR, IssueCode.UNEXPECTED_STRUCTURE, -1, e.javaClass.simpleName))
            TxdParseResult.Failure(issues)
        }
    }

    private fun fail(code: IssueCode, offset: Int, detail: String = ""): Nothing =
        throw ParseFailure(Issue(Severity.ERROR, code, offset, detail))

    /** Satu tekstur. Kesalahan pada satu tekstur hanya melewati tekstur itu. / One texture; a bad one is skipped alone. */
    private fun parseTexture(c: ChunkHeader): TxdTexture? {
        val st = ByteReader(data, c.contentStart, c.contentEnd)
        val child = st.chunk()
        if (child.type != Rw.STRUCT) {
            issues.add(Issue(Severity.WARNING, IssueCode.TXD_TEXTURE_INVALID, c.start, "no struct"))
            return null
        }
        val sr = st.contentOf(child)
        return try {
            val platform = sr.i32()
            if (platform != TxdFormat.PLATFORM_D3D9) {
                issues.add(Issue(Severity.WARNING, IssueCode.TXD_UNSUPPORTED_PLATFORM, c.start, "platform=$platform"))
                return null
            }
            sr.i32() // filter / addressing, tidak dipakai di tahap ini
            val name = sr.bytes(32).asciiTrim()
            val mask = sr.bytes(32).asciiTrim()
            sr.i32() // raster format
            val format = sr.i32()
            val dims = sr.i32()
            val w = dims and 0xFFFF
            val h = (dims ushr 16) and 0xFFFF
            val levels = (sr.i32() ushr 8) and 0xFF
            val dataSize = sr.i32()

            if (w !in 1..Limits.MAX_TEXTURE_DIM || h !in 1..Limits.MAX_TEXTURE_DIM) {
                issues.add(Issue(Severity.WARNING, IssueCode.TXD_TEXTURE_INVALID, c.start, "$name ${w}x$h"))
                return null
            }
            val expected = TxdFormat.expectedSize(format, w, h)
            if (expected < 0) {
                issues.add(Issue(Severity.WARNING, IssueCode.TXD_UNSUPPORTED_FORMAT, c.start, "$name fmt=0x${Integer.toHexString(format)}"))
                return null
            }
            if (dataSize.toLong() != expected) {
                issues.add(Issue(Severity.WARNING, IssueCode.TXD_DATA_SIZE_MISMATCH, c.start, "$name size=$dataSize expected=$expected"))
                return null
            }
            val pixels = sr.bytes(dataSize)
            TxdTexture(name, mask, w, h, levels, format, pixels)
        } catch (e: ParseFailure) {
            issues.add(Issue(Severity.WARNING, IssueCode.TXD_TEXTURE_INVALID, c.start, e.issue.detail))
            null
        }
    }

    private fun ByteArray.asciiTrim(): String {
        val end = indexOf(0.toByte()).let { if (it < 0) size else it }
        val sb = StringBuilder()
        for (i in 0 until end) {
            val b = this[i].toInt() and 0xFF
            sb.append(if (b in 32..126) b.toChar() else '?')
        }
        return sb.toString()
    }
}
