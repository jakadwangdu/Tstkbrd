package app.aura.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The whole keyboard is one view that draws itself with Canvas: a suggestion strip on top,
 * then the key rows. No child views, no allocation while typing, and every finger is tracked
 * separately so fast two-thumb typing never drops a key.
 */
class KeyboardView(context: Context, private val prefs: Prefs) : View(context) {

    interface Actions {
        fun onText(text: String)
        fun onBackspace(word: Boolean)
        fun onEnter()
        fun onShift()
        fun onSpace()
        fun onLayer(layer: Int)
        fun onEmoji()
        fun onMove(dx: Int, dy: Int)
        fun onSuggestion(index: Int)
        fun onToolbar(which: Int)
    }

    var actions: Actions? = null
    var previewEnabled = true

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val uiHandler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var palette: Palette = Theme.palette(prefs, context)
        private set

    private var rows: List<List<Key>> = emptyList()
    private var layer = 0
    private var shift = 0
    private var numberRow = false
    private var enterLabel: String? = null
    private var suggestions: List<String> = emptyList()
    private var trackpadActive = false
    private var previewKey: Key? = null
    private var altPopup: AltPopup? = null

    private val padTop = dp(4f)
    private val padBottom = dp(8f)
    private val gapV = dp(8f)
    private val padX = dp(3f)
    private val gapX = dp(4.5f)
    private val corner = dp(8f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(1.8f)
    }
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1f) }
    private val keyText = textPaint(22f, false)
    private val fnText = textPaint(14f, false)
    private val hintText = textPaint(10f, false)
    private val sugText = textPaint(17f, false)
    private val sugBold = textPaint(17f, true)
    private val previewText = textPaint(32f, false)
    private val keyOff = baselineOffset(keyText)
    private val fnOff = baselineOffset(fnText)
    private val sugOff = baselineOffset(sugText)
    private val previewOff = baselineOffset(previewText)
    private val tmp = RectF()
    private val path = Path()

    private class AltPopup(val items: String, val left: Float, val top: Float, val cell: Float, var index: Int)

    private class Pointer(val id: Int, var key: Key?, val downX: Float, val downY: Float) {
        var lastX = downX
        var lastY = downY
        var inStrip = false
        var alt = false
        var trackpad = false
        var ax = 0f
        var ay = 0f
        var swiped = 0
        var repeatCount = 0
        var longRunnable: Runnable? = null
        var repeatRunnable: Runnable? = null
    }

    private val pointers = SparseArray<Pointer>()

    private fun textPaint(size: Float, bold: Boolean): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(size)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun baselineOffset(p: Paint): Float {
        val m = p.fontMetrics
        return -(m.ascent + m.descent) / 2f
    }

    // ---- public state ------------------------------------------------------

    fun refresh() {
        palette = Theme.palette(prefs, context)
        numberRow = prefs.numberRow
        rebuild()
    }

    fun setLayer(l: Int) {
        if (layer != l) {
            layer = l
            rebuild()
        }
    }

    fun setShift(s: Int) {
        if (shift != s) {
            shift = s
            applyShown()
            invalidate()
        }
    }

    fun setEnterLabel(label: String?) {
        if (label != enterLabel) {
            enterLabel = label
            rebuild()
        }
    }

    fun setSuggestions(list: List<String>) {
        if (list != suggestions) {
            suggestions = list
            invalidate()
        }
    }

    private fun rebuild() {
        rows = Layouts.rows(layer, numberRow, enterLabel)
        applyShown()
        requestLayout()
        if (width > 0) layoutKeys(width)
        invalidate()
    }

    private fun applyShown() {
        val up = shift != 0 && layer == 0
        for (row in rows) {
            for (k in row) {
                if (k.type == T_CHAR) {
                    k.shown = if (up && k.label.isNotEmpty() && k.label[0].isLetter()) k.label.uppercase() else k.label
                }
            }
        }
    }

    // ---- geometry ---------------------------------------------------------

    private fun stripH(): Float = dp(44f)

    private fun nominalRowH(): Float = dp(46f) * prefs.heightPct / 100f

    private fun rowsTotal(): Float {
        val base = if (numberRow) 5 else 4
        return base * nominalRowH() + (base - 1) * gapV
    }

    private fun rowH(): Float {
        val n = max(rows.size, 1)
        return (rowsTotal() - (n - 1) * gapV) / n
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (stripH() + padTop + rowsTotal() + padBottom).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys(w)
    }

    private fun layoutKeys(w: Int) {
        val unit = (w - 2 * padX - 9 * gapX) / 10f
        val rh = rowH()
        val y0 = stripH() + padTop
        for ((ri, row) in rows.withIndex()) {
            val top = y0 + ri * (rh + gapV)
            var x = padX
            for ((i, k) in row.withIndex()) {
                val kw = if (k.type == T_SPACER) (unit + gapX) * k.weight else unit * k.weight + gapX * (k.weight - 1f)
                k.rect.set(x, top, x + kw, top + rh)
                x += kw
                val next = row.getOrNull(i + 1)
                if (k.type != T_SPACER && next != null && next.type != T_SPACER) x += gapX
            }
        }
    }

    private fun findKey(x: Float, y: Float): Key? {
        if (rows.isEmpty()) return null
        val rh = rowH()
        val y0 = stripH() + padTop
        var r = ((y - y0) / (rh + gapV)).toInt()
        if (r < 0) r = 0
        if (r > rows.size - 1) r = rows.size - 1
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        for (k in rows[r]) {
            if (k.type == T_SPACER) continue
            val d = if (x < k.rect.left) k.rect.left - x else if (x > k.rect.right) x - k.rect.right else 0f
            if (d < bestD) {
                bestD = d
                best = k
            }
        }
        return best
    }

    // ---- drawing ----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val pal = palette
        canvas.drawColor(pal.bg)
        drawStrip(canvas, pal)
        keyText.color = pal.text
        hintText.color = pal.muted
        for (row in rows) {
            for (k in row) {
                if (k.type == T_SPACER) continue
                fill.color = when {
                    k.type == T_SHIFT && shift == 2 -> pal.accent
                    k.pressed -> pal.pressed
                    k.type == T_ENTER -> pal.accent
                    k.type == T_CHAR || k.type == T_SPACE -> pal.key
                    else -> pal.fn
                }
                canvas.drawRoundRect(k.rect, corner, corner, fill)
                drawKeyContent(canvas, k, pal)
            }
        }
        if (previewEnabled && prefs.preview) previewKey?.let { drawPreview(canvas, it, pal) }
        altPopup?.let { drawAlt(canvas, it, pal) }
    }

    private fun drawKeyContent(c: Canvas, k: Key, pal: Palette) {
        val cx = k.rect.centerX()
        val cy = k.rect.centerY()
        val s = dp(8f)
        when (k.type) {
            T_CHAR -> {
                c.drawText(k.shown, cx, cy + keyOff, keyText)
                if (k.hint.isNotEmpty()) c.drawText(k.hint, k.rect.right - dp(8f), k.rect.top + dp(14f), hintText)
            }
            T_SHIFT -> {
                val on = shift != 0
                stroke.color = if (shift == 2) pal.onAccent else pal.text
                stroke.style = if (on) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                path.reset()
                path.moveTo(cx, cy - s)
                path.lineTo(cx + s, cy)
                path.lineTo(cx + s * 0.45f, cy)
                path.lineTo(cx + s * 0.45f, cy + s * 0.8f)
                path.lineTo(cx - s * 0.45f, cy + s * 0.8f)
                path.lineTo(cx - s * 0.45f, cy)
                path.lineTo(cx - s, cy)
                path.close()
                c.drawPath(path, stroke)
                stroke.style = Paint.Style.STROKE
            }
            T_BACK -> {
                stroke.color = pal.text
                path.reset()
                path.moveTo(cx - s * 1.1f, cy)
                path.lineTo(cx - s * 0.4f, cy - s * 0.8f)
                path.lineTo(cx + s * 1.1f, cy - s * 0.8f)
                path.lineTo(cx + s * 1.1f, cy + s * 0.8f)
                path.lineTo(cx - s * 0.4f, cy + s * 0.8f)
                path.close()
                c.drawPath(path, stroke)
                c.drawLine(cx + s * 0.05f, cy - s * 0.35f, cx + s * 0.75f, cy + s * 0.35f, stroke)
                c.drawLine(cx + s * 0.75f, cy - s * 0.35f, cx + s * 0.05f, cy + s * 0.35f, stroke)
            }
            T_LAYER -> {
                fnText.color = pal.text
                c.drawText(k.label, cx, cy + fnOff, fnText)
            }
            T_SPACE -> {
                if (trackpadActive) {
                    stroke.color = pal.accent
                    drawChevron(c, cx - dp(30f), cy, -1)
                    drawChevron(c, cx + dp(30f), cy, 1)
                } else {
                    fnText.color = pal.muted
                    c.drawText("Aura", cx, cy + fnOff, fnText)
                }
            }
            T_ENTER -> {
                val label = enterLabel
                if (label != null) {
                    fnText.color = pal.onAccent
                    c.drawText(label, cx, cy + fnOff, fnText)
                } else {
                    stroke.color = pal.onAccent
                    path.reset()
                    path.moveTo(cx + s, cy - s * 0.8f)
                    path.lineTo(cx + s, cy + s * 0.2f)
                    path.lineTo(cx - s, cy + s * 0.2f)
                    path.moveTo(cx - s * 0.35f, cy - s * 0.35f)
                    path.lineTo(cx - s, cy + s * 0.2f)
                    path.lineTo(cx - s * 0.35f, cy + s * 0.75f)
                    c.drawPath(path, stroke)
                }
            }
            T_EMOJI -> {
                stroke.color = pal.text
                drawSmiley(c, cx, cy, dp(8.5f), pal.text)
            }
        }
    }

    private fun drawChevron(c: Canvas, x: Float, cy: Float, dir: Int) {
        val s = dp(6f)
        path.reset()
        path.moveTo(x - dir * s * 0.4f, cy - s)
        path.lineTo(x + dir * s * 0.4f, cy)
        path.lineTo(x - dir * s * 0.4f, cy + s)
        c.drawPath(path, stroke)
    }

    private fun drawSmiley(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color
        fill.color = color
        c.drawCircle(cx, cy, s, stroke)
        c.drawCircle(cx - s * 0.35f, cy - s * 0.25f, s * 0.11f, fill)
        c.drawCircle(cx + s * 0.35f, cy - s * 0.25f, s * 0.11f, fill)
        tmp.set(cx - s * 0.55f, cy - s * 0.5f, cx + s * 0.55f, cy + s * 0.55f)
        c.drawArc(tmp, 25f, 130f, false, stroke)
    }

    private fun drawStrip(c: Canvas, pal: Palette) {
        val sh = stripH()
        val w = width.toFloat()
        if (suggestions.isEmpty()) {
            stroke.color = pal.text
            val s = dp(9f)
            val cy = sh / 2f
            // clipboard
            var cx = w * 0.5f / 4f
            tmp.set(cx - s * 0.75f, cy - s * 0.85f, cx + s * 0.75f, cy + s * 0.95f)
            c.drawRoundRect(tmp, s * 0.2f, s * 0.2f, stroke)
            tmp.set(cx - s * 0.35f, cy - s * 1.1f, cx + s * 0.35f, cy - s * 0.6f)
            c.drawRoundRect(tmp, s * 0.15f, s * 0.15f, stroke)
            c.drawLine(cx - s * 0.4f, cy, cx + s * 0.4f, cy, stroke)
            c.drawLine(cx - s * 0.4f, cy + s * 0.45f, cx + s * 0.4f, cy + s * 0.45f, stroke)
            // emoji
            cx = w * 1.5f / 4f
            drawSmiley(c, cx, cy, s, pal.text)
            // microphone
            cx = w * 2.5f / 4f
            stroke.color = pal.text
            tmp.set(cx - s * 0.35f, cy - s * 0.95f, cx + s * 0.35f, cy + s * 0.2f)
            c.drawRoundRect(tmp, s * 0.35f, s * 0.35f, stroke)
            tmp.set(cx - s * 0.7f, cy - s * 0.45f, cx + s * 0.7f, cy + s * 0.55f)
            c.drawArc(tmp, 0f, 180f, false, stroke)
            c.drawLine(cx, cy + s * 0.55f, cx, cy + s * 0.95f, stroke)
            c.drawLine(cx - s * 0.35f, cy + s * 0.95f, cx + s * 0.35f, cy + s * 0.95f, stroke)
            // settings gear
            cx = w * 3.5f / 4f
            c.drawCircle(cx, cy, s * 0.62f, stroke)
            c.drawCircle(cx, cy, s * 0.24f, stroke)
            for (i in 0 until 8) {
                val a = i * (Math.PI / 4.0).toFloat()
                c.drawLine(
                    cx + cos(a) * s * 0.62f, cy + sin(a) * s * 0.62f,
                    cx + cos(a) * s * 0.98f, cy + sin(a) * s * 0.98f, stroke
                )
            }
        } else {
            divider.color = pal.fn
            val order = intArrayOf(1, 0, 2)
            for (slot in 0 until 3) {
                val idx = order[slot]
                if (idx >= suggestions.size) continue
                val cx = w * (slot + 0.5f) / 3f
                val paint = if (idx == 0) sugBold else sugText
                paint.color = if (idx == 0) pal.text else pal.muted
                c.drawText(fit(suggestions[idx], paint, w / 3f - dp(16f)), cx, sh / 2f + sugOff, paint)
            }
            for (i in 1..2) c.drawLine(w * i / 3f, sh * 0.25f, w * i / 3f, sh * 0.75f, divider)
        }
    }

    private fun fit(s: String, p: Paint, maxW: Float): String {
        val n = p.breakText(s, true, maxW, null)
        return if (n >= s.length) s else s.substring(0, max(n - 1, 1)) + "…"
    }

    private fun drawPreview(c: Canvas, k: Key, pal: Palette) {
        val bw = k.rect.width() * 1.3f
        val bh = rowH() * 1.25f
        var left = k.rect.centerX() - bw / 2f
        left = max(dp(2f), min(left, width - bw - dp(2f)))
        val top = max(0f, k.rect.top - bh - dp(4f))
        tmp.set(left, top, left + bw, top + bh)
        fill.color = pal.popup
        c.drawRoundRect(tmp, dp(10f), dp(10f), fill)
        previewText.color = pal.text
        c.drawText(k.shown, tmp.centerX(), tmp.centerY() + previewOff, previewText)
    }

    private fun drawAlt(c: Canvas, a: AltPopup, pal: Palette) {
        val h = rowH()
        val total = a.cell * a.items.length
        tmp.set(a.left, a.top, a.left + total, a.top + h)
        fill.color = pal.popup
        c.drawRoundRect(tmp, dp(10f), dp(10f), fill)
        val up = shift != 0 && layer == 0
        for (i in a.items.indices) {
            val x0 = a.left + i * a.cell
            if (i == a.index) {
                tmp.set(x0 + dp(2f), a.top + dp(3f), x0 + a.cell - dp(2f), a.top + h - dp(3f))
                fill.color = pal.accent
                c.drawRoundRect(tmp, dp(8f), dp(8f), fill)
                keyText.color = pal.onAccent
            } else {
                keyText.color = pal.text
            }
            val ch = a.items[i].toString()
            c.drawText(if (up) ch.uppercase() else ch, x0 + a.cell / 2f, a.top + h / 2f + keyOff, keyText)
        }
        keyText.color = pal.text
    }

    // ---- touch --------------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i), e.getX(i), e.getY(i), false)
            }
            MotionEvent.ACTION_CANCEL -> {
                val ids = IntArray(pointers.size()) { pointers.keyAt(it) }
                for (id in ids) up(id, 0f, 0f, true)
            }
        }
        return true
    }

    private fun feedback(sound: Int) {
        if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (prefs.sound) audio.playSoundEffect(sound, 0.6f)
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (y < stripH()) {
            val p = Pointer(id, null, x, y)
            p.inStrip = true
            pointers.put(id, p)
            return
        }
        val k = findKey(x, y) ?: return
        val p = Pointer(id, k, x, y)
        pointers.put(id, p)
        k.pressed = true
        when (k.type) {
            T_CHAR -> {
                feedback(AudioManager.FX_KEYPRESS_STANDARD)
                previewKey = k
                if (k.alts.isNotEmpty()) {
                    val r = Runnable { if (pointers.get(id) === p && !p.alt) startAlt(p) }
                    p.longRunnable = r
                    uiHandler.postDelayed(r, 380)
                }
            }
            T_SHIFT -> {
                feedback(AudioManager.FX_KEYPRESS_STANDARD)
                actions?.onShift()
            }
            T_BACK -> {
                feedback(AudioManager.FX_KEYPRESS_DELETE)
                actions?.onBackspace(false)
                val r = object : Runnable {
                    override fun run() {
                        if (pointers.get(id) !== p) return
                        p.repeatCount++
                        actions?.onBackspace(p.repeatCount > 12)
                        if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        uiHandler.postDelayed(this, if (p.repeatCount > 12) 130L else 50L)
                    }
                }
                p.repeatRunnable = r
                uiHandler.postDelayed(r, 380)
            }
            T_SPACE -> feedback(AudioManager.FX_KEYPRESS_SPACEBAR)
            T_ENTER -> feedback(AudioManager.FX_KEYPRESS_RETURN)
            else -> feedback(AudioManager.FX_KEYPRESS_STANDARD)
        }
        invalidate()
    }

    private fun startAlt(p: Pointer) {
        val k = p.key ?: return
        p.alt = true
        previewKey = null
        val cell = dp(44f)
        val total = cell * k.alts.length
        var left = k.rect.centerX() - total / 2f
        left = max(dp(2f), min(left, width - total - dp(2f)))
        val top = max(0f, k.rect.top - rowH() - dp(8f))
        val idx = ((p.lastX - left) / cell).toInt()
        altPopup = AltPopup(k.alts, left, top, cell, max(0, min(k.alts.length - 1, idx)))
        if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = pointers.get(id) ?: return
        val dx = x - p.lastX
        val dy = y - p.lastY
        p.lastX = x
        p.lastY = y
        val k = p.key ?: return
        if (p.alt) {
            val a = altPopup ?: return
            val idx = ((x - a.left) / a.cell).toInt()
            val n = max(0, min(a.items.length - 1, idx))
            if (n != a.index) {
                a.index = n
                if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                invalidate()
            }
            return
        }
        when (k.type) {
            T_CHAR -> {
                val nk = findKey(x, y)
                if (nk != null && nk !== k && nk.type == T_CHAR && y >= stripH()) {
                    k.pressed = false
                    nk.pressed = true
                    p.key = nk
                    previewKey = nk
                    p.longRunnable?.let { uiHandler.removeCallbacks(it) }
                    p.longRunnable = null
                    invalidate()
                }
            }
            T_SPACE -> {
                if (!p.trackpad && prefs.trackpad && hypot(x - p.downX, y - p.downY) > dp(10f)) {
                    p.trackpad = true
                    trackpadActive = true
                    p.ax = 0f
                    p.ay = 0f
                    if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    invalidate()
                } else if (p.trackpad) {
                    p.ax += dx
                    p.ay += dy
                    val stepX = dp(9f)
                    val stepY = dp(34f)
                    val sx = (p.ax / stepX).toInt()
                    val sy = (p.ay / stepY).toInt()
                    if (sx != 0 || sy != 0) {
                        p.ax -= sx * stepX
                        p.ay -= sy * stepY
                        actions?.onMove(sx, sy)
                        if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            T_BACK -> {
                val steps = ((p.downX - x) / dp(30f)).toInt()
                if (steps > p.swiped) {
                    p.repeatRunnable?.let { uiHandler.removeCallbacks(it) }
                    p.repeatRunnable = null
                    while (p.swiped < steps) {
                        actions?.onBackspace(true)
                        p.swiped++
                    }
                }
            }
        }
    }

    private fun up(id: Int, x: Float, y: Float, cancel: Boolean) {
        val p = pointers.get(id) ?: return
        pointers.remove(id)
        p.longRunnable?.let { uiHandler.removeCallbacks(it) }
        p.repeatRunnable?.let { uiHandler.removeCallbacks(it) }
        val k = p.key
        if (k != null) k.pressed = false
        if (previewKey === k) previewKey = null
        if (p.trackpad) trackpadActive = false
        if (p.alt) {
            val a = altPopup
            altPopup = null
            if (!cancel && a != null) {
                val ch = a.items[a.index].toString()
                val up = shift != 0 && layer == 0
                actions?.onText(if (up) ch.uppercase() else ch)
            }
        } else if (!cancel) {
            if (p.inStrip) {
                if (y < stripH()) stripTap(x)
            } else if (k != null) {
                when (k.type) {
                    T_CHAR -> actions?.onText(k.shown)
                    T_SPACE -> if (!p.trackpad) actions?.onSpace()
                    T_ENTER -> actions?.onEnter()
                    T_LAYER -> actions?.onLayer(k.target)
                    T_EMOJI -> actions?.onEmoji()
                }
            }
        }
        invalidate()
    }

    private fun stripTap(x: Float) {
        val w = max(width, 1).toFloat()
        if (suggestions.isEmpty()) {
            val which = max(0, min(3, (x / (w / 4f)).toInt()))
            if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            actions?.onToolbar(which)
        } else {
            val slot = max(0, min(2, (x / (w / 3f)).toInt()))
            val idx = intArrayOf(1, 0, 2)[slot]
            if (idx < suggestions.size) {
                if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                actions?.onSuggestion(idx)
            }
        }
    }

    override fun onDetachedFromWindow() {
        uiHandler.removeCallbacksAndMessages(null)
        pointers.clear()
        for (row in rows) for (k in row) k.pressed = false
        previewKey = null
        altPopup = null
        trackpadActive = false
        super.onDetachedFromWindow()
    }
}
