package zen.gtasaskinstudio.app.rig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zen.gtasaskinstudio.app.scene.SceneBuffers
import zen.gtasaskinstudio.app.scene.withSkeleton

class AutoRigTest {

    /** Kotak Z-up 0.6 x 0.2 x 1.8, dalam format interleaved stride 6. / Z-up box, interleaved stride 6. */
    private fun interleavedBox(): FloatArray {
        val out = ArrayList<Float>()
        for (x in listOf(-0.3f, 0.3f)) for (y in listOf(-0.1f, 0.1f)) for (z in listOf(0f, 1.8f)) {
            out.add(x); out.add(y); out.add(z); out.add(0f); out.add(0f); out.add(1f)
        }
        return out.toFloatArray()
    }

    @Test fun resultCoversEveryNamedTemplateBoneInOrder() {
        val r = AutoRig.fromInterleaved(interleavedBox())
        assertNotNull(r)
        r!!
        assertEquals(HumanoidFitter.Up.Z, r.up)
        val expected = GtaStandardRig.BONES.filter { it.name.isNotEmpty() }.map { it.name }
        assertEquals(expected, r.joints.map { it.boneName })
    }

    @Test fun stridedInputIgnoresNormals() {
        val r = AutoRig.fromInterleaved(interleavedBox())!!
        val head = r.joints.first { it.boneName == " Head" }
        assertTrue(head.z > 1.0)
        assertEquals(0.0, head.x, 1e-9)
    }

    @Test fun tooFewVerticesReturnsNull() {
        assertNull(AutoRig.fromInterleaved(FloatArray(12)))
        assertNull(AutoRig.fromInterleaved(FloatArray(0)))
    }

    @Test fun geometryHasOnePointPerBoneAndOneLinePerBonedParent() {
        val g = AutoRig.geometry(AutoRig.fromInterleaved(interleavedBox())!!)
        val named = GtaStandardRig.BONES.filter { it.name.isNotEmpty() }
        assertEquals(named.size * 3, g.points.size)
        // Root (parent = kontainer) tidak punya garis. / Root (parent = container) has no line.
        assertEquals((named.size - 1) * 6, g.lines.size)
    }

    @Test fun lineEndpointsAreParentThenChild() {
        val r = AutoRig.fromInterleaved(interleavedBox())!!
        val g = AutoRig.geometry(r)
        val head = r.joints.first { it.boneName == " Head" }
        val neck = r.joints.first { it.boneName == " Neck" }
        // Brow/Jaw memakai posisi Head sebagai fallback, jadi cari garis Neck -> Head secara khusus.
        // Brow/Jaw fall back to the Head position, so look specifically for the Neck -> Head line.
        var found = false
        var i = 0
        while (i < g.lines.size) {
            val parentZ = g.lines[i + 2].toDouble()
            val childZ = g.lines[i + 5].toDouble()
            if (Math.abs(parentZ - neck.z) < 1e-4 && Math.abs(childZ - head.z) < 1e-4) found = true
            i += 6
        }
        assertTrue(found)
    }

    @Test fun withSkeletonKeepsMeshAndReplacesSkeleton() {
        val verts = interleavedBox()
        val scene = SceneBuffers(
            vertices = verts, vertexCount = verts.size / 6, indices = ShortArray(0), draws = emptyList(),
            wireIndices = ShortArray(0), skeletonLines = FloatArray(0), center = floatArrayOf(0f, 0f, 0f),
            radius = 1f, materialNames = emptyList(), framePositions = FloatArray(0),
        )
        val g = AutoRig.geometry(AutoRig.fromInterleaved(verts)!!)
        val rigged = scene.withSkeleton(g.lines, g.points)
        assertTrue(rigged.vertices === scene.vertices)
        assertEquals(g.lines.size, rigged.skeletonLines.size)
        assertEquals(g.points.size, rigged.framePositions.size)
        assertTrue(scene.framePositions.isEmpty())
    }
}

