package app.aura.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout

class AuraKeyboardService : InputMethodService(), KeyboardView.Actions {

    private lateinit var prefs: Prefs
    private lateinit var clips: ClipStore
    private lateinit var engine: SuggestionEngine

    private var root: FrameLayout? = null
    private var kb: KeyboardView? = null
    private var panel: View? = null

    private var shiftState = 0
    private var lastShiftTap = 0L
    private var lastSpaceAt = 0L
    private var passwordField = false
    private var suggestionsAllowed = true
    private var lastWasSuggestionSpace = false
    private var currentSuggestions: List<String> = emptyList()
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        clips = ClipStore(prefs)
        engine = SuggestionEngine(prefs)
        registerClipboard()
        VoiceBridge.onResult = { flushVoice() }
    }

    override fun onDestroy() {
        VoiceBridge.onResult = null
        val l = clipListener
        if (l != null) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.removePrimaryClipChangedListener(l)
        }
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val view = KeyboardView(this, prefs)
        view.actions = this
        view.refresh()
        kb = view
        val frame = FrameLayout(this)
        frame.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root = frame
        return frame
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        kb?.refresh()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        closePanel()
        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION

        passwordField = (cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        val noSuggestFlag = (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val skipVariation = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        suggestionsAllowed = cls == InputType.TYPE_CLASS_TEXT && !passwordField && !noSuggestFlag && !skipVariation

        val startLayer = if (cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        ) 1 else 0

        lastWasSuggestionSpace = false
        shiftState = 0
        val view = kb
        if (view != null) {
            view.previewEnabled = !passwordField
            view.refresh()
            view.setEnterLabel(enterLabelFor(info))
            view.setLayer(startLayer)
            view.setShift(0)
        }
        updateShiftFromCaps()
        updateSuggestions()
        flushVoice()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        closePanel()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateShiftFromCaps()
        updateSuggestions()
    }

    // ---- editor helpers ---------------------------------------------------

    private fun enterLabelFor(info: EditorInfo?): String? {
        if (info == null) return null
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val multiline = (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (multiline && action == EditorInfo.IME_ACTION_DONE) return null
        return when (action) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            else -> null
        }
    }

    private fun setShift(s: Int) {
        shiftState = s
        kb?.setShift(s)
    }

    private fun updateShiftFromCaps() {
        if (shiftState == 2 || !prefs.autoCap) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            setShift(0)
            return
        }
        val caps = ic.getCursorCapsMode(info.inputType)
        setShift(if (caps != 0) 1 else 0)
    }

    private fun currentPrefix(): String {
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: return ""
        var n = before.length
        while (n > 0 && (before[n - 1].isLetter() || before[n - 1] == '\'')) n--
        return before.substring(n)
    }

    private fun matchCase(prefix: String, word: String): String {
        if (prefix.isEmpty()) return word
        if (prefix.length > 1 && prefix.all { it.isUpperCase() }) return word.uppercase()
        if (prefix[0].isUpperCase()) return word.replaceFirstChar { it.uppercase() }
        return word
    }

    private fun updateSuggestions() {
        val view = kb ?: return
        if (!prefs.suggestions || !suggestionsAllowed) {
            currentSuggestions = emptyList()
            view.setSuggestions(currentSuggestions)
            return
        }
        val p = currentPrefix()
        currentSuggestions = if (p.isEmpty()) emptyList() else engine.suggest(p).map { matchCase(p, it) }
        view.setSuggestions(currentSuggestions)
    }

    private fun learnPrevWord() {
        if (!prefs.suggestions || !suggestionsAllowed) return
        val p = currentPrefix()
        if (p.length >= 3) engine.learn(p)
    }

    private fun afterEdit() {
        updateShiftFromCaps()
        updateSuggestions()
    }

    // ---- KeyboardView.Actions ---------------------------------------------

    override fun onText(text: String) {
        if (text.isEmpty()) return
        val ic = currentInputConnection ?: return
        val first = text[0]
        if (lastWasSuggestionSpace && text.length == 1 && ".,!?;:".indexOf(first) >= 0) {
            val before = ic.getTextBeforeCursor(1, 0)
            if (before != null && before.toString() == " ") ic.deleteSurroundingText(1, 0)
        }
        lastWasSuggestionSpace = false
        if (!(first.isLetterOrDigit() || first == '\'')) learnPrevWord()
        ic.commitText(text, 1)
        if (shiftState == 1) setShift(0)
        afterEdit()
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        lastWasSuggestionSpace = false
        if (prefs.doubleSpace && !passwordField && now - lastSpaceAt < 400) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && !before[0].isWhitespace() &&
                ".!?,;:".indexOf(before[0]) < 0
            ) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceAt = 0L
                afterEdit()
                return
            }
        }
        learnPrevWord()
        ic.commitText(" ", 1)
        lastSpaceAt = now
        afterEdit()
    }

    override fun onBackspace(word: Boolean) {
        val ic = currentInputConnection ?: return
        lastWasSuggestionSpace = false
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            afterEdit()
            return
        }
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
        if (before.isEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            afterEdit()
            return
        }
        if (word) {
            var n = before.length
            while (n > 0 && before[n - 1].isWhitespace()) n--
            while (n > 0 && !before[n - 1].isWhitespace()) n--
            val count = before.length - n
            ic.deleteSurroundingText(if (count > 0) count else 1, 0)
        } else {
            var n = 1
            if (before.length >= 2 &&
                Character.isLowSurrogate(before[before.length - 1]) &&
                Character.isHighSurrogate(before[before.length - 2])
            ) n = 2
            ic.deleteSurroundingText(n, 0)
        }
        afterEdit()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        lastWasSuggestionSpace = false
        learnPrevWord()
        if (info != null && enterLabelFor(info) != null) {
            ic.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        afterEdit()
    }

    override fun onShift() {
        val now = System.currentTimeMillis()
        val next = when {
            shiftState == 2 -> 0
            now - lastShiftTap < 350 -> 2
            shiftState == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
        setShift(next)
    }

    override fun onLayer(layer: Int) {
        kb?.setLayer(layer)
    }

    override fun onEmoji() {
        showEmoji()
    }

    override fun onMove(dx: Int, dy: Int) {
        val h = if (dx < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val v = if (dy < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        var i = 0
        while (i < kotlin.math.abs(dx) && i < 25) {
            sendDownUpKeyEvents(h)
            i++
        }
        i = 0
        while (i < kotlin.math.abs(dy) && i < 8) {
            sendDownUpKeyEvents(v)
            i++
        }
        lastWasSuggestionSpace = false
    }

    override fun onSuggestion(index: Int) {
        val ic = currentInputConnection ?: return
        val word = currentSuggestions.getOrNull(index) ?: return
        val prefix = currentPrefix()
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        lastWasSuggestionSpace = true
        if (!passwordField) engine.learn(word)
        afterEdit()
    }

    override fun onToolbar(which: Int) {
        when (which) {
            0 -> showClipboard()
            1 -> showEmoji()
            2 -> startVoice()
            else -> openSettings()
        }
    }

    // ---- panels -----------------------------------------------------------

    private fun showPanel(p: View) {
        closePanel()
        val h = kb?.height ?: 0
        val height = if (h > 0) h else dp(260)
        root?.addView(p, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        panel = p
    }

    private fun closePanel() {
        val p = panel
        if (p != null) root?.removeView(p)
        panel = null
    }

    private fun showEmoji() {
        val view = kb ?: return
        showPanel(
            EmojiPanel(
                this, view.palette, prefs,
                onPick = { e ->
                    currentInputConnection?.commitText(e, 1)
                    lastWasSuggestionSpace = false
                },
                onBack = { closePanel() },
                onDelete = { onBackspace(false) }
            )
        )
    }

    private fun showClipboard() {
        val view = kb ?: return
        clips.reload()
        showPanel(
            ClipboardPanel(
                this, view.palette, clips,
                onPaste = { t ->
                    currentInputConnection?.commitText(t, 1)
                    closePanel()
                    afterEdit()
                },
                onBack = { closePanel() }
            )
        )
    }

    // ---- clipboard capture --------------------------------------------------

    private fun registerClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val l = ClipboardManager.OnPrimaryClipChangedListener { captureClip(cm) }
        clipListener = l
        cm.addPrimaryClipChangedListener(l)
    }

    private fun captureClip(cm: ClipboardManager) {
        if (!prefs.clipboard) return
        try {
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val extras = clip.description.extras
            if (extras != null && extras.getBoolean("android.content.extra.IS_SENSITIVE", false)) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (text.isBlank() || text.length > 2000) return
            clips.add(text)
        } catch (e: Exception) {
            // Some devices refuse clipboard reads in the background. Skip quietly.
        }
    }

    // ---- voice and settings ---------------------------------------------------

    private fun startVoice() {
        try {
            val i = Intent(this, VoiceInputActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            // Nothing to do if the system refuses to start the activity.
        }
    }

    private fun flushVoice() {
        val text = VoiceBridge.pending ?: return
        val ic = currentInputConnection ?: return
        ic.commitText("$text ", 1)
        VoiceBridge.pending = null
        afterEdit()
    }

    private fun openSettings() {
        try {
            val i = Intent(this, SettingsActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            // Ignore.
        }
    }
}
