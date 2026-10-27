package zen.gtasaskinstudio.app.obj

import zen.gtasaskinstudio.app.model.InternalMaterial
import zen.gtasaskinstudio.app.model.InternalMesh
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.IssueCode
import zen.gtasaskinstudio.app.rw.Limits
import zen.gtasaskinstudio.app.rw.Severity

/**
 * Importer OBJ (Fase C). Mendukung v, vt, vn, f (segi tiga, segi empat, n-gon
 * dengan kipas), indeks negatif, usemtl. mtllib dicatat tapi .mtl belum dibaca.
 * Baris rusak dilaporkan dan dilewati; tidak pernah melempar exception.
 *
 * OBJ importer (Phase C). Supports v, vt, vn, f (triangles, quads, n-gons via
 * fan), negative indices and usemtl. mtllib is reported but .mtl is not read yet.
 * Bad lines are reported and skipped; it never throws.
 */
sealed class ObjParseResult {
    class Success(val mesh: InternalMesh, val issues: List<Issue>) : ObjParseResult()
    class Failure(val issues: List<Issue>) : ObjParseResult()
}

object ObjImporter {

    private const val MAX_REPORTED = 100
    private const val DEFAULT_MATERIAL = "default"

    fun parse(bytes: ByteArray): ObjParseResult {
        if (bytes.size > Limits.MAX_FILE_BYTES) {
            return ObjParseResult.Failure(listOf(Issue(Severity.ERROR, IssueCode.FILE_TOO_LARGE, -1, bytes.size.toString())))
        }
        return try {
            Parser(String(bytes, Charsets.ISO_8859_1)).run()
        } catch (e: RuntimeException) {
            ObjParseResult.Failure(listOf(Issue(Severity.ERROR, IssueCode.UNEXPECTED_STRUCTURE, -1, e.javaClass.simpleName)))
        }
    }

    private class FloatList {
        var data = FloatArray(256)
        var size = 0
        fun add(x: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = x
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }

    private class IntList {
        var data = IntArray(256)
        var size = 0
        fun add(x: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = x
        }
        fun toArray(): IntArray = data.copyOf(size)
    }

    private class Parser(private val text: String) {
        private val issues = ArrayList<Issue>()
        private var dropped = 0

        // Data mentah dari baris v / vt / vn (indeks 1-based di file).
        private val rawPos = FloatList()
        private val rawUv = FloatList()
        private val rawNrm = FloatList()
        private var rawUvCount = 0
        private var rawNrmCount = 0

        // Vertex keluaran: satu per kombinasi (v, vt, vn) yang dipakai face.
        private val outPos = FloatList()
        private val outUv = FloatList()
        private val outNrm = FloatList()
        private val outIndexOf = HashMap<Triple<Int, Int, Int>, Int>()
        private var outVertexCount = 0
        private val tris = IntList()
        private val triMat = IntList()

        private val materialNames = ArrayList<String>()
        private val materialIndex = HashMap<String, Int>()
        private var currentMaterial = -1

        private var cornersWithUv = 0
        private var cornersWithoutUv = 0
        private var cornersWithNrm = 0
        private var cornersWithoutNrm = 0

        private var tooMany = false

        fun run(): ObjParseResult {
            var start = 0
            var lineNo = 0
            while (start < text.length && !tooMany) {
                var end = text.indexOf('\n', start)
                if (end < 0) end = text.length
                val line = text.substring(start, end).trim()
                start = end + 1
                lineNo++
                if (line.isEmpty() || line[0] == '#') continue
                val parts = line.split(WHITESPACE)
                when (parts[0]) {
                    "v" -> readVertex(parts, lineNo, rawPos, 3)
                    "vt" -> { readVertex(parts, lineNo, rawUv, 2); rawUvCount++ }
                    "vn" -> { readVertex(parts, lineNo, rawNrm, 3); rawNrmCount++ }
                    "f" -> readFace(parts, lineNo)
                    "usemtl" -> currentMaterial = materialFor(parts.getOrElse(1) { DEFAULT_MATERIAL })
                    "mtllib" -> report(Severity.INFO, IssueCode.OBJ_DIRECTIVE_IGNORED, "line $lineNo")
                }
            }
            if (tooMany) return fail(IssueCode.TOO_MANY_ELEMENTS, "vertices or triangles over limit")
            if (tris.size == 0) return fail(IssueCode.UNEXPECTED_STRUCTURE, "no valid faces")
            if (dropped > 0) issues.add(Issue(Severity.WARNING, IssueCode.OBJ_FACE_INVALID, -1, "+$dropped more"))

            if (cornersWithUv > 0 && cornersWithoutUv > 0) {
                issues.add(Issue(Severity.WARNING, IssueCode.OBJ_ATTRIBUTE_PARTIAL, -1, "uv"))
            }
            if (cornersWithNrm > 0 && cornersWithoutNrm > 0) {
                issues.add(Issue(Severity.WARNING, IssueCode.OBJ_ATTRIBUTE_PARTIAL, -1, "normal"))
            }
            val uvs = if (cornersWithUv > 0 && cornersWithoutUv == 0) outUv.toArray() else null
            val nrm = if (cornersWithNrm > 0 && cornersWithoutNrm == 0) outNrm.toArray() else null
            if (materialNames.isEmpty()) materialFor(DEFAULT_MATERIAL)

            val mesh = InternalMesh(
                positions = outPos.toArray(),
                normals = nrm,
                uvs = uvs,
                triangles = tris.toArray(),
                triMaterial = triMat.toArray(),
                materials = materialNames.map { InternalMaterial(it, null) },
            )
            return ObjParseResult.Success(mesh, issues)
        }

        private fun readVertex(parts: List<String>, lineNo: Int, dst: FloatList, n: Int) {
            var ok = parts.size - 1 >= n
            val values = FloatArray(n)
            if (ok) {
                for (k in 0 until n) {
                    val f = parts[k + 1].toFloatOrNull()
                    if (f == null) { ok = false; break }
                    if (!f.isFinite()) {
                        report(Severity.WARNING, IssueCode.NON_FINITE_VALUE, "line $lineNo")
                        values[k] = 0f
                    } else {
                        values[k] = f
                    }
                }
            }
            if (!ok) report(Severity.WARNING, IssueCode.UNEXPECTED_STRUCTURE, "line $lineNo")
            // Selalu tambah entri (nol bila rusak) agar indeks baris lain tidak bergeser.
            // Always append (zeros when bad) so indices of other lines stay aligned.
            for (v in values) dst.add(if (ok) v else 0f)
        }

        private fun readFace(parts: List<String>, lineNo: Int) {
            if (parts.size - 1 < 3) {
                report(Severity.WARNING, IssueCode.OBJ_FACE_INVALID, "line $lineNo")
                return
            }
            val corners = IntArray(parts.size - 1)
            val hasUv = BooleanArray(corners.size)
            val hasNrm = BooleanArray(corners.size)
            for (k in corners.indices) {
                val tok = parts[k + 1].split('/')
                val v = resolve(tok[0], rawPos.size / 3)
                val vt = if (tok.size >= 2 && tok[1].isNotEmpty()) resolve(tok[1], rawUvCount) else -2
                val vn = if (tok.size >= 3 && tok[2].isNotEmpty()) resolve(tok[2], rawNrmCount) else -2
                if (v < 0 || (tok.size >= 2 && tok[1].isNotEmpty() && vt < 0) || (tok.size >= 3 && tok[2].isNotEmpty() && vn < 0)) {
                    report(Severity.WARNING, IssueCode.OBJ_FACE_INVALID, "line $lineNo")
                    return
                }
                corners[k] = outputVertex(v, vt, vn)
                hasUv[k] = vt >= 0
                hasNrm[k] = vn >= 0
                if (tooMany) return
            }
            // Kipas segi tiga: (0, k, k+1). / Triangle fan: (0, k, k+1).
            if (currentMaterial < 0) currentMaterial = materialFor(DEFAULT_MATERIAL)
            for (k in 1 until corners.size - 1) {
                if (tris.size / 3 >= Limits.MAX_TRIANGLES) { tooMany = true; return }
                tris.add(corners[0]); tris.add(corners[k]); tris.add(corners[k + 1])
                triMat.add(currentMaterial)
            }
            for (k in corners.indices) {
                if (hasUv[k]) cornersWithUv++ else cornersWithoutUv++
                if (hasNrm[k]) cornersWithNrm++ else cornersWithoutNrm++
            }
        }

        /** Indeks 0-based, atau -1 bila tidak valid. Negatif = relatif dari akhir. */
        private fun resolve(s: String, count: Int): Int {
            val i = s.toIntOrNull() ?: return -1
            return when {
                i > 0 && i <= count -> i - 1
                i < 0 && count + i >= 0 -> count + i
                else -> -1
            }
        }

        private fun outputVertex(v: Int, vt: Int, vn: Int): Int {
            val key = Triple(v, vt, vn)
            outIndexOf[key]?.let { return it }
            if (outVertexCount >= InternalMesh.MAX_VERTICES) { tooMany = true; return 0 }
            val idx = outVertexCount++
            outPos.add(rawPos.data[v * 3]); outPos.add(rawPos.data[v * 3 + 1]); outPos.add(rawPos.data[v * 3 + 2])
            if (vt >= 0) { outUv.add(rawUv.data[vt * 2]); outUv.add(rawUv.data[vt * 2 + 1]) } else { outUv.add(0f); outUv.add(0f) }
            if (vn >= 0) { outNrm.add(rawNrm.data[vn * 3]); outNrm.add(rawNrm.data[vn * 3 + 1]); outNrm.add(rawNrm.data[vn * 3 + 2]) } else { outNrm.add(0f); outNrm.add(0f); outNrm.add(0f) }
            outIndexOf[key] = idx
            return idx
        }

        private fun materialFor(name: String): Int =
            materialIndex.getOrPut(name) {
                materialNames.add(name)
                materialNames.size - 1
            }

        private fun report(sev: Severity, code: IssueCode, detail: String) {
            if (issues.size < MAX_REPORTED) issues.add(Issue(sev, code, -1, detail)) else dropped++
        }

        private fun fail(code: IssueCode, detail: String) =
            ObjParseResult.Failure(issues + Issue(Severity.ERROR, code, -1, detail))

        companion object {
            private val WHITESPACE = Regex("\\s+")
        }
    }
}
