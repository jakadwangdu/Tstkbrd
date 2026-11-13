package com.example.noirglass

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.view.View
import android.view.inputmethod.EditorInfo

class NoirKeyboardService : InputMethodService() {
    private var keyboard: NoirKeyboardView? = null

    override fun onCreateInputView(): View {
        keyboard = NoirKeyboardView(this) { action -> handleKey(action) }
        return keyboard!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboard?.setCaps(false)
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return
        when (key) {
            "SHIFT" -> keyboard?.toggleShift()
            "DELETE" -> ic.deleteSurroundingText(1, 0)
            "SPACE" -> ic.commitText(" ", 1)
            "RETURN" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action)
                else ic.commitText("\n", 1)
            }
            "123" -> keyboard?.setSymbols(true)
            "ABC" -> keyboard?.setSymbols(false)
            "MIC" -> { /* Voice input is intentionally not requested; no microphone permission is used. */ }
            "EMOJI" -> ic.commitText("😊", 1)
            else -> ic.commitText(if (keyboard?.isShifted() == true) key.uppercase() else key.lowercase(), 1)
        }
    }
}
