package app.aura.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Setup screen: enable the keyboard, pick it, try it, and change settings. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private val themeNames = arrayOf("System", "Light", "Dark")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = dp(20)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(pad, pad, pad, pad)
        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)

        col.addView(text("Aura Keyboard", 28f, true))
        col.addView(text("A fast, private keyboard. It never uses the internet.", 14f, false))

        col.addView(space(16))
        col.addView(text("1. Turn it on", 18f, true))
        col.addView(button("Open keyboard settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        col.addView(space(12))
        col.addView(text("2. Make it your keyboard", 18f, true))
        col.addView(button("Choose keyboard") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        status = text("", 14f, false)
        col.addView(status)

        col.addView(space(12))
        val test = EditText(this)
        test.hint = "Tap here to try the keyboard"
        col.addView(test, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.addView(space(20))
        col.addView(text("Settings", 18f, true))
        addSwitch(col, "Vibrate on key press", { prefs.haptic }, { prefs.haptic = it })
        addSwitch(col, "Sound on key press", { prefs.sound }, { prefs.sound = it })
        addSwitch(col, "Show key preview", { prefs.preview }, { prefs.preview = it })
        addSwitch(col, "Number row", { prefs.numberRow }, { prefs.numberRow = it })
        addSwitch(col, "Word suggestions", { prefs.suggestions }, { prefs.suggestions = it })
        addSwitch(col, "Capitalize first letter of sentences", { prefs.autoCap }, { prefs.autoCap = it })
        addSwitch(col, "Double space for period", { prefs.doubleSpace }, { prefs.doubleSpace = it })
        addSwitch(col, "Slide on space bar to move cursor", { prefs.trackpad }, { prefs.trackpad = it })
        addSwitch(col, "Save clipboard history", { prefs.clipboard }, { prefs.clipboard = it })

        col.addView(space(12))
        val themeBtn = button("") { }
        themeBtn.text = "Theme: " + themeNames[prefs.theme]
        themeBtn.setOnClickListener {
            prefs.theme = (prefs.theme + 1) % 3
            themeBtn.text = "Theme: " + themeNames[prefs.theme]
        }
        col.addView(themeBtn)

        col.addView(space(12))
        val heightLabel = text("Key height: " + prefs.heightPct + "%", 14f, false)
        col.addView(heightLabel)
        val bar = SeekBar(this)
        bar.max = 50
        bar.progress = prefs.heightPct - 80
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.heightPct = 80 + progress
                heightLabel.text = "Key height: " + prefs.heightPct + "%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        col.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.addView(space(20))
        col.addView(text("Privacy", 18f, true))
        col.addView(button("Clear clipboard history") {
            prefs.clips = ""
            Toast.makeText(this, "Clipboard history cleared", Toast.LENGTH_SHORT).show()
        })
        col.addView(button("Clear learned words") {
            prefs.userWords = ""
            Toast.makeText(this, "Learned words cleared", Toast.LENGTH_SHORT).show()
        })
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        status.text = when {
            selected -> "Aura Keyboard is your active keyboard."
            enabled -> "Turned on. Now choose it as your keyboard."
            else -> "Not turned on yet."
        }
    }

    private fun text(s: String, sizeSp: Float, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = sizeSp
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, dp(4), 0, dp(4))
        return tv
    }

    private fun space(h: Int): android.view.View {
        val v = android.view.View(this)
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
        return v
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun addSwitch(parent: LinearLayout, label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val s = Switch(this)
        s.text = label
        s.isChecked = get()
        s.setPadding(0, dp(8), 0, dp(8))
        s.setOnCheckedChangeListener { _, checked -> set(checked) }
        parent.addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
