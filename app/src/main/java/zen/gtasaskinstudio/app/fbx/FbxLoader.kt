package zen.gtasaskinstudio.app.fbx

import zen.gtasaskinstudio.app.obj.ObjLoadOutcome
import zen.gtasaskinstudio.app.scene.InternalSceneBuilder

/**
 * Parse FBX biner + bangun scene. Memakai [ObjLoadOutcome] agar viewer yang sama
 * bisa dipakai untuk OBJ dan FBX. Dipanggil dari thread latar.
 *
 * Parses binary FBX and builds the scene. Reuses [ObjLoadOutcome] so the same viewer
 * serves OBJ and FBX. Called off the main thread.
 */
object FbxLoader {
    fun load(displayName: String?, sizeBytes: Long?, bytes: ByteArray): ObjLoadOutcome =
        when (val r = FbxImporter.parse(bytes)) {
            is FbxParseResult.Failure -> ObjLoadOutcome.Failed(r.issues)
            is FbxParseResult.Success -> ObjLoadOutcome.Loaded(
                displayName = displayName,
                sizeBytes = sizeBytes,
                scene = InternalSceneBuilder.build(r.mesh),
                vertexCount = r.mesh.vertexCount,
                triangleCount = r.mesh.triangleCount,
                issues = r.issues,
            )
        }
}
