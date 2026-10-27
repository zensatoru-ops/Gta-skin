package zen.gtasaskinstudio.app.obj

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zen.gtasaskinstudio.app.rw.IssueCode
import zen.gtasaskinstudio.app.rw.Severity

class ObjImporterTest {

    private fun parse(text: String): ObjParseResult = ObjImporter.parse(text.toByteArray(Charsets.ISO_8859_1))

    private fun ok(text: String): ObjParseResult.Success {
        val r = parse(text)
        assertTrue("expected success: $r", r is ObjParseResult.Success)
        return r as ObjParseResult.Success
    }

    private val triangle = """
        v 0 0 0
        v 1 0 0
        v 0 1 0
        f 1 2 3
    """.trimIndent()

    @Test fun parsesSingleTriangle() {
        val s = ok(triangle)
        assertEquals(3, s.mesh.vertexCount)
        assertEquals(1, s.mesh.triangleCount)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), s.mesh.positions, 0f)
        assertArrayEquals(intArrayOf(0, 1, 2), s.mesh.triangles)
        assertNull(s.mesh.uvs)
        assertNull(s.mesh.normals)
        assertEquals("default", s.mesh.materials.single().name)
    }

    @Test fun quadIsTriangulatedAsFan() {
        val s = ok("v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3 4")
        assertEquals(2, s.mesh.triangleCount)
        assertArrayEquals(intArrayOf(0, 1, 2, 0, 2, 3), s.mesh.triangles)
    }

    @Test fun negativeIndicesAreRelativeToCurrentCount() {
        val s = ok("v 0 0 0\nv 1 0 0\nv 0 1 0\nf -3 -2 -1")
        assertArrayEquals(intArrayOf(0, 1, 2), s.mesh.triangles)
    }

    @Test fun uvAndNormalAreParsedPerCorner() {
        val s = ok(
            """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            vt 0 0
            vt 1 0
            vt 0 1
            vn 0 0 1
            f 1/1/1 2/2/1 3/3/1
            """.trimIndent(),
        )
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), s.mesh.uvs!!, 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), s.mesh.normals!!, 0f)
    }

    @Test fun sharedCornerIsNotDuplicated() {
        val s = ok("v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3\nf 1 3 4")
        assertEquals(4, s.mesh.vertexCount)
        assertEquals(2, s.mesh.triangleCount)
    }

    @Test fun usemtlAssignsMaterialPerFace() {
        val s = ok(
            """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            v 1 1 0
            usemtl skin
            f 1 2 3
            usemtl hair
            f 2 4 3
            """.trimIndent(),
        )
        assertArrayEquals(intArrayOf(0, 1), s.mesh.triMaterial)
        assertEquals(listOf("skin", "hair"), s.mesh.materials.map { it.name })
    }

    @Test fun facesBeforeUsemtlGetDefaultMaterial() {
        val s = ok("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\nusemtl x\n")
        assertEquals("default", s.mesh.materials[0].name)
    }

    @Test fun mixedUvPresenceDropsUvWithWarning() {
        val s = ok("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nf 1/1 2 3\nf 1 2 3")
        assertNull(s.mesh.uvs)
        assertTrue(s.issues.any { it.code == IssueCode.OBJ_ATTRIBUTE_PARTIAL && it.detail == "uv" })
    }

    @Test fun outOfRangeFaceIsSkippedWithWarning() {
        val s = ok("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 99\nf 1 2 3")
        assertEquals(1, s.mesh.triangleCount)
        assertTrue(s.issues.any { it.code == IssueCode.OBJ_FACE_INVALID && it.severity == Severity.WARNING })
    }

    @Test fun faceWithTwoCornersIsSkipped() {
        val s = ok("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2\nf 1 2 3")
        assertEquals(1, s.mesh.triangleCount)
        assertTrue(s.issues.any { it.code == IssueCode.OBJ_FACE_INVALID })
    }

    @Test fun nonFiniteVertexIsZeroedWithWarning() {
        val s = ok("v NaN 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3")
        assertEquals(0f, s.mesh.positions[0], 0f)
        assertTrue(s.issues.any { it.code == IssueCode.NON_FINITE_VALUE })
    }

    @Test fun malformedVertexKeepsIndicesAligned() {
        // Baris v rusak tetap menghasilkan entri, sehingga f 3 masih menunjuk titik ketiga.
        // A malformed v line still adds an entry, so f 3 still points at the third vertex.
        val s = ok("v 0 0\nv 1 0 0\nv 0 1 0\nf 2 3 3")
        assertEquals(2, s.mesh.vertexCount)
        assertEquals(1f, s.mesh.positions[0], 0f)
        assertTrue(s.issues.any { it.code == IssueCode.UNEXPECTED_STRUCTURE })
    }

    @Test fun mtllibIsReportedAsInfo() {
        val s = ok("mtllib model.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3")
        assertTrue(s.issues.any { it.code == IssueCode.OBJ_DIRECTIVE_IGNORED && it.severity == Severity.INFO })
    }

    @Test fun crlfLineEndingsAreAccepted() {
        val s = ok("v 0 0 0\r\nv 1 0 0\r\nv 0 1 0\r\nf 1 2 3\r\n")
        assertEquals(1, s.mesh.triangleCount)
    }

    @Test fun emptyFileFails() {
        assertTrue(parse("") is ObjParseResult.Failure)
    }

    @Test fun fileWithoutFacesFails() {
        val r = parse("v 0 0 0\nv 1 0 0\n")
        assertTrue(r is ObjParseResult.Failure)
    }

    @Test fun tooManyVerticesFails() {
        val sb = StringBuilder()
        for (i in 0 until 70_000) sb.append("v ").append(i).append(" 0 0\n")
        sb.append("f")
        for (i in 1..70_000) sb.append(' ').append(i)
        sb.append('\n')
        val r = parse(sb.toString())
        assertTrue(r is ObjParseResult.Failure)
        assertTrue((r as ObjParseResult.Failure).issues.any { it.code == IssueCode.TOO_MANY_ELEMENTS })
    }

    @Test fun truncatedInputNeverThrows() {
        val full = "v 0 0 0\nvt 0 0\nvn 0 0 1\nusemtl a\nf 1/1/1 2/1/1 3/1/1\n".toByteArray(Charsets.ISO_8859_1)
        for (cut in 0..full.size) {
            val r = ObjImporter.parse(full.copyOf(cut))
            assertTrue(r is ObjParseResult.Success || r is ObjParseResult.Failure)
        }
    }

    @Test fun binaryGarbageNeverThrows() {
        val rnd = java.util.Random(7)
        for (k in 0 until 50) {
            val b = ByteArray(2000) { rnd.nextInt(256).toByte() }
            val r = ObjImporter.parse(b)
            assertTrue(r is ObjParseResult.Success || r is ObjParseResult.Failure)
        }
    }
}
