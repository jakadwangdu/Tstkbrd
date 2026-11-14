package app.aura.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private fun label(context: Context, text: String, sizeSp: Float, color: Int): TextView {
    val tv = TextView(context)
    tv.text = text
    tv.textSize = sizeSp
    tv.setTextColor(color)
    tv.gravity = Gravity.CENTER
    return tv
}

private fun roundBg(color: Int, radiusPx: Float): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radiusPx
    return g
}

/** Emoji drawer: category tabs along the bottom, a recents tab, and a delete key. */
class EmojiPanel(
    context: Context,
    private val pal: Palette,
    private val prefs: Prefs,
    private val onPick: (String) -> Unit,
    private val onBack: () -> Unit,
    private val onDelete: () -> Unit
) : LinearLayout(context) {

    private val grid = GridView(context)
    private val tabViews = ArrayList<TextView>()
    private var current = 1
    private var items: List<String> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv: TextView = if (convertView is TextView) convertView else {
                val t = TextView(context)
                t.gravity = Gravity.CENTER
                t.textSize = 24f
                t.setTextColor(pal.text)
                t.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(46))
                t
            }
            tv.text = items[position]
            return tv
        }
    }

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(pal.bg)

        grid.numColumns = 8
        grid.adapter = adapter
        grid.setSelector(ColorDrawable(Color.TRANSPARENT))
        grid.setOnItemClickListener { _, _, position, _ ->
            val e = items[position]
            onPick(e)
            addRecent(e)
        }
        addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val abc = label(context, "ABC", 14f, pal.text)
        abc.setPadding(context.dp(14), 0, context.dp(14), 0)
        abc.setOnClickListener { onBack() }
        bar.addView(abc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val tabRow = LinearLayout(context)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.gravity = Gravity.CENTER_VERTICAL
        val tabLabels = ArrayList<String>()
        tabLabels.add("🕘")
        for (c in EmojiData.categories) tabLabels.add(c.first)
        for ((i, t) in tabLabels.withIndex()) {
            val tv = label(context, t, 20f, pal.text)
            tv.setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
            tv.setOnClickListener { select(i) }
            tabViews.add(tv)
            tabRow.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val scroll = HorizontalScrollView(context)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(tabRow)
        bar.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val del = label(context, "⌫", 20f, pal.text)
        del.setPadding(context.dp(14), 0, context.dp(14), 0)
        del.setOnClickListener { onDelete() }
        bar.addView(del, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(46)))

        select(if (recents().isEmpty()) 1 else 0)
    }

    private fun recents(): List<String> = prefs.recents.split(' ').filter { it.isNotEmpty() }

    private fun addRecent(e: String) {
        val list = ArrayList(recents())
        list.remove(e)
        list.add(0, e)
        prefs.recents = list.take(32).joinToString(" ")
        if (current == 0) select(0)
    }

    private fun select(i: Int) {
        current = i
        items = if (i == 0) recents() else EmojiData.categories[i - 1].second
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        for ((idx, tv) in tabViews.withIndex()) {
            if (idx == i) tv.background = roundBg(pal.key, context.dp(10).toFloat())
            else tv.background = null
        }
    }
}

/** Clipboard history: tap a clip to paste it, tap the star to pin it. */
class ClipboardPanel(
    context: Context,
    private val pal: Palette,
    private val store: ClipStore,
    private val onPaste: (String) -> Unit,
    private val onBack: () -> Unit
) : LinearLayout(context) {

    private val list = LinearLayout(context)

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(pal.bg)

        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val abc = label(context, "ABC", 14f, pal.text)
        abc.setPadding(context.dp(14), 0, context.dp(14), 0)
        abc.setOnClickListener { onBack() }
        bar.addView(abc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val title = label(context, "Clipboard", 15f, pal.muted)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val clear = label(context, "Clear", 14f, pal.text)
        clear.setPadding(context.dp(14), 0, context.dp(14), 0)
        clear.setOnClickListener {
            store.clearUnpinned()
            render()
        }
        bar.addView(clear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(46)))

        list.orientation = LinearLayout.VERTICAL
        list.setPadding(context.dp(8), 0, context.dp(8), context.dp(8))
        val scroll = ScrollView(context)
        scroll.addView(list)
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        render()
    }

    private fun render() {
        list.removeAllViews()
        val all = store.all()
        if (all.isEmpty()) {
            val empty = label(context, "Text you copy shows up here.", 14f, pal.muted)
            empty.setPadding(0, context.dp(24), 0, 0)
            list.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        for (c in all) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = roundBg(pal.key, context.dp(10).toFloat())

            val text = TextView(context)
            text.text = c.text
            text.textSize = 15f
            text.setTextColor(pal.text)
            text.maxLines = 2
            text.ellipsize = android.text.TextUtils.TruncateAt.END
            text.setPadding(context.dp(12), context.dp(10), context.dp(8), context.dp(10))
            text.setOnClickListener { onPaste(c.text) }
            row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val pin = label(context, if (c.pinned) "★" else "☆", 20f, if (c.pinned) pal.accent else pal.muted)
            pin.setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            pin.setOnClickListener {
                store.togglePin(c.text)
                render()
            }
            row.addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = context.dp(6)
            list.addView(row, lp)
        }
    }
}
