package zen.gtasaskinstudio.app.obj

import zen.gtasaskinstudio.app.model.InternalMaterial
import zen.gtasaskinstudio.app.model.InternalMesh
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.scene.InternalSceneBuilder
import zen.gtasaskinstudio.app.scene.SceneBuffers

/** Hasil siap-tampil dari satu berkas OBJ. / Ready-to-show result for one OBJ file. */
sealed class ObjLoadOutcome {
    class Loaded(
        val displayName: String?,
        val sizeBytes: Long?,
        val scene: SceneBuffers,
        val vertexCount: Int,
        val triangleCount: Int,
        val issues: List<Issue>,
    ) : ObjLoadOutcome()

    class Failed(val issues: List<Issue>) : ObjLoadOutcome()
}

/** Parse + bangun scene OBJ. Murni JVM; dipanggil dari thread latar. / Parse and build an OBJ scene. Pure JVM; called off the main thread. */
object ObjLoader {

    /**
     * [mtlBytes] opsional. Tanpa .mtl, nama tekstur setiap material = nama material
     * (cocok dengan TXD bila nama materialnya sama, seperti pada DFF).
     *
     * [mtlBytes] is optional. Without a .mtl, each material's texture name is the material
     * name itself (matches a TXD when the names are the same, as in DFF).
     */
    fun load(displayName: String?, sizeBytes: Long?, bytes: ByteArray, mtlBytes: ByteArray? = null): ObjLoadOutcome =
        when (val r = ObjImporter.parse(bytes)) {
            is ObjParseResult.Failure -> ObjLoadOutcome.Failed(r.issues)
            is ObjParseResult.Success -> {
                val texMap = mtlBytes?.let { MtlParser.parse(it) } ?: emptyMap()
                val mesh = withTextureNames(r.mesh, texMap)
                ObjLoadOutcome.Loaded(
                    displayName = displayName,
                    sizeBytes = sizeBytes,
                    scene = InternalSceneBuilder.build(mesh),
                    vertexCount = mesh.vertexCount,
                    triangleCount = mesh.triangleCount,
                    issues = r.issues,
                )
            }
        }

    /** Salin mesh dengan nama tekstur material diisi dari peta .mtl, atau nama material bila tidak ada. */
    fun withTextureNames(mesh: InternalMesh, texMap: Map<String, String>): InternalMesh =
        InternalMesh(
            positions = mesh.positions,
            normals = mesh.normals,
            uvs = mesh.uvs,
            triangles = mesh.triangles,
            triMaterial = mesh.triMaterial,
            materials = mesh.materials.map { m ->
                InternalMaterial(m.name, texMap[m.name] ?: m.name)
            },
        )
}
