package zen.gtasaskinstudio.app.obj

/**
 * Pembaca .mtl minimal (Fase C): hanya `newmtl` dan `map_Kd`. Hasilnya peta
 * nama material -> nama tekstur (path dan ekstensi dibuang), supaya cocok dengan
 * nama tekstur TXD. Baris lain diabaikan. Tidak pernah melempar exception.
 *
 * Minimal .mtl reader (Phase C): only `newmtl` and `map_Kd`. Produces a map from
 * material name to texture name (path and extension stripped) so it matches TXD
 * texture names. Other lines are ignored. Never throws.
 */
object MtlParser {

    fun parse(bytes: ByteArray): Map<String, String> {
        val text = String(bytes, Charsets.ISO_8859_1)
        val out = HashMap<String, String>()
        var current: String? = null
        for (raw in text.split('\n')) {
            val parts = raw.trim().split(Regex("\\s+"))
            when (parts.getOrNull(0)) {
                "newmtl" -> current = parts.getOrNull(1)
                "map_Kd" -> {
                    val name = current ?: continue
                    // Opsi seperti -s 1 1 1 didahului path; path selalu token terakhir.
                    // Options such as -s 1 1 1 come before the path; the path is the last token.
                    val file = parts.lastOrNull()?.takeIf { it.length > 1 && it != "map_Kd" } ?: continue
                    val base = file.substringAfterLast('/').substringAfterLast('\\')
                    val stem = base.substringBeforeLast('.', base)
                    if (stem.isNotEmpty()) out.putIfAbsent(name, stem)
                }
            }
        }
        return out
    }
}
