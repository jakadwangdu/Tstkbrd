package com.example.noirglass

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/** Canvas-rendered keyboard tuned to the supplied dark-glass reference. */
class NoirKeyboardView(context: Context, private val onKey: (String) -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(211, 209, 201); textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = mutableListOf<Pair<RectF, String>>()
    private var shifted = false
    private var symbols = false
    private var caps = false

    fun setCaps(value: Boolean) { caps = value; invalidate() }
    fun toggleShift() { shifted = !shifted; invalidate() }
    fun isShifted() = shifted || caps
    fun setSymbols(value: Boolean) { symbols = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(238f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); bounds.clear()
        // Deep charcoal glass panel, soft inner edge and subtle vignette.
        val panel = RectF(dp(3f), dp(3f), w-dp(3f), h-dp(2f))
        bg.shader = LinearGradient(0f, 0f, 0f, h, Color.rgb(47,47,44), Color.rgb(18,18,17), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(panel, dp(22f), dp(22f), bg); bg.shader = null
        bg.style = Paint.Style.STROKE; bg.strokeWidth = dp(1f); bg.color = Color.argb(65, 218, 210, 187)
        canvas.drawRoundRect(panel, dp(22f), dp(22f), bg); bg.style = Paint.Style.FILL

        // Toolbar: add, tiny mode glyphs, circular utility, punctuation and microphone.
        val cy = dp(25f)
        circle(canvas, dp(35f), cy, dp(12f), Color.argb(100, 170, 166, 151)); drawText(canvas, "+", dp(35f), cy+dp(5f), 19f, Color.WHITE)
        drawText(canvas, "ᴬᵃ", w*.27f, cy+dp(3f), 10f, 0xffaaa99f.toInt())
        circle(canvas, w*.39f, cy, dp(10f), Color.argb(100, 180, 177, 165), stroke = true)
        drawText(canvas, "▰", w*.50f, cy+dp(3f), 9f, 0xff9c9a91.toInt())
        drawText(canvas, "14    16    18", w*.64f, cy+dp(3f), 8f, 0xffaaa89e.toInt())
        circle(canvas, w-dp(34f), cy, dp(14f), 0xffb9b8b0.toInt()); drawText(canvas, "♩", w-dp(34f), cy+dp(5f), 17f, 0xff343430.toInt())

        val top = dp(48f); val rowH = dp(34f); val gap = dp(2f)
        val rows = if (symbols) listOf("1234567890", "@#\$%&-+()") else listOf("QWERTYUIOP", "ASDFGHJKL")
        rows.forEachIndexed { ri, row ->
            val y = top + ri*(rowH+gap)
            val side = if (ri == 1) dp(15f) else dp(5f)
            val count = row.length
            val usable = w - side*2
            val keyW = usable / count
            row.forEachIndexed { i, ch ->
                val r = RectF(side+i*keyW+dp(1f), y, side+(i+1)*keyW-dp(1f), y+rowH-dp(2f))
                keyPaint.color = Color.argb(13, 255,255,255); canvas.drawRoundRect(r, dp(5f), dp(5f), keyPaint)
                drawText(canvas, ch.toString(), r.centerX(), r.centerY()+dp(4f), 13f, 0xffc8c6bd.toInt())
                bounds.add(r to ch.toString())
            }
        }
        val y = top + 2*(rowH+gap)
        // Third letter row has the same wide shift/delete end keys as the reference.
        if (symbols) {
            val row = "*/:;!?'" + "\""
            val left = dp(44f); val right = w-dp(44f); val kw = (right-left)/row.length
            row.forEachIndexed { i, ch ->
                val r = RectF(left+i*kw+dp(1f), y, left+(i+1)*kw-dp(1f), y+rowH-dp(2f))
                keyPaint.color=Color.argb(13,255,255,255); canvas.drawRoundRect(r,dp(5f),dp(5f),keyPaint)
                drawText(canvas,ch.toString(),r.centerX(),r.centerY()+dp(4f),13f,0xffc8c6bd.toInt()); bounds.add(r to ch.toString())
            }
        } else {
            addKey(canvas, RectF(dp(4f), y, dp(43f), y+rowH-dp(2f)), "SHIFT", if (shifted) 0xff716d60.toInt() else 0x33222222, 12f)
            val row = "ZXCVBNM"; val left = dp(45f); val right = w-dp(45f); val kw=(right-left)/row.length
            row.forEachIndexed { i,ch ->
                val r=RectF(left+i*kw+dp(1f),y,left+(i+1)*kw-dp(1f),y+rowH-dp(2f))
                keyPaint.color=Color.argb(13,255,255,255); canvas.drawRoundRect(r,dp(5f),dp(5f),keyPaint)
                drawText(canvas,ch.toString(),r.centerX(),r.centerY()+dp(4f),13f,0xffc8c6bd.toInt()); bounds.add(r to ch.toString())
            }
            addKey(canvas, RectF(w-dp(43f), y, w-dp(4f), y+rowH-dp(2f)), "DELETE", 0x33222222, 12f)
        }
        val bottomY = y + rowH + dp(1f)
        // Bottom utility row: number, emoji, wide space and return.
        addKey(canvas, RectF(dp(5f), bottomY, dp(48f), bottomY+dp(31f)), if (symbols) "ABC" else "123", 0x33222222, 10f)
        addKey(canvas, RectF(dp(51f), bottomY, dp(91f), bottomY+dp(31f)), "EMOJI", 0x33222222, 10f)
        addKey(canvas, RectF(dp(94f), bottomY, w-dp(76f), bottomY+dp(31f)), "SPACE", 0x33222222, 11f)
        addKey(canvas, RectF(w-dp(72f), bottomY, w-dp(5f), bottomY+dp(31f)), "RETURN", 0x33222222, 10f)
        // Home indicator echoes the screenshot's bottom inset.
        keyPaint.color = 0xff77766f.toInt(); canvas.drawRoundRect(RectF(w*.36f, h-dp(7f), w*.64f, h-dp(5f)), dp(2f), dp(2f), keyPaint)
    }

    private fun addKey(c: Canvas, r: RectF, label: String, color: Int, size: Float) {
        keyPaint.color = color; c.drawRoundRect(r, dp(6f), dp(6f), keyPaint)
        drawText(c, when(label){"SHIFT"->"⬆";"RETURN"->"Return";"SPACE"->"Space";"EMOJI"->"☻";else->label}, r.centerX(), r.centerY()+dp(4f), size, if(label=="EMOJI") 0xffc5ad62.toInt() else 0xffc8c6bd.toInt())
        bounds.add(r to label)
    }
    private fun circle(c: Canvas, x: Float, y: Float, radius: Float, color: Int, stroke: Boolean=false) {
        keyPaint.color=color; keyPaint.style=if(stroke) Paint.Style.STROKE else Paint.Style.FILL; keyPaint.strokeWidth=dp(1.5f); c.drawCircle(x,y,radius,keyPaint); keyPaint.style=Paint.Style.FILL
    }
    private fun drawText(c: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int) {
        text.textSize=dp(size); text.color=color; c.drawText(value,x,baseline,text)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = bounds.lastOrNull { it.first.contains(event.x,event.y) }?.second ?: return true
            when(hit) {
                "SHIFT" -> onKey("SHIFT"); "DELETE" -> onKey("DELETE"); "123" -> onKey(if(symbols) "ABC" else "123"); "ABC" -> onKey("ABC"); "EMOJI" -> onKey("EMOJI")
                "SPACE" -> onKey("SPACE"); "RETURN" -> onKey("RETURN")
                else -> onKey(if(isShifted()) hit.uppercase() else hit.lowercase())
            }
            if (shifted && hit != "SHIFT") { shifted=false; invalidate() }
            return true
        }
        return true
    }
}
