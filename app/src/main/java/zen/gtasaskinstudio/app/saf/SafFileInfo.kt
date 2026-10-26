package zen.gtasaskinstudio.app.saf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Info dasar berkas yang dibaca lewat SAF — tidak ada path absolut di mana
 * pun, hanya content:// Uri yang diberikan sistem lewat ACTION_OPEN_DOCUMENT.
 * Sesuai prinsip kerja di dokumen ringkasan: akses file lewat SAF, bukan
 * path absolut (lihat GTA_SA_Skin_Studio_Ringkasan_Lengkap.md bagian 7).
 *
 * Basic file info read via SAF — no absolute path anywhere, only the
 * content:// Uri handed back by the system through ACTION_OPEN_DOCUMENT.
 */
data class SafFileInfo(
    val uri: Uri,
    val displayName: String?,
    val sizeBytes: Long?,
)

sealed class SafReadResult {
    data class Success(val info: SafFileInfo) : SafReadResult()
    data class Failure(val reason: SafFailureReason, val cause: Throwable? = null) : SafReadResult()
}

enum class SafFailureReason {
    CANNOT_OPEN,
    CANNOT_READ,
    UNKNOWN,
}

/**
 * Query metadata ringan lewat ContentResolver, tidak membuka seluruh isi
 * berkas (Phase 2 tidak punya parser — itu baru datang di Phase 3).
 */
fun readSafFileInfo(context: Context, uri: Uri): SafReadResult {
    return try {
        var name: String? = null
        var size: Long? = null

        val cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: return SafReadResult.Failure(SafFailureReason.CANNOT_OPEN)
        cursor.use {
            val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
            if (it.moveToFirst()) {
                if (nameIdx >= 0) name = it.getString(nameIdx)
                if (sizeIdx >= 0 && !it.isNull(sizeIdx)) size = it.getLong(sizeIdx)
            }
        }

        // Pastikan deskriptor berkas benar-benar bisa dibuka — query metadata
        // di atas bisa sukses walau isi berkas sudah tidak bisa dibaca
        // (mis. izin dicabut, penyedia storage hilang).
        context.contentResolver.openFileDescriptor(uri, "r")?.use { } ?: return SafReadResult.Failure(
            SafFailureReason.CANNOT_READ
        )

        SafReadResult.Success(SafFileInfo(uri = uri, displayName = name, sizeBytes = size))
    } catch (e: SecurityException) {
        SafReadResult.Failure(SafFailureReason.CANNOT_OPEN, e)
    } catch (e: java.io.FileNotFoundException) {
        SafReadResult.Failure(SafFailureReason.CANNOT_READ, e)
    } catch (e: Exception) {
        SafReadResult.Failure(SafFailureReason.UNKNOWN, e)
    }
}

/** Mime types yang diterima picker Phase 2 — belum ada validasi konten, hanya ekstensi/mime. */
val SUPPORTED_MIME_TYPES = arrayOf("*/*") // .dff dan .txd tidak punya mime terdaftar
