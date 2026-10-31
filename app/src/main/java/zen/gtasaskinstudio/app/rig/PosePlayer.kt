package zen.gtasaskinstudio.app.rig

import zen.gtasaskinstudio.app.ifp.IfpAnimation
import zen.gtasaskinstudio.app.ifp.IfpKeys
import zen.gtasaskinstudio.app.ifp.IfpSequence
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hasil memainkan satu pose: sendi di ruang model, jumlah bone yang digerakkan IFP, dan bone yang tidak ada di animasi
 * (memakai rotasi diam). / Result of playing one pose: joints in model space, how many bones the IFP drove, and the bones
 * missing from the animation (which use the rest rotation).
 */
class PoseFrame(val joints: List<AutoRigJoint>, val animatedBones: Int, val missing: List<String>, val shift: DoubleArray)

/**
 * Memainkan keyframe ped.ifp pada skeleton hasil auto-rig (kinematika maju). Rotasi IFP adalah rotasi LOKAL mutlak
 * tiap bone (menggantikan rotasi frame), jadi pose dihitung dari rantai: posisi anak = posisi induk + rotasi dunia induk
 * x offset lokal. Offset lokal = arah standar GTA x panjang tulang hasil estimasi (model dipakai untuk panjang saja).
 * Pose digambar di SAMPING model (digeser ke sisi +lateral), karena mesh belum punya bobot untuk ikut bergerak.
 *
 * Plays ped.ifp keyframes on an auto-rig skeleton (forward kinematics). IFP rotations are ABSOLUTE LOCAL rotations per
 * bone (they replace the frame rotation), so the pose follows the chain: child position = parent position + parent world
 * rotation x local offset. Local offset = GTA standard direction x estimated bone length (the model only supplies lengths).
 * The pose is drawn BESIDE the model (shifted to the +lateral side) because the mesh has no weights to follow it yet.
 *
 * Konvensi ruang GTA: X = kanan, Y = depan, Z = atas. Ruang model: atas = sumbu tinggi, +lateral = sisi L,
 * depan = lateral x atas (arah hadap tidak bisa dibaca dari mesh; tukar kiri/kanan membalik hadap).
 * GTA space: X = right, Y = forward, Z = up. Model space: up = height axis, +lateral = L side, forward = lateral x up
 * (facing cannot be read from the mesh; swapping left/right flips it).
 */
object PosePlayer {

    /** Bone yang dipakai untuk menaksir skala model terhadap skeleton stok. / Bones used to estimate model scale vs. stock. */
    private val SCALE_BONES = intArrayOf(7, 10, 13, 17, 18, 22, 23, 27, 28, 30, 31)

    /** Animasi satu frame berisi pose berdiri bawaan, untuk tampilan awal tanpa ped.ifp. / One-frame animation of the built-in standing pose, for a first view without ped.ifp. */
    fun stanceAnimation(): IfpAnimation {
        val seqs = GtaStandardRig.WEIGHTED.map { b ->
            val q = GtaRestPose.STANCE_Q[b.index]
            IfpSequence(b.name, 3, 1, (b.hanimId ?: 0).toLong(), IfpKeys(floatArrayOf(0f), FloatArray(4) { q[it].toFloat() }, null))
        }
        return IfpAnimation("stance", seqs)
    }

    fun duration(anim: IfpAnimation): Float {
        var d = 0f
        for (s in anim.sequences) {
            val t = s.keys?.times ?: continue
            if (t.isNotEmpty()) d = max(d, t[t.size - 1])
        }
        return d
    }

    /**
     * Rotasi (w,x,y,z) di waktu [t] (detik), interpolasi slerp; di luar rentang ditahan di ujung.
     * Rotation (w,x,y,z) at time [t] seconds, slerp-interpolated; clamped to the ends outside the range.
     */
    internal fun sampleRotation(k: IfpKeys, t: Float, out: DoubleArray) {
        val n = k.times.size
        if (n == 0) { out[0] = 1.0; out[1] = 0.0; out[2] = 0.0; out[3] = 0.0; return }
        val i1 = upperIndex(k.times, t)
        if (i1 <= 0) { load(k.rotations, 0, out); return }
        if (i1 >= n) { load(k.rotations, n - 1, out); return }
        val i0 = i1 - 1
        val span = k.times[i1] - k.times[i0]
        val a = if (span > 1e-6f) ((t - k.times[i0]) / span).toDouble() else 0.0
        slerp(k.rotations, i0, i1, a, out)
    }

    /** Translasi root (x,y,z) di waktu [t]; null bila urutan tanpa translasi. / Root translation at [t]; null without translation. */
    internal fun sampleTranslation(k: IfpKeys, t: Float, out: DoubleArray): Boolean {
        val tr = k.translations ?: return false
        val n = k.times.size
        if (n == 0) return false
        val i1 = upperIndex(k.times, t)
        if (i1 <= 0) { for (c in 0..2) out[c] = tr[c].toDouble(); return true }
        if (i1 >= n) { for (c in 0..2) out[c] = tr[(n - 1) * 3 + c].toDouble(); return true }
        val i0 = i1 - 1
        val span = k.times[i1] - k.times[i0]
        val a = if (span > 1e-6f) ((t - k.times[i0]) / span).toDouble() else 0.0
        for (c in 0..2) out[c] = tr[i0 * 3 + c] * (1 - a) + tr[i1 * 3 + c] * a
        return true
    }

    /** Indeks pertama dengan waktu > t (n bila tidak ada). / First index with time > t (n if none). */
    private fun upperIndex(times: FloatArray, t: Float): Int {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun load(r: FloatArray, i: Int, out: DoubleArray) {
        for (c in 0..3) out[c] = r[i * 4 + c].toDouble()
        normalize(out)
    }

    private fun normalize(q: DoubleArray) {
        val n = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (n < 1e-9) { q[0] = 1.0; q[1] = 0.0; q[2] = 0.0; q[3] = 0.0 } else for (c in 0..3) q[c] /= n
    }

    private fun slerp(r: FloatArray, i0: Int, i1: Int, a: Double, out: DoubleArray) {
        val q0 = DoubleArray(4) { r[i0 * 4 + it].toDouble() }
        val q1 = DoubleArray(4) { r[i1 * 4 + it].toDouble() }
        normalize(q0); normalize(q1)
        var dot = q0[0] * q1[0] + q0[1] * q1[1] + q0[2] * q1[2] + q0[3] * q1[3]
        if (dot < 0) { for (c in 0..3) q1[c] = -q1[c]; dot = -dot }
        if (dot > 0.9995) {
            for (c in 0..3) out[c] = q0[c] + (q1[c] - q0[c]) * a
        } else {
            val theta = acos(min(1.0, dot))
            val s = sin(theta)
            val w0 = sin((1 - a) * theta) / s
            val w1 = sin(a * theta) / s
            for (c in 0..3) out[c] = q0[c] * w0 + q1[c] * w1
        }
        normalize(out)
    }

    /** Matriks rotasi 3x3 (baris-utama, vektor kolom) dari kuaternion w,x,y,z. / 3x3 row-major column-vector matrix from w,x,y,z. */
    internal fun matrix(q: DoubleArray): DoubleArray {
        val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]
        return doubleArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
            2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
            2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y),
        )
    }

    private fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val o = DoubleArray(9)
        for (r in 0..2) for (c in 0..2) o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
        return o
    }

    private fun apply(m: DoubleArray, v: DoubleArray) = doubleArrayOf(
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
        m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
    )

    private fun dist(a: AutoRigJoint, b: AutoRigJoint): Double {
        val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun unit(v: DoubleArray): DoubleArray {
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (n < 1e-12) doubleArrayOf(0.0, 0.0, 0.0) else doubleArrayOf(v[0] / n, v[1] / n, v[2] / n)
    }

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0],
    )

    /** Sumbu atas model (satuan), tandanya mengikuti arah pelvis ke kepala. / Model up axis (unit), signed toward the head. */
    internal fun upVector(rest: AutoRigResult, byName: Map<String, AutoRigJoint>): DoubleArray {
        val axis = when (rest.up) {
            HumanoidFitter.Up.X -> doubleArrayOf(1.0, 0.0, 0.0)
            HumanoidFitter.Up.Y -> doubleArrayOf(0.0, 1.0, 0.0)
            HumanoidFitter.Up.Z -> doubleArrayOf(0.0, 0.0, 1.0)
        }
        val head = byName[" Head"]
        val pelvis = byName[" Pelvis"]
        if (head != null && pelvis != null) {
            val d = axis[0] * (head.x - pelvis.x) + axis[1] * (head.y - pelvis.y) + axis[2] * (head.z - pelvis.z)
            if (d < 0) return doubleArrayOf(-axis[0], -axis[1], -axis[2])
        }
        return axis
    }

    /** Skala model terhadap skeleton stok: median rasio panjang tulang estimasi / panjang stok. / Model scale vs stock: median bone-length ratio. */
    internal fun modelScale(byName: Map<String, AutoRigJoint>): Double {
        val ratios = ArrayList<Double>()
        for (i in SCALE_BONES) {
            val b = GtaStandardRig.BONES[i]
            val p = GtaStandardRig.BONES.getOrNull(b.parent) ?: continue
            val jb = byName[b.name] ?: continue
            val jp = byName[p.name] ?: continue
            val len = dist(jb, jp)
            if (len > 1e-9 && GtaRestPose.STOCK_LEN[i] > 1e-6) ratios.add(len / GtaRestPose.STOCK_LEN[i])
        }
        if (ratios.isEmpty()) return 1.0
        ratios.sort()
        return ratios[ratios.size / 2]
    }

    /**
     * Memainkan [anim] di waktu [time] (detik) pada [rest]. Mengembalikan null bila skeleton tidak lengkap.
     * Plays [anim] at [time] seconds on [rest]. Returns null if the skeleton is incomplete.
     * [beside] false menggambar pose di tempat (untuk mesh yang ikut bergerak). / false places the pose in place (for a mesh that follows).
     */
    fun pose(rest: AutoRigResult, anim: IfpAnimation, time: Float, beside: Boolean = true): PoseFrame? {
        val byName = rest.joints.associateBy { it.boneName }
        val bones = GtaStandardRig.BONES
        if (bones.any { it.name.isNotEmpty() && byName[it.name] == null }) return null
        val lThigh = byName[" L Thigh"] ?: return null
        val rThigh = byName[" R Thigh"] ?: return null
        val rootJoint = byName["Root"] ?: return null

        val up = upVector(rest, byName)
        var lat = doubleArrayOf(lThigh.x - rThigh.x, lThigh.y - rThigh.y, lThigh.z - rThigh.z)
        val along = lat[0] * up[0] + lat[1] * up[1] + lat[2] * up[2]
        lat = unit(doubleArrayOf(lat[0] - along * up[0], lat[1] - along * up[1], lat[2] - along * up[2]))
        if (lat[0] == 0.0 && lat[1] == 0.0 && lat[2] == 0.0) return null
        val fwd = cross(lat, up)
        // GTA -> model: X(kanan) = -lat, Y(depan) = fwd, Z(atas) = up. / GTA -> model.
        val toModel = doubleArrayOf(
            -lat[0], fwd[0], up[0],
            -lat[1], fwd[1], up[1],
            -lat[2], fwd[2], up[2],
        )

        val scale = modelScale(byName)
        val bySeq = HashMap<Long, IfpSequence>()
        for (s in anim.sequences) if (s.keys != null) bySeq[s.boneId] = s

        val n = bones.size
        val world = arrayOfNulls<DoubleArray>(n)
        val pos = arrayOfNulls<DoubleArray>(n)
        val q = DoubleArray(4)
        val tmp = DoubleArray(3)
        var animated = 0
        val missing = ArrayList<String>()
        for (i in 1 until n) {
            val b = bones[i]
            val seq = b.hanimId?.let { bySeq[it.toLong()] }
            if (seq != null) {
                sampleRotation(seq.keys!!, time, q)
                animated++
            } else {
                for (c in 0..3) q[c] = GtaRestPose.REST_Q[i][c]
                if (b.name.isNotEmpty()) missing.add(b.name)
            }
            val local = matrix(q)
            val parentIdx = b.parent
            if (parentIdx <= 0) {
                world[i] = local
                val rootPos = DoubleArray(3)
                if (seq?.keys != null && sampleTranslation(seq.keys, time, tmp)) {
                    for (c in 0..2) rootPos[c] = tmp[c] * scale
                }
                pos[i] = rootPos
            } else {
                val pw = world[parentIdx]!!
                world[i] = mul(pw, local)
                val parentName = bones[parentIdx].name
                val local3 = if (seq?.keys != null && sampleTranslation(seq.keys, time, tmp)) {
                    // Urutan tipe 4 mengganti offset lokal (mis. Pelvis). / Type-4 sequences replace the local offset (e.g. Pelvis).
                    doubleArrayOf(tmp[0] * scale, tmp[1] * scale, tmp[2] * scale)
                } else {
                    val len = dist(byName[b.name]!!, byName[parentName]!!)
                    val d = unit(GtaRestPose.DIR[i])
                    doubleArrayOf(d[0] * len, d[1] * len, d[2] * len)
                }
                val off = apply(pw, local3)
                val pp = pos[parentIdx]!!
                pos[i] = doubleArrayOf(pp[0] + off[0], pp[1] + off[1], pp[2] + off[2])
            }
        }

        // Geser ke samping model: selebar rentang tangan estimasi. / Shift beside the model by the estimated arm span.
        val lh = byName[" L Hand"]
        val rh = byName[" R Hand"]
        val span = if (lh != null && rh != null) dist(lh, rh) else 1.0
        val shiftLen = if (beside) span * 0.8 else 0.0
        val shift = doubleArrayOf(lat[0] * shiftLen, lat[1] * shiftLen, lat[2] * shiftLen)

        val joints = ArrayList<AutoRigJoint>()
        for (i in 1 until n) {
            val b = bones[i]
            if (b.name.isEmpty()) continue
            val m = apply(toModel, pos[i]!!)
            joints.add(
                AutoRigJoint(
                    b.name,
                    rootJoint.x + shift[0] + m[0],
                    rootJoint.y + shift[1] + m[1],
                    rootJoint.z + shift[2] + m[2],
                )
            )
        }
        for (j in joints) if (!(abs(j.x) < 1e9 && abs(j.y) < 1e9 && abs(j.z) < 1e9)) return null
        return PoseFrame(joints, animated, missing, shift)
    }
}
