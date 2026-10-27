package zen.gtasaskinstudio.app.fbx

import zen.gtasaskinstudio.app.model.InternalMaterial
import zen.gtasaskinstudio.app.model.InternalMesh
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.IssueCode
import zen.gtasaskinstudio.app.rw.Limits
import zen.gtasaskinstudio.app.rw.Severity

/**
 * Importer FBX biner (Fase D, versi awal). Mengambil geometri mesh pertama
 * (Vertices, PolygonVertexIndex, normal dan UV per corner). Yang belum didukung
 * dilaporkan sebagai INFO: transform dan satuan Model, serta mapping material.
 * Semua material dipetakan ke satu material.
 *
 * Binary FBX importer (Phase D, first version). Reads the first Mesh geometry
 * (Vertices, PolygonVertexIndex, per-corner normals and UVs). Unsupported parts are
 * reported as INFO: Model transform and units, and material mapping. All faces map
 * to a single material.
 *
 * Varian yang didukung (matriks Fase D): FBX biner 7.x, ReferenceInformationType
 * Direct atau IndexToDirect, MappingInformationType ByPolygonVertex atau ByVertice.
 *
 * Supported variants (Phase D matrix): binary FBX 7.x, ReferenceInformationType Direct
 * or IndexToDirect, MappingInformationType ByPolygonVertex or ByVertice.
 */
sealed class FbxParseResult {
    class Success(val mesh: InternalMesh, val issues: List<Issue>) : FbxParseResult()
    class Failure(val issues: List<Issue>) : FbxParseResult()
}

object FbxImporter {

    private const val MAX_REPORTED = 100

    fun parse(bytes: ByteArray): FbxParseResult {
        if (bytes.size > Limits.MAX_FILE_BYTES) {
            return FbxParseResult.Failure(listOf(Issue(Severity.ERROR, IssueCode.FILE_TOO_LARGE, -1, bytes.size.toString())))
        }
        return try {
            build(FbxBinaryReader.read(bytes))
        } catch (e: FbxFailure) {
            FbxParseResult.Failure(listOf(e.issue))
        } catch (e: RuntimeException) {
            FbxParseResult.Failure(listOf(Issue(Severity.ERROR, IssueCode.UNEXPECTED_STRUCTURE, -1, e.javaClass.simpleName)))
        }
    }

    private fun fail(code: IssueCode, detail: String): Nothing = throw FbxFailure(Issue(Severity.ERROR, code, -1, detail))

    private fun build(doc: FbxBinaryReader.Document): FbxParseResult {
        val issues = ArrayList<Issue>()
        val objects = doc.roots.firstOrNull { it.name == "Objects" } ?: fail(IssueCode.NO_GEOMETRY, "no Objects")
        val meshes = objects.children.filter { it.name == "Geometry" && it.props.getOrNull(2) == "Mesh" }
        if (meshes.isEmpty()) fail(IssueCode.NO_GEOMETRY, "no Mesh geometry")
        if (meshes.size > 1) issues.add(Issue(Severity.WARNING, IssueCode.MULTI_GEOMETRY, -1, meshes.size.toString()))
        val g = meshes[0]

        val verts = g.child("Vertices")?.props?.firstOrNull() as? DoubleArray
            ?: fail(IssueCode.UNEXPECTED_STRUCTURE, "Vertices")
        if (verts.size % 3 != 0) fail(IssueCode.UNEXPECTED_STRUCTURE, "Vertices count")
        val pvi = g.child("PolygonVertexIndex")?.props?.firstOrNull() as? IntArray
            ?: fail(IssueCode.UNEXPECTED_STRUCTURE, "PolygonVertexIndex")
        val vCount = verts.size / 3

        // Polygon: daftar corner (indeks vertex + posisi corner). Akhir polygon = indeks negatif (-(i)-1).
        // Polygon: corner list (vertex index + corner position). Polygon end = negative index -(i)-1.
        val polys = ArrayList<IntArray>()           // isi: posisi corner dalam pvi
        val cur = ArrayList<Int>()
        for (k in pvi.indices) {
            val v = pvi[k]
            cur.add(k)
            if (v < 0) {
                polys.add(cur.toIntArray())
                cur.clear()
            }
        }
        if (cur.isNotEmpty()) issues.add(Issue(Severity.WARNING, IssueCode.UNEXPECTED_STRUCTURE, -1, "unterminated polygon"))

        val uvLayer = g.child("LayerElementUV")
        val nrmLayer = g.child("LayerElementNormal")
        val uvVals = uvLayer?.child("UV")?.props?.firstOrNull() as? DoubleArray
        val nrmVals = nrmLayer?.child("Normals")?.props?.firstOrNull() as? DoubleArray
        val hasUv = uvVals != null
        val hasNrm = nrmVals != null
        if (uvLayer != null && uvVals == null) fail(IssueCode.UNEXPECTED_STRUCTURE, "LayerElementUV without UV")
        if (nrmLayer != null && nrmVals == null) fail(IssueCode.UNEXPECTED_STRUCTURE, "LayerElementNormal without Normals")
        val uvIdx = indexMap(uvLayer, "UVIndex")
        val nrmIdx = indexMap(nrmLayer, null)
        val uvByVertex = mappingIsByVertex(uvLayer)
        val nrmByVertex = mappingIsByVertex(nrmLayer)

        // Ekspansi ke vertex keluaran dengan dedupe (vertex, uv, normal). / Expand to output vertices, deduped by (vertex, uv, normal).
        val outPos = FloatList()
        val outUv = FloatList()
        val outNrm = FloatList()
        val outIndex = HashMap<Triple<Int, Int, Int>, Int>()
        var outCount = 0
        val tris = IntList()
        val dropped = intArrayOf(0)

        fun corner(cornerPos: Int): Int {
            val vIdx = pvi[cornerPos].let { if (it < 0) it.inv() else it }
            if (vIdx < 0 || vIdx >= vCount) fail(IssueCode.TRIANGLE_INDEX_OUT_OF_RANGE, "vertex=$vIdx")
            val uv = if (!hasUv) -1 else {
                val ui = if (uvByVertex) vIdx else cornerPos
                val mapped = if (uvIdx != null) uvIdx.getOrNull(ui) ?: fail(IssueCode.TRIANGLE_INDEX_OUT_OF_RANGE, "uv") else ui
                if (mapped < 0 || mapped * 2 + 1 >= uvVals!!.size) fail(IssueCode.TRIANGLE_INDEX_OUT_OF_RANGE, "uv=$mapped")
                mapped
            }
            val nrm = if (!hasNrm) -1 else {
                val ni = if (nrmByVertex) vIdx else cornerPos
                if (ni < 0 || ni * 3 + 2 >= nrmVals!!.size) fail(IssueCode.TRIANGLE_INDEX_OUT_OF_RANGE, "normal=$ni")
                ni
            }
            val key = Triple(vIdx, uv, nrm)
            outIndex[key]?.let { return it }
            if (outCount >= InternalMesh.MAX_VERTICES) fail(IssueCode.TOO_MANY_ELEMENTS, "vertices over limit")
            val id = outCount++
            outPos.add(verts[vIdx * 3].toFloat()); outPos.add(verts[vIdx * 3 + 1].toFloat()); outPos.add(verts[vIdx * 3 + 2].toFloat())
            if (uv >= 0) { outUv.add(uvVals!![uv * 2].toFloat()); outUv.add(uvVals[uv * 2 + 1].toFloat()) } else { outUv.add(0f); outUv.add(0f) }
            if (nrm >= 0) { outNrm.add(nrmVals!![nrm * 3].toFloat()); outNrm.add(nrmVals[nrm * 3 + 1].toFloat()); outNrm.add(nrmVals[nrm * 3 + 2].toFloat()) }
            else { outNrm.add(0f); outNrm.add(0f); outNrm.add(0f) }
            outIndex[key] = id
            return id
        }

        for (poly in polys) {
            if (poly.size < 3) { dropped[0]++; continue }
            // Kipas segi tiga dari corner pertama. / Fan triangulation from the first corner.
            val c0 = corner(poly[0])
            for (k in 1 until poly.size - 1) {
                if (tris.size / 3 >= Limits.MAX_TRIANGLES) fail(IssueCode.TOO_MANY_ELEMENTS, "triangles over limit")
                tris.add(c0); tris.add(corner(poly[k])); tris.add(corner(poly[k + 1]))
            }
        }
        if (dropped[0] > 0) issues.add(Issue(Severity.WARNING, IssueCode.UNEXPECTED_STRUCTURE, -1, "polygons<3: ${dropped[0]}"))
        if (tris.size == 0) fail(IssueCode.UNEXPECTED_STRUCTURE, "no polygons")

        issues.add(Issue(Severity.INFO, IssueCode.FBX_PARTIAL, -1, "transform/units"))
        issues.add(Issue(Severity.INFO, IssueCode.FBX_PARTIAL, -1, "materials"))
        val matName = objects.children.firstOrNull { it.name == "Material" }
            ?.props?.getOrNull(1)?.let { (it as? String)?.substringBefore('\u0000') }
            ?.takeIf { it.isNotEmpty() } ?: "default"
        val triCount = tris.size / 3
        val mesh = InternalMesh(
            positions = outPos.toArray(),
            normals = if (hasNrm) outNrm.toArray() else null,
            uvs = if (hasUv) outUv.toArray() else null,
            triangles = tris.toArray(),
            triMaterial = IntArray(triCount),
            materials = listOf(InternalMaterial(matName, null)),
        )
        return FbxParseResult.Success(mesh, issues.take(MAX_REPORTED))
    }

    /** Indeks direct/indirect dari child "UVIndex" bila ada; null bila Direct. / Index array when IndexToDirect; null when Direct. */
    private fun indexMap(layer: FbxNode?, childName: String?): IntArray? {
        if (layer == null || childName == null) return null
        return layer.child(childName)?.props?.firstOrNull() as? IntArray
    }

    private fun mappingIsByVertex(layer: FbxNode?): Boolean {
        val mapping = layer?.child("MappingInformationType")?.props?.firstOrNull() as? String
        return mapping == "ByVertice" || mapping == "ByVertex"
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
}
