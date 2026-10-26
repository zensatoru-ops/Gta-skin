package zen.gtasaskinstudio.app.i18n

import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.IssueCode
import zen.gtasaskinstudio.app.rw.Severity

/** Pemetaan kode masalah parser ke kunci string. `when` ini wajib lengkap (kompiler memeriksa). */
fun IssueCode.strKey(): StrKey = when (this) {
    IssueCode.FILE_TOO_LARGE -> StrKey.IssueFileTooLarge
    IssueCode.TRUNCATED -> StrKey.IssueTruncated
    IssueCode.CHUNK_EXCEEDS_PARENT -> StrKey.IssueChunkExceedsParent
    IssueCode.NO_CLUMP -> StrKey.IssueNoClump
    IssueCode.NO_GEOMETRY -> StrKey.IssueNoGeometry
    IssueCode.TOO_MANY_ELEMENTS -> StrKey.IssueTooManyElements
    IssueCode.NATIVE_GEOMETRY_UNSUPPORTED -> StrKey.IssueNativeGeometry
    IssueCode.UNEXPECTED_STRUCTURE -> StrKey.IssueUnexpectedStructure
    IssueCode.MULTI_CLUMP -> StrKey.IssueMultiClump
    IssueCode.MULTI_MORPH -> StrKey.IssueMultiMorph
    IssueCode.MULTI_GEOMETRY -> StrKey.IssueMultiGeometry
    IssueCode.UNSUPPORTED_CHUNKS -> StrKey.IssueUnsupportedChunks
    IssueCode.TRAILING_BYTES -> StrKey.IssueTrailingBytes
    IssueCode.SKIN_FORMAT_UNKNOWN -> StrKey.IssueSkinFormatUnknown
    IssueCode.TRIANGLE_INDEX_OUT_OF_RANGE -> StrKey.IssueTriangleIndex
    IssueCode.MATERIAL_INDEX_OUT_OF_RANGE -> StrKey.IssueMaterialIndex
    IssueCode.NON_FINITE_VALUE -> StrKey.IssueNonFinite
    IssueCode.FRAME_PARENT_INVALID -> StrKey.IssueFrameParent
    IssueCode.ATOMIC_REF_INVALID -> StrKey.IssueAtomicRef
    IssueCode.SKIN_BONE_INDEX_OUT_OF_RANGE -> StrKey.IssueSkinBoneIndex
    IssueCode.SKIN_WEIGHT_SUM -> StrKey.IssueSkinWeightSum
    IssueCode.SKIN_UNWEIGHTED_VERTICES -> StrKey.IssueSkinUnweighted
    IssueCode.SKIN_BONE_COUNT_MISMATCH -> StrKey.IssueSkinBoneCount
}

fun Severity.strKey(): StrKey = when (this) {
    Severity.ERROR -> StrKey.SeverityError
    Severity.WARNING -> StrKey.SeverityWarning
    Severity.INFO -> StrKey.SeverityInfo
}

/** Satu baris teks: "[PERINGATAN] pesan — detail (offset N)". */
fun Lang.describe(issue: Issue): String {
    val sb = StringBuilder()
    sb.append('[').append(str(issue.severity.strKey())).append("] ")
    sb.append(str(issue.code.strKey()))
    if (issue.detail.isNotEmpty()) sb.append(" — ").append(issue.detail)
    if (issue.offset >= 0) sb.append(" (").append(str(StrKey.AtOffset)).append(' ').append(issue.offset).append(')')
    return sb.toString()
}
