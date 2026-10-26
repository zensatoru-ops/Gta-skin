package zen.gtasaskinstudio.app.scene

import zen.gtasaskinstudio.app.rw.Frame

/** Satu baris pohon frame: indeks frame dan kedalamannya. */
class FrameRow(val frameIndex: Int, val depth: Int)

object FrameTree {
    /**
     * Urutan DFS (induk sebelum anak, saudara menurut indeks). Frame yang
     * tak terjangkau dari akar manapun (siklus / induk tak valid) tetap
     * ditampilkan di akhir pada kedalaman 0 supaya tidak ada yang hilang.
     */
    fun flatten(frames: List<Frame>): List<FrameRow> {
        val n = frames.size
        val children = Array(n) { ArrayList<Int>() }
        val roots = ArrayList<Int>()
        for (f in frames) {
            if (f.parent in 0 until n && f.parent != f.index) children[f.parent].add(f.index) else roots.add(f.index)
        }
        val out = ArrayList<FrameRow>(n)
        val seen = BooleanArray(n)
        val stack = ArrayList<FrameRow>()
        for (r in roots.reversed()) stack.add(FrameRow(r, 0))
        while (stack.isNotEmpty()) {
            val row = stack.removeAt(stack.size - 1)
            if (seen[row.frameIndex]) continue
            seen[row.frameIndex] = true
            out.add(row)
            for (c in children[row.frameIndex].reversed()) stack.add(FrameRow(c, row.depth + 1))
        }
        for (i in 0 until n) if (!seen[i]) out.add(FrameRow(i, 0))
        return out
    }
}
