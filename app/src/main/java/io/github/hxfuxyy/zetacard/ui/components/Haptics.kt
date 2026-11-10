package io.github.hxfuxyy.zetacard.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

class Haptics(private val feedback: HapticFeedback) {
    fun click() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    fun toggle(on: Boolean) = feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun threshold() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
