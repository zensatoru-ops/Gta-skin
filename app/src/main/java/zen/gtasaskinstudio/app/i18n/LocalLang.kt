package zen.gtasaskinstudio.app.i18n

import androidx.compose.runtime.compositionLocalOf

/** Bahasa aktif untuk pohon Compose. Dipisah dari AppStrings.kt agar string bisa dites tanpa Compose. */
val LocalLang = compositionLocalOf { Lang.ID }
