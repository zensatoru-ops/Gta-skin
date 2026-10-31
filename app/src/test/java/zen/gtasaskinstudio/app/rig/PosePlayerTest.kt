package zen.gtasaskinstudio.app.rig

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zen.gtasaskinstudio.app.ifp.IfpAnimation
import zen.gtasaskinstudio.app.ifp.IfpKeys
import zen.gtasaskinstudio.app.ifp.IfpSequence

/** Tes rig: memainkan pose IFP sintetis pada hasil auto-rig. / Rig test: plays synthetic IFP poses on an auto-rig result. */
class PosePlayerTest {

    /** Kotak Z-up (lebar 0,6, tebal 0,2, tinggi 1,8) dikali [k]. / Z-up box times [k]. */
    private fun rig(k: Float = 1f): AutoRigResult {
        val out = ArrayList<Float>()
        for (x in listOf(-0.3f * k, 0.3f * k)) for (y in listOf(-0.1f * k, 0.1f * k)) for (z in listOf(0f, 1.8f * k)) {
            out.add(x); out.add(y); out.add(z); out.add(0f); out.add(0f); out.add(1f)
        }
        return AutoRig.fromInterleaved(out.toFloatArray())!!
    }

    private fun seq(boneId: Int, q: DoubleArray, t: DoubleArray? = null, times: FloatArray = floatArrayOf(0f)): IfpSequence {
        val rots = FloatArray(times.size * 4) { q[it % 4].toFloat() }
        val trs = t?.let { FloatArray(times.size * 3) { i -> it[i % 3].toFloat() } }
        return IfpSequence("b$boneId", if (t != null) 4 else 3, times.size, boneId.toLong(), IfpKeys(times, rots, trs))
    }

    /** Animasi yang memberi tiap bone pose berdiri (IDLE_stance). / Animation giving every bone the standing pose. */
    private fun restAnim(rootQ: DoubleArray = GtaRestPose.STANCE_Q[1], skip: Set<Int> = emptySet()): IfpAnimation {
        val seqs = GtaStandardRig.WEIGHTED.filter { it.hanimId !in skip }.map {
            seq(it.hanimId!!, if (it.index == 1) rootQ else GtaRestPose.STANCE_Q[it.index])
        }
        return IfpAnimation("stand", seqs)
    }

    private fun dist(a: AutoRigJoint, b: AutoRigJoint): Double {
        val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun byName(p: PoseFrame) = p.joints.associateBy { it.boneName }

    private fun qMul(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
        a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
        a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
        a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0],
    )

    private fun axisAngle(x: Double, y: Double, z: Double, deg: Double): DoubleArray {
        val h = Math.toRadians(deg) / 2
        return doubleArrayOf(cos(h), x * sin(h), y * sin(h), z * sin(h))
    }

    @Test fun restPoseStandsUpright() {
        val r = rig()
        val p = PosePlayer.pose(r, restAnim(), 0f)
        assertNotNull(p)
        val j = byName(p!!)
        assertTrue(j[" Head"]!!.z > j[" Pelvis"]!!.z + 0.3)
        assertTrue(j[" L Foot"]!!.z < j[" Pelvis"]!!.z - 0.3)
        assertTrue(j[" R Foot"]!!.z < j[" Pelvis"]!!.z - 0.3)
        // Lengan menjuntai: tangan jauh di bawah leher. / Arms hang: hands well below the neck.
        assertTrue(j[" L Hand"]!!.z < j[" Neck"]!!.z - 0.3)
        assertTrue(j[" R Hand"]!!.z < j[" Neck"]!!.z - 0.3)
        assertEquals(32, p.animatedBones)
        assertTrue(p.missing.isEmpty())
    }

    @Test fun poseKeepsEveryBoneLength() {
        val r = rig()
        val rest = r.joints.associateBy { it.boneName }
        val q = axisAngle(0.3, 0.5, 0.8, 70.0)
        val anim = IfpAnimation("twist", GtaStandardRig.WEIGHTED.map { seq(it.hanimId!!, if (it.index == 1) GtaRestPose.STANCE_Q[1] else qMul(q, GtaRestPose.STANCE_Q[it.index])) })
        val j = byName(PosePlayer.pose(r, anim, 0f)!!)
        for (b in GtaStandardRig.BONES) {
            if (b.name.isEmpty() || b.parent <= 1) continue
            val pn = GtaStandardRig.BONES[b.parent].name
            assertEquals(b.name, dist(rest[b.name]!!, rest[pn]!!), dist(j[b.name]!!, j[pn]!!), 1e-9)
        }
    }

    @Test fun rootTurnAboutVerticalKeepsHeightsAndRadius() {
        val r = rig()
        val base = byName(PosePlayer.pose(r, restAnim(), 0f)!!)
        val turned = byName(PosePlayer.pose(r, restAnim(qMul(axisAngle(0.0, 0.0, 1.0, 90.0), GtaRestPose.STANCE_Q[1])), 0f)!!)
        val c0 = base["Root"]!!
        val c1 = turned["Root"]!!
        for (name in base.keys) {
            assertEquals(name, base[name]!!.z, turned[name]!!.z, 1e-9)
            val rb = Math.hypot(base[name]!!.x - c0.x, base[name]!!.y - c0.y)
            val rt = Math.hypot(turned[name]!!.x - c1.x, turned[name]!!.y - c1.y)
            assertEquals(name, rb, rt, 1e-9)
        }
        // Putaran 90 derajat benar-benar mengubah posisi samping. / The 90 degree turn really moves things sideways.
        assertTrue(abs(base[" L Hand"]!!.x - turned[" L Hand"]!!.x) + abs(base[" L Hand"]!!.y - turned[" L Hand"]!!.y) > 0.1)
    }

    @Test fun rollingRootOverLaysTheBodyDown() {
        val r = rig()
        val lying = byName(PosePlayer.pose(r, restAnim(qMul(axisAngle(1.0, 0.0, 0.0, 90.0), GtaRestPose.STANCE_Q[1])), 0f)!!)
        val head = lying[" Head"]!!
        val pelvis = lying[" Pelvis"]!!
        assertTrue(abs(head.z - pelvis.z) < 0.15)
        assertTrue(Math.hypot(head.x - pelvis.x, head.y - pelvis.y) > 0.3)
    }

    @Test fun bonesMissingFromAnimationUseRestRotation() {
        val r = rig()
        val full = byName(PosePlayer.pose(r, restAnim(), 0f)!!)
        // Bone daun (tanpa anak) tidak menggeser apa pun bila rotasinya diganti. / Leaf bones move nothing when their rotation changes.
        val leaves = restAnim(skip = setOf(8, 301, 302, 44, 54))
        val pl = PosePlayer.pose(r, leaves, 0f)!!
        assertEquals(listOf("Jaw", "R breast", "L breast", " L Toe0", " R Toe0").toSet(), pl.missing.toSet())
        assertEquals(27, pl.animatedBones)
        for (name in full.keys) assertEquals(name, full[name]!!.x, byName(pl)[name]!!.x, 1e-9)
        // Bone yang punya anak, bila hilang, memakai rotasi diam DFF dan menggeser anaknya. / A bone with children, when missing, uses the DFF rest rotation and moves its children.
        val noSpine = PosePlayer.pose(r, restAnim(skip = setOf(2)), 0f)!!
        assertTrue(noSpine.missing.contains(" Spine"))
        val a = full[" Neck"]!!
        val b = byName(noSpine)[" Neck"]!!
        assertTrue(abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z) > 0.01)
    }

    @Test fun poseSitsBesideTheModelOnTheLeftSide() {
        val r = rig()
        val rest = r.joints.associateBy { it.boneName }
        val p = PosePlayer.pose(r, restAnim(), 0f)!!
        val j = byName(p)
        val latSign = if (rest[" L Thigh"]!!.x > rest[" R Thigh"]!!.x) 1 else -1
        assertTrue((j[" Pelvis"]!!.x - rest[" Pelvis"]!!.x) * latSign > 0.3)
    }

    @Test fun scaledModelScalesThePoseButKeepsItsShape() {
        val small = byName(PosePlayer.pose(rig(1f), restAnim(), 0f)!!)
        val big = byName(PosePlayer.pose(rig(2f), restAnim(), 0f)!!)
        val hs = small[" Head"]!!.z - small[" Pelvis"]!!.z
        val hb = big[" Head"]!!.z - big[" Pelvis"]!!.z
        assertEquals(2.0, hb / hs, 1e-6)
    }

    @Test fun keyframesInterpolateBetweenFramesAndClampOutsideTheRange() {
        val k = IfpKeys(
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 0f, 0f, 0f, 0.70710677f, 0.70710677f, 0f, 0f),
            null,
        )
        val q = DoubleArray(4)
        PosePlayer.sampleRotation(k, -1f, q); assertEquals(1.0, q[0], 1e-6)
        PosePlayer.sampleRotation(k, 5f, q); assertEquals(0.70710677, q[0], 1e-6)
        PosePlayer.sampleRotation(k, 0.5f, q)
        // Setengah jalan antara 0 dan 90 derajat pada X = 45 derajat. / Halfway between 0 and 90 degrees about X = 45 degrees.
        assertEquals(cos(Math.toRadians(22.5)), q[0], 1e-6)
        assertEquals(sin(Math.toRadians(22.5)), q[1], 1e-6)
    }

    @Test fun rootTranslationFollowsTheAnimationAndIsScaled() {
        val r = rig()
        val moved = IfpAnimation("move", GtaStandardRig.WEIGHTED.map {
            if (it.index == 1) seq(0, GtaRestPose.STANCE_Q[1], doubleArrayOf(0.0, 0.0, 0.5)) else seq(it.hanimId!!, GtaRestPose.STANCE_Q[it.index])
        })
        val a = byName(PosePlayer.pose(r, restAnim(), 0f)!!)
        val b = byName(PosePlayer.pose(r, moved, 0f)!!)
        val scale = PosePlayer.modelScale(r.joints.associateBy { it.boneName })
        assertEquals(0.5 * scale, b[" Pelvis"]!!.z - a[" Pelvis"]!!.z, 1e-6)
    }

    @Test fun incompleteRigReturnsNull() {
        val r = rig()
        val cut = AutoRigResult(r.up, r.joints.filter { it.boneName != " Head" })
        assertNull(PosePlayer.pose(cut, restAnim(), 0f))
    }

    @Test fun durationIsTheLatestKeyframe() {
        val a = IfpAnimation("x", listOf(seq(0, doubleArrayOf(1.0, 0.0, 0.0, 0.0), times = floatArrayOf(0f, 2.5f)), seq(1, doubleArrayOf(1.0, 0.0, 0.0, 0.0), times = floatArrayOf(0f, 1f))))
        assertEquals(2.5f, PosePlayer.duration(a), 1e-6f)
    }
}
