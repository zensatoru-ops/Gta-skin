package zen.gtasaskinstudio.app.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zen.gtasaskinstudio.app.rw.Atomic
import zen.gtasaskinstudio.app.rw.DffModel
import zen.gtasaskinstudio.app.rw.Frame
import zen.gtasaskinstudio.app.rw.Skin

class BindPoseTest {

    private fun ident() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun skin(numBones: Int, ibm: FloatArray) =
        Skin(numBones, 4, IntArray(0), ByteArray(0), FloatArray(0), ibm)

    @Test
    fun bonePositionIsNegatedTranslationForIdentityRotation() {
        val ibm = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, -2f, -3f, 1f)
        val p = BindPose.bonePositions(skin(1, ibm))
        assertEquals(1f, p[0], 1e-6f); assertEquals(2f, p[1], 1e-6f); assertEquals(3f, p[2], 1e-6f)
    }

    @Test
    fun boneOrderFollowsHanimNodeListNotFrameOrder() {
        val f0 = Frame(0, -1, ident(), floatArrayOf(0f, 0f, 0f), 0)
        val f1 = Frame(1, 0, ident(), floatArrayOf(0f, 0f, 0f), 0).also { it.boneId = 51 }
        val f2 = Frame(2, 0, ident(), floatArrayOf(0f, 0f, 0f), 0).also { it.boneId = 2 }
        f0.hanimNodeIds = intArrayOf(2, 51)
        val model = DffModel(0x36003, listOf(f0, f1, f2), emptyList(), emptyList())
        val map = BindPose.boneFrames(model, skin(2, FloatArray(32)))!!
        assertEquals(listOf(2, 1), map.toList() as Any)
    }

    @Test
    fun boneOrderUnknownWhenCountsDiffer() {
        val f0 = Frame(0, -1, ident(), floatArrayOf(0f, 0f, 0f), 0)
        f0.hanimNodeIds = intArrayOf(1)
        val model = DffModel(0x36003, listOf(f0), emptyList(), emptyList())
        assertTrue(BindPose.boneFrames(model, skin(2, FloatArray(32))) == null)
    }

    @Test
    fun frameWorldChainsRowVectorTransforms() {
        // induk memutar x->y; anak berada di (1,0,0) lokal
        val rot = floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        val parent = Frame(0, -1, rot, floatArrayOf(10f, 0f, 0f), 0)
        val child = Frame(1, 0, ident(), floatArrayOf(1f, 0f, 0f), 0)
        val model = DffModel(0x36003, listOf(parent, child), emptyList(), listOf(Atomic(1, 0, 0)))
        val w = BindPose.frameWorld(model, 1)
        assertEquals(10f, w.pos[0], 1e-6f); assertEquals(1f, w.pos[1], 1e-6f)
    }
}
