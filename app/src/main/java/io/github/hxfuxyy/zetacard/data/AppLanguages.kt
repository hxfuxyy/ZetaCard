package io.github.hxfuxyy.zetacard.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

data class AppLanguage(val tag: String, val label: String)

object AppLanguages {
    private const val PREFERENCES = "language"
    private const val KEY_TAG = "tag"

    val all = listOf(
        AppLanguage("en", "English"),
        AppLanguage("uk", "Українська"),
        AppLanguage("ru", "Русский"),
        AppLanguage("es", "Español"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("fr", "Français"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("it", "Italiano"),
        AppLanguage("pl", "Polski"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("id", "Bahasa Indonesia"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("zh-CN", "简体中文"),
        AppLanguage("zh-TW", "繁體中文"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("ar", "العربية"),
    )

    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return if (locales == null || locales.isEmpty) "" else locales[0].toLanguageTag()
        }
        return stored(context)
    }

    fun select(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return
        }
        activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY_TAG, tag).commit()
        activity.recreate()
    }

    @JvmStatic
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = stored(base)
        if (tag.isEmpty()) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(tag))
        return base.createConfigurationContext(configuration)
    }

    fun matches(selected: String, tag: String): Boolean = selected.isNotEmpty() && selected.equals(tag, ignoreCase = true)

    private fun stored(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_TAG, "") ?: ""
}
