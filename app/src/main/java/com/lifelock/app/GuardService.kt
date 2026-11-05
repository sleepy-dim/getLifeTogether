package com.lifelock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

class GuardService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var fg = ""
    private var last = System.currentTimeMillis()
    private var lastBlock = 0L

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val dt = (now - last).coerceIn(0L, 10000L)
            last = now
            val pm = getSystemService(PowerManager::class.java)
            if (fg.isNotEmpty() && pm.isInteractive && Store.limits(this@GuardService).any { it.pkg == fg })
                Store.addUsage(this@GuardService, fg, dt)
            check(fg)
            h.postDelayed(this, 2000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        last = System.currentTimeMillis()
        Store.rollover(this)
        h.removeCallbacks(tick)
        h.post(tick)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = e.packageName?.toString() ?: return
        if (p in ignored()) return
        fg = p
        check(p)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        h.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun check(p: String) {
        if (p.isEmpty() || p == packageName) return
        val now = System.currentTimeMillis()
        val allowed = if (Store.focusUntil(this) > now) allowedInFocus() else emptySet()
        val r = Store.blockReason(this, p, allowed) ?: return
        if (now - lastBlock < 1500) return
        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        h.postDelayed({
            startActivity(Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("pkg", p).putExtra("kind", r.first).putExtra("msg", r.second))
        }, 250)
    }

    private fun ignored(): Set<String> {
        val s = mutableSetOf("com.android.systemui")
        getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { s.add(it.packageName) }
        return s
    }

    private fun allowedInFocus(): Set<String> {
        val s = mutableSetOf(packageName, "com.whatsapp", "com.whatsapp.w4b", "com.android.systemui",
            "com.android.phone", "com.android.server.telecom", "com.android.incallui",
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
            "com.samsung.android.incallui", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.android.mms")
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { s.add(it) }
        Telephony.Sms.getDefaultSmsPackage(this)?.let { s.add(it) }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0).forEach { s.add(it.activityInfo.packageName) }
        return s
    }
}
