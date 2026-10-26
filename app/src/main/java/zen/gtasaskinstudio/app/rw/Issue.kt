package zen.gtasaskinstudio.app.rw

/**
 * Laporan masalah terstruktur dari parser/validator. UI memetakan [IssueCode]
 * ke teks Indonesia/English; [detail] hanya angka/ID teknis (bukan kalimat).
 *
 * Structured problem report from the parser/validator. The UI maps
 * [IssueCode] to Indonesian/English text; [detail] holds only technical
 * numbers/IDs, never a sentence.
 */
enum class Severity { ERROR, WARNING, INFO }

enum class IssueCode {
    FILE_TOO_LARGE,
    TRUNCATED,
    CHUNK_EXCEEDS_PARENT,
    NO_CLUMP,
    NO_GEOMETRY,
    TOO_MANY_ELEMENTS,
    NATIVE_GEOMETRY_UNSUPPORTED,
    UNEXPECTED_STRUCTURE,
    MULTI_CLUMP,
    MULTI_MORPH,
    MULTI_GEOMETRY,
    UNSUPPORTED_CHUNKS,
    TRAILING_BYTES,
    SKIN_FORMAT_UNKNOWN,
    TRIANGLE_INDEX_OUT_OF_RANGE,
    MATERIAL_INDEX_OUT_OF_RANGE,
    NON_FINITE_VALUE,
    FRAME_PARENT_INVALID,
    ATOMIC_REF_INVALID,
    SKIN_BONE_INDEX_OUT_OF_RANGE,
    SKIN_WEIGHT_SUM,
    SKIN_UNWEIGHTED_VERTICES,
    SKIN_BONE_COUNT_MISMATCH,
}

data class Issue(
    val severity: Severity,
    val code: IssueCode,
    /** Offset byte di berkas (-1 = tidak ada) / byte offset in the file (-1 = none). */
    val offset: Int = -1,
    val detail: String = "",
)

/** Dilempar parser internal, diubah jadi [Issue] di batas API publik. / Internal; converted to [Issue] at the public boundary. */
class ParseFailure(val issue: Issue) : Exception("${issue.code} @${issue.offset} ${issue.detail}")

/**
 * Batas keras untuk mencegah alokasi tak terbatas. Angka adalah usulan awal
 * (lihat TEST_PLAN.md bagian 2), bukan fakta format.
 *
 * Hard limits to prevent unbounded allocation. Values are initial proposals
 * (see TEST_PLAN.md section 2), not facts about the format.
 */
object Limits {
    const val MAX_FILE_BYTES = 64 * 1024 * 1024
    const val MAX_VERTICES = 65_535          // indeks 16-bit / 16-bit indices
    const val MAX_TRIANGLES = 131_070
    const val MAX_FRAMES = 4_096
    const val MAX_GEOMETRIES = 64
    const val MAX_MATERIALS = 1_024
    const val MAX_UV_LAYERS = 8
    const val MAX_MORPH_TARGETS = 16
    const val MAX_HANIM_NODES = 256
    const val MAX_STRING = 128
}
