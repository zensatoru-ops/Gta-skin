package zen.gtasaskinstudio.app.rig

/**
 * Estimasi posisi sendi humanoid dari bounding box mesh, memakai proporsi skeleton standar GTA SA.
 * Hasilnya ESTIMASI, bukan rig terverifikasi: titik awal yang harus dikoreksi manual.
 * Pure Kotlin, JVM-testable.
 *
 * Estimates humanoid joint positions from the mesh bounding box, using the standard GTA SA
 * skeleton proportions. This is an ESTIMATE, not a verified rig: a starting point to correct manually.
 *
 * Konvensi: [Up] menentukan sumbu tinggi; sumbu lateral dipilih otomatis (rentang non-tinggi terbesar).
 * Sisi "L" ada di +lateral dari pusat, "R" di -lateral; [estimate] punya parameter `mirror` untuk menukarnya,
 * karena arah hadap model tidak bisa dibaca dari bounding box.
 * Convention: [Up] picks the height axis; the lateral axis is chosen automatically (largest non-height extent).
 * The "L" side is at +lateral from the center, "R" at -lateral; [estimate] has a `mirror` parameter to swap them,
 * because the facing direction cannot be read from the bounding box.
 */
object HumanoidFitter {

    enum class Up { X, Y, Z }

    /**
     * Proporsi per bone: tinggi (0 = dasar mesh, 1 = puncak) dan posisi lateral (0 = tengah,
     * 1 = tepi bounding box; positif = sisi L). Median dari 116 DFF skin GTA SA; hanya angka,
     * tanpa aset game. Kunci = nama bone persis dari [GtaStandardRig].
     *
     * Per-bone proportions: height (0 = mesh bottom, 1 = top) and lateral position (0 = center,
     * 1 = bounding-box edge; positive = L side). Median of 116 GTA SA skin DFFs; numbers only,
     * no game assets. Key = exact bone name from [GtaStandardRig].
     */
    private class Prop(val h: Double, val lat: Double)

    // Pembacaan ujung lengan. / Arm tip reading.
    private const val TIP_BAND = 0.85          // pita lateral terluar / outermost lateral band
    private const val MIN_TIP_VERTICES = 20
    private const val HAND_DH = 0.043
    private const val HAND_DLAT = -0.052
    private const val FOREARM_T = 0.52         // siku di 52% jalur bahu-tangan / elbow at 52% of shoulder-to-hand
    private const val FINGER_T = 0.16
    private const val FINGER01_T = 0.22

    private val PROPORTIONS: Map<String, Prop> = mapOf(
        "Root" to Prop(0.538, 0.0),
        " Pelvis" to Prop(0.538, 0.0),
        " L Thigh" to Prop(0.538, 0.237),
        " R Thigh" to Prop(0.538, -0.237),
        " Spine" to Prop(0.534, 0.0),
        "Belly" to Prop(0.626, 0.0),
        " Spine1" to Prop(0.677, 0.0),
        "L breast" to Prop(0.749, 0.205),
        "R breast" to Prop(0.749, -0.205),
        " Neck" to Prop(0.826, 0.0),
        "Bip01 L Clavicle" to Prop(0.826, 0.070),
        "Bip01 R Clavicle" to Prop(0.826, -0.070),
        " Head" to Prop(0.899, 0.0),
        "L Brow" to Prop(0.942, 0.048),
        "R Brow" to Prop(0.942, -0.048),
        "Jaw" to Prop(0.900, 0.0),
        " L UpperArm" to Prop(0.812, 0.405),
        " R UpperArm" to Prop(0.812, -0.405),
        " L ForeArm" to Prop(0.669, 0.646),
        " R ForeArm" to Prop(0.669, -0.646),
        " L Hand" to Prop(0.542, 0.866),
        " R Hand" to Prop(0.542, -0.866),
        " L Finger" to Prop(0.500, 0.944),
        " R Finger" to Prop(0.500, -0.944),
        "L Finger01" to Prop(0.474, 0.956),
        "R Finger01" to Prop(0.474, -0.956),
        " L Calf" to Prop(0.290, 0.310),
        " R Calf" to Prop(0.290, -0.310),
        " L Foot" to Prop(0.068, 0.368),
        " R Foot" to Prop(0.068, -0.368),
        " L Toe0" to Prop(0.002, 0.368),
        " R Toe0" to Prop(0.002, -0.368),
    )

    /**
     * Tebak sumbu tinggi: sumbu dengan rentang terbesar. Humanoid berdiri hampir selalu paling tinggi
     * di sumbu ini. Pemanggil boleh menimpanya. / Guess the height axis: the axis with the largest extent.
     * An upright humanoid is nearly always tallest along it. The caller may override this.
     */
    fun detectUp(positions: FloatArray): Up? {
        if (positions.size < 9 || positions.size % 3 != 0) return null
        val mn = DoubleArray(3) { Double.MAX_VALUE }
        val mx = DoubleArray(3) { -Double.MAX_VALUE }
        var i = 0
        while (i < positions.size) {
            for (k in 0..2) {
                val v = positions[i + k].toDouble()
                if (v < mn[k]) mn[k] = v
                if (v > mx[k]) mx[k] = v
            }
            i += 3
        }
        val ext = DoubleArray(3) { mx[it] - mn[it] }
        val best = ext.indices.maxByOrNull { ext[it] } ?: return null
        if (ext[best] <= 1e-9) return null
        return Up.values()[best]
    }

    /**
     * Sendi per bone template. Kunci = nama bone persis dari [GtaStandardRig]. Nilai = (x, y, z) di ruang mesh.
     * Joints per template bone. Key = exact bone name from [GtaStandardRig]. Value = (x, y, z) in mesh space.
     */
    fun estimate(positions: FloatArray, up: Up, mirror: Boolean = false): Map<String, DoubleArray>? {
        if (positions.size < 9 || positions.size % 3 != 0) return null
        val mn = DoubleArray(3) { Double.MAX_VALUE }
        val mx = DoubleArray(3) { -Double.MAX_VALUE }
        var i = 0
        while (i < positions.size) {
            for (k in 0..2) {
                val v = positions[i + k].toDouble()
                if (v < mn[k]) mn[k] = v
                if (v > mx[k]) mx[k] = v
            }
            i += 3
        }
        val upAxis = up.ordinal
        // Sumbu lateral = sumbu non-tinggi dengan rentang lebih besar (lebar badan atau bentang tangan
        // lebih besar dari kedalaman). Mod bisa diekspor dengan sumbu yang berbeda-beda.
        // Lateral axis = the non-height axis with the larger extent (body width or arm span exceeds depth).
        // Mods can be exported with different axes.
        val a1 = (upAxis + 1) % 3
        val a2 = (upAxis + 2) % 3
        val latAxis = if (mx[a1] - mn[a1] >= mx[a2] - mn[a2]) a1 else a2
        val depAxis = 3 - upAxis - latAxis
        val height = mx[upAxis] - mn[upAxis]
        if (height <= 1e-9) return null
        val halfWidth = (mx[latAxis] - mn[latAxis]) / 2.0
        val latCenter = (mx[latAxis] + mn[latAxis]) / 2.0
        val depCenter = (mx[depAxis] + mn[depAxis]) / 2.0

        fun place(h: Double, lat: Double) = DoubleArray(3).also {
            it[upAxis] = mn[upAxis] + h * height
            it[latAxis] = latCenter + lat * halfWidth
            it[depAxis] = depCenter
        }

        val out = HashMap<String, DoubleArray>()
        for (b in GtaStandardRig.BONES) {
            if (b.name.isEmpty()) continue
            val p = PROPORTIONS[b.name] ?: continue
            out[b.name] = place(p.h, (if (mirror) -p.lat else p.lat))
        }

        // Pose lengan dibaca dari mesh: ujung lateral terluar (tangan) menentukan tinggi dan jarak tangan,
        // jadi pose T, A, atau lengan turun sama-sama terbaca. Offset (tangan = ujung + 0,043 tinggi,
        // - 0,052 lebar) diukur dari 120 DFF; simpangan baku 0,011 untuk tinggi.
        // Arm pose is read from the mesh: the outermost lateral tip (the hand) sets the hand's height and
        // reach, so T pose, A pose and arms-down all read correctly. Offsets measured on 120 DFFs.
        for (side in intArrayOf(+1, -1)) {
            val sign = if (mirror) -side else side
            var sumH = 0.0
            var sumLat = 0.0
            var n = 0
            var v = 0
            while (v < positions.size) {
                val lat = sign * (positions[v + latAxis] - latCenter) / halfWidth
                if (lat >= TIP_BAND) {
                    sumH += (positions[v + upAxis] - mn[upAxis]) / height
                    sumLat += lat
                    n++
                }
                v += 3
            }
            if (n < MIN_TIP_VERTICES) continue
            val tipH = sumH / n
            if (tipH < 0.30 || tipH > 0.95) continue
            val handH = tipH + HAND_DH
            val handLat = (sumLat / n + HAND_DLAT).coerceIn(0.40, 1.0)
            val tag = if (side > 0) " L " else " R "
            val shoulder = PROPORTIONS[tag + "UpperArm"] ?: continue
            val shLat = Math.abs(shoulder.lat)
            val dh = handH - shoulder.h
            val dl = handLat - shLat
            fun at(t: Double) = place(shoulder.h + dh * t, sign * (shLat + dl * t))
            out[tag + "ForeArm"] = at(FOREARM_T)
            out[tag + "Hand"] = at(1.0)
            out[tag + "Finger"] = at(1.0 + FINGER_T)
            out["${if (side > 0) "L" else "R"} Finger01"] = at(1.0 + FINGER01_T)
        }
        return out
    }
}
