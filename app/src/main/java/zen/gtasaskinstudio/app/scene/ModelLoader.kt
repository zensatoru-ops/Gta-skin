package zen.gtasaskinstudio.app.scene

import zen.gtasaskinstudio.app.rw.DffModel
import zen.gtasaskinstudio.app.rw.DffParseResult
import zen.gtasaskinstudio.app.rw.DffParser
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.Severity
import zen.gtasaskinstudio.app.rw.SkinStats

/** Hasil siap-tampil dari satu berkas DFF. [scene] null bila validasi menemukan kesalahan (tidak digambar). */
class LoadedModel(
    val displayName: String?,
    val sizeBytes: Long?,
    val model: DffModel,
    val issues: List<Issue>,
    val geometryIndex: Int,
    val skinStats: SkinStats?,
    val scene: SceneBuffers?,
)

sealed class LoadOutcome {
    class Loaded(val value: LoadedModel) : LoadOutcome()
    class Failed(val issues: List<Issue>) : LoadOutcome()
}

/** Parse + validasi + bangun scene. Murni JVM; dipanggil dari thread latar. */
object ModelLoader {
    fun load(displayName: String?, sizeBytes: Long?, bytes: ByteArray): LoadOutcome =
        when (val r = DffParser.parse(bytes)) {
            is DffParseResult.Failure -> LoadOutcome.Failed(r.issues)
            is DffParseResult.Success -> {
                val gi = SceneBuilder.geometryIndex(r.model)
                val blocked = r.issues.any { it.severity == Severity.ERROR }
                val scene = if (blocked) null else SceneBuilder.build(r.model)
                LoadOutcome.Loaded(
                    LoadedModel(displayName, sizeBytes, r.model, r.issues, gi, r.skinStats[gi], scene)
                )
            }
        }
}
