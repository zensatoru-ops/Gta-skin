package zen.gtasaskinstudio.app.scene

import kotlin.math.sqrt
import zen.gtasaskinstudio.app.rw.DffModel
import zen.gtasaskinstudio.app.rw.Frame
import zen.gtasaskinstudio.app.rw.Geometry

/** Satu panggilan gambar: rentang indeks untuk satu material. */
class MeshDraw(val materialIndex: Int, val start: Int, val count: Int)

/**
 * Data siap-upload untuk renderer. Dibangun murni di CPU tanpa API Android
 * sehingga bisa diuji di JVM. Hanya geometri pertama yang dipakai atomic
 * pertama yang digambar (lihat MULTI_GEOMETRY di parser).
 *
 * Upload-ready data for the renderer. Built purely on the CPU with no
 * Android APIs so it can be unit-tested on the JVM.
 */
class SceneBuffers(
    /** Interleaved: px, py, pz, nx, ny, nz per vertex. */
    val vertices: FloatArray,
    val vertexCount: Int,
    /** Indeks segitiga, dikelompokkan per material; nilai 0..65535 disimpan di Short. */
    val indices: ShortArray,
    val draws: List<MeshDraw>,
    /** 2 indeks per sisi segitiga untuk mode wireframe. */
    val wireIndices: ShortArray,
    /** Pasangan titik (x,y,z)-(x,y,z) per garis skeleton, dalam ruang model. */
    val skeletonLines: FloatArray,
    val center: FloatArray,
    val radius: Float,
    val materialNames: List<String?>,
    /** Titik skeleton (3 float per frame) di ruang model. */
    val framePositions: FloatArray,
)

object SceneBuilder {

    /** Indeks geometri yang digambar: milik atomic pertama bila valid, jika tidak yang pertama. */
    fun geometryIndex(model: DffModel): Int =
        model.atomics.firstOrNull()?.geometry?.takeIf { it in model.geometries.indices } ?: 0

    fun build(model: DffModel): SceneBuffers {
        val atomicFrame = model.atomics.firstOrNull()?.frame ?: -1
        return buildGeometry(model.geometries[geometryIndex(model)], model, BindPose.frameWorld(model, atomicFrame))
    }

    private fun buildGeometry(g: Geometry, model: DffModel, atomic: BindPose.World): SceneBuffers {
        val frames = model.frames
        val n = g.vertexCount
        val normals = g.normals ?: computeNormals(g)
        val rot = atomic.rot
        val tmp = FloatArray(3)

        val vertices = FloatArray(n * 6)
        var minX = Float.POSITIVE_INFINITY; var minY = minX; var minZ = minX
        var maxX = Float.NEGATIVE_INFINITY; var maxY = maxX; var maxZ = maxX
        for (i in 0 until n) {
            // Transform frame atomic: mesh -> dunia (identitas pada sampel Alice).
            atomic.point(g.positions[i * 3], g.positions[i * 3 + 1], g.positions[i * 3 + 2], tmp, 0)
            val x = tmp[0]; val y = tmp[1]; val z = tmp[2]
            vertices[i * 6] = x; vertices[i * 6 + 1] = y; vertices[i * 6 + 2] = z
            val nx0 = normals[i * 3]; val ny0 = normals[i * 3 + 1]; val nz0 = normals[i * 3 + 2]
            vertices[i * 6 + 3] = nx0 * rot[0] + ny0 * rot[3] + nz0 * rot[6]
            vertices[i * 6 + 4] = nx0 * rot[1] + ny0 * rot[4] + nz0 * rot[7]
            vertices[i * 6 + 5] = nx0 * rot[2] + ny0 * rot[5] + nz0 * rot[8]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }
        if (n == 0) { minX = 0f; minY = 0f; minZ = 0f; maxX = 0f; maxY = 0f; maxZ = 0f }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; val cz = (minZ + maxZ) / 2
        val dx = maxX - minX; val dy = maxY - minY; val dz = maxZ - minZ
        val radius = maxOf(sqrt(dx * dx + dy * dy + dz * dz) / 2f, 1e-3f)

        val nMat = maxOf(g.materials.size, 1)
        val bin = g.binMeshes
        val indices: ShortArray
        val draws = ArrayList<MeshDraw>()
        if (bin != null) {
            // Bin Mesh menentukan material (lihat DffModel.Geometry.binMeshes).
            var total = 0
            for (b in bin) total += b.indices.size
            indices = ShortArray(total)
            var o = 0
            for (b in bin) {
                if (b.indices.isEmpty()) continue
                for (i in b.indices) indices[o++] = i.toShort()
                draws.add(MeshDraw(b.materialIndex.coerceIn(0, nMat - 1), o - b.indices.size, b.indices.size))
            }
        } else {
            // Counting sort per material dari id material di triangle.
            val counts = IntArray(nMat)
            for (t in 0 until g.triangleCount) counts[g.triMaterial[t].coerceIn(0, nMat - 1)]++
            val starts = IntArray(nMat)
            var acc = 0
            for (m in 0 until nMat) { starts[m] = acc; acc += counts[m] * 3 }
            indices = ShortArray(g.triangleCount * 3)
            val cursor = starts.copyOf()
            for (t in 0 until g.triangleCount) {
                val m = g.triMaterial[t].coerceIn(0, nMat - 1)
                val o = cursor[m]
                indices[o] = g.triIndices[t * 3].toShort()
                indices[o + 1] = g.triIndices[t * 3 + 1].toShort()
                indices[o + 2] = g.triIndices[t * 3 + 2].toShort()
                cursor[m] = o + 3
            }
            for (m in 0 until nMat) if (counts[m] > 0) draws.add(MeshDraw(m, starts[m], counts[m] * 3))
        }

        val wire = ShortArray(g.triangleCount * 6)
        for (t in 0 until g.triangleCount) {
            val a = g.triIndices[t * 3].toShort()
            val b = g.triIndices[t * 3 + 1].toShort()
            val c = g.triIndices[t * 3 + 2].toShort()
            val o = t * 6
            wire[o] = a; wire[o + 1] = b
            wire[o + 2] = b; wire[o + 3] = c
            wire[o + 4] = c; wire[o + 5] = a
        }

        val skin = g.skin
        val boneMap = if (skin != null) BindPose.boneFrames(model, skin) else null
        val framePos: FloatArray
        val lines = ArrayList<Float>()
        if (skin != null && boneMap != null) {
            // Pose bind dari Skin PLG: pas dengan mesh. / Bind pose: matches the mesh.
            val bind = BindPose.bonePositions(skin)
            framePos = FloatArray(skin.numBones * 3)
            for (b in 0 until skin.numBones) atomic.point(bind[b * 3], bind[b * 3 + 1], bind[b * 3 + 2], framePos, b * 3)
            val boneOfFrame = HashMap<Int, Int>()
            for (b in boneMap.indices) if (boneMap[b] >= 0) boneOfFrame.putIfAbsent(boneMap[b], b)
            for (b in boneMap.indices) {
                if (boneMap[b] < 0) continue
                var p = frames[boneMap[b]].parent
                var guard = 0
                while (p in frames.indices && p !in boneOfFrame && guard++ <= frames.size) p = frames[p].parent
                val pb = boneOfFrame[p] ?: continue
                if (pb == b) continue
                for (k in 0 until 3) lines.add(framePos[pb * 3 + k])
                for (k in 0 until 3) lines.add(framePos[b * 3 + k])
            }
        } else {
            framePos = frameWorldPositions(frames)
            for (f in frames) {
                if (f.parent in frames.indices && f.parent != f.index) {
                    for (k in 0 until 3) lines.add(framePos[f.parent * 3 + k])
                    for (k in 0 until 3) lines.add(framePos[f.index * 3 + k])
                }
            }
        }

        return SceneBuffers(
            vertices = vertices,
            vertexCount = n,
            indices = indices,
            draws = draws,
            wireIndices = wire,
            skeletonLines = lines.toFloatArray(),
            center = floatArrayOf(cx, cy, cz),
            radius = radius,
            materialNames = g.materials.map { it.textureName },
            framePositions = framePos,
        )
    }

    private fun computeNormals(g: Geometry): FloatArray {
        val n = g.vertexCount
        val out = FloatArray(n * 3)
        for (t in 0 until g.triangleCount) {
            val a = g.triIndices[t * 3]; val b = g.triIndices[t * 3 + 1]; val c = g.triIndices[t * 3 + 2]
            if (a !in 0 until n || b !in 0 until n || c !in 0 until n) continue
            val ux = g.positions[b * 3] - g.positions[a * 3]
            val uy = g.positions[b * 3 + 1] - g.positions[a * 3 + 1]
            val uz = g.positions[b * 3 + 2] - g.positions[a * 3 + 2]
            val vx = g.positions[c * 3] - g.positions[a * 3]
            val vy = g.positions[c * 3 + 1] - g.positions[a * 3 + 1]
            val vz = g.positions[c * 3 + 2] - g.positions[a * 3 + 2]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            for (v in intArrayOf(a, b, c)) { out[v * 3] += nx; out[v * 3 + 1] += ny; out[v * 3 + 2] += nz }
        }
        for (i in 0 until n) {
            val l = sqrt(out[i * 3] * out[i * 3] + out[i * 3 + 1] * out[i * 3 + 1] + out[i * 3 + 2] * out[i * 3 + 2])
            if (l > 1e-12f) { out[i * 3] /= l; out[i * 3 + 1] /= l; out[i * 3 + 2] /= l } else { out[i * 3 + 2] = 1f }
        }
        return out
    }

    /**
     * Posisi dunia tiap frame. Konvensi RenderWare: vektor baris, matriks
     * lokal = (right, up, at, pos); dunia = lokal * dunia_induk.
     * Siklus atau induk tak valid diperlakukan sebagai akar (validator
     * sudah melaporkannya sebagai error).
     */
    fun frameWorldPositions(frames: List<Frame>): FloatArray {
        val n = frames.size
        val rot = FloatArray(n * 9)
        val pos = FloatArray(n * 3)
        val done = BooleanArray(n)

        fun parentOf(i: Int): Int {
            val p = frames[i].parent
            return if (p in 0 until n && p != i) p else -1
        }

        for (start in 0 until n) {
            if (done[start]) continue
            val chain = ArrayList<Int>()
            var cur = start
            while (cur != -1 && !done[cur] && chain.size <= n) {
                chain.add(cur)
                cur = parentOf(cur)
            }
            val cyclic = chain.size > n
            for (k in chain.indices.reversed()) {
                val i = chain[k]
                val l = frames[i].rotation
                val p = frames[i].position
                val par = if (cyclic && k == chain.size - 1) -1 else parentOf(i)
                if (par == -1 || !done[par]) {
                    for (j in 0 until 9) rot[i * 9 + j] = l[j]
                    for (j in 0 until 3) pos[i * 3 + j] = p[j]
                } else {
                    val r0 = par * 9
                    for (row in 0 until 3) {
                        for (col in 0 until 3) {
                            rot[i * 9 + row * 3 + col] =
                                l[row * 3] * rot[r0 + col] +
                                    l[row * 3 + 1] * rot[r0 + 3 + col] +
                                    l[row * 3 + 2] * rot[r0 + 6 + col]
                        }
                    }
                    for (col in 0 until 3) {
                        pos[i * 3 + col] =
                            p[0] * rot[r0 + col] + p[1] * rot[r0 + 3 + col] + p[2] * rot[r0 + 6 + col] + pos[par * 3 + col]
                    }
                }
                done[i] = true
            }
        }
        return pos
    }
}
