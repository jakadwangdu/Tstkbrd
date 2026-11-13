package app.aura.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import kotlin.math.hypot

/**
 * Aura Keyboard OS: native Android InputMethodService.
 * Dark espresso keys, uniform keycaps, a space bar that turns into a cursor trackpad
 * (hold 180 ms or drag more than 6 dp), double-tap space for a period,
 * and a backspace that deletes whole words on hold or left swipe.
 *
 * Setup:
 *  1. res/xml/method.xml:
 *       <input-method xmlns:android="http://schemas.android.com/apk/res/android" />
 *  2. AndroidManifest.xml, inside <application>:
 *       <service android:name=".AuraKeyboardIME"
 *                android:label="Aura Keyboard"
 *                android:permission="android.permission.BIND_INPUT_METHOD"
 *                android:exported="true">
 *           <intent-filter><action android:name="android.view.InputMethod" /></intent-filter>
 *           <meta-data android:name="android.view.im" android:resource="@xml/method" />
 *       </service>
 *  3. Install, then enable "Aura Keyboard" in Settings > System > Keyboard.
 */
class AuraKeyboardIME : InputMethodService() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private val letterKeys = mutableListOf<Button>()
    private var shift = false
    private var numbers = false
    private var lastSpaceAt = 0L

    private val keyHeightDp = 44
    private val gapDp = 3
    private val stepDp = 9      // dp of drag per caret step
    private val lineDp = 22     // dp of vertical drag per line jump
    private val tapMs = 180L
    private val moveSlopDp = 6

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(12), dp(8), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF211E1B.toInt(), 0xFF171513.toInt())
            ).apply {
                val r = dp(34).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        render()
        return root
    }

    // ---- layout -----------------------------------------------------------

    private fun render() {
        root.removeAllViews()
        letterKeys.clear()
        val rows = if (numbers) {
            listOf("1234567890", "-/:;()₹&@\"", ".,?!'")
        } else {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        root.addView(keyRow(rows[0]))
        root.addView(keyRow(rows[1]))
        root.addView(thirdRow(rows[2]))
        root.addView(bottomRow())
    }

    private fun newRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        weightSum = 10f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(keyHeightDp)
        ).also { it.topMargin = dp(gapDp * 2) }
    }

    private fun keyRow(chars: String): LinearLayout {
        val row = newRow()
        chars.forEach { row.addView(letterKey(it.toString())) }
        return row
    }

    private fun thirdRow(chars: String): LinearLayout {
        val row = newRow()
        if (numbers) {
            row.addView(fnKey("#+=", 1.5f) { /* extend with a symbols layer */ })
        } else {
            row.addView(fnKey("↑", 1.5f) { shift = !shift; refreshCase() })
        }
        chars.forEach { row.addView(letterKey(it.toString())) }
        row.addView(backspaceKey(1.5f))
        return row
    }

    private fun bottomRow(): LinearLayout {
        val row = newRow()
        row.addView(fnKey(if (numbers) "ABC" else "123", 1.4f) { numbers = !numbers; render() })
        row.addView(fnKey("🐌", 1.1f) { /* open emoji drawer here */ })
        row.addView(spaceKey(4.6f))
        row.addView(fnKey("Return", 1.9f) { currentInputConnection?.commitText("\n", 1) })
        return row
    }

    // ---- keys -------------------------------------------------------------

    private fun baseKey(label: String, weight: Float, fn: Boolean): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(0xFFECE6DC.toInt())
            textSize = if (label.length > 2) 13f else 17f
            minWidth = 0; minHeight = 0
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(if (fn) 0xFF2A2723.toInt() else 0xFF34302B.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                .also { it.setMargins(dp(gapDp), 0, dp(gapDp), 0) }
        }

    private fun letterKey(ch: String): Button {
        val b = baseKey(if (shift) ch.uppercase() else ch, 1f, false)
        if (!numbers) letterKeys.add(b)
        b.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val out = if (shift && !numbers) ch.uppercase() else ch
            currentInputConnection?.commitText(out, 1)
            if (shift) { shift = false; refreshCase() }
        }
        return b
    }

    private fun fnKey(label: String, weight: Float, action: () -> Unit): Button =
        baseKey(label, weight, true).also { b ->
            b.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                action()
            }
        }

    private fun refreshCase() {
        val chars = "qwertyuiopasdfghjklzxcvbnm"
        letterKeys.forEachIndexed { i, b ->
            val c = chars[i].toString()
            b.text = if (shift) c.uppercase() else c
        }
    }

    // ---- space bar trackpad ----------------------------------------------

    private fun sendKey(code: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun spaceKey(weight: Float): Button {
        val b = baseKey("space", weight, true)
        var downX = 0f; var downY = 0f; var lastX = 0f; var lastY = 0f
        var accX = 0f; var accY = 0f
        var tracking = false

        val startTracking = Runnable {
            tracking = true
            b.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            b.text = "◄ ── ● ── ►"
        }

        b.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                    accX = 0f; accY = 0f; tracking = false
                    ui.postDelayed(startTracking, tapMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!tracking && hypot(e.x - downX, e.y - downY) > dp(moveSlopDp)) {
                        ui.removeCallbacks(startTracking)
                        startTracking.run()
                        accX = e.x - downX; accY = e.y - downY
                    } else if (tracking) {
                        accX += e.x - lastX; accY += e.y - lastY
                    }
                    lastX = e.x; lastY = e.y
                    if (tracking) {
                        val step = dp(stepDp)
                        var moved = false
                        while (accX >= step) { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT); accX -= step; moved = true }
                        while (accX <= -step) { sendKey(KeyEvent.KEYCODE_DPAD_LEFT); accX += step; moved = true }
                        val line = dp(lineDp)
                        while (accY >= line) { sendKey(KeyEvent.KEYCODE_DPAD_DOWN); accY -= line; moved = true }
                        while (accY <= -line) { sendKey(KeyEvent.KEYCODE_DPAD_UP); accY += line; moved = true }
                        if (moved) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(startTracking)
                    val quick = e.eventTime - e.downTime < tapMs
                    if (!tracking && e.actionMasked == MotionEvent.ACTION_UP && quick) tapSpace()
                    tracking = false
                    b.text = "space"
                    true
                }
                else -> false
            }
        }
        return b
    }

    private fun tapSpace() {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val doubleTap = now - lastSpaceAt < 400 && before.length == 2 &&
            before[1] == ' ' && !before[0].isWhitespace() && before[0] !in ".!?,"
        if (doubleTap) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
        } else {
            ic.commitText(" ", 1)
        }
        lastSpaceAt = now
    }

    // ---- backspace: tap, hold and swipe delete -----------------------------

    private fun deleteChar() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        var n = before.length
        while (n > 0 && before[n - 1].isWhitespace()) n--
        while (n > 0 && !before[n - 1].isWhitespace()) n--
        val count = before.length - n
        if (count > 0) ic.deleteSurroundingText(count, 0)
    }

    private fun backspaceKey(weight: Float): Button {
        val b = baseKey("⌫", weight, true)
        var startX = 0f
        var wordsSwiped = 0
        var held = false
        val repeat = object : Runnable {
            override fun run() {
                held = true
                deleteWord()
                b.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                ui.postDelayed(this, 130)
            }
        }
        b.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x; wordsSwiped = 0; held = false
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    deleteChar()
                    ui.postDelayed(repeat, 420)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val steps = ((startX - e.x) / dp(26)).toInt()
                    if (steps > wordsSwiped) {
                        ui.removeCallbacks(repeat)
                        while (wordsSwiped < steps) { deleteWord(); wordsSwiped++ }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(repeat)
                    true
                }
                else -> false
            }
        }
        return b
    }
}
