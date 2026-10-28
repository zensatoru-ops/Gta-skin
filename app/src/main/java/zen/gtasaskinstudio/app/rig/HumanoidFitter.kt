package zen.gtasaskinstudio.app.rig

/**
 * Estimasi posisi sendi humanoid dari bounding box mesh. Hasilnya ESTIMASI, bukan rig
 * terverifikasi: dipakai sebagai titik awal yang bisa dikoreksi manual.
 * Pure Kotlin, JVM-testable.
 *
 * Estimates humanoid joint positions from the mesh bounding box. This is an ESTIMATE, not a
 * verified rig: it is a starting point that can be corrected manually. Pure Kotlin, JVM-testable.
 *
 * Konvensi: [up] menentukan sumbu tinggi. Sisi "L" berada di +X dari pusat, "R" di -X.
 * Convention: [up] picks the height axis. "L" side is at +X from center, "R" at -X.
 */
object HumanoidFitter {

    enum class Up { X, Y, Z }

    /** Proporsi tinggi (0 = telapak kaki, 1 = puncak kepala); perkiraan proporsi humanoid, bukan data GTA. / Height fractions (0 = sole, 1 = top); approximate human proportions, not GTA data. */
    private val HEIGHT_FRACTION = mapOf(
        "Hip" to 0.52, "Spine" to 0.60, "Spine1" to 0.68, "Neck" to 0.82, "Head" to 0.88,
        "Knee" to 0.28, "Ankle" to 0.04,
        "Shoulder" to 0.80, "Elbow" to 0.62, "Wrist" to 0.52,
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
     * Bone yang tidak dicakup estimasi ini (jari, alis, dada, rahang) diisi posisi induknya.
     */
    fun estimate(positions: FloatArray, up: Up): Map<String, DoubleArray>? {
        if (positions.size < 9 || positions.size % 3 != 0) return null
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            val x = positions[i].toDouble(); val y = positions[i + 1].toDouble(); val z = positions[i + 2].toDouble()
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        // Sumbu tinggi, sumbu lebar (X dari L/R), sumbu kedalaman. / Height, width (L/R) and depth axes.
        val (lo, hi) = when (up) {
            Up.X -> minX to maxX
            Up.Y -> minY to maxY
            Up.Z -> minZ to maxZ
        }
        val height = hi - lo
        if (height <= 1e-9) return null
        val cx = (minX + maxX) / 2.0
        val halfWidth = (maxX - minX) / 2.0
        val cy = (minY + maxY) / 2.0
        val cz = (minZ + maxZ) / 2.0

        fun at(frac: Double, side: Double, depth: Double): DoubleArray {
            val h = lo + frac * height
            return when (up) {
                Up.X -> doubleArrayOf(h, cy + depth, cz + side)
                Up.Y -> doubleArrayOf(cx + side, h, cz + depth)
                Up.Z -> doubleArrayOf(cx + side, cy + depth, h)
            }
        }

        val shoulder = 0.18 * halfWidth * 2.0
        val hip = 0.10 * halfWidth * 2.0
        val out = HashMap<String, DoubleArray>()
        out[" Pelvis"] = at(HEIGHT_FRACTION["Hip"]!!, 0.0, 0.0)
        out[" Spine"] = at(HEIGHT_FRACTION["Spine"]!!, 0.0, 0.0)
        out[" Spine1"] = at(HEIGHT_FRACTION["Spine1"]!!, 0.0, 0.0)
        out[" Neck"] = at(HEIGHT_FRACTION["Neck"]!!, 0.0, 0.0)
        out[" Head"] = at(HEIGHT_FRACTION["Head"]!!, 0.0, 0.0)
        out[" L Thigh"] = at(HEIGHT_FRACTION["Hip"]!!, +hip, 0.0)
        out[" R Thigh"] = at(HEIGHT_FRACTION["Hip"]!!, -hip, 0.0)
        out[" L Calf"] = at(HEIGHT_FRACTION["Knee"]!!, +hip, 0.0)
        out[" R Calf"] = at(HEIGHT_FRACTION["Knee"]!!, -hip, 0.0)
        out[" L Foot"] = at(HEIGHT_FRACTION["Ankle"]!!, +hip, 0.0)
        out[" R Foot"] = at(HEIGHT_FRACTION["Ankle"]!!, -hip, 0.0)
        out["Bip01 L Clavicle"] = at(HEIGHT_FRACTION["Neck"]!!, +shoulder * 0.5, 0.0)
        out["Bip01 R Clavicle"] = at(HEIGHT_FRACTION["Neck"]!!, -shoulder * 0.5, 0.0)
        out[" L UpperArm"] = at(HEIGHT_FRACTION["Shoulder"]!!, +shoulder, 0.0)
        out[" R UpperArm"] = at(HEIGHT_FRACTION["Shoulder"]!!, -shoulder, 0.0)
        out[" L ForeArm"] = at(HEIGHT_FRACTION["Elbow"]!!, +shoulder * 1.4, 0.0)
        out[" R ForeArm"] = at(HEIGHT_FRACTION["Elbow"]!!, -shoulder * 1.4, 0.0)
        out[" L Hand"] = at(HEIGHT_FRACTION["Wrist"]!!, +shoulder * 1.7, 0.0)
        out[" R Hand"] = at(HEIGHT_FRACTION["Wrist"]!!, -shoulder * 1.7, 0.0)

        // Root dan bone tanpa estimasi: posisi induknya. / Root and uncovered bones: the parent's position.
        for (b in GtaStandardRig.BONES) {
            if (b.name.isEmpty()) continue
            if (out.containsKey(b.name)) continue
            val parentName = GtaStandardRig.BONES.getOrNull(b.parent)?.name
            out[b.name] = out[parentName] ?: at(0.0, 0.0, 0.0)
        }
        return out
    }
}
