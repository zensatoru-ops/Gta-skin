package zen.gtasaskinstudio.app.util

import java.util.Locale

private val UNITS = listOf("B", "KB", "MB", "GB")

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "-"
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < UNITS.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.2f %s", value, UNITS[unit])
}

/** 0x36003 -> "3.6.0.3" (mayor.minor.revisi.build). */
fun rwVersionString(version: Int): String =
    "${(version shr 16) and 0xFF}.${(version shr 12) and 0xF}.${(version shr 8) and 0xF}.${version and 0xFF}"
