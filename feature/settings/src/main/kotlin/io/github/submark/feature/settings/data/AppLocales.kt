package io.github.submark.feature.settings.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.github.submark.core.data.settings.AppLanguage

/**
 * Per-app language via AppCompat (system per-app locales on API 33+, the AppCompat metadata service below).
 * The system-level choice is the source of truth because users can also change it in system settings.
 */
internal object AppLocales {

    fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.SIMPLIFIED_CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** null when the platform reports a locale SubMark does not offer. */
    fun current(): AppLanguage? = fromTag(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    fun fromTag(tags: String): AppLanguage? {
        val first = tags.split(',').firstOrNull()?.trim().orEmpty()
        return when {
            first.isEmpty() -> AppLanguage.SYSTEM
            first.startsWith("zh", ignoreCase = true) -> AppLanguage.SIMPLIFIED_CHINESE
            first.startsWith("en", ignoreCase = true) -> AppLanguage.ENGLISH
            else -> null
        }
    }
}
