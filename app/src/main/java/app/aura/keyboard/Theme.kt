package app.aura.keyboard

import android.content.Context
import android.content.res.Configuration

class Palette(
    val bg: Int,
    val key: Int,
    val fn: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val onAccent: Int,
    val pressed: Int,
    val popup: Int
)

object Theme {
    private val DARK = Palette(
        bg = 0xFF1C1A18.toInt(),
        key = 0xFF34302B.toInt(),
        fn = 0xFF272421.toInt(),
        text = 0xFFECE6DC.toInt(),
        muted = 0xFF9C9387.toInt(),
        accent = 0xFFD9A45B.toInt(),
        onAccent = 0xFF211E1B.toInt(),
        pressed = 0xFF4A443D.toInt(),
        popup = 0xFF4A443D.toInt()
    )

    private val LIGHT = Palette(
        bg = 0xFFE6DFD3.toInt(),
        key = 0xFFFBF8F2.toInt(),
        fn = 0xFFCFC6B8.toInt(),
        text = 0xFF2A2622.toInt(),
        muted = 0xFF6F665B.toInt(),
        accent = 0xFFB26A1E.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        pressed = 0xFFE9E2D6.toInt(),
        popup = 0xFFFFFFFF.toInt()
    )

    fun palette(prefs: Prefs, context: Context): Palette {
        val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val night = mask == Configuration.UI_MODE_NIGHT_YES
        return when (prefs.theme) {
            1 -> LIGHT
            2 -> DARK
            else -> if (night) DARK else LIGHT
        }
    }
}
