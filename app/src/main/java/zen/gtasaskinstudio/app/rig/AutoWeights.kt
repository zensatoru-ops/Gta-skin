package zen.gtasaskinstudio.app.rig

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Bobot skin per vertex: sampai 4 pengaruh (batas GTA SA). [bones] berisi indeks bone TEMPLATE ([GtaStandardRig.BONES]),
 * bukan indeks Skin PLG; 0 berarti slot kosong. / Per-vertex skin weights: up to 4 influences (the GTA SA limit).
 * [bones] holds TEMPLATE bone indexes ([GtaStandardRig.BONES]), not Skin PLG indexes; 0 means an empty slot.
 */
class AutoWeightResult(val vertexCount: Int, val bones: IntArray, val weights: FloatArray, val height: Double = 1.0)

/** Segmen tulang: dari sendi [a] ke titik [b], di ruang model. / A bone segment from joint [a] to point [b] in model space. */
class BoneSegment(val a: DoubleArray, val b: DoubleArray)

/**
 * Auto-weight untuk model tanpa bobot: jarak vertex ke segmen tulang hasil auto-rig, dihaluskan lewat tepi mesh.
 * Hanya tebakan awal; bone kecil (perut, dada, alis, rahang, ujung jari) digabung ke bone utama terdekat di hierarkinya.
 *
 * Auto-weights for models without weights: vertex distance to the auto-rig bone segments, smoothed over mesh edges.
 * A first guess only; minor bones (belly, breasts, brows, jaw, finger tips) are merged into the nearest main bone up the hierarchy.
 */
object AutoWeights {

    class Options(
        /** Pangkat jarak terbalik; makin besar makin tajam. / Inverse-distance power; larger is sharper. */
        val power: Double = 6.0,
        /** Pengaruh yang jaraknya lebih dari ini x jarak terdekat dibuang. / Influences farther than this x the nearest distance are dropped. */
        val cutoff: Double = 1.5,
        /** Pengali jarak untuk bone sisi seberang. / Distance multiplier for bones on the opposite side. */
        val sidePenalty: Double = 2.5,
        val smoothIterations: Int = 2,
        val smoothLambda: Double = 0.5,
        /** Eksponen jari-jari bone: jarak dibagi jari-jari^ini; 0 = tanpa. / Bone radius exponent: distance divided by radius^this; 0 = off. */
        val radiusPower: Double = 0.7,
        /** Awal segmen paha, sebagai pecahan jalur paha->lutut. / Start of the thigh segment as a fraction of the thigh->knee path. */
        val thighStart: Double = 0.2,
        /** Panjang segmen pinggul ke bawah, pecahan tinggi badan. / Pelvis segment length downward, fraction of body height. */
        val pelvisDrop: Double = 0.03,
        /** Jari-jari per bone (pecahan tinggi badan). / Radius per bone (fraction of body height). */
        val radius: Map<Int, Double> = DEFAULT_RADIUS,
    )

    /** Jari-jari tiap bone (pecahan tinggi badan), disetel pada separuh dari 116 DFF stok. / Bone radius (fraction of body height), tuned on half of the 116 stock DFFs. */
    val DEFAULT_RADIUS: Map<Int, Double> = mapOf(
        2 to 0.062, 5 to 0.062, 7 to 0.090, 10 to 0.040, 13 to 0.080, 11 to 0.030, 12 to 0.030, 17 to 0.030, 18 to 0.023, 19 to 0.020, 20 to 0.015, 22 to 0.030, 23 to 0.023, 24 to 0.020, 25 to 0.015, 3 to 0.045, 4 to 0.045, 27 to 0.034, 28 to 0.047, 29 to 0.020, 30 to 0.034, 31 to 0.047, 32 to 0.020,
    )

    /** Bone yang dipakai sebagai pengaruh (indeks template). / Bones used as influences (template indexes). */
    val SKIN_BONES: IntArray = intArrayOf(2, 5, 7, 10, 13, 11, 12, 17, 18, 19, 20, 22, 23, 24, 25, 3, 4, 27, 28, 29, 30, 31, 32)

    private val LEFT = setOf(4, 27, 28, 29, 12, 17, 18, 19, 20)
    private val RIGHT = setOf(3, 30, 31, 32, 11, 22, 23, 24, 25)

    /** Bone kecil -> bone utama. / Minor bone -> main bone. */
    fun mainBone(templateIndex: Int): Int = when (templateIndex) {
        1 -> 2
        6 -> 5
        8, 9 -> 7
        14, 15, 16 -> 13
        21 -> 20
        26 -> 25
        else -> templateIndex
    }

    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun add(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])
    private fun mul(a: DoubleArray, k: Double) = doubleArrayOf(a[0] * k, a[1] * k, a[2] * k)
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun len(a: DoubleArray) = sqrt(dot(a, a))
    private fun unit(a: DoubleArray): DoubleArray {
        val l = len(a)
        return if (l < 1e-12) doubleArrayOf(0.0, 0.0, 0.0) else mul(a, 1 / l)
    }

    private fun joint(byName: Map<String, DoubleArray>, name: String): DoubleArray? = byName[name]

    /**
     * Segmen tiap bone utama dari sendi [joints] (nama bone -> posisi). Bone ujung memakai perpanjangan yang sama
     * di rig diam dan rig berpose, jadi dua panggilan memberi segmen yang berpadanan.
     * Segment of each main bone from [joints] (bone name -> position). End bones use extensions that are the same
     * for the rest rig and a posed rig, so two calls give corresponding segments.
     * [height] = tinggi badan; dipakai untuk panjang perpanjangan. / body height, used for extension lengths.
     */
    fun segments(joints: Map<String, DoubleArray>, height: Double, options: Options = Options()): Map<Int, BoneSegment>? {
        fun j(n: String) = joint(joints, n)
        val pelvis = j(" Pelvis") ?: return null
        val spine = j(" Spine") ?: return null
        val spine1 = j(" Spine1") ?: return null
        val neck = j(" Neck") ?: return null
        val head = j(" Head") ?: return null
        val lClav = j("Bip01 L Clavicle") ?: return null
        val rClav = j("Bip01 R Clavicle") ?: return null
        val lUp = j(" L UpperArm") ?: return null
        val rUp = j(" R UpperArm") ?: return null
        val lFore = j(" L ForeArm") ?: return null
        val rFore = j(" R ForeArm") ?: return null
        val lHand = j(" L Hand") ?: return null
        val rHand = j(" R Hand") ?: return null
        val lFing = j(" L Finger") ?: return null
        val rFing = j(" R Finger") ?: return null
        val lFing1 = j("L Finger01") ?: return null
        val rFing1 = j("R Finger01") ?: return null
        val lThigh = j(" L Thigh") ?: return null
        val rThigh = j(" R Thigh") ?: return null
        val lCalf = j(" L Calf") ?: return null
        val rCalf = j(" R Calf") ?: return null
        val lFoot = j(" L Foot") ?: return null
        val rFoot = j(" R Foot") ?: return null
        val lToe = j(" L Toe0") ?: return null
        val rToe = j(" R Toe0") ?: return null

        val torso = unit(sub(spine1, pelvis))
        val out = HashMap<Int, BoneSegment>()
        out[2] = BoneSegment(pelvis, sub(pelvis, mul(torso, options.pelvisDrop * height)))
        out[5] = BoneSegment(spine, spine1)
        out[7] = BoneSegment(spine1, neck)
        out[10] = BoneSegment(neck, head)
        out[13] = BoneSegment(head, add(head, mul(unit(sub(head, neck)), 0.07 * height)))
        out[11] = BoneSegment(neck, rUp)
        out[12] = BoneSegment(neck, lUp)
        out[17] = BoneSegment(lUp, lFore)
        out[18] = BoneSegment(lFore, lHand)
        out[19] = BoneSegment(lHand, lFing)
        out[20] = BoneSegment(lFing, add(lFing1, mul(unit(sub(lFing1, lFing)), 0.02 * height)))
        out[22] = BoneSegment(rUp, rFore)
        out[23] = BoneSegment(rFore, rHand)
        out[24] = BoneSegment(rHand, rFing)
        out[25] = BoneSegment(rFing, add(rFing1, mul(unit(sub(rFing1, rFing)), 0.02 * height)))
        out[4] = BoneSegment(add(lThigh, mul(sub(lCalf, lThigh), options.thighStart)), lCalf)
        out[27] = BoneSegment(lCalf, lFoot)
        out[28] = BoneSegment(lFoot, lToe)
        out[29] = BoneSegment(lToe, add(lToe, mul(unit(sub(lToe, lFoot)), 0.03 * height)))
        out[3] = BoneSegment(add(rThigh, mul(sub(rCalf, rThigh), options.thighStart)), rCalf)
        out[30] = BoneSegment(rCalf, rFoot)
        out[31] = BoneSegment(rFoot, rToe)
        out[32] = BoneSegment(rToe, add(rToe, mul(unit(sub(rToe, rFoot)), 0.03 * height)))
        return out
    }

    private fun distToSegment(p: DoubleArray, s: BoneSegment): Double {
        val ab = sub(s.b, s.a)
        val l2 = dot(ab, ab)
        val t = if (l2 < 1e-18) 0.0 else max(0.0, min(1.0, dot(sub(p, s.a), ab) / l2))
        val dx = p[0] - (s.a[0] + ab[0] * t)
        val dy = p[1] - (s.a[1] + ab[1] * t)
        val dz = p[2] - (s.a[2] + ab[2] * t)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * @param positions posisi vertex, 3 float per vertex. / vertex positions, 3 floats per vertex.
     * @param edges pasangan indeks vertex (u0,v0,u1,v1,...) untuk penghalusan; boleh kosong. / vertex index pairs for smoothing; may be empty.
     */
    fun compute(positions: FloatArray, edges: IntArray, rig: AutoRigResult, options: Options = Options()): AutoWeightResult? {
        val n = positions.size / 3
        if (n < 3) return null
        val joints = rig.joints.associate { it.boneName to doubleArrayOf(it.x, it.y, it.z) }
        val byName = rig.joints.associateBy { it.boneName }
        val up = PosePlayer.upVector(rig, byName)
        var minUp = Double.POSITIVE_INFINITY
        var maxUp = Double.NEGATIVE_INFINITY
        for (v in 0 until n) {
            val h = positions[v * 3] * up[0] + positions[v * 3 + 1] * up[1] + positions[v * 3 + 2] * up[2]
            if (h < minUp) minUp = h
            if (h > maxUp) maxUp = h
        }
        val height = maxUp - minUp
        if (height < 1e-9) return null
        val segs = segments(joints, height, options) ?: return null

        val lThigh = joints[" L Thigh"]!!
        val rThigh = joints[" R Thigh"]!!
        var lat = sub(lThigh, rThigh)
        val along = dot(lat, up)
        lat = unit(sub(lat, mul(up, along)))
        val center = joints[" Pelvis"]!!
        val margin = 0.03 * height

        val k = SKIN_BONES.size
        val dense = FloatArray(n * k)
        val eps = 0.01 * height
        val d = DoubleArray(k)
        val p = DoubleArray(3)
        for (v in 0 until n) {
            p[0] = positions[v * 3].toDouble(); p[1] = positions[v * 3 + 1].toDouble(); p[2] = positions[v * 3 + 2].toDouble()
            val side = (p[0] - center[0]) * lat[0] + (p[1] - center[1]) * lat[1] + (p[2] - center[2]) * lat[2]
            var dmin = Double.POSITIVE_INFINITY
            for (i in 0 until k) {
                val b = SKIN_BONES[i]
                var dist = distToSegment(p, segs[b]!!)
                // Bone sisi kiri tidak boleh menarik vertex di sisi kanan jauh dari tengah, dan sebaliknya.
                // A left bone must not pull vertices far on the right side, and vice versa.
                if (b in LEFT && side < -margin) dist *= options.sidePenalty
                if (b in RIGHT && side > margin) dist *= options.sidePenalty
                if (options.radiusPower != 0.0) dist /= (options.radius[b] ?: 0.05).pow(options.radiusPower)
                d[i] = dist
                if (dist < dmin) dmin = dist
            }
            var sum = 0.0
            val limit = dmin * options.cutoff + eps
            for (i in 0 until k) {
                if (d[i] <= limit) {
                    val w = 1.0 / (d[i] + eps).pow(options.power)
                    dense[v * k + i] = w.toFloat()
                    sum += w
                }
            }
            if (sum > 0) for (i in 0 until k) dense[v * k + i] = (dense[v * k + i] / sum).toFloat()
        }

        smooth(dense, k, n, edges, options)

        val bones = IntArray(n * 4)
        val weights = FloatArray(n * 4)
        val order = IntArray(k)
        for (v in 0 until n) {
            for (i in 0 until k) order[i] = i
            // 4 terbesar lewat seleksi sederhana. / Top 4 by simple selection.
            var total = 0f
            for (s in 0 until 4) {
                var best = s
                for (i in s + 1 until k) if (dense[v * k + order[i]] > dense[v * k + order[best]]) best = i
                val t = order[s]; order[s] = order[best]; order[best] = t
                val w = dense[v * k + order[s]]
                if (w > 1e-4f) {
                    bones[v * 4 + s] = SKIN_BONES[order[s]]
                    weights[v * 4 + s] = w
                    total += w
                }
            }
            if (total > 0f) for (s in 0 until 4) weights[v * 4 + s] /= total
            else { bones[v * 4] = SKIN_BONES[0]; weights[v * 4] = 1f }
        }
        return AutoWeightResult(n, bones, weights, height)
    }

    private fun smooth(dense: FloatArray, k: Int, n: Int, edges: IntArray, o: Options) {
        if (o.smoothIterations <= 0 || edges.size < 2) return
        val deg = IntArray(n)
        var m = 0
        while (m + 1 < edges.size) {
            val a = edges[m]; val b = edges[m + 1]
            if (a in 0 until n && b in 0 until n && a != b) { deg[a]++; deg[b]++ }
            m += 2
        }
        val acc = FloatArray(n * k)
        val lambda = o.smoothLambda.toFloat()
        repeat(o.smoothIterations) {
            java.util.Arrays.fill(acc, 0f)
            var e = 0
            while (e + 1 < edges.size) {
                val a = edges[e]; val b = edges[e + 1]
                e += 2
                if (a !in 0 until n || b !in 0 until n || a == b) continue
                for (i in 0 until k) {
                    acc[a * k + i] += dense[b * k + i]
                    acc[b * k + i] += dense[a * k + i]
                }
            }
            for (v in 0 until n) {
                if (deg[v] == 0) continue
                val inv = 1f / deg[v]
                for (i in 0 until k) dense[v * k + i] = (1 - lambda) * dense[v * k + i] + lambda * acc[v * k + i] * inv
            }
        }
    }

    /** Pasangan tepi (u,v) dari indeks segitiga (a,b,c,...). / Edge pairs (u,v) from triangle indexes (a,b,c,...). */
    fun edgesFromTriangles(tri: IntArray): IntArray {
        val t = tri.size / 3
        val out = IntArray(t * 6)
        for (i in 0 until t) {
            val a = tri[i * 3]; val b = tri[i * 3 + 1]; val c = tri[i * 3 + 2]
            out[i * 6] = a; out[i * 6 + 1] = b
            out[i * 6 + 2] = b; out[i * 6 + 3] = c
            out[i * 6 + 4] = c; out[i * 6 + 5] = a
        }
        return out
    }
}
