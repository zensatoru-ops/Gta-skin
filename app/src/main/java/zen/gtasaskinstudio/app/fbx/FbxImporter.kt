package zen.gtasaskinstudio.app.fbx

import kotlin.math.cos
import kotlin.math.sin
import zen.gtasaskinstudio.app.model.InternalMaterial
import zen.gtasaskinstudio.app.model.InternalMesh
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.IssueCode
import zen.gtasaskinstudio.app.rw.Limits
import zen.gtasaskinstudio.app.rw.Severity

/**
 * Importer FBX biner (Fase D). Menggabungkan semua geometri Mesh menjadi satu mesh.
 * Tiap geometri memakai transform Model induknya (rantai parent, Lcl T/R/S, dan
 * offset geometrik), serta material yang terhubung lewat Connections. Satuan
 * (UnitScaleFactor) belum diterapkan dan dilaporkan INFO.
 *
 * Binary FBX importer (Phase D). Merges all Mesh geometries into one mesh. Each
 * geometry uses its parent Model transform (parent chain, Lcl T/R/S and geometric
 * offsets) and the material linked through Connections. Units (UnitScaleFactor) are
 * not applied yet and are reported as INFO.
 *
 * Varian yang didukung: FBX biner 7.x, ReferenceInformationType Direct atau
 * IndexToDirect, MappingInformationType ByPolygonVertex atau ByVertice.
 * Pivot dan offset Lcl belum diterapkan.
 */
sealed class FbxParseResult {
    class Success(val mesh: InternalMesh, val issues: List<Issue>) : FbxParseResult()
    class Failure(val issues: List<Issue>) : FbxParseResult()
}

object FbxImporter {

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

    /** Bagian hasil satu geometri, sudah ditransformasi, dengan indeks lokal. / One geometry's result, transformed, with local indices. */
    private class Part(
        val positions: FloatArray,
        val normals: FloatArray?,
        val uvs: FloatArray?,
        val tris: IntArray,
        val material: Int,
    )

    private fun build(doc: FbxBinaryReader.Document): FbxParseResult {
        val issues = ArrayList<Issue>()
        val objects = doc.roots.firstOrNull { it.name == "Objects" } ?: fail(IssueCode.NO_GEOMETRY, "no Objects")

        // Relasi OO: child -> parent. / OO relations: child -> parent.
        val parentsOf = HashMap<Long, MutableList<Long>>()
        val childrenOf = HashMap<Long, MutableList<Long>>()
        doc.roots.firstOrNull { it.name == "Connections" }?.children?.forEach { c ->
            if (c.name != "C" || c.props.getOrNull(0) != "OO") return@forEach
            val child = c.props.getOrNull(1) as? Long ?: return@forEach
            val parent = c.props.getOrNull(2) as? Long ?: return@forEach
            parentsOf.getOrPut(child) { mutableListOf() }.add(parent)
            childrenOf.getOrPut(parent) { mutableListOf() }.add(child)
        }

        val models = HashMap<Long, FbxNode>()
        val materialNames = HashMap<Long, String>()
        for (o in objects.children) {
            val id = o.props.getOrNull(0) as? Long ?: continue
            when (o.name) {
                "Model" -> models[id] = o
                "Material" -> materialNames[id] = (o.props.getOrNull(1) as? String)?.substringBefore('\u0000')
                    ?.takeIf { it.isNotEmpty() } ?: "material$id"
            }
        }

        val geoms = objects.children.filter { it.name == "Geometry" && it.props.getOrNull(2) == "Mesh" }
        if (geoms.isEmpty()) fail(IssueCode.NO_GEOMETRY, "no Mesh geometry")

        // Matriks dunia per Model, dengan memo dan batas kedalaman. / World matrix per Model, memoized, depth-limited.
        val worldCache = HashMap<Long, DoubleArray>()
        fun modelParent(id: Long): Long? = parentsOf[id]?.firstOrNull { models.containsKey(it) }
        fun world(id: Long, depth: Int): DoubleArray {
            worldCache[id]?.let { return it }
            val node = models[id] ?: return Mat.identity()
            val local = Mat.trs(props70(node, "Lcl Translation"), props70(node, "Lcl Rotation"), props70(node, "Lcl Scaling", 1.0))
            val parent = modelParent(id)
            val m = if (parent != null && depth < 64) Mat.mul(world(parent, depth + 1), local) else local
            worldCache[id] = m
            return m
        }

        val matList = ArrayList<String>()
        val matIndex = HashMap<String, Int>()
        fun materialFor(modelId: Long?): Int {
            val id = modelId ?: return matIndexOf("default", matList, matIndex)
            val mat = childrenOf[id]?.firstOrNull { materialNames.containsKey(it) }
            val name = mat?.let { materialNames[it] } ?: "default"
            return matIndexOf(name, matList, matIndex)
        }

        val parts = ArrayList<Part>()
        for (g in geoms) {
            val geoId = g.props.getOrNull(0) as? Long ?: continue
            val modelId = modelParent(geoId)
            val geomOffset = modelId?.let { models[it] }?.let { m ->
                Mat.trs(props70(m, "GeometricTranslation"), props70(m, "GeometricRotation"), props70(m, "GeometricScaling", 1.0))
            } ?: Mat.identity()
            val xf = if (modelId != null) Mat.mul(world(modelId, 0), geomOffset) else geomOffset
            parts.add(buildPart(g, xf, materialFor(modelId)))
        }

        // Gabung semua bagian dengan offset indeks vertex. / Merge all parts with vertex index offsets.
        var totalVerts = 0
        var totalTris = 0
        for (p in parts) {
            totalVerts += p.positions.size / 3
            totalTris += p.tris.size / 3
        }
        if (totalVerts > InternalMesh.MAX_VERTICES) fail(IssueCode.TOO_MANY_ELEMENTS, "vertices=$totalVerts")
        if (totalTris > Limits.MAX_TRIANGLES) fail(IssueCode.TOO_MANY_ELEMENTS, "triangles=$totalTris")
        if (totalTris == 0) fail(IssueCode.UNEXPECTED_STRUCTURE, "no polygons")

        val anyUv = parts.any { it.uvs != null }
        val anyNrm = parts.any { it.normals != null }
        val pos = FloatArray(totalVerts * 3)
        val uvs = if (anyUv) FloatArray(totalVerts * 2) else null
        val nrm = if (anyNrm) FloatArray(totalVerts * 3) else null
        val tris = IntArray(totalTris * 3)
        val triMat = IntArray(totalTris)
        var vOff = 0
        var tOff = 0
        for (p in parts) {
            val n = p.positions.size / 3
            System.arraycopy(p.positions, 0, pos, vOff * 3, n * 3)
            p.uvs?.let { System.arraycopy(it, 0, uvs!!, vOff * 2, n * 2) }
            p.normals?.let { System.arraycopy(it, 0, nrm!!, vOff * 3, n * 3) }
            val t = p.tris.size / 3
            for (k in p.tris.indices) tris[tOff * 3 + k] = p.tris[k] + vOff
            for (k in 0 until t) triMat[tOff + k] = p.material
            vOff += n
            tOff += t
        }

        issues.add(Issue(Severity.INFO, IssueCode.FBX_PARTIAL, -1, "units"))
        val mesh = InternalMesh(
            positions = pos,
            normals = nrm,
            uvs = uvs,
            triangles = tris,
            triMaterial = triMat,
            materials = (if (matList.isEmpty()) listOf("default") else matList).map { InternalMaterial(it, null) },
        )
        return FbxParseResult.Success(mesh, issues)
    }

    private fun matIndexOf(name: String, list: MutableList<String>, index: MutableMap<String, Int>): Int =
        index.getOrPut(name) {
            list.add(name)
            list.size - 1
        }

    /** Ambil nilai P dari Properties70 (tiga angka, atau [defaultValue] untuk skala). / Read a P entry from Properties70 (three numbers, or defaultValue for scale). */
    private fun props70(model: FbxNode, name: String, defaultValue: Double = 0.0): DoubleArray {
        val p = model.child("Properties70")?.children?.firstOrNull { it.name == "P" && it.props.getOrNull(0) == name }
        val vals = p?.props?.drop(4)?.filterIsInstance<Double>() ?: emptyList()
        return if (vals.size >= 3) doubleArrayOf(vals[0], vals[1], vals[2]) else doubleArrayOf(defaultValue, defaultValue, defaultValue)
    }

    private fun buildPart(g: FbxNode, xf: DoubleArray, material: Int): Part {
        val verts = g.child("Vertices")?.props?.firstOrNull() as? DoubleArray
            ?: fail(IssueCode.UNEXPECTED_STRUCTURE, "Vertices")
        if (verts.size % 3 != 0) fail(IssueCode.UNEXPECTED_STRUCTURE, "Vertices count")
        val pvi = g.child("PolygonVertexIndex")?.props?.firstOrNull() as? IntArray
            ?: fail(IssueCode.UNEXPECTED_STRUCTURE, "PolygonVertexIndex")
        val vCount = verts.size / 3

        // Polygon: daftar posisi corner dalam pvi; akhir polygon = indeks negatif -(i)-1.
        val polys = ArrayList<IntArray>()
        val cur = ArrayList<Int>()
        for (k in pvi.indices) {
            cur.add(k)
            if (pvi[k] < 0) {
                polys.add(cur.toIntArray())
                cur.clear()
            }
        }

        val uvLayer = g.child("LayerElementUV")
        val nrmLayer = g.child("LayerElementNormal")
        val uvVals = uvLayer?.child("UV")?.props?.firstOrNull() as? DoubleArray
        val nrmVals = nrmLayer?.child("Normals")?.props?.firstOrNull() as? DoubleArray
        if (uvLayer != null && uvVals == null) fail(IssueCode.UNEXPECTED_STRUCTURE, "LayerElementUV without UV")
        if (nrmLayer != null && nrmVals == null) fail(IssueCode.UNEXPECTED_STRUCTURE, "LayerElementNormal without Normals")
        val hasUv = uvVals != null
        val hasNrm = nrmVals != null
        val uvIdx = uvLayer?.child("UVIndex")?.props?.firstOrNull() as? IntArray
        val uvByVertex = mappingIsByVertex(uvLayer)
        val nrmByVertex = mappingIsByVertex(nrmLayer)

        val mat = Mat.linear(xf)
        val outPos = ArrayList<Float>()
        val outUv = ArrayList<Float>()
        val outNrm = ArrayList<Float>()
        val outIndex = HashMap<Triple<Int, Int, Int>, Int>()
        val tris = ArrayList<Int>()

        fun corner(cornerPos: Int): Int {
            val raw = pvi[cornerPos]
            val vIdx = if (raw < 0) raw.inv() else raw
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
            val id = outPos.size / 3
            if (id >= InternalMesh.MAX_VERTICES) fail(IssueCode.TOO_MANY_ELEMENTS, "vertices over limit")
            val x = verts[vIdx * 3]; val y = verts[vIdx * 3 + 1]; val z = verts[vIdx * 3 + 2]
            val p = Mat.point(mat, xf, x, y, z)
            outPos.add(p[0]); outPos.add(p[1]); outPos.add(p[2])
            if (uv >= 0) { outUv.add(uvVals!![uv * 2].toFloat()); outUv.add(uvVals[uv * 2 + 1].toFloat()) } else { outUv.add(0f); outUv.add(0f) }
            if (nrm >= 0) {
                val d = Mat.dir(mat, nrmVals!![nrm * 3], nrmVals[nrm * 3 + 1], nrmVals[nrm * 3 + 2])
                outNrm.add(d[0]); outNrm.add(d[1]); outNrm.add(d[2])
            } else {
                outNrm.add(0f); outNrm.add(0f); outNrm.add(0f)
            }
            outIndex[key] = id
            return id
        }

        for (poly in polys) {
            if (poly.size < 3) continue
            // Kipas segi tiga dari corner pertama. / Fan triangulation from the first corner.
            val c0 = corner(poly[0])
            for (k in 1 until poly.size - 1) {
                val c1 = corner(poly[k])
                val c2 = corner(poly[k + 1])
                tris.add(c0); tris.add(c1); tris.add(c2)
            }
        }
        return Part(
            positions = outPos.toFloatArray(),
            normals = if (hasNrm) outNrm.toFloatArray() else null,
            uvs = if (hasUv) outUv.toFloatArray() else null,
            tris = tris.toIntArray(),
            material = material,
        )
    }

    private fun mappingIsByVertex(layer: FbxNode?): Boolean {
        val mapping = layer?.child("MappingInformationType")?.props?.firstOrNull() as? String
        return mapping == "ByVertice" || mapping == "ByVertex"
    }

    /** Matriks 4x4 baris-besar. Rotasi Euler urutan XYZ (R = Rz * Ry * Rx), sudut dalam derajat. / Row-major 4x4. Euler XYZ (R = Rz * Ry * Rx), degrees. */
    private object Mat {
        fun identity(): DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)

        fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
            val r = DoubleArray(16)
            for (i in 0 until 4) for (j in 0 until 4) {
                var s = 0.0
                for (k in 0 until 4) s += a[i * 4 + k] * b[k * 4 + j]
                r[i * 4 + j] = s
            }
            return r
        }

        /** M = T * R * S. */
        fun trs(t: DoubleArray, rDeg: DoubleArray, s: DoubleArray): DoubleArray {
            val rx = Math.toRadians(rDeg[0]); val ry = Math.toRadians(rDeg[1]); val rz = Math.toRadians(rDeg[2])
            val cx = cos(rx); val sx = sin(rx); val cy = cos(ry); val sy = sin(ry); val cz = cos(rz); val sz = sin(rz)
            // Rz * Ry * Rx
            val r00 = cz * cy; val r01 = cz * sy * sx - sz * cx; val r02 = cz * sy * cx + sz * sx
            val r10 = sz * cy; val r11 = sz * sy * sx + cz * cx; val r12 = sz * sy * cx - cz * sx
            val r20 = -sy;     val r21 = cy * sx;                 val r22 = cy * cx
            return doubleArrayOf(
                r00 * s[0], r01 * s[1], r02 * s[2], t[0],
                r10 * s[0], r11 * s[1], r12 * s[2], t[1],
                r20 * s[0], r21 * s[1], r22 * s[2], t[2],
                0.0, 0.0, 0.0, 1.0,
            )
        }

        /** Bagian linear 3x3 (untuk normal). / Linear 3x3 part (for normals). */
        fun linear(m: DoubleArray): DoubleArray = doubleArrayOf(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10])

        /** Titik: M * (geometric offset) * (x,y,z). xf sudah menyertakan offset geometrik. */
        fun point(linear: DoubleArray, xf: DoubleArray, x: Double, y: Double, z: Double): FloatArray {
            val px = xf[0] * x + xf[1] * y + xf[2] * z + xf[3]
            val py = xf[4] * x + xf[5] * y + xf[6] * z + xf[7]
            val pz = xf[8] * x + xf[9] * y + xf[10] * z + xf[11]
            return floatArrayOf(px.toFloat(), py.toFloat(), pz.toFloat())
        }

        /** Arah (normal): rotasi+skala, lalu dinormalkan. / Direction: rotation+scale, then normalized. */
        fun dir(linear: DoubleArray, x: Double, y: Double, z: Double): FloatArray {
            val dx = linear[0] * x + linear[1] * y + linear[2] * z
            val dy = linear[3] * x + linear[4] * y + linear[5] * z
            val dz = linear[6] * x + linear[7] * y + linear[8] * z
            val len = Math.sqrt(dx * dx + dy * dy + dz * dz)
            return if (len > 1e-12) floatArrayOf((dx / len).toFloat(), (dy / len).toFloat(), (dz / len).toFloat())
            else floatArrayOf(0f, 0f, 1f)
        }
    }
}
