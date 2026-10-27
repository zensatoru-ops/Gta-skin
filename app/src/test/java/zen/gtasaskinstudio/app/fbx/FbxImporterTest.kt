package zen.gtasaskinstudio.app.fbx

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zen.gtasaskinstudio.app.rw.IssueCode
import zen.gtasaskinstudio.app.rw.Severity

/** Penulis FBX biner minimal untuk fixture tes. Offset dihitung absolut seperti berkas asli. */
private typealias NodeBuilder = (Int) -> ByteArray

private object FbxFixture {
    private const val MAGIC = "Kaydara FBX Binary  \u0000"

    fun le(v: Long, n: Int): ByteArray = ByteArray(n) { ((v ushr (8 * it)) and 0xFF).toByte() }
    fun u32(v: Int): ByteArray = le(v.toLong(), 4)

    fun str(s: String): ByteArray {
        val b = s.toByteArray(Charsets.ISO_8859_1)
        return byteArrayOf('S'.code.toByte()) + u32(b.size) + b
    }

    fun longProp(v: Long): ByteArray = byteArrayOf('L'.code.toByte()) + le(v, 8)

    fun dArr(vals: DoubleArray, zlib: Boolean = false): ByteArray {
        val raw = ByteArray(vals.size * 8)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().put(vals)
        return arrayProp('d', vals.size, raw, zlib)
    }

    fun iArr(vals: IntArray): ByteArray {
        val raw = ByteArray(vals.size * 4)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(vals)
        return arrayProp('i', vals.size, raw, false)
    }

    private fun arrayProp(t: Char, count: Int, raw: ByteArray, zlib: Boolean): ByteArray {
        val data: ByteArray
        val enc: Int
        if (zlib) {
            val d = Deflater()
            d.setInput(raw); d.finish()
            val out = ByteArray(raw.size * 2 + 64)
            val n = d.deflate(out)
            d.end()
            data = out.copyOf(n); enc = 1
        } else {
            data = raw; enc = 0
        }
        return byteArrayOf(t.code.toByte()) + u32(count) + u32(enc) + u32(data.size) + data
    }

    /** Node dengan header 13 byte (versi < 7500) atau 25 byte (>= 7500). */
    fun node(name: String, props: List<ByteArray>, children: List<NodeBuilder> = emptyList(), wide: Boolean = false): NodeBuilder = { start ->
        val hdr = if (wide) 25 else 13
        val nameB = name.toByteArray(Charsets.ISO_8859_1)
        val propB = props.fold(ByteArray(0)) { a, b -> a + b }
        var cur = start + hdr + nameB.size + propB.size
        val childB = ByteArrayOutputStream()
        for (c in children) {
            val enc = c(cur)
            childB.write(enc)
            cur += enc.size
        }
        if (children.isNotEmpty()) {
            childB.write(ByteArray(hdr))
            cur += hdr
        }
        val out = ByteArrayOutputStream()
        out.write(if (wide) le(cur.toLong(), 8) else u32(cur))
        out.write(if (wide) le(props.size.toLong(), 8) else u32(props.size))
        out.write(if (wide) le(propB.size.toLong(), 8) else u32(propB.size))
        out.write(nameB.size)
        out.write(nameB)
        out.write(propB)
        out.write(childB.toByteArray())
        out.toByteArray()
    }

    fun file(version: Int, vararg roots: NodeBuilder): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.ISO_8859_1))
        out.write(byteArrayOf(0x1A, 0x00))
        out.write(u32(version))
        var cur = 27
        for (r in roots) {
            val enc = r(cur)
            out.write(enc)
            cur += enc.size
        }
        out.write(ByteArray(if (version >= 7500) 25 else 13))
        return out.toByteArray()
    }

    fun geometry(
        id: Long,
        verts: DoubleArray,
        pvi: IntArray,
        extra: List<NodeBuilder> = emptyList(),
        wide: Boolean = false,
    ): NodeBuilder = node(
        "Geometry",
        listOf(longProp(id), str("Cube\u0000\u0001Geometry"), str("Mesh")),
        listOf(
            node("Vertices", listOf(dArr(verts)), wide = wide),
            node("PolygonVertexIndex", listOf(iArr(pvi)), wide = wide),
        ) + extra,
        wide,
    )

    fun objects(vararg geoms: NodeBuilder, wide: Boolean = false): NodeBuilder =
        node("Objects", emptyList(), geoms.toList(), wide)
}

class FbxImporterTest {

    private val square = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0)
    private val quadPvi = intArrayOf(0, 1, 2, -4) // polygon 4 corner, akhir = -(3)-1

    private fun parse(bytes: ByteArray): FbxParseResult = FbxImporter.parse(bytes)

    private fun ok(bytes: ByteArray): FbxParseResult.Success {
        val r = parse(bytes)
        assertTrue("expected success: ${(r as? FbxParseResult.Failure)?.issues}", r is FbxParseResult.Success)
        return r as FbxParseResult.Success
    }

    @Test fun parsesSingleTriangle() {
        val file = FbxFixture.file(7400, FbxFixture.objects(FbxFixture.geometry(1, square.copyOf(9), intArrayOf(0, 1, -3))))
        val s = ok(file)
        assertEquals(3, s.mesh.vertexCount)
        assertEquals(1, s.mesh.triangleCount)
        assertArrayEquals(intArrayOf(0, 1, 2), s.mesh.triangles)
    }

    @Test fun quadPolygonIsFanTriangulated() {
        val file = FbxFixture.file(7400, FbxFixture.objects(FbxFixture.geometry(1, square, quadPvi)))
        val s = ok(file)
        assertEquals(2, s.mesh.triangleCount)
        assertEquals(4, s.mesh.vertexCount)
    }

    @Test fun uvByPolygonVertexDirectIsReadPerCorner() {
        val uv = FbxFixture.node(
            "LayerElementUV", listOf(FbxFixture.longProp(0)),
            listOf(
                FbxFixture.node("UV", listOf(FbxFixture.dArr(doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)))),
                FbxFixture.node("MappingInformationType", listOf(FbxFixture.str("ByPolygonVertex"))),
                FbxFixture.node("ReferenceInformationType", listOf(FbxFixture.str("Direct"))),
            ),
        )
        val s = ok(FbxFixture.file(7400, FbxFixture.objects(FbxFixture.geometry(1, square, quadPvi, listOf(uv)))))
        val uvs = s.mesh.uvs ?: error("uvs expected")
        assertEquals(8, uvs.size)
        assertEquals(1f, uvs[2], 0f) // corner 1 = (1,0)
    }

    @Test fun uvIndexToDirectUsesIndexArray() {
        val uv = FbxFixture.node(
            "LayerElementUV", listOf(FbxFixture.longProp(0)),
            listOf(
                FbxFixture.node("UV", listOf(FbxFixture.dArr(doubleArrayOf(0.25, 0.5, 0.75, 0.5)))),
                FbxFixture.node("UVIndex", listOf(FbxFixture.iArr(intArrayOf(0, 1, 1, 0)))),
                FbxFixture.node("MappingInformationType", listOf(FbxFixture.str("ByPolygonVertex"))),
                FbxFixture.node("ReferenceInformationType", listOf(FbxFixture.str("IndexToDirect"))),
            ),
        )
        val s = ok(FbxFixture.file(7400, FbxFixture.objects(FbxFixture.geometry(1, square, quadPvi, listOf(uv)))))
        val uvs = s.mesh.uvs ?: error("uvs expected")
        assertEquals(0.75f, uvs[2], 1e-6f) // corner 1 -> UVIndex[1] = 1 -> (0.75, 0.5)
        assertEquals(0.5f, uvs[3], 1e-6f)
    }

    @Test fun normalsByVerticeAreIndexedByVertex() {
        val nrm = FbxFixture.node(
            "LayerElementNormal", listOf(FbxFixture.longProp(0)),
            listOf(
                FbxFixture.node("Normals", listOf(FbxFixture.dArr(DoubleArray(12) { if (it % 3 == 2) 1.0 else 0.0 }))),
                FbxFixture.node("MappingInformationType", listOf(FbxFixture.str("ByVertice"))),
                FbxFixture.node("ReferenceInformationType", listOf(FbxFixture.str("Direct"))),
            ),
        )
        val s = ok(FbxFixture.file(7400, FbxFixture.objects(FbxFixture.geometry(1, square, quadPvi, listOf(nrm)))))
        val n = s.mesh.normals ?: error("normals expected")
        assertEquals(1f, n[2], 0f)
    }

    @Test fun zlibCompressedArrayIsDecoded() {
        val file = FbxFixture.file(
            7400,
            FbxFixture.objects(
                FbxFixture.node(
                    "Geometry",
                    listOf(FbxFixture.longProp(1), FbxFixture.str("C\u0000\u0001Geometry"), FbxFixture.str("Mesh")),
                    listOf(
                        FbxFixture.node("Vertices", listOf(FbxFixture.dArr(square, zlib = true))),
                        FbxFixture.node("PolygonVertexIndex", listOf(FbxFixture.iArr(quadPvi))),
                    ),
                ),
            ),
        )
        assertEquals(2, ok(file).mesh.triangleCount)
    }

    @Test fun wideHeaderVersion7500Parses() {
        val file = FbxFixture.file(7500, FbxFixture.objects(FbxFixture.geometry(1, square.copyOf(9), intArrayOf(0, 1, -3), wide = true), wide = true))
        assertEquals(1, ok(file).mesh.triangleCount)
    }

    @Test fun notBinaryFbxFails() {
        val r = parse("; FBX 7.4.0 project file\n".toByteArray(Charsets.ISO_8859_1))
        assertTrue(r is FbxParseResult.Failure)
    }

    @Test fun missingGeometryFails() {
        val r = parse(FbxFixture.file(7400, FbxFixture.objects()))
        assertTrue(r is FbxParseResult.Failure)
        assertTrue((r as FbxParseResult.Failure).issues.any { it.code == IssueCode.NO_GEOMETRY })
    }

    @Test fun outOfRangeVertexFails() {
        val r = parse(FbxFixture.file(7400, FbxFixture.objects(FbxFixture.geometry(1, square, intArrayOf(0, 1, -99)))))
        assertTrue(r is FbxParseResult.Failure)
    }

    @Test fun multipleMeshesWarnAndUseFirst() {
        val s = ok(
            FbxFixture.file(
                7400,
                FbxFixture.objects(
                    FbxFixture.geometry(1, square.copyOf(9), intArrayOf(0, 1, -3)),
                    FbxFixture.geometry(2, square, quadPvi),
                ),
            ),
        )
        assertEquals(1, s.mesh.triangleCount)
        assertTrue(s.issues.any { it.code == IssueCode.MULTI_GEOMETRY && it.severity == Severity.WARNING })
    }

    @Test fun unsupportedPartsAreReportedAsInfo() {
        val s = ok(FbxFixture.file(7400, FbxFixture.objects(FbxFixture.geometry(1, square.copyOf(9), intArrayOf(0, 1, -3)))))
        assertTrue(s.issues.count { it.code == IssueCode.FBX_PARTIAL && it.severity == Severity.INFO } >= 2)
    }

    @Test fun everyTruncationNeverThrows() {
        val full = FbxFixture.file(7400, FbxFixture.objects(FbxFixture.geometry(1, square, quadPvi)))
        for (cut in 0..full.size) {
            val r = FbxImporter.parse(full.copyOf(cut))
            assertTrue(r is FbxParseResult.Success || r is FbxParseResult.Failure)
        }
    }

    @Test fun randomGarbageAfterMagicNeverThrows() {
        val rnd = java.util.Random(11)
        val header = FbxFixture.file(7400)
        for (k in 0 until 60) {
            val b = header.copyOf(27) + ByteArray(600) { rnd.nextInt(256).toByte() }
            val r = FbxImporter.parse(b)
            assertTrue(r is FbxParseResult.Success || r is FbxParseResult.Failure)
        }
    }
}
