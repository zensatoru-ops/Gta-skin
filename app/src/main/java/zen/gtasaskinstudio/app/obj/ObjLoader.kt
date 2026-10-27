package zen.gtasaskinstudio.app.obj

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
    fun load(displayName: String?, sizeBytes: Long?, bytes: ByteArray): ObjLoadOutcome =
        when (val r = ObjImporter.parse(bytes)) {
            is ObjParseResult.Failure -> ObjLoadOutcome.Failed(r.issues)
            is ObjParseResult.Success -> ObjLoadOutcome.Loaded(
                displayName = displayName,
                sizeBytes = sizeBytes,
                scene = InternalSceneBuilder.build(r.mesh),
                vertexCount = r.mesh.vertexCount,
                triangleCount = r.mesh.triangleCount,
                issues = r.issues,
            )
        }
}
