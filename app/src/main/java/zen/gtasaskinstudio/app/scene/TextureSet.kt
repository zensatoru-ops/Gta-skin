package zen.gtasaskinstudio.app.scene

import zen.gtasaskinstudio.app.rw.TxdDecoder
import zen.gtasaskinstudio.app.rw.TxdTexture

/**
 * Helper tekstur untuk renderer. Dekode dilakukan saat dibutuhkan (lazy), bukan
 * semua di awal, supaya memori tidak meledak pada TXD besar.
 *
 * Texture helpers for the renderer. Decoding is lazy (only when a material needs
 * it), so large TXD files do not allocate everything up front.
 */
object TextureSet {

    /** RGBA8 (urutan byte R, G, B, A), mip 0 saja. / RGBA8 bytes (R, G, B, A order), mip 0 only. */
    fun rgba(t: TxdTexture): ByteArray {
        val argb = TxdDecoder.toArgb(t)
        val out = ByteArray(argb.size * 4)
        for (i in argb.indices) {
            val p = argb[i]
            val o = i * 4
            out[o] = (p shr 16).toByte()        // R
            out[o + 1] = (p shr 8).toByte()     // G
            out[o + 2] = p.toByte()             // B
            out[o + 3] = (p ushr 24).toByte()   // A
        }
        return out
    }

    /** Cari tekstur bernama [name] tanpa membedakan huruf besar/kecil. / Case-insensitive lookup by name. */
    fun find(textures: List<TxdTexture>, name: String?): TxdTexture? {
        if (name.isNullOrEmpty()) return null
        return textures.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
