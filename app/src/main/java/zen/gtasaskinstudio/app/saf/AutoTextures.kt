package zen.gtasaskinstudio.app.saf

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import java.io.File
import zen.gtasaskinstudio.app.rw.Limits
import zen.gtasaskinstudio.app.rw.TxdFormat
import zen.gtasaskinstudio.app.rw.TxdTexture
import zen.gtasaskinstudio.app.scene.PngNames

/** Hasil pencarian PNG otomatis di folder model. / Result of the automatic PNG search in the model's folder. */
class AutoTextureResult(
    val textures: List<TxdTexture>,
    /** Folder yang dipindai, atau null bila tidak ada. / Folder scanned, or null. */
    val folder: String?,
    /** True bila izin akses penyimpanan belum diberikan. / True when storage access is not granted. */
    val needsPermission: Boolean,
    /** True bila path folder tidak bisa diturunkan dari sumber ini. / True when the folder path cannot be derived from this source. */
    val noPath: Boolean,
)

/**
 * Mencari PNG yang namanya sama dengan nama material di folder yang sama dengan
 * berkas model, lalu mengubahnya menjadi [TxdTexture] berformat A8R8G8B8.
 *
 * Finds PNGs named like the model's material names in the same folder as the model
 * file, and converts them to [TxdTexture] in A8R8G8B8 format.
 */
object AutoTextures {

    private const val EXTERNAL_AUTHORITY = "com.android.externalstorage.documents"

    /** Izin untuk membaca folder: Akses semua file (Android 11+), atau READ_EXTERNAL_STORAGE. */
    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun find(context: Context, uri: Uri, wanted: Collection<String>): AutoTextureResult {
        if (wanted.isEmpty()) return AutoTextureResult(emptyList(), null, needsPermission = false, noPath = false)
        if (!hasStorageAccess(context)) {
            return AutoTextureResult(emptyList(), null, needsPermission = true, noPath = false)
        }
        val dir = folderOf(uri)
            ?: return AutoTextureResult(emptyList(), null, needsPermission = false, noPath = true)
        val files = dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        val out = ArrayList<TxdTexture>()
        for ((name, fileName) in PngNames.match(files, wanted)) {
            decode(File(dir, fileName), name)?.let { out.add(it) }
        }
        return AutoTextureResult(out, dir.absolutePath, needsPermission = false, noPath = false)
    }

    /** Folder tempat berkas [uri] berada, atau null bila tidak bisa dibaca langsung. / Folder holding [uri], or null if not directly readable. */
    fun folderOf(uri: Uri): File? {
        if (uri.authority != EXTERNAL_AUTHORITY) return null
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val path = PngNames.pathFromDocId(docId) ?: return null
        val parent = File(path).parentFile ?: return null
        return parent.takeIf { it.isDirectory && it.canRead() }
    }

    private fun decode(file: File, name: String): TxdTexture? {
        if (!file.isFile || file.length() > Limits.MAX_FILE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth > Limits.MAX_TEXTURE_DIM || bounds.outHeight > Limits.MAX_TEXTURE_DIM) return null

        val bmp = BitmapFactory.decodeFile(file.path) ?: return null
        try {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            // Urutan memori BGRA (little-endian u32 0xAARRGGBB), sama dengan decoder A8R8G8B8.
            // Memory order BGRA (little-endian u32 0xAARRGGBB), matching the A8R8G8B8 decoder.
            val data = ByteArray(px.size * 4)
            for (i in px.indices) {
                val p = px[i]
                val o = i * 4
                data[o] = p.toByte()
                data[o + 1] = (p shr 8).toByte()
                data[o + 2] = (p shr 16).toByte()
                data[o + 3] = (p ushr 24).toByte()
            }
            return TxdTexture(
                name = name,
                maskName = "",
                width = w,
                height = h,
                levels = 1,
                d3dFormat = TxdFormat.A8R8G8B8,
                data = data,
            )
        } finally {
            bmp.recycle()
        }
    }
}
