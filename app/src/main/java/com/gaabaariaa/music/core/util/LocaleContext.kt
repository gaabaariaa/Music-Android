package com.gaabaariaa.music.core.util

import android.content.Context
import android.content.res.Configuration
import com.gaabaariaa.music.domain.model.AppLanguage
import java.util.Locale

/** A context whose resources, number formats and layout direction follow the in-app language. */
fun Context.withLanguage(language: AppLanguage): Context {
    val tag = when (language) {
        AppLanguage.SYSTEM -> return this
        AppLanguage.FA -> "fa"
        AppLanguage.EN -> "en"
    }
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}
