package app.aura.keyboard

import android.graphics.RectF

const val T_CHAR = 0
const val T_SHIFT = 1
const val T_BACK = 2
const val T_LAYER = 3
const val T_SPACE = 4
const val T_ENTER = 5
const val T_EMOJI = 6
const val T_SPACER = 7

class Key(
    val type: Int,
    val label: String,
    val weight: Float = 1f,
    val alts: String = "",
    val target: Int = 0
) {
    val rect = RectF()
    var shown: String = label
    var pressed = false
    var hint: String = ""
}

/** Key rows for each layer: 0 = letters, 1 = numbers and symbols, 2 = more symbols. */
object Layouts {
    private val ALT: Map<Char, String> = mapOf(
        'a' to "àáâäæãåā",
        'c' to "çćč",
        'e' to "èéêëēėę",
        'i' to "ìíîïīį",
        'l' to "ł",
        'n' to "ñń",
        'o' to "òóôöõøœō",
        's' to "ßśš",
        'u' to "ùúûüū",
        'y' to "ÿý",
        'z' to "žźż",
        '.' to "…!?:;,",
        ',' to ";:",
        '-' to "–—_",
        '"' to "“”«»",
        '\'' to "‘’`",
        '?' to "¿",
        '!' to "¡",
        '(' to "[{<",
        ')' to "]}>",
        '/' to "\\|",
        '=' to "≠≈",
        '+' to "±",
        '*' to "×†",
        '%' to "‰",
        '₹' to "\$€£¥"
    )

    private val DIGIT: Map<Char, String> = mapOf(
        'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
        'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0"
    )

    private fun charKey(c: Char, numberRow: Boolean, letters: Boolean): Key {
        var alts = ALT[c] ?: ""
        var hint = ""
        val d = DIGIT[c]
        if (letters && d != null && !numberRow) {
            alts = d + alts
            hint = d
        }
        val k = Key(T_CHAR, c.toString(), 1f, alts)
        k.hint = hint
        return k
    }

    private fun charRow(chars: String, numberRow: Boolean, letters: Boolean): MutableList<Key> {
        val row = ArrayList<Key>()
        for (c in chars) row.add(charKey(c, numberRow, letters))
        return row
    }

    private fun bottom(layer: Int, enterLabel: String?): List<Key> {
        val row = ArrayList<Key>()
        if (layer == 0) row.add(Key(T_LAYER, "?123", 1.5f, "", 1))
        else row.add(Key(T_LAYER, "ABC", 1.5f, "", 0))
        row.add(Key(T_CHAR, ",", 1f, ALT[','] ?: ""))
        row.add(Key(T_EMOJI, "", 1f))
        row.add(Key(T_SPACE, "", 4f))
        row.add(Key(T_CHAR, ".", 1f, ALT['.'] ?: ""))
        row.add(Key(T_ENTER, enterLabel ?: "", 1.5f))
        return row
    }

    fun rows(layer: Int, numberRow: Boolean, enterLabel: String?): List<List<Key>> {
        val out = ArrayList<List<Key>>()
        when (layer) {
            0 -> {
                if (numberRow) out.add(charRow("1234567890", true, false))
                out.add(charRow("qwertyuiop", numberRow, true))
                val r2 = ArrayList<Key>()
                r2.add(Key(T_SPACER, "", 0.5f))
                r2.addAll(charRow("asdfghjkl", numberRow, true))
                r2.add(Key(T_SPACER, "", 0.5f))
                out.add(r2)
                val r3 = ArrayList<Key>()
                r3.add(Key(T_SHIFT, "", 1.5f))
                r3.addAll(charRow("zxcvbnm", numberRow, true))
                r3.add(Key(T_BACK, "", 1.5f))
                out.add(r3)
            }
            1 -> {
                out.add(charRow("1234567890", true, false))
                out.add(charRow("@#₹_&-+()/", true, false))
                val r3 = ArrayList<Key>()
                r3.add(Key(T_LAYER, "=\\<", 1.5f, "", 2))
                r3.addAll(charRow("*\"':;!?", true, false))
                r3.add(Key(T_BACK, "", 1.5f))
                out.add(r3)
            }
            else -> {
                out.add(charRow("~`|•√π÷×¶∆", true, false))
                out.add(charRow("£¢€¥^°={}\\", true, false))
                val r3 = ArrayList<Key>()
                r3.add(Key(T_LAYER, "?123", 1.5f, "", 1))
                r3.addAll(charRow("%©®™✓[]", true, false))
                r3.add(Key(T_BACK, "", 1.5f))
                out.add(r3)
            }
        }
        out.add(bottom(layer, enterLabel))
        return out
    }
}
