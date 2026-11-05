package com.lifelock.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class Goal(val id: String, var title: String, var end: Long, var apps: List<String>,
           var last: Long, var streak: Int, var best: Int, val created: Long)

class Limit(val pkg: String, var min: Int, var streak: Int, var ev: Long, val cd: Long)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("lifelock", Context.MODE_PRIVATE)
    fun today(): Long = LocalDate.now().toEpochDay()

    // ---------- goals ----------
    fun goals(c: Context): MutableList<Goal> {
        val a = JSONArray(sp(c).getString("goals", "[]"))
        return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            val ap = o.getJSONArray("a")
            Goal(o.getString("id"), o.getString("t"), o.getLong("e"),
                List(ap.length()) { ap.getString(it) },
                o.getLong("l"), o.getInt("s"), o.getInt("b"), o.getLong("c"))
        }
    }

    fun saveGoals(c: Context, l: List<Goal>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("t", it.title).put("e", it.end)
                .put("a", JSONArray(it.apps)).put("l", it.last).put("s", it.streak)
                .put("b", it.best).put("c", it.created))
        }
        sp(c).edit().putString("goals", a.toString()).apply()
    }

    fun addGoal(c: Context, title: String, end: Long, apps: List<String>) {
        val l = goals(c)
        val now = System.currentTimeMillis()
        l.add(Goal(now.toString(), title, end, apps, 0L, 0, 0, now))
        saveGoals(c, l)
    }

    fun checkIn(c: Context, id: String) {
        val l = goals(c)
        val g = l.firstOrNull { it.id == id } ?: return
        val t = today()
        if (g.last != t) {
            g.streak = if (g.last == t - 1) g.streak + 1 else 1
            g.best = maxOf(g.best, g.streak)
            g.last = t
            saveGoals(c, l)
        }
    }

    fun liveStreak(g: Goal) = if (g.last >= today() - 1) g.streak else 0
    fun canDelete(g: Goal) = today() >= g.end || System.currentTimeMillis() - g.created < 600000L
    fun deleteGoal(c: Context, id: String) = saveGoals(c, goals(c).filter { it.id != id })

    // ---------- limits ----------
    fun limits(c: Context): MutableList<Limit> {
        val a = JSONArray(sp(c).getString("limits", "[]"))
        return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            Limit(o.getString("p"), o.getInt("m"), o.getInt("s"), o.getLong("e"), o.getLong("c"))
        }
    }

    fun saveLimits(c: Context, l: List<Limit>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("p", it.pkg).put("m", it.min).put("s", it.streak).put("e", it.ev).put("c", it.cd)) }
        sp(c).edit().putString("limits", a.toString()).apply()
    }

    /** A limit can be created, or lowered. It cannot be raised. */
    fun addLimit(c: Context, pkg: String, min: Int): Boolean {
        val l = limits(c)
        val ex = l.firstOrNull { it.pkg == pkg }
        if (ex != null) {
            if (min > ex.min) return false
            ex.min = min
        } else l.add(Limit(pkg, min, 0, today() - 1, today()))
        saveLimits(c, l)
        return true
    }

    /** Limits are locked for 7 days after creation. */
    fun removeLimit(c: Context, pkg: String): Boolean {
        val l = limits(c)
        val x = l.firstOrNull { it.pkg == pkg } ?: return true
        if (today() - x.cd < 7) return false
        saveLimits(c, l.filter { it.pkg != pkg })
        return true
    }

    fun usageMs(c: Context, p: String, d: Long = today()) = sp(c).getLong("u_${p}_$d", 0L)
    fun addUsage(c: Context, p: String, ms: Long) {
        sp(c).edit().putLong("u_${p}_${today()}", usageMs(c, p) + ms).apply()
    }
    fun extra(c: Context, p: String, d: Long = today()) = sp(c).getInt("x_${p}_$d", 0)

    /** Evaluates finished days and updates the "days under limit" streak. */
    fun rollover(c: Context) {
        val t = today()
        val l = limits(c)
        var changed = false
        for (x in l) {
            if (t - x.ev > 60) { x.ev = t - 60; changed = true }
            while (x.ev < t - 1) {
                val d = x.ev + 1
                val ok = usageMs(c, x.pkg, d) <= x.min * 60000L && extra(c, x.pkg, d) == 0
                x.streak = if (ok) x.streak + 1 else 0
                x.ev = d
                changed = true
            }
        }
        if (changed) saveLimits(c, l)
    }

    // ---------- passes & settings ----------
    private fun weekStart() = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    fun passN(c: Context) = sp(c).getInt("pn", 3)
    fun passMin(c: Context) = sp(c).getInt("pm", 10)
    fun remindHour(c: Context) = sp(c).getInt("rh", 20)
    fun why(c: Context) = sp(c).getString("why", "") ?: ""
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun setWhy(c: Context, s: String) = sp(c).edit().putString("why", s).apply()

    fun passesLeft(c: Context): Int {
        val s = sp(c)
        if (s.getLong("pw", 0L) != weekStart()) s.edit().putLong("pw", weekStart()).putInt("pu", 0).apply()
        return passN(c) - s.getInt("pu", 0)
    }

    fun usePass(c: Context, pkg: String): Boolean {
        if (passesLeft(c) <= 0) return false
        sp(c).edit().putInt("pu", sp(c).getInt("pu", 0) + 1)
            .putInt("x_${pkg}_${today()}", extra(c, pkg) + passMin(c)).apply()
        return true
    }

    // ---------- focus ----------
    fun focusUntil(c: Context) = sp(c).getLong("fu", 0L)
    fun startFocus(c: Context, min: Int) = sp(c).edit().putLong("fu", System.currentTimeMillis() + min * 60000L).apply()

    // ---------- the rule engine ----------
    fun blockReason(c: Context, p: String, focusAllowed: Set<String>): Pair<String, String>? {
        val now = System.currentTimeMillis()
        val fu = focusUntil(c)
        if (fu > now && p !in focusAllowed)
            return "focus" to "Focus session: ${(fu - now) / 60000 + 1} min left.\nOnly WhatsApp, calls and SMS are allowed."
        val t = today()
        val g = goals(c).firstOrNull { t < it.end && p in it.apps }
        if (g != null) return "goal" to "Goal: ${g.title}\nBlocked until ${LocalDate.ofEpochDay(g.end)}"
        val l = limits(c).firstOrNull { it.pkg == p }
        if (l != null && usageMs(c, p) >= (l.min + extra(c, p)) * 60000L)
            return "limit" to "Daily limit reached (${l.min} min + ${extra(c, p)} extra)."
        return null
    }
}
