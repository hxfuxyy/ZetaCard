package io.github.hxfuxyy.zetacard.core

object CaptionRules {
    const val MAX_LENGTH = 32

    fun text(name: String?): String? {
        val clean = CardLabels.stripControl(name).take(MAX_LENGTH).trim()
        return clean.ifEmpty { null }
    }
}
