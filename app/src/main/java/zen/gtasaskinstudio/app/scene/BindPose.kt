package zen.gtasaskinstudio.app.scene

import zen.gtasaskinstudio.app.rw.DffModel
import zen.gtasaskinstudio.app.rw.Skin

/**
 * Pose bind dari Skin PLG. Matriks inverse-bone menyimpan pose yang cocok dengan
 * mesh; hierarki frame hanyalah pose istirahat dan bisa berbeda skala/rotasi
 * (terbukti pada dua sampel). Viewer memakai pose bind agar tulang pas dengan mesh.
 *
 * Bind pose from the Skin PLG. The inverse-bone matrices hold the pose that
 * matches the mesh; the frame hierarchy is only a rest pose and may differ in
 * scale/orientation (seen on both samples). The viewer uses the bind pose so
 * bones line up with the mesh.
 */
object BindPose {

    /**
     * bone index (urutan HAnim) -> index frame, atau -1 bila tidak ketemu.
     * Mengembalikan null bila urutan HAnim tidak tersedia / tidak cocok dengan skin.
     */
    fun boneFrames(model: DffModel, skin: Skin): IntArray? {
        val root = model.frames.firstOrNull { it.hanimNodeIds.isNotEmpty() } ?: return null
        if (root.hanimNodeIds.size != skin.numBones) return null
        val byId = HashMap<Int, Int>()
        for (f in model.frames) f.boneId?.let { byId.putIfAbsent(it, f.index) }
        return IntArray(skin.numBones) { byId[root.hanimNodeIds[it]] ?: -1 }
    }

    /**
     * Posisi asal tiap bone di ruang mesh = -t * R^T dari matriks inverse-bone
     * (vektor baris; baris = right, up, at, pos). Asumsi rigid; skala/shear diabaikan.
     * Hasil: 3 float per bone.
     */
    fun bonePositions(skin: Skin): FloatArray {
        val q = skin.inverseMatrices
        val out = FloatArray(skin.numBones * 3)
        for (b in 0 until skin.numBones) {
            val o = b * 16
            val t0 = q[o + 12]; val t1 = q[o + 13]; val t2 = q[o + 14]
            for (c in 0 until 3) {
                out[b * 3 + c] = -(t0 * q[o + c * 4] + t1 * q[o + c * 4 + 1] + t2 * q[o + c * 4 + 2])
            }
        }
        return out
    }

    /** Matriks dunia frame (rotasi 3x3 baris-utama + posisi), vektor baris. */
    class World(val rot: FloatArray, val pos: FloatArray) {
        fun point(x: Float, y: Float, z: Float, out: FloatArray, o: Int) {
            for (c in 0 until 3) out[o + c] = x * rot[c] + y * rot[3 + c] + z * rot[6 + c] + pos[c]
        }
    }

    val IDENTITY get() = World(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), floatArrayOf(0f, 0f, 0f))

    fun frameWorld(model: DffModel, index: Int): World {
        if (index !in model.frames.indices) return IDENTITY
        val chain = ArrayList<Int>()
        var c = index
        while (c in model.frames.indices && chain.size <= model.frames.size && c !in chain) {
            chain.add(c)
            c = model.frames[c].parent
        }
        var w = IDENTITY
        for (i in chain.reversed()) {
            val l = model.frames[i].rotation
            val p = model.frames[i].position
            val nr = FloatArray(9)
            for (r in 0 until 3) for (k in 0 until 3) {
                nr[r * 3 + k] = l[r * 3] * w.rot[k] + l[r * 3 + 1] * w.rot[3 + k] + l[r * 3 + 2] * w.rot[6 + k]
            }
            val np = FloatArray(3)
            w.point(p[0], p[1], p[2], np, 0)
            w = World(nr, np)
        }
        return w
    }

    /** Jarak terbesar antara pose bind (di ruang dunia) dan pose frame, untuk laporan. */
    fun maxRestDeviation(model: DffModel, skin: Skin, atomic: World): Float {
        val map = boneFrames(model, skin) ?: return 0f
        val bind = bonePositions(skin)
        val frameWorld = SceneBuilder.frameWorldPositions(model.frames)
        val tmp = FloatArray(3)
        var worst = 0f
        for (b in map.indices) {
            val f = map[b]
            if (f < 0) continue
            atomic.point(bind[b * 3], bind[b * 3 + 1], bind[b * 3 + 2], tmp, 0)
            var d2 = 0f
            for (k in 0 until 3) { val d = tmp[k] - frameWorld[f * 3 + k]; d2 += d * d }
            val d = kotlin.math.sqrt(d2)
            if (d > worst) worst = d
        }
        return worst
    }
}
