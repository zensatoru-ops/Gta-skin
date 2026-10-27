package zen.gtasaskinstudio.app.obj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjLoaderTest {

    @Test fun validFileProducesSceneWithCounts() {
        val bytes = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".toByteArray(Charsets.ISO_8859_1)
        val r = ObjLoader.load("tri.obj", bytes.size.toLong(), bytes)
        assertTrue(r is ObjLoadOutcome.Loaded)
        val l = r as ObjLoadOutcome.Loaded
        assertEquals("tri.obj", l.displayName)
        assertEquals(3, l.vertexCount)
        assertEquals(1, l.triangleCount)
        assertEquals(1, l.scene.draws.size)
        assertEquals(3, l.scene.vertexCount)
    }

    @Test fun garbageFileFailsWithIssues() {
        val r = ObjLoader.load("x.obj", 3, byteArrayOf(1, 2, 3))
        assertTrue(r is ObjLoadOutcome.Failed)
        assertTrue((r as ObjLoadOutcome.Failed).issues.isNotEmpty())
    }

    @Test fun warningsArePassedThroughToViewer() {
        val bytes = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 99\nf 1 2 3\n".toByteArray(Charsets.ISO_8859_1)
        val l = ObjLoader.load(null, null, bytes) as ObjLoadOutcome.Loaded
        assertTrue(l.issues.isNotEmpty())
        assertEquals(1, l.triangleCount)
    }
}
