package zen.gtasaskinstudio.app.scene

import kotlin.math.abs

/**
 * Pemilihan material dari posisi sentuh (CPU, tanpa API Android, bisa diuji di JVM).
 * Sinar dibuat dari matriks MVP yang dipakai renderer, lalu diuji ke tiap segitiga.
 *
 * Material picking from a touch position (CPU, no Android APIs, JVM-testable). A ray is
 * built from the MVP matrix the renderer uses, then tested against each triangle.
 */
object Picking {

    /** Invers matriks 4x4 (urutan kolom-besar seperti Android Matrix). Null bila singular. / Inverse of a column-major 4x4; null if singular. */
    fun invert(m: FloatArray): FloatArray? {
        // Eliminasi Gauss-Jordan pada matriks tambahan [A | I]. / Gauss-Jordan elimination on [A | I].
        val a = Array(4) { r -> DoubleArray(8) { c -> if (c < 4) m[c * 4 + r].toDouble() else if (c - 4 == r) 1.0 else 0.0 } }
        for (col in 0 until 4) {
            var pivot = col
            for (r in col + 1 until 4) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            if (abs(a[pivot][col]) < 1e-12) return null
            if (pivot != col) {
                val t = a[pivot]; a[pivot] = a[col]; a[col] = t
            }
            val p = a[col][col]
            for (c in 0 until 8) a[col][c] /= p
            for (r in 0 until 4) {
                if (r == col) continue
                val f = a[r][col]
                if (f != 0.0) for (c in 0 until 8) a[r][c] -= f * a[col][c]
            }
        }
        val out = FloatArray(16)
        for (r in 0 until 4) for (c in 0 until 4) out[c * 4 + r] = a[r][c + 4].toFloat()
        return out
    }

    /**
     * Material di bawah titik layar ([nx], [ny] dalam NDC: -1..1, y ke atas), atau null bila tidak mengenai apa pun.
     * Material under the screen point ([nx], [ny] in NDC: -1..1, y up), or null if nothing is hit.
     */
    fun pickMaterial(scene: SceneBuffers, mvp: FloatArray, nx: Float, ny: Float): Int? {
        val inv = invert(mvp) ?: return null
        val near = unproject(inv, nx, ny, -1.0)
        val far = unproject(inv, nx, ny, 1.0)
        val dir = DoubleArray(3) { far[it] - near[it] }

        val v = scene.vertices
        val idx = scene.indices
        var bestT = Double.MAX_VALUE
        var best: Int? = null
        for (draw in scene.draws) {
            val end = minOf(draw.start + draw.count, idx.size)
            var j = draw.start
            while (j + 2 < end) {
                val a = (idx[j].toInt() and 0xFFFF) * 6
                val b = (idx[j + 1].toInt() and 0xFFFF) * 6
                val c = (idx[j + 2].toInt() and 0xFFFF) * 6
                if (a + 2 < v.size && b + 2 < v.size && c + 2 < v.size) {
                    val t = intersect(near, dir, v, a, b, c)
                    if (t != null && t < bestT) {
                        bestT = t
                        best = draw.materialIndex
                    }
                }
                j += 3
            }
        }
        return best
    }

    private fun unproject(inv: FloatArray, nx: Float, ny: Float, nz: Double): DoubleArray {
        val q = mulVec(inv, doubleArrayOf(nx.toDouble(), ny.toDouble(), nz, 1.0))
        val w = if (abs(q[3]) < 1e-12) 1.0 else q[3]
        return doubleArrayOf(q[0] / w, q[1] / w, q[2] / w)
    }

    /** Perkalian matriks-kolom dengan vektor 4 komponen. / Column-major matrix times 4-vector. */
    private fun mulVec(m: FloatArray, v: DoubleArray): DoubleArray {
        val out = DoubleArray(4)
        for (r in 0 until 4) {
            var s = 0.0
            for (c in 0 until 4) s += m[c * 4 + r].toDouble() * v[c]
            out[r] = s
        }
        return out
    }

    /** Möller–Trumbore. Mengembalikan t > 0 di sepanjang sinar, atau null. / Returns t >= 0 along the ray, or null. */
    private fun intersect(o: DoubleArray, d: DoubleArray, v: FloatArray, a: Int, b: Int, c: Int): Double? {
        val e1 = DoubleArray(3) { v[b + it].toDouble() - v[a + it].toDouble() }
        val e2 = DoubleArray(3) { v[c + it].toDouble() - v[a + it].toDouble() }
        val p = cross(d, e2)
        val det = dot(e1, p)
        if (abs(det) < 1e-12) return null
        val inv = 1.0 / det
        val s = DoubleArray(3) { o[it] - v[a + it].toDouble() }
        val u = dot(s, p) * inv
        if (u < 0.0 || u > 1.0) return null
        val q = cross(s, e1)
        val w = dot(d, q) * inv
        if (w < 0.0 || u + w > 1.0) return null
        val t = dot(e2, q) * inv
        return if (t >= 0.0) t else null
    }

    private fun dot(x: DoubleArray, y: DoubleArray): Double = x[0] * y[0] + x[1] * y[1] + x[2] * y[2]

    private fun cross(x: DoubleArray, y: DoubleArray): DoubleArray = doubleArrayOf(
        x[1] * y[2] - x[2] * y[1],
        x[2] * y[0] - x[0] * y[2],
        x[0] * y[1] - x[1] * y[0],
    )
}
