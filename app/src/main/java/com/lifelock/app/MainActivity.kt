package com.lifelock.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var box: LinearLayout
    private var focusTv: TextView? = null

    private val ticker = object : Runnable {
        override fun run() {
            val left = Store.focusUntil(this@MainActivity) - System.currentTimeMillis()
            val tv = focusTv
            if (tv != null) {
                if (left > 0) tv.text = fmt(left) else render()
            }
            h.postDelayed(this, 1000)
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d left".format(s / 3600, s / 60 % 60, s % 60)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@MainActivity, 16); setPadding(p, p, p, p)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(box) })
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        ReminderReceiver.schedule(this)
    }

    override fun onResume() { super.onResume(); render(); h.post(ticker) }
    override fun onPause() { super.onPause(); h.removeCallbacks(ticker) }

    // ---------- helpers ----------
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun guardOn(): Boolean {
        val en = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return en.contains("$packageName/")
    }

    private fun label(p: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
    } catch (e: Exception) { p }

    private fun apps(): List<Pair<String, String>> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }

    private fun ask(title: String, def: String, numeric: Boolean = true, ok: (String) -> Unit) {
        val e = EditText(this).apply {
            setText(def)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this).setTitle(title).setView(e)
            .setPositiveButton("OK") { _, _ -> ok(e.text.toString().trim()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickOne(title: String, done: (String) -> Unit) {
        val a = apps()
        AlertDialog.Builder(this).setTitle(title)
            .setItems(a.map { it.first }.toTypedArray()) { _, i -> done(a[i].second) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickMulti(title: String, done: (List<String>) -> Unit) {
        val a = apps()
        val sel = BooleanArray(a.size)
        AlertDialog.Builder(this).setTitle(title)
            .setMultiChoiceItems(a.map { it.first }.toTypedArray(), sel) { _, i, v -> sel[i] = v }
            .setPositiveButton("Save") { _, _ -> done(a.filterIndexed { i, _ -> sel[i] }.map { it.second }) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun sec(s: String) {
        box.addView(Ui.tv(this, s, 20f, bold = true).apply { setPadding(0, Ui.dp(this@MainActivity, 24), 0, 0) })
    }

    // ---------- screen ----------
    private fun render() {
        val c = this
        box.removeAllViews(); focusTv = null
        Store.rollover(c)
        box.addView(Ui.tv(c, "LifeLock", 32f, bold = true))
        box.addView(Ui.tv(c, "Get your life together.", 14f, Ui.MUTED))

        // setup
        val on = guardOn(); val ov = Settings.canDrawOverlays(c)
        if (!on || !ov) {
            val sc = Ui.card(c); box.addView(sc)
            sc.addView(Ui.tv(c, "Setup needed ⚠️", 18f, bold = true))
            sc.addView(Ui.tv(c, "Blocking does nothing until you finish this. If the Accessibility switch is greyed out: App info → ⋮ menu → \"Allow restricted settings\", then try again.", 13f, Ui.MUTED))
            if (!on) sc.addView(Ui.btn(c, "1. Turn on LifeLock Guard (Accessibility)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            if (!ov) sc.addView(Ui.btn(c, "2. Allow display over other apps") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
            sc.addView(Ui.btn(c, "App info (restricted settings / battery: Unrestricted)", Ui.CARD2) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        }

        // focus
        sec("Focus session")
        val fc = Ui.card(c); box.addView(fc)
        val left = Store.focusUntil(c) - System.currentTimeMillis()
        if (left > 0) {
            val t = Ui.tv(c, fmt(left), 26f, Ui.ACCENT, true)
            fc.addView(t); focusTv = t
            fc.addView(Ui.tv(c, "Locked in. Only WhatsApp, calls and SMS work. This cannot be cancelled.", 13f, Ui.MUTED))
        } else {
            fc.addView(Ui.tv(c, "Locks every app except WhatsApp, calls and SMS. No cancel button.", 13f, Ui.MUTED))
            fc.addView(Ui.btn(c, "Start focus session") {
                ask("Focus minutes", "60") { m ->
                    val n = m.toIntOrNull() ?: 0
                    if (n < 1) return@ask
                    AlertDialog.Builder(c).setTitle("Lock for $n min?")
                        .setMessage("You will not be able to cancel this.")
                        .setPositiveButton("Lock me in") { _, _ -> Store.startFocus(c, n); render() }
                        .setNegativeButton("Not now", null).show()
                }
            })
        }

        // goals
        sec("Goals & streaks")
        val t = Store.today()
        Store.goals(c).forEach { g ->
            val cd = Ui.card(c); box.addView(cd)
            val active = t < g.end
            cd.addView(Ui.tv(c, g.title, 18f, bold = true))
            cd.addView(Ui.tv(c, if (active) "Until ${LocalDate.ofEpochDay(g.end)} · ${g.end - t} days left" else "Completed 🎉", 13f, Ui.MUTED))
            cd.addView(Ui.tv(c, "🔥 ${Store.liveStreak(g)}-day streak · best ${g.best}", 16f))
            if (g.apps.isNotEmpty()) cd.addView(Ui.tv(c, "Blocked: " + g.apps.joinToString { label(it) }, 13f, Ui.MUTED))
            if (active) {
                val done = g.last == t
                cd.addView(Ui.btn(c, if (done) "Checked in today ✓" else "Check in today", if (done) Ui.CARD2 else Ui.ACCENT) {
                    if (!done) { Store.checkIn(c, g.id); render() }
                })
            }
            cd.addView(Ui.btn(c, "Delete", Ui.CARD2) {
                if (Store.canDelete(g)) { Store.deleteGoal(c, g.id); render() }
                else toast("Locked until ${LocalDate.ofEpochDay(g.end)}. Goals can only be deleted in the first 10 minutes.")
            })
        }
        box.addView(Ui.btn(c, "+ New goal") { newGoal() })

        // limits
        sec("App time limits (per day)")
        Store.limits(c).forEach { l ->
            val cd = Ui.card(c); box.addView(cd)
            cd.addView(Ui.tv(c, label(l.pkg), 18f, bold = true))
            cd.addView(Ui.tv(c, "${Store.usageMs(c, l.pkg) / 60000} / ${l.min + Store.extra(c, l.pkg)} min used today", 14f))
            cd.addView(Ui.tv(c, "🔥 ${l.streak} days under limit", 14f, Ui.MUTED))
            cd.addView(Ui.btn(c, "Remove", Ui.CARD2) {
                if (Store.removeLimit(c, l.pkg)) render() else toast("Limits are locked for 7 days after you create them.")
            })
        }
        box.addView(Ui.btn(c, "+ Limit an app") {
            pickOne("Choose app") { pkg ->
                ask("Minutes per day", "30") { m ->
                    val n = m.toIntOrNull() ?: 0
                    if (n < 1) return@ask
                    if (!Store.addLimit(c, pkg, n)) toast("An existing limit can only be lowered, not raised.")
                    render()
                }
            }
        })

        // passes
        sec("Passes & settings")
        val pc = Ui.card(c); box.addView(pc)
        pc.addView(Ui.tv(c, "${Store.passesLeft(c)} of ${Store.passN(c)} passes left this week", 16f, bold = true))
        pc.addView(Ui.tv(c, "Each pass = +${Store.passMin(c)} min on a limited app. Reminders at ${Store.remindHour(c)}:00, then hourly until 10pm if you haven't checked in.", 13f, Ui.MUTED))
        pc.addView(Ui.btn(c, "Edit passes & reminder time", Ui.CARD2) {
            ask("Passes per week", Store.passN(c).toString()) { a ->
                ask("Minutes per pass", Store.passMin(c).toString()) { m ->
                    ask("Reminder hour (0-23)", Store.remindHour(c).toString()) { r ->
                        Store.setInt(c, "pn", a.toIntOrNull() ?: 3)
                        Store.setInt(c, "pm", m.toIntOrNull() ?: 10)
                        Store.setInt(c, "rh", (r.toIntOrNull() ?: 20).coerceIn(0, 23))
                        ReminderReceiver.schedule(c); render()
                    }
                }
            }
        })
        pc.addView(Ui.btn(c, "My reason (shown on block screen)", Ui.CARD2) {
            ask("Why are you doing this?", Store.why(c), false) { s -> Store.setWhy(c, s); render() }
        })
    }

    private fun newGoal() {
        ask("Goal (e.g. No food delivery)", "", false) { title ->
            if (title.isEmpty()) return@ask
            val n = LocalDate.now().plusMonths(1).withDayOfMonth(1)
            val dp = DatePickerDialog(this, { _, y, m, d ->
                val end = LocalDate.of(y, m + 1, d).toEpochDay()
                pickMulti("Block apps until then (optional)") { chosen ->
                    Store.addGoal(this, title, end, chosen); render()
                }
            }, n.year, n.monthValue - 1, n.dayOfMonth)
            dp.datePicker.minDate = System.currentTimeMillis() + 86400000L
            dp.setTitle("Keep going until…")
            dp.show()
        }
    }
}
