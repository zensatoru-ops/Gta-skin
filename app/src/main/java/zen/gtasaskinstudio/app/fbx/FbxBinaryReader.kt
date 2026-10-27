package zen.gtasaskinstudio.app.fbx

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.IssueCode
import zen.gtasaskinstudio.app.rw.Limits
import zen.gtasaskinstudio.app.rw.Severity

/**
 * Node FBX. Properti disimpan sebagai: Short, Boolean, Int, Float, Double, Long,
 * String, ByteArray (raw), atau array tipe primitif (FloatArray, DoubleArray,
 * IntArray, LongArray, BooleanArray).
 *
 * FBX node. Properties are stored as: Short, Boolean, Int, Float, Double, Long,
 * String, ByteArray (raw), or primitive arrays.
 */
class FbxNode(val name: String, val props: List<Any>, val children: List<FbxNode>) {
    fun child(name: String): FbxNode? = children.firstOrNull { it.name == name }
}

internal class FbxFailure(val issue: Issue) : Exception(issue.code.name)

/**
 * Pembaca FBX biner (versi 7.x). Hanya struktur node yang dibaca; interpretasi
 * (mesh, material) ada di [FbxImporter]. Setiap pembacaan dibatasi panjang
 * berkasnya, jadi berkas rusak menghasilkan [FbxFailure], bukan crash.
 *
 * Binary FBX reader (7.x). Only the node structure is read; meaning (mesh, material)
 * lives in [FbxImporter]. Every read is bounded by the file length, so a corrupt file
 * yields [FbxFailure], not a crash.
 */
object FbxBinaryReader {

    private const val MAGIC = "Kaydara FBX Binary  \u0000"
    private const val HEADER_SIZE = 27
    private const val MAX_DEPTH = 64
    private const val MAX_NODES = 2_000_000

    class Document(val version: Int, val roots: List<FbxNode>)

    fun read(bytes: ByteArray): Document {
        if (bytes.size < HEADER_SIZE || String(bytes, 0, 21, Charsets.ISO_8859_1) != MAGIC) {
            throw FbxFailure(Issue(Severity.ERROR, IssueCode.UNEXPECTED_STRUCTURE, 0, "not binary FBX"))
        }
        val r = Reader(bytes)
        val version = r.buf.getInt(23)
        r.wide = version >= 7500
        r.pos = HEADER_SIZE
        val roots = ArrayList<FbxNode>()
        while (r.pos + r.headerSize <= bytes.size) {
            val node = r.node(0) ?: break
            roots.add(node)
        }
        return Document(version, roots)
    }

    private class Reader(val data: ByteArray) {
        val buf: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var wide = false
        var pos = 0
        var nodes = 0

        val headerSize: Int get() = if (wide) 25 else 13

        private fun need(n: Long) {
            if (n < 0 || pos + n > data.size) {
                throw FbxFailure(Issue(Severity.ERROR, IssueCode.TRUNCATED, pos, "need=$n"))
            }
        }

        private fun u32(): Long { need(4); val v = buf.getInt(pos).toLong() and 0xFFFFFFFFL; pos += 4; return v }
        private fun u64(): Long { need(8); val v = buf.getLong(pos); pos += 8; return v }
        private fun u8(): Int { need(1); return data[pos++].toInt() and 0xFF }

        /** Node biasa, atau null untuk record kosong (penanda akhir daftar). / A node, or null for the empty end-of-list record. */
        fun node(depth: Int): FbxNode? {
            if (depth > MAX_DEPTH) throw FbxFailure(Issue(Severity.ERROR, IssueCode.TOO_MANY_ELEMENTS, pos, "depth"))
            if (++nodes > MAX_NODES) throw FbxFailure(Issue(Severity.ERROR, IssueCode.TOO_MANY_ELEMENTS, pos, "nodes"))
            val endOffset: Long
            val numProps: Long
            val propListLen: Long
            if (wide) {
                endOffset = u64(); numProps = u64(); propListLen = u64()
            } else {
                endOffset = u32(); numProps = u32(); propListLen = u32()
            }
            val nameLen = u8()
            if (endOffset == 0L) return null
            if (endOffset > data.size || endOffset < pos) {
                throw FbxFailure(Issue(Severity.ERROR, IssueCode.TRUNCATED, pos, "endOffset=$endOffset"))
            }
            need(nameLen.toLong())
            val name = String(data, pos, nameLen, Charsets.ISO_8859_1)
            pos += nameLen

            val props = ArrayList<Any>()
            val propsStart = pos
            if (numProps > Limits.MAX_STRING.toLong() * 64) {
                throw FbxFailure(Issue(Severity.ERROR, IssueCode.TOO_MANY_ELEMENTS, pos, "props=$numProps"))
            }
            for (i in 0 until numProps) props.add(prop())
            if (propListLen > 0) pos = (propsStart + propListLen).toInt().coerceAtMost(data.size)

            val children = ArrayList<FbxNode>()
            if (pos < endOffset) {
                while (pos < endOffset) {
                    val c = node(depth + 1) ?: break
                    children.add(c)
                }
            }
            pos = endOffset.toInt()
            return FbxNode(name, props, children)
        }

        private fun prop(): Any {
            val t = u8().toChar()
            return when (t) {
                'Y' -> { need(2); val v = buf.getShort(pos); pos += 2; v }
                'C' -> u8() != 0
                'I' -> { need(4); val v = buf.getInt(pos); pos += 4; v }
                'F' -> { need(4); val v = buf.getFloat(pos); pos += 4; v }
                'D' -> { need(8); val v = buf.getDouble(pos); pos += 8; v }
                'L' -> { need(8); val v = buf.getLong(pos); pos += 8; v }
                'f', 'd', 'l', 'i', 'b' -> array(t)
                'S', 'R' -> {
                    val len = u32()
                    if (len > Limits.MAX_FILE_BYTES) throw FbxFailure(Issue(Severity.ERROR, IssueCode.TOO_MANY_ELEMENTS, pos, "len=$len"))
                    need(len)
                    val slice = data.copyOfRange(pos, pos + len.toInt())
                    pos += len.toInt()
                    if (t == 'S') String(slice, Charsets.ISO_8859_1) else slice
                }
                else -> throw FbxFailure(Issue(Severity.ERROR, IssueCode.UNEXPECTED_STRUCTURE, pos, "prop type '$t'"))
            }
        }

        private fun array(t: Char): Any {
            val count = u32()
            val encoding = u32()
            val compLen = u32()
            val elem = when (t) { 'f', 'i' -> 4; 'b' -> 1; else -> 8 }
            val expected = count * elem
            if (count > Limits.MAX_FILE_BYTES / elem) {
                throw FbxFailure(Issue(Severity.ERROR, IssueCode.TOO_MANY_ELEMENTS, pos, "array=$count"))
            }
            val raw: ByteArray
            if (encoding == 0L) {
                need(expected)
                raw = data.copyOfRange(pos, pos + expected.toInt())
                pos += expected.toInt()
            } else {
                need(compLen)
                raw = inflate(data, pos, compLen.toInt(), expected.toInt())
                pos += compLen.toInt()
            }
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val n = count.toInt()
            return when (t) {
                'd' -> DoubleArray(n).also { bb.asDoubleBuffer().get(it) }
                'f' -> FloatArray(n).also { bb.asFloatBuffer().get(it) }
                'i' -> IntArray(n).also { bb.asIntBuffer().get(it) }
                'l' -> LongArray(n).also { bb.asLongBuffer().get(it) }
                else -> BooleanArray(n) { raw[it].toInt() != 0 }
            }
        }

        private fun inflate(src: ByteArray, off: Int, len: Int, expected: Int): ByteArray {
            val inf = Inflater()
            try {
                inf.setInput(src, off, len)
                val out = ByteArray(expected)
                var got = 0
                while (got < expected && !inf.finished()) {
                    val k = inf.inflate(out, got, expected - got)
                    if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break
                    got += k
                }
                if (got != expected) {
                    throw FbxFailure(Issue(Severity.ERROR, IssueCode.TRUNCATED, off, "inflated=$got expected=$expected"))
                }
                return out
            } catch (e: DataFormatException) {
                throw FbxFailure(Issue(Severity.ERROR, IssueCode.UNEXPECTED_STRUCTURE, off, "zlib"))
            } finally {
                inf.end()
            }
        }
    }
}
