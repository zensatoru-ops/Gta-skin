package zen.gtasaskinstudio.app.i18n

import java.util.Locale

/**
 * Bahasa aplikasi dipilih di dalam aplikasi (toggle ID/EN), tidak hanya
 * mengikuti locale sistem Android. Ini memenuhi persyaratan "Indonesia +
 * English penuh" — pengguna bisa ganti bahasa kapan saja, termasuk pesan
 * error, tanpa keluar dari aplikasi.
 *
 * App language is chosen inside the app (ID/EN toggle), not only derived
 * from the system locale. This satisfies the "full Indonesian + English"
 * requirement — the user can switch language at any time, including error
 * messages, without leaving the app.
 */
enum class Lang { ID, EN }

/**
 * Kunci string terpusat. Setiap kunci punya pasangan (ID, EN) di tabel di
 * bawah, jadi satu bahasa tidak bisa tertinggal tanpa terlihat di kode.
 *
 * Centralized string keys. Every key has an (ID, EN) pair in the table
 * below, so one language cannot silently be missing.
 */
enum class StrKey {
    AppTitle,
    OpenFile,
    OpenFileHint,
    NoFileSelected,
    FileSelected,
    FileName,
    FileSize,
    FileUri,
    ErrorTitle,
    ErrorCannotOpen,
    ErrorCannotRead,
    ErrorUnknown,
    ErrorRetry,
    PhaseNotice,
    LangToggle,

    // Phase 3-5
    Parsing,
    FileTooLarge,
    OnlyDffNow,
    TabModel,
    TabInfo,
    TabSkeleton,
    TabIssues,
    Wireframe,
    MaterialColors,
    ShowSkeleton,
    ResetCamera,
    ViewerHelp,
    TextureNote,
    CannotRender,
    ParseFailedTitle,
    InfoRw,
    InfoGeometries,
    InfoVertices,
    InfoTriangles,
    InfoMaterials,
    InfoFrames,
    InfoUvLayers,
    InfoPrelit,
    InfoNormals,
    InfoYes,
    InfoNo,
    InfoSkinBones,
    InfoTextures,
    SkinTitle,
    SkinNone,
    SkinMaxInfl,
    SkinBadIndex,
    SkinBadSum,
    SkinUnweighted,
    SkinWeightRange,
    SkinTopBones,
    FrameTreeTitle,
    FrameUnnamed,
    SeverityError,
    SeverityWarning,
    SeverityInfo,
    NoIssues,
    AtOffset,

    // Issue messages / pesan masalah
    IssueFileTooLarge,
    IssueTruncated,
    IssueChunkExceedsParent,
    IssueNoClump,
    IssueNoGeometry,
    IssueTooManyElements,
    IssueNativeGeometry,
    IssueUnexpectedStructure,
    IssueMultiClump,
    IssueMultiMorph,
    IssueMultiGeometry,
    IssueUnsupportedChunks,
    IssueTrailingBytes,
    IssueSkinFormatUnknown,
    IssueTriangleIndex,
    IssueMaterialIndex,
    IssueNonFinite,
    IssueFrameParent,
    IssueAtomicRef,
    IssueSkinBoneIndex,
    IssueSkinWeightSum,
    IssueSkinUnweighted,
    IssueSkinBoneCount,
}

private val table: Map<StrKey, Pair<String, String>> = mapOf(
    StrKey.AppTitle to ("GTA SA Skin Studio" to "GTA SA Skin Studio"),
    StrKey.OpenFile to ("Buka Berkas" to "Open File"),
    StrKey.OpenFileHint to (
        "Pilih berkas .dff lewat pengelola berkas sistem." to
            "Pick a .dff file through the system file manager."
        ),
    StrKey.NoFileSelected to ("Belum ada berkas dipilih." to "No file selected yet."),
    StrKey.FileSelected to ("Berkas terpilih" to "Selected file"),
    StrKey.FileName to ("Nama" to "Name"),
    StrKey.FileSize to ("Ukuran" to "Size"),
    StrKey.FileUri to ("URI" to "URI"),
    StrKey.ErrorTitle to ("Terjadi Kesalahan" to "Something Went Wrong"),
    StrKey.ErrorCannotOpen to (
        "Tidak bisa membuka berkas ini. Pastikan berkas masih ada dan bisa diakses." to
            "Could not open this file. Make sure it still exists and is accessible."
        ),
    StrKey.ErrorCannotRead to (
        "Tidak bisa membaca berkas ini. Berkas mungkin rusak atau bukan jenis yang didukung." to
            "Could not read this file. It may be corrupted or an unsupported type."
        ),
    StrKey.ErrorUnknown to ("Terjadi kesalahan tak terduga." to "An unexpected error occurred."),
    StrKey.ErrorRetry to ("Coba Lagi" to "Try Again"),
    StrKey.PhaseNotice to (
        "Phase 3-5: membaca .dff, menampilkan model 3D, memeriksa skeleton dan skin. Tekstur dan ekspor belum tersedia." to
            "Phases 3-5: reads .dff, shows the 3D model, inspects skeleton and skin. Textures and export are not available yet."
        ),
    StrKey.LangToggle to ("EN" to "ID"),

    StrKey.Parsing to ("Membaca dan memeriksa berkas…" to "Reading and validating the file…"),
    StrKey.FileTooLarge to (
        "Berkas terlalu besar (maksimal %d MB)." to "File is too large (maximum %d MB)."
        ),
    StrKey.OnlyDffNow to (
        "Saat ini hanya berkas .dff yang dibaca. Dukungan .txd menyusul di Phase 7." to
            "Only .dff files are read for now. .txd support comes in Phase 7."
        ),
    StrKey.TabModel to ("Model" to "Model"),
    StrKey.TabInfo to ("Info" to "Info"),
    StrKey.TabSkeleton to ("Skeleton" to "Skeleton"),
    StrKey.TabIssues to ("Catatan" to "Issues"),
    StrKey.Wireframe to ("Mode wireframe" to "Wireframe mode"),
    StrKey.MaterialColors to ("Warna per material" to "Color per material"),
    StrKey.ShowSkeleton to ("Tampilkan skeleton" to "Show skeleton"),
    StrKey.ResetCamera to ("Reset kamera" to "Reset camera"),
    StrKey.ViewerHelp to (
        "1 jari: putar. 2 jari: geser. Cubit: zoom." to
            "One finger: rotate. Two fingers: pan. Pinch: zoom."
        ),
    StrKey.TextureNote to (
        "Tekstur belum didukung (Phase 7); material ditampilkan polos." to
            "Textures are not supported yet (Phase 7); materials are shown flat."
        ),
    StrKey.CannotRender to (
        "Model tidak digambar karena validasi menemukan kesalahan. Lihat tab Catatan." to
            "The model is not drawn because validation found errors. See the Issues tab."
        ),
    StrKey.ParseFailedTitle to ("Berkas tidak bisa dibaca sebagai DFF" to "The file could not be read as a DFF"),
    StrKey.InfoRw to ("Versi RenderWare" to "RenderWare version"),
    StrKey.InfoGeometries to ("Geometri" to "Geometries"),
    StrKey.InfoVertices to ("Vertex" to "Vertices"),
    StrKey.InfoTriangles to ("Segitiga" to "Triangles"),
    StrKey.InfoMaterials to ("Material" to "Materials"),
    StrKey.InfoFrames to ("Frame" to "Frames"),
    StrKey.InfoUvLayers to ("Lapisan UV" to "UV layers"),
    StrKey.InfoPrelit to ("Warna vertex" to "Vertex colors"),
    StrKey.InfoNormals to ("Normal" to "Normals"),
    StrKey.InfoYes to ("Ada" to "Yes"),
    StrKey.InfoNo to ("Tidak ada" to "No"),
    StrKey.InfoSkinBones to ("Bone pada skin" to "Skin bones"),
    StrKey.InfoTextures to ("Tekstur yang dirujuk" to "Referenced textures"),
    StrKey.SkinTitle to ("Statistik skin" to "Skin statistics"),
    StrKey.SkinNone to ("Model ini tidak memiliki data skin." to "This model has no skin data."),
    StrKey.SkinMaxInfl to ("Pengaruh maks. per vertex" to "Max influences per vertex"),
    StrKey.SkinBadIndex to ("Vertex dengan indeks bone tidak valid" to "Vertices with invalid bone index"),
    StrKey.SkinBadSum to ("Vertex dengan jumlah bobot menyimpang" to "Vertices with off-sum weights"),
    StrKey.SkinUnweighted to ("Vertex tanpa bobot" to "Unweighted vertices"),
    StrKey.SkinWeightRange to ("Rentang jumlah bobot" to "Weight sum range"),
    StrKey.SkinTopBones to ("Bone dengan vertex terbanyak" to "Bones with the most vertices"),
    StrKey.FrameTreeTitle to ("Hierarki frame" to "Frame hierarchy"),
    StrKey.FrameUnnamed to ("(tanpa nama)" to "(unnamed)"),
    StrKey.SeverityError to ("KESALAHAN" to "ERROR"),
    StrKey.SeverityWarning to ("PERINGATAN" to "WARNING"),
    StrKey.SeverityInfo to ("INFO" to "INFO"),
    StrKey.NoIssues to ("Tidak ada catatan." to "No issues found."),
    StrKey.AtOffset to ("offset" to "offset"),

    StrKey.IssueFileTooLarge to ("Berkas melebihi batas ukuran." to "File exceeds the size limit."),
    StrKey.IssueTruncated to (
        "Berkas terpotong: data berakhir lebih cepat dari yang dinyatakan." to
            "File is truncated: data ends earlier than declared."
        ),
    StrKey.IssueChunkExceedsParent to (
        "Ukuran chunk melebihi wadahnya; berkas rusak atau bukan DFF." to
            "A chunk's size exceeds its container; the file is corrupt or not a DFF."
        ),
    StrKey.IssueNoClump to ("Clump tidak ditemukan; bukan DFF yang dikenali." to "No Clump found; not a recognized DFF."),
    StrKey.IssueNoGeometry to ("DFF tidak memiliki geometri." to "The DFF contains no geometry."),
    StrKey.IssueTooManyElements to ("Jumlah elemen melebihi batas aman." to "Element count exceeds the safe limit."),
    StrKey.IssueNativeGeometry to (
        "Geometri native (mobile/WDGL) belum didukung." to "Native (mobile/WDGL) geometry is not supported yet."
        ),
    StrKey.IssueUnexpectedStructure to ("Struktur tak terduga di berkas." to "Unexpected structure in the file."),
    StrKey.IssueMultiClump to (
        "Ada lebih dari satu clump; hanya yang pertama dibaca." to "More than one clump; only the first is read."
        ),
    StrKey.IssueMultiMorph to (
        "Ada beberapa morph target; hanya yang pertama dipakai." to "Multiple morph targets; only the first is used."
        ),
    StrKey.IssueMultiGeometry to (
        "Ada lebih dari satu geometri; hanya yang pertama digambar." to "More than one geometry; only the first is drawn."
        ),
    StrKey.IssueUnsupportedChunks to (
        "Chunk berikut belum didukung dan diabaikan (ID heks)." to
            "These chunks are not supported and were ignored (hex IDs)."
        ),
    StrKey.IssueTrailingBytes to (
        "Ada byte sisa setelah data geometri." to "Extra bytes remain after the geometry data."
        ),
    StrKey.IssueSkinFormatUnknown to (
        "Format Skin PLG tidak dikenali; skin diabaikan." to "Skin PLG format not recognized; skin ignored."
        ),
    StrKey.IssueTriangleIndex to (
        "Indeks segitiga menunjuk vertex yang tidak ada." to "A triangle index points to a vertex that does not exist."
        ),
    StrKey.IssueMaterialIndex to ("Indeks material tidak valid." to "Invalid material index."),
    StrKey.IssueNonFinite to ("Ada nilai NaN atau tak hingga." to "NaN or infinite values found."),
    StrKey.IssueFrameParent to (
        "Hierarki frame tidak valid (induk di luar rentang atau siklus)." to
            "Invalid frame hierarchy (parent out of range or cycle)."
        ),
    StrKey.IssueAtomicRef to (
        "Atomic merujuk frame atau geometri yang tidak ada." to "An atomic references a missing frame or geometry."
        ),
    StrKey.IssueSkinBoneIndex to ("Indeks bone pada skin di luar rentang." to "Skin bone index out of range."),
    StrKey.IssueSkinWeightSum to (
        "Jumlah bobot skin pada beberapa vertex tidak sama dengan 1 (tidak diperbaiki)." to
            "Skin weights on some vertices do not sum to 1 (not repaired)."
        ),
    StrKey.IssueSkinUnweighted to (
        "Beberapa vertex tidak punya bobot skin." to "Some vertices have no skin weights."
        ),
    StrKey.IssueSkinBoneCount to (
        "Jumlah bone skin berbeda dari hierarki HAnim." to "Skin bone count differs from the HAnim hierarchy."
        ),
)

fun Lang.str(key: StrKey): String {
    val pair = table[key] ?: return key.name
    return if (this == Lang.ID) pair.first else pair.second
}

/** Teks dengan placeholder printf, selalu memakai Locale.US agar angka konsisten. */
fun Lang.fmt(key: StrKey, vararg args: Any?): String = String.format(Locale.US, str(key), *args)
