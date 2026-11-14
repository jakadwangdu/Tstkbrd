package app.aura.keyboard

import android.content.Context
import android.content.SharedPreferences

/** All user settings and small stored lists. Everything stays on the device. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    var haptic: Boolean
        get() = sp.getBoolean("haptic", true)
        set(v) { sp.edit().putBoolean("haptic", v).apply() }

    var sound: Boolean
        get() = sp.getBoolean("sound", false)
        set(v) { sp.edit().putBoolean("sound", v).apply() }

    var preview: Boolean
        get() = sp.getBoolean("preview", true)
        set(v) { sp.edit().putBoolean("preview", v).apply() }

    var numberRow: Boolean
        get() = sp.getBoolean("numberRow", false)
        set(v) { sp.edit().putBoolean("numberRow", v).apply() }

    var suggestions: Boolean
        get() = sp.getBoolean("suggestions", true)
        set(v) { sp.edit().putBoolean("suggestions", v).apply() }

    var autoCap: Boolean
        get() = sp.getBoolean("autoCap", true)
        set(v) { sp.edit().putBoolean("autoCap", v).apply() }

    var doubleSpace: Boolean
        get() = sp.getBoolean("doubleSpace", true)
        set(v) { sp.edit().putBoolean("doubleSpace", v).apply() }

    var trackpad: Boolean
        get() = sp.getBoolean("trackpad", true)
        set(v) { sp.edit().putBoolean("trackpad", v).apply() }

    var clipboard: Boolean
        get() = sp.getBoolean("clipboard", true)
        set(v) { sp.edit().putBoolean("clipboard", v).apply() }

    /** 0 = follow system, 1 = light, 2 = dark. */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }

    /** Key height in percent, 80 to 130. */
    var heightPct: Int
        get() = sp.getInt("heightPct", 100)
        set(v) { sp.edit().putInt("heightPct", v).apply() }

    var recents: String
        get() = sp.getString("recents", "") ?: ""
        set(v) { sp.edit().putString("recents", v).apply() }

    var clips: String
        get() = sp.getString("clips", "") ?: ""
        set(v) { sp.edit().putString("clips", v).apply() }

    var userWords: String
        get() = sp.getString("userWords", "") ?: ""
        set(v) { sp.edit().putString("userWords", v).apply() }
}

class Clip(val text: String, val pinned: Boolean)

/** Clipboard history: newest first, pinned clips stay, at most 20 unpinned. */
class ClipStore(private val prefs: Prefs) {
    private val items = ArrayList<Clip>()

    init {
        reload()
    }

    fun reload() {
        items.clear()
        val raw = prefs.clips
        if (raw.isEmpty()) return
        for (part in raw.split('\u001E')) {
            if (part.length > 1) items.add(Clip(part.substring(1), part[0] == '1'))
        }
    }

    private fun save() {
        prefs.clips = items.joinToString("\u001E") { (if (it.pinned) "1" else "0") + it.text }
    }

    fun all(): List<Clip> = items.sortedByDescending { it.pinned }

    fun add(text: String) {
        val clean = text.replace('\u001E', ' ')
        if (clean.isBlank()) return
        var pinned = false
        val iter = items.iterator()
        while (iter.hasNext()) {
            val c = iter.next()
            if (c.text == clean) {
                pinned = c.pinned
                iter.remove()
            }
        }
        items.add(0, Clip(clean, pinned))
        while (items.count { !it.pinned } > 20) {
            val idx = items.indexOfLast { !it.pinned }
            if (idx < 0) break
            items.removeAt(idx)
        }
        save()
    }

    fun togglePin(text: String) {
        val idx = items.indexOfFirst { it.text == text }
        if (idx < 0) return
        val c = items[idx]
        items[idx] = Clip(c.text, !c.pinned)
        save()
    }

    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        save()
    }
}
