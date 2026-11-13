package com.example.noirglass

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(android.graphics.Color.rgb(238, 232, 218))
        }
        val title = TextView(this).apply {
            text = "NOIR GLASS"
            textSize = 27f
            letterSpacing = .18f
            setTextColor(android.graphics.Color.rgb(35, 34, 30))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "A quiet, dark-glass keyboard.\nEnable it once, then select it from your keyboard switcher."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(92, 87, 77))
            setPadding(0, 16, 0, 28)
        }
        fun action(label: String, callback: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { callback() }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(action("1 · Enable Noir Glass Keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(action("2 · Choose keyboard / Try it") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }
}
