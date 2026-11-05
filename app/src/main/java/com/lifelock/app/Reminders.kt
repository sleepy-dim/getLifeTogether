package com.lifelock.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        val t = Store.today()
        val pend = Store.goals(c).filter { t < it.end && it.last != t }
        if (pend.isNotEmpty()) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("remind", "Check-in reminders", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(c, "remind")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Check in to keep your streak 🔥")
                .setContentText(pend.joinToString { it.title })
                .setContentIntent(open).setAutoCancel(true).build()
            nm.notify(1, n)
        }
        schedule(c)
    }

    companion object {
        fun schedule(c: Context) {
            val now = LocalDateTime.now()
            val hr = Store.remindHour(c)
            var next = LocalDate.now().atTime(hr, 0)
            if (!next.isAfter(now)) {
                // after the reminder hour: nag every hour until 10pm, then wait for tomorrow
                next = if (now.hour < 22) now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                else LocalDate.now().plusDays(1).atTime(hr, 0)
            }
            val ms = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val pi = PendingIntent.getBroadcast(c, 1, Intent(c, ReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) { ReminderReceiver.schedule(c) }
}
