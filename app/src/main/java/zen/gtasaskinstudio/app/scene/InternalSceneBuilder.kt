package zen.gtasaskinstudio.app.scene

import kotlin.math.sqrt
import zen.gtasaskinstudio.app.model.InternalMesh

/**
 * Mengubah [InternalMesh] (dari importer mana pun) menjadi [SceneBuffers] yang
 * sama dengan jalur DFF, sehingga renderer tidak perlu tahu sumbernya. Tanpa
 * skeleton: garis dan titik tulang kosong.
 *
 * Converts an [InternalMesh] (from any importer) into the same [SceneBuffers] the
 * DFF path uses, so the renderer does not need to know the source. No skeleton:
 * bone lines and points are empty.
 */
object InternalSceneBuilder {

    fun build(mesh: InternalMesh): SceneBuffers {
        val n = mesh.vertexCount
        val normals = mesh.normals ?: computeNormals(mesh)
        val p = mesh.positions
        val vertices = FloatArray(n * 6)
        var minX = Float.POSITIVE_INFINITY; var minY = minX; var minZ = minX
        var maxX = Float.NEGATIVE_INFINITY; var maxY = maxX; var maxZ = maxX
        for (i in 0 until n) {
            val x = p[i * 3]; val y = p[i * 3 + 1]; val z = p[i * 3 + 2]
            vertices[i * 6] = x; vertices[i * 6 + 1] = y; vertices[i * 6 + 2] = z
            vertices[i * 6 + 3] = normals[i * 3]; vertices[i * 6 + 4] = normals[i * 3 + 1]; vertices[i * 6 + 5] = normals[i * 3 + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }
        val dx = maxX - minX; val dy = maxY - minY; val dz = maxZ - minZ
        val radius = maxOf(sqrt(dx * dx + dy * dy + dz * dz) / 2f, 1e-3f)
        val center = floatArrayOf((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)

        val tc = mesh.triangleCount
        val nMat = mesh.materials.size
        val counts = IntArray(nMat)
        for (t in 0 until tc) counts[mesh.triMaterial[t]]++
        val starts = IntArray(nMat)
        var acc = 0
        for (m in 0 until nMat) { starts[m] = acc; acc += counts[m] * 3 }
        val indices = ShortArray(tc * 3)
        val cursor = starts.copyOf()
        val wire = ShortArray(tc * 6)
        for (t in 0 until tc) {
            val a = mesh.triangles[t * 3]; val b = mesh.triangles[t * 3 + 1]; val c = mesh.triangles[t * 3 + 2]
            val o = cursor[mesh.triMaterial[t]]
            indices[o] = a.toShort(); indices[o + 1] = b.toShort(); indices[o + 2] = c.toShort()
            cursor[mesh.triMaterial[t]] = o + 3
            // Tiga sisi per segitiga, dua indeks per garis. / Three edges per triangle, two indices per line.
            wire[t * 6] = a.toShort(); wire[t * 6 + 1] = b.toShort()
            wire[t * 6 + 2] = b.toShort(); wire[t * 6 + 3] = c.toShort()
            wire[t * 6 + 4] = c.toShort(); wire[t * 6 + 5] = a.toShort()
        }
        val draws = ArrayList<MeshDraw>()
        for (m in 0 until nMat) if (counts[m] > 0) draws.add(MeshDraw(m, starts[m], counts[m] * 3))

        return SceneBuffers(
            vertices = vertices,
            vertexCount = n,
            indices = indices,
            draws = draws,
            wireIndices = wire,
            skeletonLines = FloatArray(0),
            center = center,
            radius = radius,
            materialNames = mesh.materials.map { it.textureName },
            framePositions = FloatArray(0),
            uvs = mesh.uvs ?: FloatArray(n * 2),
        )
    }

    /** Normal per vertex dari rata-rata normal face (dinormalkan). / Per-vertex normals averaged from face normals. */
    fun computeNormals(mesh: InternalMesh): FloatArray {
        val n = mesh.vertexCount
        val acc = FloatArray(n * 3)
        val p = mesh.positions
        for (t in 0 until mesh.triangleCount) {
            val a = mesh.triangles[t * 3]; val b = mesh.triangles[t * 3 + 1]; val c = mesh.triangles[t * 3 + 2]
            val ux = p[b * 3] - p[a * 3]; val uy = p[b * 3 + 1] - p[a * 3 + 1]; val uz = p[b * 3 + 2] - p[a * 3 + 2]
            val vx = p[c * 3] - p[a * 3]; val vy = p[c * 3 + 1] - p[a * 3 + 1]; val vz = p[c * 3 + 2] - p[a * 3 + 2]
            // Hasil silang tidak dinormalkan: area ikut menimbang. / Cross product not normalized: area weights it.
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            for (v in intArrayOf(a, b, c)) {
                acc[v * 3] += nx; acc[v * 3 + 1] += ny; acc[v * 3 + 2] += nz
            }
        }
        for (i in 0 until n) {
            val x = acc[i * 3]; val y = acc[i * 3 + 1]; val z = acc[i * 3 + 2]
            val len = sqrt(x * x + y * y + z * z)
            if (len > 1e-12f) {
                acc[i * 3] = x / len; acc[i * 3 + 1] = y / len; acc[i * 3 + 2] = z / len
            } else {
                acc[i * 3] = 0f; acc[i * 3 + 1] = 0f; acc[i * 3 + 2] = 1f
            }
        }
        return acc
    }
}
