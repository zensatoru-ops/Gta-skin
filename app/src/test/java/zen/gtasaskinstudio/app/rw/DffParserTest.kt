package zen.gtasaskinstudio.app.rw

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DffParserTest {

    private fun success(bytes: ByteArray): DffParseResult.Success {
        val r = DffParser.parse(bytes)
        assertTrue("expected Success but got $r", r is DffParseResult.Success)
        return r as DffParseResult.Success
    }

    private fun failure(bytes: ByteArray): DffParseResult.Failure {
        val r = DffParser.parse(bytes)
        assertTrue("expected Failure but got $r", r is DffParseResult.Failure)
        return r as DffParseResult.Failure
    }

    private fun Iterable<Issue>.has(sev: Severity, code: IssueCode) = any { it.severity == sev && it.code == code }

    @Test fun structSizeHighByteIsRepairedWithWarning() {
        val d = SyntheticDff.build()
        // ukuran struct clump pertama: byte atas = 0x01 (pola KasumiFK / mai_korean)
        // The clump struct size: high byte set to 0x01 (KasumiFK / mai_korean pattern)
        assertEquals(Rw.STRUCT.toByte(), d[12]) // struct pertama di dalam clump / first child of the clump
        d[19] = 1 // byte atas field ukuran: offset 12 + 4 + 3 / high byte of the size field
        val s = success(d)
        assertTrue(s.issues.has(Severity.WARNING, IssueCode.STRUCT_SIZE_HIGH_BYTE))
        assertEquals(1, s.model.geometries.size)
    }

    @Test fun trailingNonChunkBytesAfterClumpAreWarned() {
        val base = SyntheticDff.build()
        val tail = "\r\n/nv/P1dN02CwE==".toByteArray(Charsets.US_ASCII)
        val s = success(base + tail)
        assertTrue(s.issues.has(Severity.WARNING, IssueCode.TRAILING_BYTES))
        assertEquals(1, s.model.geometries.size)
    }

    @Test fun validMinimalDffParses() {
        val s = success(SyntheticDff.build())
        assertEquals(0x36003, s.model.rwVersion)
        assertEquals(1, s.model.frames.size)
        assertEquals(1, s.model.geometries.size)
        val g = s.model.geometries[0]
        assertEquals(3, g.vertexCount)
        assertEquals(1, g.triangleCount)
        assertEquals(1, g.materials.size)
        assertEquals(listOf(0, 1, 2), g.triIndices.toList())
        assertTrue("no errors expected: ${s.issues}", s.issues.none { it.severity == Severity.ERROR })
    }

    @Test fun emptyInputIsNoClump() {
        assertEquals(IssueCode.NO_CLUMP, failure(ByteArray(0)).issues.last().code)
    }

    @Test fun everyTruncationFailsWithoutThrowing() {
        val full = SyntheticDff.build(skinWeights = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f))
        for (len in 0 until full.size) {
            val r = DffParser.parse(full.copyOf(len))
            assertTrue("length $len should fail", r is DffParseResult.Failure)
        }
    }

    @Test fun hugeVertexCountIsRejectedBeforeAllocation() {
        val f = failure(SyntheticDff.build(vertexCountField = 0x7FFFFFFF))
        assertEquals(IssueCode.TOO_MANY_ELEMENTS, f.issues.last().code)
    }

    @Test fun negativeVertexCountIsRejected() {
        val f = failure(SyntheticDff.build(vertexCountField = -5))
        assertEquals(IssueCode.TOO_MANY_ELEMENTS, f.issues.last().code)
    }

    @Test fun nativeGeometryIsReportedNotParsed() {
        val f = failure(SyntheticDff.build(nativeFlag = true))
        assertEquals(IssueCode.NATIVE_GEOMETRY_UNSUPPORTED, f.issues.last().code)
    }

    @Test fun triangleIndexOutOfRangeIsAnError() {
        val s = success(SyntheticDff.build(thirdIndex = 9))
        assertTrue(s.issues.has(Severity.ERROR, IssueCode.TRIANGLE_INDEX_OUT_OF_RANGE))
    }

    @Test fun validSkinHasNoSkinIssues() {
        val s = success(SyntheticDff.build(skinWeights = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)))
        val skin = s.model.geometries[0].skin
        assertNotNull(skin)
        assertEquals(1, skin!!.numBones)
        assertTrue("unexpected: ${s.issues}", s.issues.none { it.code.name.startsWith("SKIN") && it.code != IssueCode.SKIN_BONE_ORDER_UNKNOWN }) // DFF sintetis tanpa HAnim
        val stats = s.skinStats[0]
        assertNotNull(stats)
        assertEquals(0, stats!!.badIndexVertices)
        assertEquals(0, stats.badSumVertices)
    }

    @Test fun weightsNotSummingToOneAreReportedNotRepaired() {
        val w = floatArrayOf(0.5f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val s = success(SyntheticDff.build(skinWeights = w))
        assertTrue(s.issues.has(Severity.WARNING, IssueCode.SKIN_WEIGHT_SUM))
        assertEquals(0.5f, s.model.geometries[0].skin!!.weights[0], 0f)
    }

    @Test fun boneIndexBeyondBoneCountIsAnError() {
        val w = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val s = success(SyntheticDff.build(skinWeights = w, skinBoneIndex = 7))
        assertTrue(s.issues.has(Severity.ERROR, IssueCode.SKIN_BONE_INDEX_OUT_OF_RANGE))
    }

    @Test fun unweightedVerticesAreReported() {
        val w = FloatArray(12)
        val s = success(SyntheticDff.build(skinWeights = w))
        assertTrue(s.issues.has(Severity.WARNING, IssueCode.SKIN_UNWEIGHTED_VERTICES))
    }

    @Test fun oversizedInputIsRejected() {
        val f = failure(ByteArray(Limits.MAX_FILE_BYTES + 1))
        assertEquals(IssueCode.FILE_TOO_LARGE, f.issues.last().code)
    }

    @Test fun randomCorruptionNeverThrows() {
        val base = SyntheticDff.build(skinWeights = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f))
        val rnd = Random(20261025L) // seed tetap agar dapat diulang / fixed seed for repeatability
        repeat(4000) { iteration ->
            val data = base.copyOf()
            val flips = 1 + rnd.nextInt(4)
            repeat(flips) { data[rnd.nextInt(data.size)] = rnd.nextInt(256).toByte() }
            try {
                DffParser.parse(data)
            } catch (t: Throwable) {
                fail("iteration $iteration threw $t")
            }
        }
    }
}
