package zen.gtasaskinstudio.app.scene

/**
 * Logika nama PNG murni (tanpa Android) supaya bisa diuji di JVM.
 * Pure PNG name logic (no Android) so it can be tested on the JVM.
 */
object PngNames {

    /**
     * Ubah docId SAF "primary:Dir/file.fbx" menjadi path absolut.
     * Hanya volume utama (primary) yang didukung; kartu SD / volume lain mengembalikan null.
     *
     * Convert a SAF docId "primary:Dir/file.fbx" to an absolute path. Only the primary
     * volume is supported; SD cards or other volumes return null.
     */
    fun pathFromDocId(docId: String, storageRoot: String = "/storage/emulated/0"): String? {
        val colon = docId.indexOf(':')
        if (colon <= 0) return null
        val volume = docId.substring(0, colon)
        val rest = docId.substring(colon + 1)
        if (volume != "primary") return null
        if (rest.isEmpty() || rest.split('/').any { it == ".." }) return null
        return "$storageRoot/$rest"
    }

    /** Ekstensi gambar yang didukung. / Supported image extensions. */
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "tga")

    /** Nama tanpa ekstensi gambar (tanpa membedakan huruf), atau null bila bukan gambar yang didukung. / Name without a supported image extension, or null. */
    fun imageStem(fileName: String): String? {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return null
        val ext = fileName.substring(dot + 1).lowercase()
        if (ext !in IMAGE_EXTENSIONS) return null
        return fileName.substring(0, dot)
    }

    /**
     * Pasangkan tiap nama yang dibutuhkan dengan file PNG yang namanya sama, tanpa
     * membedakan huruf. Kunci hasil = nama yang diminta; nilai = nama file.
     *
     * Match each wanted name to a PNG file with the same name, case-insensitively.
     * Key = wanted name; value = file name.
     */
    fun match(fileNames: List<String>, wanted: Collection<String>): Map<String, String> {
        val byStem = HashMap<String, String>()
        for (f in fileNames) {
            val stem = imageStem(f) ?: continue
            byStem.putIfAbsent(stem.lowercase(), f)
        }
        val out = LinkedHashMap<String, String>()
        for (w in wanted) {
            val f = byStem[w.lowercase()] ?: continue
            out[w] = f
        }
        return out
    }
}
