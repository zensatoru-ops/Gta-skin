package zen.gtasaskinstudio.app.saf

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Membaca isi berkas lewat SAF dengan batas ukuran keras. Tidak pernah
 * mengalokasikan lebih dari [maxBytes] (+ satu buffer kecil).
 *
 * Reads file content through SAF with a hard size cap. Never allocates more
 * than [maxBytes] (plus one small buffer).
 */
sealed class SafBytesResult {
    class Success(val bytes: ByteArray) : SafBytesResult()
    data object TooLarge : SafBytesResult()
    class Failure(val cause: Throwable?) : SafBytesResult()
}

fun readSafBytes(context: Context, uri: Uri, knownSize: Long?, maxBytes: Int): SafBytesResult {
    if (knownSize != null && knownSize > maxBytes) return SafBytesResult.TooLarge
    return try {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return SafBytesResult.Failure(null)
        stream.use { input ->
            val chunk = ByteArray(64 * 1024)
            val out = ByteArrayOutputStream(
                if (knownSize != null && knownSize in 1..maxBytes.toLong()) knownSize.toInt() else 64 * 1024
            )
            var total = 0L
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > maxBytes) return SafBytesResult.TooLarge
                out.write(chunk, 0, n)
            }
            SafBytesResult.Success(out.toByteArray())
        }
    } catch (e: IOException) {
        SafBytesResult.Failure(e)
    } catch (e: SecurityException) {
        SafBytesResult.Failure(e)
    } catch (e: OutOfMemoryError) {
        SafBytesResult.Failure(e)
    }
}
