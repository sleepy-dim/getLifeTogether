package com.lifelock.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    val BG = Color.parseColor("#0F1115")
    val CARD = Color.parseColor("#1A1D24")
    val CARD2 = Color.parseColor("#2A2E38")
    val ACCENT = Color.parseColor("#7C5CFF")
    val MUTED = Color.parseColor("#9AA0AE")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun round(c: Context, color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(c, r).toFloat()
    }

    fun tv(c: Context, s: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(c).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(c, 4), 0, 0)
        }

    fun btn(c: Context, s: String, bg: Int = ACCENT, onClick: () -> Unit) = Button(c).apply {
        text = s; isAllCaps = false; setTextColor(Color.WHITE)
        background = round(c, bg, 12)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 10) }
        setOnClickListener { onClick() }
    }

    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = round(c, CARD, 16)
        val p = dp(c, 16); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 12) }
    }
}
