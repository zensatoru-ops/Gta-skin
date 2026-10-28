package zen.gtasaskinstudio.app.rig

/** Satu sendi hasil estimasi. / One estimated joint. */
data class AutoRigJoint(val boneName: String, val x: Double, val y: Double, val z: Double)

/** Hasil auto-rig: sumbu tinggi yang dipakai dan sendi per bone template. / Auto-rig result: height axis used and joints per template bone. */
data class AutoRigResult(val up: HumanoidFitter.Up, val joints: List<AutoRigJoint>)

/**
 * Pintu masuk auto-rig untuk UI. Hanya estimasi dari bounding box; hasilnya harus dikoreksi manual.
 * Entry point for the UI. Bounding-box estimate only; the result must be corrected manually.
 */
object AutoRig {

    /** [vertices] interleaved px,py,pz,nx,ny,nz (stride 6), seperti SceneBuffers. / Interleaved as in SceneBuffers. */
    fun fromInterleaved(vertices: FloatArray, stride: Int = 6): AutoRigResult? {
        if (stride < 3) return null
        val count = vertices.size / stride
        if (count < 3) return null
        val pos = FloatArray(count * 3)
        for (v in 0 until count) {
            pos[v * 3] = vertices[v * stride]
            pos[v * 3 + 1] = vertices[v * stride + 1]
            pos[v * 3 + 2] = vertices[v * stride + 2]
        }
        val up = HumanoidFitter.detectUp(pos) ?: return null
        val joints = HumanoidFitter.estimate(pos, up) ?: return null
        val list = GtaStandardRig.BONES.filter { it.name.isNotEmpty() }.mapNotNull { b ->
            val p = joints[b.name] ?: return@mapNotNull null
            AutoRigJoint(b.name, p[0], p[1], p[2])
        }
        return AutoRigResult(up, list)
    }

    /**
     * Garis dan titik skeleton dari hasil estimasi, mengikuti hierarki [GtaStandardRig]: satu garis per bone
     * yang punya induk bertulang (frame 0 hanyalah kontainer, tanpa posisi).
     * Skeleton lines and points from an estimate, following the [GtaStandardRig] hierarchy: one line per bone
     * with a real parent (frame 0 is only the container and has no position).
     */
    fun geometry(result: AutoRigResult): AutoRigGeometry {
        val byName = result.joints.associateBy { it.boneName }
        val lines = ArrayList<Float>()
        val points = ArrayList<Float>()
        for (b in GtaStandardRig.BONES) {
            if (b.name.isEmpty()) continue
            val j = byName[b.name] ?: continue
            points.add(j.x.toFloat()); points.add(j.y.toFloat()); points.add(j.z.toFloat())
            val parent = GtaStandardRig.BONES.getOrNull(b.parent) ?: continue
            if (parent.name.isEmpty()) continue
            val pj = byName[parent.name] ?: continue
            lines.add(pj.x.toFloat()); lines.add(pj.y.toFloat()); lines.add(pj.z.toFloat())
            lines.add(j.x.toFloat()); lines.add(j.y.toFloat()); lines.add(j.z.toFloat())
        }
        return AutoRigGeometry(lines.toFloatArray(), points.toFloatArray())
    }
}

/** Garis (pasangan titik) dan titik sendi siap pakai untuk renderer. / Line pairs and joint points ready for the renderer. */
class AutoRigGeometry(val lines: FloatArray, val points: FloatArray)

