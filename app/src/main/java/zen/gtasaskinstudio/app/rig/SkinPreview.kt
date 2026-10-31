package zen.gtasaskinstudio.app.rig

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pratinjau skinning: memindahkan vertex mesh mengikuti pose skeleton (linear blend skinning di CPU), memakai bobot
 * dari [AutoWeights]. Rotasi tiap bone = swing dari arah segmen diam ke arah segmen berpose; bone torso juga disejajarkan
 * lateralnya (garis bahu / pinggul) supaya badan tidak berputar salah. Puntiran sepanjang lengan/kaki tidak diikuti.
 * Hanya pratinjau; bukan bobot akhir.
 *
 * Skinning preview: moves mesh vertices to follow the skeleton pose (CPU linear blend skinning) using the weights from
 * [AutoWeights]. Each bone's rotation is the swing from the rest segment direction to the posed one; torso bones are also
 * aligned laterally (shoulder / hip line) so the body does not twist wrongly. Twist along arms/legs is not followed.
 * A preview only; not final weights.
 */
object SkinPreview {

    /** Transformasi satu bone: x' = rot * (x - restPivot) + posedPivot. / One bone transform. */
    class Xf(val rot: DoubleArray, val restPivot: DoubleArray, val posedPivot: DoubleArray)

    private val TORSO = setOf(2, 5, 7, 10, 13, 11, 12)

    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun unit(a: DoubleArray): DoubleArray {
        val l = sqrt(dot(a, a))
        return if (l < 1e-12) doubleArrayOf(0.0, 0.0, 0.0) else doubleArrayOf(a[0] / l, a[1] / l, a[2] / l)
    }

    private val IDENT = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

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

    /** Rotasi sudut [angle] di sekitar sumbu satuan [k] (Rodrigues). / Rotation by [angle] about unit axis [k] (Rodrigues). */
    internal fun axisAngle(k: DoubleArray, angle: Double): DoubleArray {
        val c = cos(angle); val s = sin(angle); val t = 1 - c
        val x = k[0]; val y = k[1]; val z = k[2]
        return doubleArrayOf(
            t * x * x + c, t * x * y - s * z, t * x * z + s * y,
            t * x * y + s * z, t * y * y + c, t * y * z - s * x,
            t * x * z - s * y, t * y * z + s * x, t * z * z + c,
        )
    }

    /** Rotasi terkecil yang memutar arah satuan [a] ke [b]. / Smallest rotation turning unit direction [a] into [b]. */
    internal fun swing(a: DoubleArray, b: DoubleArray): DoubleArray {
        val d = dot(a, b).coerceIn(-1.0, 1.0)
        val axis = cross(a, b)
        val sinA = sqrt(dot(axis, axis))
        if (sinA < 1e-9) {
            if (d > 0) return IDENT
            // Berlawanan arah: pilih sumbu tegak lurus sembarang. / Opposite: pick any perpendicular axis.
            val helper = if (abs(a[0]) < 0.9) doubleArrayOf(1.0, 0.0, 0.0) else doubleArrayOf(0.0, 1.0, 0.0)
            return axisAngle(unit(cross(a, helper)), Math.PI)
        }
        return axisAngle(unit(axis), atan2(sinA, d))
    }

    private fun joints(list: List<AutoRigJoint>) = list.associate { it.boneName to doubleArrayOf(it.x, it.y, it.z) }

    /**
     * Transformasi per bone utama dari rig diam [rest] ke rig berpose [posed]; null bila skeleton tidak lengkap.
     * Per-main-bone transforms from the rest rig to the posed rig; null if the skeleton is incomplete.
     */
    fun transforms(rest: List<AutoRigJoint>, posed: List<AutoRigJoint>, height: Double): Map<Int, Xf>? {
        val jr = joints(rest)
        val jp = joints(posed)
        val sr = AutoWeights.segments(jr, height) ?: return null
        val sp = AutoWeights.segments(jp, height) ?: return null
        fun lateral(j: Map<String, DoubleArray>, left: String, right: String): DoubleArray? {
            val l = j[left] ?: return null
            val r = j[right] ?: return null
            return unit(sub(l, r))
        }
        val shoulderR = lateral(jr, " L UpperArm", " R UpperArm")
        val shoulderP = lateral(jp, " L UpperArm", " R UpperArm")
        val hipR = lateral(jr, " L Thigh", " R Thigh")
        val hipP = lateral(jp, " L Thigh", " R Thigh")
        val out = HashMap<Int, Xf>()
        for (b in AutoWeights.SKIN_BONES) {
            val a = sr[b] ?: return null
            val p = sp[b] ?: return null
            val dr = unit(sub(a.b, a.a))
            val dp = unit(sub(p.b, p.a))
            var rot = swing(dr, dp)
            if (b in TORSO) {
                val latR = if (b == 2) hipR else shoulderR
                val latP = if (b == 2) hipP else shoulderP
                if (latR != null && latP != null) {
                    val l1 = apply(rot, latR)
                    // Proyeksikan ke bidang tegak lurus dp, lalu putar di sekitar dp. / Project onto the plane normal to dp, then twist about dp.
                    val u = unit(sub(l1, doubleArrayOf(dp[0] * dot(l1, dp), dp[1] * dot(l1, dp), dp[2] * dot(l1, dp))))
                    val v = unit(sub(latP, doubleArrayOf(dp[0] * dot(latP, dp), dp[1] * dot(latP, dp), dp[2] * dot(latP, dp))))
                    val angle = atan2(dot(dp, cross(u, v)), dot(u, v))
                    if (!angle.isNaN()) rot = mul(axisAngle(dp, angle), rot)
                }
            }
            out[b] = Xf(rot, a.a, p.a)
        }
        return out
    }

    /**
     * Linear blend skinning. [vertices] interleaved px,py,pz,nx,ny,nz (stride 6); hasilnya array baru dengan tata letak sama.
     * [vertices] interleaved px,py,pz,nx,ny,nz (stride 6); the result is a new array with the same layout.
     */
    fun skin(vertices: FloatArray, w: AutoWeightResult, xf: Map<Int, Xf>): FloatArray {
        val out = vertices.copyOf()
        val n = w.vertexCount
        val rot = Array(33) { xf[it]?.rot }
        val rp = Array(33) { xf[it]?.restPivot }
        val pp = Array(33) { xf[it]?.posedPivot }
        for (v in 0 until n) {
            val px = vertices[v * 6].toDouble(); val py = vertices[v * 6 + 1].toDouble(); val pz = vertices[v * 6 + 2].toDouble()
            val nx = vertices[v * 6 + 3].toDouble(); val ny = vertices[v * 6 + 4].toDouble(); val nz = vertices[v * 6 + 5].toDouble()
            var ox = 0.0; var oy = 0.0; var oz = 0.0
            var mx = 0.0; var my = 0.0; var mz = 0.0
            var total = 0.0
            for (s in 0 until 4) {
                val wt = w.weights[v * 4 + s].toDouble()
                if (wt <= 0.0) continue
                val b = w.bones[v * 4 + s]
                val r = rot[b] ?: continue
                val a = rp[b]!!; val q = pp[b]!!
                val dx = px - a[0]; val dy = py - a[1]; val dz = pz - a[2]
                ox += wt * (r[0] * dx + r[1] * dy + r[2] * dz + q[0])
                oy += wt * (r[3] * dx + r[4] * dy + r[5] * dz + q[1])
                oz += wt * (r[6] * dx + r[7] * dy + r[8] * dz + q[2])
                mx += wt * (r[0] * nx + r[1] * ny + r[2] * nz)
                my += wt * (r[3] * nx + r[4] * ny + r[5] * nz)
                mz += wt * (r[6] * nx + r[7] * ny + r[8] * nz)
                total += wt
            }
            if (total <= 1e-9) continue
            out[v * 6] = (ox / total).toFloat(); out[v * 6 + 1] = (oy / total).toFloat(); out[v * 6 + 2] = (oz / total).toFloat()
            val nl = sqrt(mx * mx + my * my + mz * mz)
            if (nl > 1e-9) { out[v * 6 + 3] = (mx / nl).toFloat(); out[v * 6 + 4] = (my / nl).toFloat(); out[v * 6 + 5] = (mz / nl).toFloat() }
        }
        return out
    }
}
