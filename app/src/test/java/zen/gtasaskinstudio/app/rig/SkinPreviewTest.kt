package zen.gtasaskinstudio.app.rig

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tes bobot otomatis dan pratinjau skinning pada "manusia" sintetis. / Auto-weight and skinning-preview tests on a synthetic "human". */
class SkinPreviewTest {

    private fun rig(): AutoRigResult {
        val out = ArrayList<Float>()
        for (x in listOf(-0.3f, 0.3f)) for (y in listOf(-0.1f, 0.1f)) for (z in listOf(0f, 1.8f)) {
            out.add(x); out.add(y); out.add(z); out.add(0f); out.add(0f); out.add(1f)
        }
        return AutoRig.fromInterleaved(out.toFloatArray())!!
    }

    private class Human(val rig: AutoRigResult, val positions: FloatArray, val interleaved: FloatArray, val probe: Map<Int, Int>)

    /** Delapan sudut kotak (membentuk tinggi 1,8) ditambah satu titik di tengah tiap segmen bone utama. */
    private fun human(): Human {
        val r = rig()
        val joints = r.joints.associate { it.boneName to doubleArrayOf(it.x, it.y, it.z) }
        val pts = ArrayList<Float>()
        for (x in listOf(-0.3f, 0.3f)) for (y in listOf(-0.1f, 0.1f)) for (z in listOf(0f, 1.8f)) { pts.add(x); pts.add(y); pts.add(z) }
        val segs = AutoWeights.segments(joints, 1.8)!!
        val probe = LinkedHashMap<Int, Int>()
        for (b in AutoWeights.SKIN_BONES) {
            val s = segs[b]!!
            probe[b] = pts.size / 3
            for (c in 0..2) pts.add(((s.a[c] + s.b[c]) / 2).toFloat())
        }
        val pos = pts.toFloatArray()
        val inter = FloatArray(pos.size / 3 * 6)
        for (v in 0 until pos.size / 3) {
            inter[v * 6] = pos[v * 3]; inter[v * 6 + 1] = pos[v * 3 + 1]; inter[v * 6 + 2] = pos[v * 3 + 2]; inter[v * 6 + 5] = 1f
        }
        return Human(r, pos, inter, probe)
    }

    @Test fun midpointOfEachBoneIsDominatedByThatBone() {
        val h = human()
        val w = AutoWeights.compute(h.positions, IntArray(0), h.rig)!!
        for ((bone, v) in h.probe) {
            assertEquals("bone $bone", bone, w.bones[v * 4])
            assertTrue("bone $bone weight", w.weights[v * 4] > 0.5f)
        }
    }

    @Test fun weightsAreNormalizedAndUseOnlySkinBones() {
        val h = human()
        // Tepi sembarang antar vertex untuk melatih penghalusan. / Arbitrary edges between vertices to exercise smoothing.
        val n = h.positions.size / 3
        val edges = IntArray((n - 1) * 2) { if (it % 2 == 0) it / 2 else it / 2 + 1 }
        val w = AutoWeights.compute(h.positions, edges, h.rig)!!
        for (v in 0 until n) {
            var sum = 0f
            for (s in 0 until 4) {
                sum += w.weights[v * 4 + s]
                if (w.weights[v * 4 + s] > 0f) assertTrue(w.bones[v * 4 + s] in AutoWeights.SKIN_BONES.toList())
            }
            assertEquals(1.0, sum.toDouble(), 1e-4)
        }
    }

    @Test fun leftAndRightLegsStaySeparate() {
        val h = human()
        val w = AutoWeights.compute(h.positions, IntArray(0), h.rig)!!
        val lCalf = h.probe[27]!!
        val rCalf = h.probe[30]!!
        assertEquals(27, w.bones[lCalf * 4])
        assertEquals(30, w.bones[rCalf * 4])
    }

    @Test fun degenerateInputGivesNull() {
        assertNull(AutoWeights.compute(FloatArray(6), IntArray(0), rig()))
        val flat = FloatArray(30) { if (it % 3 == 2) 1f else 0f }
        assertNull(AutoWeights.compute(flat, IntArray(0), rig()))
    }

    @Test fun mainBoneMergesMinorBones() {
        assertEquals(5, AutoWeights.mainBone(6))
        assertEquals(7, AutoWeights.mainBone(8))
        assertEquals(13, AutoWeights.mainBone(16))
        assertEquals(20, AutoWeights.mainBone(21))
        assertEquals(17, AutoWeights.mainBone(17))
    }

    @Test fun restPoseLeavesTheMeshUnchanged() {
        val h = human()
        val w = AutoWeights.compute(h.positions, IntArray(0), h.rig)!!
        val xf = SkinPreview.transforms(h.rig.joints, h.rig.joints, w.height)!!
        val out = SkinPreview.skin(h.interleaved, w, xf)
        for (i in out.indices) assertEquals(h.interleaved[i].toDouble(), out[i].toDouble(), 1e-5)
    }

    @Test fun movingEveryJointMovesTheWholeMeshByTheSameAmount() {
        val h = human()
        val w = AutoWeights.compute(h.positions, IntArray(0), h.rig)!!
        val moved = h.rig.joints.map { AutoRigJoint(it.boneName, it.x + 0.5, it.y - 0.25, it.z + 1.0) }
        val out = SkinPreview.skin(h.interleaved, w, SkinPreview.transforms(h.rig.joints, moved, w.height)!!)
        for (v in 0 until w.vertexCount) {
            assertEquals(h.interleaved[v * 6] + 0.5, out[v * 6].toDouble(), 1e-4)
            assertEquals(h.interleaved[v * 6 + 1] - 0.25, out[v * 6 + 1].toDouble(), 1e-4)
            assertEquals(h.interleaved[v * 6 + 2] + 1.0, out[v * 6 + 2].toDouble(), 1e-4)
        }
    }

    @Test fun standingPoseCarriesBoneMidpointsToTheirPosedSegments() {
        val h = human()
        val w = AutoWeights.compute(h.positions, IntArray(0), h.rig)!!
        val posed = PosePlayer.pose(h.rig, PosePlayer.stanceAnimation(), 0f, beside = false)!!
        val xf = SkinPreview.transforms(h.rig.joints, posed.joints, w.height)!!
        val out = SkinPreview.skin(h.interleaved, w, xf)
        val sp = AutoWeights.segments(posed.joints.associate { it.boneName to doubleArrayOf(it.x, it.y, it.z) }, w.height)!!
        for ((bone, v) in h.probe) {
            val s = sp[bone]!!
            var err = 0.0
            for (c in 0..2) { val d = out[v * 6 + c] - (s.a[c] + s.b[c]) / 2; err += d * d }
            // Pengaruh bone tetangga di titik tengah kecil, jadi simpangannya kecil. / Neighbour influence at midpoints is small.
            assertTrue("bone $bone err ${sqrt(err)}", sqrt(err) < 0.06 * w.height)
        }
    }

    @Test fun swingTurnsOneDirectionIntoAnotherIncludingOpposites() {
        val a = doubleArrayOf(1.0, 0.0, 0.0)
        for (b in listOf(doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, -1.0), doubleArrayOf(-1.0, 0.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0))) {
            val m = SkinPreview.swing(a, b)
            val r = doubleArrayOf(m[0] * a[0], m[3] * a[0], m[6] * a[0])
            for (c in 0..2) assertEquals(b[c], r[c], 1e-9)
        }
    }

    @Test fun incompleteRigReturnsNullTransforms() {
        val r = rig()
        val cut = r.joints.filter { it.boneName != " Head" }
        assertNull(SkinPreview.transforms(cut, r.joints, 1.8))
        assertNotNull(SkinPreview.transforms(r.joints, r.joints, 1.8))
    }
}
