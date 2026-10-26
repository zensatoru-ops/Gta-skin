package zen.gtasaskinstudio.app.i18n

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStringsTest {
    @Test
    fun everyKeyHasIndonesianAndEnglish() {
        for (key in StrKey.entries) {
            for (lang in Lang.entries) {
                val text = lang.str(key)
                assertTrue("${lang} missing ${key}", text.isNotBlank())
                assertNotEquals("${lang} falls back to key name for ${key}", key.name, text)
            }
        }
    }

    @Test
    fun errorMessagesDifferBetweenLanguages() {
        val errors = listOf(StrKey.ErrorCannotOpen, StrKey.ErrorCannotRead, StrKey.ErrorUnknown, StrKey.ErrorRetry)
        for (key in errors) {
            assertNotEquals(Lang.ID.str(key), Lang.EN.str(key))
        }
    }
}
