// SPDX-License-Identifier: MIT
package com.eloquence.tts

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocalizedSamples {
    fun forTag(context: Context, tag: String): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration).getString(R.string.sample_text)
    }
}
