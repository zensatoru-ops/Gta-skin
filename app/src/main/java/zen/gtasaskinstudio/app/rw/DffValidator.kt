package zen.gtasaskinstudio.app.rw

import kotlin.math.abs

/**
 * Statistik skin per geometri. Tidak ada nilai yang diperbaiki diam-diam —
 * bobot dan indeks hanya dihitung dan dilaporkan.
 *
 * Skin statistics per geometry. Nothing is silently repaired — weights and
 * indices are only counted and reported.
 */
class SkinStats(
    val vertexCount: Int,
    val numBones: Int,
    val maxInfluences: Int,
    val badIndexVertices: Int,
    val badSumVertices: Int,
    val unweightedVertices: Int,
    val nonFiniteWeights: Int,
    /** Jumlah vertex yang dipengaruhi tiap bone (indeks = bone). */
    val verticesPerBone: IntArray,
    val minWeightSum: Float,
    val maxWeightSum: Float,
)

class ValidationReport(
    val issues: List<Issue>,
    /** Kunci = indeks geometri. */
    val skinStats: Map<Int, SkinStats>,
) {
    /** Ada kesalahan yang membuat geometri tidak aman digambar. */
    val hasErrors: Boolean get() = issues.any { it.severity == Severity.ERROR }
}

object DffValidator {

    private const val REST_POSE_TOLERANCE = 0.05f // usulan / proposal
    private const val WEIGHT_SUM_TOLERANCE = 1e-3f // usulan, aturan target UNKNOWN / proposal; target rule UNKNOWN

    fun validate(model: DffModel): ValidationReport {
        val issues = ArrayList<Issue>()
        val stats = HashMap<Int, SkinStats>()

        validateFrames(model, issues)
        validateAtomics(model, issues)
        for ((gi, g) in model.geometries.withIndex()) {
            validateGeometry(gi, g, issues)
            val skin = g.skin
            if (skin != null) {
                val s = skinStats(g, skin)
                stats[gi] = s
                reportSkin(gi, s, model, issues)
            }
        }
        return ValidationReport(issues, stats)
    }

    private fun validateFrames(model: DffModel, issues: MutableList<Issue>) {
        val n = model.frames.size
        var bad = 0
        for (f in model.frames) {
            if (f.parent < -1 || f.parent >= n || f.parent == f.index) {
                bad++
                continue
            }
            // deteksi siklus / cycle detection
            var steps = 0
            var cur = f.parent
            while (cur != -1 && steps <= n) {
                cur = model.frames[cur].parent.let { p -> if (p < -1 || p >= n) -1 else p }
                steps++
            }
            if (steps > n) bad++
        }
        if (bad > 0) issues.add(Issue(Severity.ERROR, IssueCode.FRAME_PARENT_INVALID, -1, "frames=$bad"))
    }

    private fun validateAtomics(model: DffModel, issues: MutableList<Issue>) {
        var bad = 0
        for (a in model.atomics) {
            val frameOk = model.frames.isEmpty() || a.frame in 0 until model.frames.size
            val geomOk = a.geometry in 0 until model.geometries.size
            if (!frameOk || !geomOk) bad++
        }
        if (bad > 0) issues.add(Issue(Severity.ERROR, IssueCode.ATOMIC_REF_INVALID, -1, "atomics=$bad"))
    }

    private fun validateGeometry(gi: Int, g: Geometry, issues: MutableList<Issue>) {
        var badIdx = 0
        for (i in g.triIndices) if (i < 0 || i >= g.vertexCount) badIdx++
        if (badIdx > 0) issues.add(Issue(Severity.ERROR, IssueCode.TRIANGLE_INDEX_OUT_OF_RANGE, -1, "geometry=$gi indices=$badIdx"))

        var badMat = 0
        for (m in g.triMaterial) if (m < 0 || m >= g.materials.size) badMat++
        if (badMat > 0 && g.binMeshes == null) issues.add(Issue(Severity.ERROR, IssueCode.MATERIAL_INDEX_OUT_OF_RANGE, -1, "geometry=$gi triangles=$badMat"))

        g.binMeshes?.let { meshes ->
            var badBinIdx = 0
            var badBinMat = 0
            for (m in meshes) {
                if (m.materialIndex < 0 || m.materialIndex >= g.materials.size) badBinMat++
                for (i in m.indices) if (i < 0 || i >= g.vertexCount) badBinIdx++
            }
            if (badBinIdx > 0) issues.add(Issue(Severity.ERROR, IssueCode.TRIANGLE_INDEX_OUT_OF_RANGE, -1, "geometry=$gi binMeshIndices=$badBinIdx"))
            if (badBinMat > 0) issues.add(Issue(Severity.ERROR, IssueCode.MATERIAL_INDEX_OUT_OF_RANGE, -1, "geometry=$gi binMeshes=$badBinMat"))
        }

        var nonFinite = 0
        for (v in g.positions) if (!v.isFinite()) nonFinite++
        g.normals?.let { for (v in it) if (!v.isFinite()) nonFinite++ }
        if (nonFinite > 0) issues.add(Issue(Severity.ERROR, IssueCode.NON_FINITE_VALUE, -1, "geometry=$gi values=$nonFinite"))
    }

    private fun skinStats(g: Geometry, skin: Skin): SkinStats {
        val n = g.vertexCount
        val perBone = IntArray(maxOf(skin.numBones, 1))
        var badIndex = 0
        var badSum = 0
        var unweighted = 0
        var nonFinite = 0
        var maxInfl = 0
        var minSum = Float.POSITIVE_INFINITY
        var maxSum = Float.NEGATIVE_INFINITY
        for (v in 0 until n) {
            var sum = 0f
            var infl = 0
            var vertexBad = false
            for (k in 0 until 4) {
                val w = skin.weights[v * 4 + k]
                if (!w.isFinite()) {
                    nonFinite++
                    continue
                }
                if (w > 0f) {
                    infl++
                    sum += w
                    val bone = skin.boneIndices[v * 4 + k].toInt() and 0xFF
                    if (bone >= skin.numBones) vertexBad = true else perBone[bone]++
                }
            }
            if (vertexBad) badIndex++
            if (infl == 0) {
                unweighted++
            } else {
                if (abs(sum - 1f) > WEIGHT_SUM_TOLERANCE) badSum++
                if (sum < minSum) minSum = sum
                if (sum > maxSum) maxSum = sum
            }
            if (infl > maxInfl) maxInfl = infl
        }
        if (minSum == Float.POSITIVE_INFINITY) {
            minSum = 0f
            maxSum = 0f
        }
        return SkinStats(n, skin.numBones, maxInfl, badIndex, badSum, unweighted, nonFinite, perBone, minSum, maxSum)
    }

    private fun reportSkin(gi: Int, s: SkinStats, model: DffModel, issues: MutableList<Issue>) {
        if (s.badIndexVertices > 0) {
            issues.add(Issue(Severity.ERROR, IssueCode.SKIN_BONE_INDEX_OUT_OF_RANGE, -1, "geometry=$gi vertices=${s.badIndexVertices} bones=${s.numBones}"))
        }
        if (s.nonFiniteWeights > 0) {
            issues.add(Issue(Severity.ERROR, IssueCode.NON_FINITE_VALUE, -1, "geometry=$gi weights=${s.nonFiniteWeights}"))
        }
        if (s.badSumVertices > 0) {
            issues.add(Issue(Severity.WARNING, IssueCode.SKIN_WEIGHT_SUM, -1, "geometry=$gi vertices=${s.badSumVertices} min=${s.minWeightSum} max=${s.maxWeightSum}"))
        }
        if (s.unweightedVertices > 0) {
            issues.add(Issue(Severity.WARNING, IssueCode.SKIN_UNWEIGHTED_VERTICES, -1, "geometry=$gi vertices=${s.unweightedVertices}"))
        }
        val g = model.geometries.getOrNull(gi)
        val skin = g?.skin
        if (skin != null) {
            val atomicFrame = model.atomics.firstOrNull()?.frame ?: -1
            if (zen.gtasaskinstudio.app.scene.BindPose.boneFrames(model, skin) == null) {
                issues.add(Issue(Severity.WARNING, IssueCode.SKIN_BONE_ORDER_UNKNOWN, -1, "geometry=$gi"))
            } else {
                val dev = zen.gtasaskinstudio.app.scene.BindPose.maxRestDeviation(
                    model, skin, zen.gtasaskinstudio.app.scene.BindPose.frameWorld(model, atomicFrame)
                )
                if (dev > REST_POSE_TOLERANCE) {
                    issues.add(Issue(Severity.INFO, IssueCode.SKIN_REST_POSE_DIFFERS, -1, "geometry=$gi maxDeviation=${"%.3f".format(dev)}"))
                }
            }
        }
        val hanimRoot = model.frames.firstOrNull { it.hanimNodeCount > 0 }
        if (hanimRoot != null && hanimRoot.hanimNodeCount != s.numBones) {
            issues.add(Issue(Severity.WARNING, IssueCode.SKIN_BONE_COUNT_MISMATCH, -1, "skin=${s.numBones} hanim=${hanimRoot.hanimNodeCount}"))
        }
    }
}
