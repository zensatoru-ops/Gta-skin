package zen.gtasaskinstudio.app.model

/**
 * Kontrak model internal (Fase C). Semua importer (OBJ sekarang, FBX nanti)
 * menghasilkan bentuk ini; renderer dan exporter hanya membaca bentuk ini.
 *
 * Internal model contract (Phase C). Every importer (OBJ now, FBX later) produces
 * this shape; the renderer and exporters only read it.
 *
 * Aturan / rules:
 * - Indeks 0-based. / Indices are 0-based.
 * - [positions] 3 float per vertex, [uvs] 2 float per vertex, [normals] 3 float per vertex.
 * - [triangles] 3 indeks per segitiga; [triMaterial] 1 indeks material per segitiga.
 * - Jumlah vertex dibatasi 65535 agar muat indeks 16-bit yang dipakai renderer.
 * - Tanpa skeleton: model dari OBJ tidak punya rig (lihat v3 bagian 5.9).
 */
class InternalMaterial(
    val name: String,
    /** Nama tekstur dari .mtl atau TXD; null bila belum ada. / Texture name from .mtl or TXD; null if none yet. */
    val textureName: String?,
)

class InternalMesh(
    val positions: FloatArray,
    val normals: FloatArray?,
    val uvs: FloatArray?,
    val triangles: IntArray,
    val triMaterial: IntArray,
    val materials: List<InternalMaterial>,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = triangles.size / 3

    init {
        require(positions.size % 3 == 0) { "positions must be xyz triples" }
        require(vertexCount in 1..MAX_VERTICES) { "vertexCount=$vertexCount" }
        require(triangles.size % 3 == 0) { "triangles must be index triples" }
        require(triMaterial.size == triangleCount) { "one material per triangle" }
        require(normals == null || normals.size == vertexCount * 3) { "normals size" }
        require(uvs == null || uvs.size == vertexCount * 2) { "uvs size" }
        require(materials.isNotEmpty()) { "at least one material" }
        for (i in triangles) require(i in 0 until vertexCount) { "triangle index $i out of range" }
        for (m in triMaterial) require(m in materials.indices) { "material $m out of range" }
    }

    companion object {
        const val MAX_VERTICES = 65_535
    }
}
