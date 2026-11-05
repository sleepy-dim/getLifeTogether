package com.lifelock.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.LinearLayout

class BlockActivity : Activity() {
    private var timer: CountDownTimer? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val kind = intent.getStringExtra("kind") ?: ""
        val msg = intent.getStringExtra("msg") ?: ""
        val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Ui.BG)
            val p = Ui.dp(this@BlockActivity, 28); setPadding(p, p, p, p)
        }
        root.addView(Ui.tv(this, "🔒", 56f).apply { gravity = Gravity.CENTER })
        root.addView(Ui.tv(this, "$name is blocked", 24f, bold = true).apply { gravity = Gravity.CENTER })
        root.addView(Ui.tv(this, msg, 16f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        val why = Store.why(this)
        if (why.isNotEmpty()) root.addView(Ui.tv(this, "\"$why\"", 17f, Ui.ACCENT).apply {
            gravity = Gravity.CENTER; setPadding(0, Ui.dp(this@BlockActivity, 20), 0, 0)
        })
        root.addView(Ui.btn(this, "Go home") { goHome() })

        if (kind == "limit" && Store.passesLeft(this) > 0) {
            val pb = Ui.btn(this, "Wait 10s…", Ui.CARD2) {}
            pb.isEnabled = false; pb.alpha = 0.5f
            root.addView(pb)
            timer = object : CountDownTimer(10000, 1000) {
                override fun onTick(ms: Long) { pb.text = "Think about it… ${ms / 1000 + 1}s" }
                override fun onFinish() {
                    pb.isEnabled = true; pb.alpha = 1f
                    pb.text = "Use a pass (+${Store.passMin(this@BlockActivity)} min) · ${Store.passesLeft(this@BlockActivity)} left this week"
                    pb.setOnClickListener {
                        if (Store.usePass(this@BlockActivity, pkg)) {
                            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                            finish()
                        }
                    }
                }
            }.start()
        }
        setContentView(root)
    }

    override fun onBackPressed() = goHome()

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
