package zen.gtasaskinstudio.app.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickingTest {

    /** Vertex interleaved: px, py, pz, nx, ny, nz. / Interleaved vertex: px, py, pz, nx, ny, nz. */
    private fun v(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z, 0f, 0f, 1f)

    private fun scene(vertices: FloatArray, indices: ShortArray, draws: List<MeshDraw>) = SceneBuffers(
        vertices = vertices,
        vertexCount = vertices.size / 6,
        indices = indices,
        draws = draws,
        wireIndices = ShortArray(0),
        skeletonLines = FloatArray(0),
        center = FloatArray(3),
        radius = 1f,
        materialNames = listOf("a", "b", "c"),
        framePositions = FloatArray(0),
    )

    /** MVP identitas: sinar dari NDC (0,0) berjalan sepanjang +Z. / Identity MVP: the ray at NDC (0,0) runs along +Z. */
    private val identity = FloatArray(16) { if (it % 5 == 0) 1f else 0f }

    @Test fun centerRayHitsTriangleAndReturnsItsMaterial() {
        val verts = v(-1f, -1f, 0f) + v(1f, -1f, 0f) + v(0f, 1f, 0f)
        val s = scene(verts, shortArrayOf(0, 1, 2), listOf(MeshDraw(2, 0, 3)))
        assertEquals(2, Picking.pickMaterial(s, identity, 0f, 0f))
    }

    @Test fun nearestTriangleAlongTheRayWins() {
        // Material 0 di z=-0.5 lebih dekat ke kamera daripada material 1 di z=0. / Material 0 at z=-0.5 is nearer than material 1 at z=0.
        val front = v(-1f, -1f, -0.5f) + v(1f, -1f, -0.5f) + v(0f, 1f, -0.5f)
        val back = v(-1f, -1f, 0f) + v(1f, -1f, 0f) + v(0f, 1f, 0f)
        val s = scene(front + back, shortArrayOf(0, 1, 2, 3, 4, 5), listOf(MeshDraw(0, 0, 3), MeshDraw(1, 3, 3)))
        assertEquals(0, Picking.pickMaterial(s, identity, 0f, 0f))
    }

    @Test fun pointOutsideTheTriangleMisses() {
        val verts = v(-1f, -1f, 0f) + v(1f, -1f, 0f) + v(0f, 1f, 0f)
        val s = scene(verts, shortArrayOf(0, 1, 2), listOf(MeshDraw(0, 0, 3)))
        assertTrue(Picking.pickMaterial(s, identity, 0.9f, 0.9f) == null)
    }

    @Test fun singularMatrixReturnsNull() {
        val verts = v(-1f, -1f, 0f) + v(1f, -1f, 0f) + v(0f, 1f, 0f)
        val s = scene(verts, shortArrayOf(0, 1, 2), listOf(MeshDraw(0, 0, 3)))
        assertTrue(Picking.pickMaterial(s, FloatArray(16), 0f, 0f) == null)
    }

    @Test fun invertOfIdentityIsIdentity() {
        val inv = Picking.invert(identity)!!
        for (i in 0 until 16) assertEquals(identity[i], inv[i], 1e-6f)
    }
}
