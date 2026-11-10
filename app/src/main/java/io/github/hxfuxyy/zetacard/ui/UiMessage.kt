package io.github.hxfuxyy.zetacard.ui

import android.content.res.Resources

sealed interface UiText {
    data class Res(val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(val id: Int, val count: Int) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Res -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args.toTypedArray())
        is Plural -> resources.getQuantityString(id, count, count)
    }
}

enum class MessageTone { INFO, SUCCESS, ERROR }

data class UiMessage(val text: UiText, val tone: MessageTone = MessageTone.INFO)
