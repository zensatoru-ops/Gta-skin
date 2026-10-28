package zen.gtasaskinstudio.app.rig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RigTest {

    /** Kotak humanoid sederhana: tinggi 1.8 di sumbu Z, lebar 0.6 di X. / Simple box humanoid: height 1.8 along Z, width 0.6 along X. */
    private fun box(): FloatArray {
        val corners = ArrayList<Float>()
        for (x in listOf(-0.3f, 0.3f)) for (y in listOf(-0.1f, 0.1f)) for (z in listOf(0f, 1.8f)) {
            corners.add(x); corners.add(y); corners.add(z)
        }
        return corners.toFloatArray()
    }

    @Test fun templateHasFrameCountAndParentsInRange() {
        assertEquals(GtaStandardRig.FRAME_COUNT, GtaStandardRig.BONES.size)
        for (b in GtaStandardRig.BONES.drop(1)) assertTrue("parent of ${b.name}", b.parent in 0 until b.index)
    }

    @Test fun templateHasThirtyTwoWeightedBonesWithUniqueIds() {
        assertEquals(32, GtaStandardRig.WEIGHTED.size)
        assertEquals(32, GtaStandardRig.WEIGHTED.map { it.hanimId }.toSet().size)
    }

    @Test fun joinedHeightOrderIsPlausibleForZUp() {
        val j = HumanoidFitter.estimate(box(), HumanoidFitter.Up.Z)
        assertNotNull(j)
        j!!
        val foot = j[" L Foot"]!![2]
        val pelvis = j[" Pelvis"]!![2]
        val neck = j[" Neck"]!![2]
        val head = j[" Head"]!![2]
        assertTrue(foot < pelvis)
        assertTrue(pelvis < neck)
        assertTrue(neck < head)
    }

    @Test fun leftAndRightAreMirroredAroundCenter() {
        val j = HumanoidFitter.estimate(box(), HumanoidFitter.Up.Z)!!
        val l = j[" L UpperArm"]!!
        val r = j[" R UpperArm"]!!
        assertEquals(0.0, l[0] + r[0], 1e-9)
        assertEquals(l[2], r[2], 1e-9)
        assertTrue(l[0] > 0.0)
    }

    @Test fun everyTemplateBoneGetsAPosition() {
        val j = HumanoidFitter.estimate(box(), HumanoidFitter.Up.Z)!!
        for (b in GtaStandardRig.BONES) if (b.name.isNotEmpty()) assertTrue("missing ${b.name}", j.containsKey(b.name))
    }

    @Test fun degenerateInputReturnsNull() {
        assertTrue(HumanoidFitter.estimate(FloatArray(3), HumanoidFitter.Up.Z) == null)
        val flat = floatArrayOf(0f, 0f, 1f, 1f, 1f, 1f, 2f, 2f, 1f)
        assertTrue(HumanoidFitter.estimate(flat, HumanoidFitter.Up.Z) == null)
    }

    @Test fun detectUpPicksLongestAxis() {
        assertEquals(HumanoidFitter.Up.Z, HumanoidFitter.detectUp(box()))
        // Sama seperti box, tapi tingginya di Y. / Same box but tall along Y.
        val yUp = ArrayList<Float>()
        for (x in listOf(-0.3f, 0.3f)) for (y in listOf(0f, 1.8f)) for (z in listOf(-0.1f, 0.1f)) {
            yUp.add(x); yUp.add(y); yUp.add(z)
        }
        val yUpArr = yUp.toFloatArray()
        assertEquals(HumanoidFitter.Up.Y, HumanoidFitter.detectUp(yUpArr))
        assertTrue(HumanoidFitter.detectUp(FloatArray(3)) == null)
    }

    @Test fun legJointsAreOrderedHipKneeAnkle() {
        val j = HumanoidFitter.estimate(box(), HumanoidFitter.Up.Z)!!
        val hip = j[" L Thigh"]!![2]
        val knee = j[" L Calf"]!![2]
        val ankle = j[" L Foot"]!![2]
        assertTrue("hip > knee", hip > knee)
        assertTrue("knee > ankle", knee > ankle)
    }
}
