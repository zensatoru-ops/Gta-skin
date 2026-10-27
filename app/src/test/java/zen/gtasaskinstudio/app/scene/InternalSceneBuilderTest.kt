package zen.gtasaskinstudio.app.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zen.gtasaskinstudio.app.model.InternalMaterial
import zen.gtasaskinstudio.app.model.InternalMesh
import zen.gtasaskinstudio.app.obj.ObjImporter
import zen.gtasaskinstudio.app.obj.ObjParseResult

class InternalSceneBuilderTest {

    private fun meshOf(text: String): InternalMesh {
        val r = ObjImporter.parse(text.toByteArray(Charsets.ISO_8859_1))
        assertTrue(r is ObjParseResult.Success)
        return (r as ObjParseResult.Success).mesh
    }

    @Test fun flatTriangleGetsUnitNormalAlongZ() {
        val m = meshOf("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3")
        val n = InternalSceneBuilder.computeNormals(m)
        for (i in 0 until 3) {
            assertEquals(0f, n[i * 3], 1e-6f)
            assertEquals(0f, n[i * 3 + 1], 1e-6f)
            assertEquals(1f, Math.abs(n[i * 3 + 2]), 1e-6f)
        }
    }

    @Test fun degenerateNormalFallsBackToZ() {
        val m = meshOf("v 0 0 0\nv 1 0 0\nv 2 0 0\nf 1 2 3")
        val n = InternalSceneBuilder.computeNormals(m)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), n.copyOfRange(0, 3), 1e-6f)
    }

    @Test fun indicesAreGroupedByMaterialWithDrawRanges() {
        val m = meshOf(
            """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            v 1 1 0
            usemtl a
            f 1 2 3
            usemtl b
            f 2 4 3
            f 1 2 4
            """.trimIndent(),
        )
        val s = InternalSceneBuilder.build(m)
        assertEquals(4, s.vertexCount)
        assertEquals(2, s.draws.size)
        assertEquals(0, s.draws[0].materialIndex)
        assertEquals(0, s.draws[0].start)
        assertEquals(3, s.draws[0].count)
        assertEquals(1, s.draws[1].materialIndex)
        assertEquals(3, s.draws[1].start)
        assertEquals(6, s.draws[1].count)
        assertEquals(9, s.indices.size)
    }

    @Test fun boundsCenterAndRadiusCoverGeometry() {
        val s = InternalSceneBuilder.build(meshOf("v 0 0 0\nv 2 0 0\nv 0 2 0\nf 1 2 3"))
        assertArrayEquals(floatArrayOf(1f, 1f, 0f), s.center, 1e-6f)
        assertEquals(Math.sqrt(8.0) / 2, s.radius.toDouble(), 1e-5)
    }

    @Test fun wireframeHasThreeEdgesPerTriangle() {
        val s = InternalSceneBuilder.build(meshOf("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3"))
        assertEquals(6, s.wireIndices.size)
        assertArrayEquals(shortArrayOf(0, 1, 1, 2, 2, 0), s.wireIndices)
    }

    @Test fun textureNamesComeFromMaterialsNotObjNames() {
        val m = InternalMesh(
            positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            normals = null,
            uvs = null,
            triangles = intArrayOf(0, 1, 2),
            triMaterial = intArrayOf(0),
            materials = listOf(InternalMaterial("skin_obj_name", "Alice_skin")),
        )
        val s = InternalSceneBuilder.build(m)
        assertEquals(listOf<String?>("Alice_skin"), s.materialNames)
        assertEquals(6, s.uvs.size) // tanpa UV: 2 float per vertex berisi nol / no UV: 2 zero floats per vertex
        assertTrue(s.skeletonLines.isEmpty())
        assertTrue(s.framePositions.isEmpty())
    }
}
