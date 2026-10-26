package zen.gtasaskinstudio.app.rw

import java.io.ByteArrayOutputStream

/**
 * Pembangun DFF sintetis kecil untuk tes — dibuat sendiri, bebas hak, dan
 * TIDAK membuktikan kompatibilitas dengan game (hanya struktur yang diuji
 * parser). Satu segitiga, tiga vertex, satu material, satu frame.
 *
 * Tiny synthetic DFF builder for tests — self-made, rights-free, and does
 * NOT prove game compatibility (only the structure the parser reads).
 */
internal class Out {
    private val b = ByteArrayOutputStream()
    fun i32(v: Int): Out { for (s in 0..24 step 8) b.write((v ushr s) and 0xFF); return this }
    fun u16(v: Int): Out { b.write(v and 0xFF); b.write((v ushr 8) and 0xFF); return this }
    fun u8(v: Int): Out { b.write(v and 0xFF); return this }
    fun f32(v: Float): Out = i32(v.toRawBits())
    fun raw(a: ByteArray): Out { b.write(a); return this }
    fun bytes(): ByteArray = b.toByteArray()
}

internal object SyntheticDff {
    const val LIB = 0x1803ffff // RW 3.6.0.3

    fun chunk(type: Int, content: ByteArray = ByteArray(0)): ByteArray =
        Out().i32(type).i32(content.size).i32(LIB).raw(content).bytes()

    private fun floats(vararg v: Float): ByteArray {
        val o = Out()
        for (x in v) o.f32(x)
        return o.bytes()
    }

    private fun frameList(): ByteArray {
        val frame = Out()
            .raw(floats(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)) // rotasi identitas
            .raw(floats(0f, 0f, 0f))                         // posisi
            .i32(-1).i32(0).bytes()
        val struct = chunk(Rw.STRUCT, Out().i32(1).raw(frame).bytes())
        return chunk(Rw.FRAME_LIST, struct + chunk(Rw.EXTENSION))
    }

    private fun geometryStruct(nativeFlag: Boolean, vertexCountField: Int, thirdIndex: Int): ByteArray {
        val flags = 0x12 or (if (nativeFlag) Rw.GEO_NATIVE else 0)
        val o = Out().i32(flags).i32(1).i32(vertexCountField).i32(1)
        o.u16(1).u16(0).u16(0).u16(thirdIndex)               // v2, v1, material, v3
        o.raw(floats(0f, 0f, 0f, 1f))                        // bounding sphere
        o.i32(1).i32(1)                                      // hasVertices, hasNormals
        o.raw(floats(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f))    // posisi
        o.raw(floats(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f))    // normal
        return o.bytes()
    }

    private fun materialList(): ByteArray {
        val matStruct = Out().i32(0).i32(-1).i32(0).i32(0).raw(floats(1f, 1f, 1f)).bytes()
        val material = chunk(Rw.MATERIAL, chunk(Rw.STRUCT, matStruct) + chunk(Rw.EXTENSION))
        return chunk(Rw.MATERIAL_LIST, chunk(Rw.STRUCT, Out().i32(1).i32(-1).bytes()) + material)
    }

    /** Skin satu bone; [weights] 4 float untuk tiap dari 3 vertex, [boneIndex] indeks bone vertex. */
    private fun skin(weights: FloatArray, boneIndex: Int): ByteArray {
        val o = Out().u8(1).u8(1).u8(1).u8(0)   // numBones, used, maxWeights, pad
        o.u8(0)                                 // used bones
        for (v in 0 until 3) o.u8(boneIndex).u8(0).u8(0).u8(0)
        for (w in weights) o.f32(w)
        for (i in 0 until 16) o.f32(if (i % 5 == 0) 1f else 0f)
        o.raw(ByteArray(12))
        return chunk(Rw.SKIN, o.bytes())
    }

    fun build(
        nativeFlag: Boolean = false,
        vertexCountField: Int = 3,
        thirdIndex: Int = 2,
        skinWeights: FloatArray? = null,
        skinBoneIndex: Int = 0,
    ): ByteArray {
        val geomExt = if (skinWeights != null) chunk(Rw.EXTENSION, skin(skinWeights, skinBoneIndex)) else chunk(Rw.EXTENSION)
        val geometry = chunk(
            Rw.GEOMETRY,
            chunk(Rw.STRUCT, geometryStruct(nativeFlag, vertexCountField, thirdIndex)) + materialList() + geomExt,
        )
        val geomList = chunk(Rw.GEOMETRY_LIST, chunk(Rw.STRUCT, Out().i32(1).bytes()) + geometry)
        val atomic = chunk(Rw.ATOMIC, chunk(Rw.STRUCT, Out().i32(0).i32(0).i32(5).i32(0).bytes()) + chunk(Rw.EXTENSION))
        val clumpStruct = chunk(Rw.STRUCT, Out().i32(1).i32(0).i32(0).bytes())
        return chunk(Rw.CLUMP, clumpStruct + frameList() + geomList + atomic + chunk(Rw.EXTENSION))
    }
}
