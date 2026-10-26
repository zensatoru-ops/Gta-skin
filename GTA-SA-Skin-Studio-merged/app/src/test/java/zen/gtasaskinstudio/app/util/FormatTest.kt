package zen.gtasaskinstudio.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun nullAndNegativeShowDash() {
        assertEquals("-", formatBytes(null))
        assertEquals("-", formatBytes(-1))
    }

    @Test fun smallValuesStayInBytes() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test fun sampleFilesFromAudit() {
        assertEquals("5.07 MB", formatBytes(5_320_238L))   // A74_Alice.dff
        assertEquals("16.01 MB", formatBytes(16_791_960L)) // A74_Alice.txd
    }

    @Test fun capsAtGigabytes() {
        assertEquals("1024.00 GB", formatBytes(1024L * 1024 * 1024 * 1024))
    }

    @Test fun rwVersionString() {
        assertEquals("3.6.0.3", rwVersionString(0x36003))
    }
}
