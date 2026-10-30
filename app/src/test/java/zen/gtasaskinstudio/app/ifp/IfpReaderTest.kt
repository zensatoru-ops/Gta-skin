package zen.gtasaskinstudio.app.ifp

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zen.gtasaskinstudio.app.rig.GtaStandardRig

class IfpReaderTest {

    private fun ByteArrayOutputStream.u32(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.name(s: String) {
        val b = s.toByteArray(Charsets.ISO_8859_1)
        write(b); repeat(24 - b.size) { write(0) }
    }

    /** IFP sintetis: satu animasi, satu objek per entri [objects] (nama, id), 2 keyframe tiap objek. */
    private fun ifp(objects: List<Pair<String, Int>>, frameType: Int = 3, animCount: Int = 1): ByteArray {
        val o = ByteArrayOutputStream()
        o.write("ANP3".toByteArray()); o.u32(0); o.name("test"); o.u32(animCount)
        repeat(animCount) {
            o.name("anim"); o.u32(objects.size); o.u32(0); o.u32(1)
            for ((n, id) in objects) {
                o.name(n); o.u32(frameType); o.u32(2); o.u32(id)
                val fb = if (frameType == 4) 16 else 10
                repeat(2 * fb) { o.write(0) }
            }
        }
        return o.toByteArray()
    }

    private fun templateObjects() = GtaStandardRig.WEIGHTED.map { it.name to it.hanimId!! }

    @Test fun parsesSyntheticFile() {
        val r = IfpReader.parse(ifp(templateObjects())) as IfpResult.Success
        assertEquals(1, r.file.animations.size)
        assertEquals(32, r.file.animations[0].sequences.size)
        assertEquals("anim", r.file.animations[0].name)
    }

    @Test fun rootFramesWithTranslationAreSkippedCorrectly() {
        val r = IfpReader.parse(ifp(listOf("Root" to 0, " Pelvis" to 1), frameType = 4, animCount = 2)) as IfpResult.Success
        assertEquals(2, r.file.animations.size)
        assertEquals(2, r.file.animations[1].sequences.size)
    }

    @Test fun fullTemplateMatchesExactly() {
        val r = IfpReader.parse(ifp(templateObjects())) as IfpResult.Success
        val report = IfpRigCheck.check(r.file)
        assertTrue(report.allMatch)
        assertTrue(report.unknownIds.isEmpty())
    }

    @Test fun missingBoneIsReported() {
        val objs = templateObjects().filter { it.second != 8 }
        val report = IfpRigCheck.check((IfpReader.parse(ifp(objs)) as IfpResult.Success).file)
        assertFalse(report.allMatch)
        assertEquals(listOf("Jaw"), report.missing.map { it.name })
    }

    @Test fun sameIdWithDifferentNameIsNotExact() {
        // "Pelvis" tanpa spasi awal tidak sama dengan " Pelvis". / "Pelvis" without the leading space is not " Pelvis".
        val objs = templateObjects().map { if (it.second == 1) "Pelvis" to 1 else it }
        val report = IfpRigCheck.check((IfpReader.parse(ifp(objs)) as IfpResult.Success).file)
        val pelvis = report.bones.first { it.hanimId == 1 }
        assertEquals(0, pelvis.exact)
        assertEquals(1, pelvis.sameIdOtherName)
    }

    @Test fun unknownIdsAreListedButMarkersAreIgnored() {
        val objs = templateObjects() + listOf("Extra" to 999, "Marker" to -1, " L Finger0Nub" to 304)
        val report = IfpRigCheck.check((IfpReader.parse(ifp(objs)) as IfpResult.Success).file)
        assertEquals(setOf(999L), report.unknownIds)
    }

    @Test fun rejectsBadInput() {
        assertTrue(IfpReader.parse(ByteArray(10)) is IfpResult.Failure)
        val notAnp = ifp(templateObjects()).also { it[3] = '2'.code.toByte() }
        assertTrue(IfpReader.parse(notAnp) is IfpResult.Failure)
        val truncated = ifp(templateObjects()).copyOf(200)
        assertTrue(IfpReader.parse(truncated) is IfpResult.Failure)
        assertTrue(IfpReader.parse(ifp(templateObjects(), frameType = 9)) is IfpResult.Failure)
    }

    @Test fun absurdAnimationCountIsRejected() {
        val b = ifp(templateObjects())
        b[32] = 0xFF.toByte(); b[33] = 0xFF.toByte(); b[34] = 0xFF.toByte(); b[35] = 0x7F
        assertTrue(IfpReader.parse(b) is IfpResult.Failure)
    }
}
