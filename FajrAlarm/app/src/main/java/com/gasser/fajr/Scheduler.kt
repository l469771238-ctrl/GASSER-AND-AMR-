package com.gasser.fajr

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate

object Scheduler {
    /** Next trigger (epoch millis) of Fajr + offset minutes, strictly in the future. */
    fun nextTrigger(offsetMin: Int): Long {
        val now = System.currentTimeMillis()
        var d = LocalDate.now(Prayer.ZONE).minusDays(1)
        for (i in 0 until 4) {
            val t = Prayer.fajr(d) + offsetMin * 60000L
            if (t > now + 1000) return t
            d = d.plusDays(1)
        }
        return now + 86400000L
    }

    /** Returns false if exact alarms are not permitted. */
    fun scheduleAll(c: Context): Boolean {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val allowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        for (slot in 0..1) {
            val op = PendingIntent.getBroadcast(
                c, slot, Intent(c, AlarmReceiver::class.java).putExtra("slot", slot),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Store.on(c, slot) && allowed && !Store.key(c).isNullOrEmpty()) {
                val show = PendingIntent.getActivity(
                    c, 10 + slot, Intent(c, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val t = nextTrigger(Store.offset(c, slot))
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), op)
            } else {
                am.cancel(op)
            }
        }
        return allowed
    }
}
