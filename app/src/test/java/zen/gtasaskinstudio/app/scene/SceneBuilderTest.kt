package zen.gtasaskinstudio.app.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zen.gtasaskinstudio.app.rw.DffParseResult
import zen.gtasaskinstudio.app.rw.DffParser
import zen.gtasaskinstudio.app.rw.Frame
import zen.gtasaskinstudio.app.rw.SyntheticDff

class SceneBuilderTest {

    private fun frame(i: Int, parent: Int, x: Float, y: Float, z: Float) =
        Frame(i, parent, floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), floatArrayOf(x, y, z), 0)

    @Test fun buildsOneDrawForOneTriangle() {
        val r = DffParser.parse(SyntheticDff.build()) as DffParseResult.Success
        val s = SceneBuilder.build(r.model)
        assertEquals(3, s.vertexCount)
        assertEquals(18, s.vertices.size)
        assertEquals(3, s.indices.size)
        assertEquals(6, s.wireIndices.size)
        assertEquals(1, s.draws.size)
        assertEquals(0, s.draws[0].start)
        assertEquals(3, s.draws[0].count)
        assertTrue(s.radius > 0f)
    }

    @Test fun worldPositionsAccumulateAlongParents() {
        val frames = listOf(frame(0, -1, 1f, 0f, 0f), frame(1, 0, 0f, 1f, 0f), frame(2, 1, 0f, 0f, 1f))
        val p = SceneBuilder.frameWorldPositions(frames)
        assertEquals(listOf(1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f), p.toList())
    }

    @Test fun worldPositionsRespectParentRotation() {
        // Induk memutar 90 derajat di sumbu Z (baris: right=(0,1,0), up=(-1,0,0), at=(0,0,1)).
        val rotated = Frame(0, -1, floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f), floatArrayOf(0f, 0f, 0f), 0)
        val child = frame(1, 0, 1f, 0f, 0f)
        val p = SceneBuilder.frameWorldPositions(listOf(rotated, child))
        assertEquals(0f, p[3], 1e-6f)
        assertEquals(1f, p[4], 1e-6f)
        assertEquals(0f, p[5], 1e-6f)
    }

    @Test fun cyclicParentsDoNotHang() {
        val frames = listOf(frame(0, 1, 1f, 0f, 0f), frame(1, 0, 0f, 1f, 0f))
        val p = SceneBuilder.frameWorldPositions(frames)
        assertEquals(6, p.size)
    }

    @Test fun frameTreeKeepsEveryFrameEvenWithCycles() {
        val frames = listOf(frame(0, -1, 0f, 0f, 0f), frame(1, 0, 0f, 0f, 0f), frame(2, 3, 0f, 0f, 0f), frame(3, 2, 0f, 0f, 0f))
        val rows = FrameTree.flatten(frames)
        assertEquals(4, rows.size)
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.frameIndex }.sorted())
        assertEquals(1, rows.first { it.frameIndex == 1 }.depth)
    }

    @Test fun modelLoaderBlocksSceneWhenValidationHasErrors() {
        val bad = SyntheticDff.build(thirdIndex = 9)
        val o = ModelLoader.load("x.dff", bad.size.toLong(), bad) as LoadOutcome.Loaded
        assertEquals(null, o.value.scene)
        val good = SyntheticDff.build()
        val g = ModelLoader.load("x.dff", good.size.toLong(), good) as LoadOutcome.Loaded
        assertTrue(g.value.scene != null)
    }
}
