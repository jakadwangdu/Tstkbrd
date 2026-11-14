package app.aura.keyboard

import android.content.Context

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** Hands voice-typing results from VoiceInputActivity to the keyboard service (same process). */
object VoiceBridge {
    @Volatile
    var pending: String? = null

    @Volatile
    var onResult: (() -> Unit)? = null
}
