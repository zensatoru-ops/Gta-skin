package zen.gtasaskinstudio.app.saf

import android.content.Context
import android.net.Uri
import zen.gtasaskinstudio.app.rw.Limits
import zen.gtasaskinstudio.app.rw.TxdTexture

/**
 * Baca satu PNG/TGA/JPG yang dipilih pengguna untuk slot material. Null bila gagal.
 * Read one PNG/TGA/JPG the user picked for a material slot. Null on failure.
 */
fun loadSlotTexture(context: Context, uri: Uri): TxdTexture? {
    val info = (readSafFileInfo(context, uri) as? SafReadResult.Success)?.info ?: return null
    val bytes = (readSafBytes(context, info.uri, info.sizeBytes, Limits.MAX_FILE_BYTES) as? SafBytesResult.Success)
        ?.bytes ?: return null
    val fileName = info.displayName ?: return null
    val stem = fileName.substringBeforeLast('.', fileName)
    val ext = fileName.substringAfterLast('.', "")
    return AutoTextures.decodeBytes(stem, ext, bytes)
}
