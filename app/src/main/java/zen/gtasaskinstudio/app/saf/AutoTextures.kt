package zen.gtasaskinstudio.app.saf

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import zen.gtasaskinstudio.app.scene.TgaDecoder

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
        if (file.extension.equals("tga", ignoreCase = true)) return decodeTga(file, name)
        return decodeRaster(file, name)
    }

    /** TGA didekode dengan decoder murni, karena BitmapFactory tidak membaca TGA. / TGA uses the pure decoder; BitmapFactory cannot read TGA. */
    private fun decodeTga(file: File, name: String): TxdTexture? {
        val img = TgaDecoder.decode(file.readBytes()) ?: return null
        return TxdTexture(
            name = name,
            maskName = "",
            width = img.width,
            height = img.height,
            levels = 1,
            d3dFormat = TxdFormat.A8R8G8B8,
            data = img.bgra,
        )
    }

    /**
     * Dekode satu gambar dari byte yang sudah dibaca (dipakai saat user memilih PNG/TGA untuk slot).
     * Decode one image from bytes already read (used when the user picks a PNG/TGA for a slot).
     */
    fun decodeBytes(name: String, ext: String, bytes: ByteArray): TxdTexture? {
        if (ext.equals("tga", ignoreCase = true)) {
            val img = TgaDecoder.decode(bytes) ?: return null
            return TxdTexture(name, "", img.width, img.height, 1, TxdFormat.A8R8G8B8, img.bgra)
        }
        if (ext.lowercase() !in PngNames.IMAGE_EXTENSIONS) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth > Limits.MAX_TEXTURE_DIM || bounds.outHeight > Limits.MAX_TEXTURE_DIM) return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            toA8R8G8B8(bmp, name)
        } finally {
            bmp.recycle()
        }
    }

    private fun decodeRaster(file: File, name: String): TxdTexture? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth > Limits.MAX_TEXTURE_DIM || bounds.outHeight > Limits.MAX_TEXTURE_DIM) return null

        val bmp = BitmapFactory.decodeFile(file.path) ?: return null
        try {
            return toA8R8G8B8(bmp, name)
        } finally {
            bmp.recycle()
        }
    }

    /** Ubah bitmap ARGB ke urutan memori BGRA untuk TxdTexture A8R8G8B8. / Convert an ARGB bitmap to BGRA memory order for an A8R8G8B8 TxdTexture. */
    private fun toA8R8G8B8(bmp: Bitmap, name: String): TxdTexture {
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
    }
}
