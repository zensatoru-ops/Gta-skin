package zen.gtasaskinstudio.app.rw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ByteReaderTest {
    @Test fun readingPastEndThrowsTruncated() {
        val r = ByteReader(byteArrayOf(1, 2, 3), 0, 3)
        r.u16()
        try {
            r.i32()
            fail("expected ParseFailure")
        } catch (e: ParseFailure) {
            assertEquals(IssueCode.TRUNCATED, e.issue.code)
        }
    }

    @Test fun littleEndianValues() {
        val r = ByteReader(byteArrayOf(0x34, 0x12, 0x78, 0x56, 0x34, 0x12), 0, 6)
        assertEquals(0x1234, r.u16())
        assertEquals(0x12345678, r.i32())
    }

    @Test fun chunkLargerThanParentIsRejected() {
        val data = Out().i32(Rw.CLUMP).i32(1000).i32(SyntheticDff.LIB).bytes()
        val r = ByteReader(data, 0, data.size)
        try {
            r.chunk()
            fail("expected ParseFailure")
        } catch (e: ParseFailure) {
            assertEquals(IssueCode.CHUNK_EXCEEDS_PARENT, e.issue.code)
        }
    }

    @Test fun negativeChunkSizeIsRejected() {
        val data = Out().i32(Rw.CLUMP).i32(-1).i32(SyntheticDff.LIB).bytes()
        try {
            ByteReader(data, 0, data.size).chunk()
            fail("expected ParseFailure")
        } catch (e: ParseFailure) {
            assertEquals(IssueCode.CHUNK_EXCEEDS_PARENT, e.issue.code)
        }
    }

    @Test fun rwVersionDecoding() {
        assertEquals(0x36003, Rw.rwVersion(0x1803ffff))
        assertEquals(0x30000, Rw.rwVersion(0x300))
    }

    @Test fun multiplyOverflowIsRejected() {
        try {
            ByteReader.multiplyOrFail(Int.MAX_VALUE, 4)
            fail("expected ParseFailure")
        } catch (e: ParseFailure) {
            assertTrue(e.issue.code == IssueCode.TOO_MANY_ELEMENTS)
        }
    }
}
