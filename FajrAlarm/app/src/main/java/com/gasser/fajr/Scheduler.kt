package com.gasser.fajr

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import java.time.Instant
import java.time.LocalDate

object Scheduler {
    private const val PREF="schedules"
    fun canSchedule(c: Context): Boolean {
        val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT<31 || am.canScheduleExactAlarms()
    }
    fun nextPrayer(c: Context, slot: Int, now: Long = System.currentTimeMillis()): Long {
        var date=Instant.ofEpochMilli(now).atZone(Prayer.ZONE).toLocalDate()
        repeat(3) { val t=Prayer.forDate(c,date)[slot];if(t>now+1000)return t;date=date.plusDays(1) }
        throw IllegalStateException("No next prayer")
    }
    fun nextCustom(c: Context, now: Long = System.currentTimeMillis()): Long {
        if(!Store.customDaily(c)) return Store.customAt(c).coerceAtLeast(now+2000)
        val zone=Prayer.ZONE;val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var t=today.atTime(Store.customHour(c),Store.customMinute(c)).atZone(zone).toInstant().toEpochMilli()
        if(t<=now+1000)t=today.plusDays(1).atTime(Store.customHour(c),Store.customMinute(c)).atZone(zone).toInstant().toEpochMilli()
        return t
    }
    fun selectedCustomAt(h: Int,m: Int, now: Long=System.currentTimeMillis()): Long {
        val today=Instant.ofEpochMilli(now).atZone(Prayer.ZONE).toLocalDate();var t=today.atTime(h,m).atZone(Prayer.ZONE).toInstant().toEpochMilli()
        if(t<=now+1000)t=today.plusDays(1).atTime(h,m).atZone(Prayer.ZONE).toInstant().toEpochMilli()
        return t
    }
    private fun op(c: Context, slot: Int)=PendingIntent.getBroadcast(c,slot,Intent(c,AlarmReceiver::class.java).setAction("com.gasser.fajr.ALARM.$slot").putExtra("slot",slot),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    // Cancel legacy broadcasts from the old two-Fajr build during migration.
    fun cancelLegacy(c: Context) {
        val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for(i in 0..1) am.cancel(PendingIntent.getBroadcast(c,i,Intent(c,AlarmReceiver::class.java).putExtra("slot",i),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    fun scheduleAll(c: Context, preserveImminent: Boolean=false): Boolean { for(slot in 0..6)schedule(c,slot,preserveImminent);return canSchedule(c) }
    fun schedule(c: Context, slot: Int, preserveImminent: Boolean=false) {
        val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager;val operation=op(c,slot);val p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE)
        val old=p.getLong("wall$slot",0);val now=System.currentTimeMillis()
        if(preserveImminent && Store.on(c,slot) && old in now..(now+60000))return
        am.cancel(operation);p.edit().remove("wall$slot").apply()
        if(!Store.on(c,slot)||!canSchedule(c)||(Store.needsKey(c,slot)&&Store.keys(c).isEmpty()))return
        if(slot==5&&!Store.customDaily(c)&&Store.customAt(c)<=0)return
        if(slot==6&&Store.quickElapsed(c)<=0)return
        try {
            if(slot==6){
                val elapsed=Store.quickElapsed(c).coerceAtLeast(SystemClock.elapsedRealtime()+2000)
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,elapsed,operation)
                p.edit().putLong("wall$slot",now+(elapsed-SystemClock.elapsedRealtime())).apply()
            } else {
                val t=if(slot<5)nextPrayer(c,slot) else nextCustom(c)
                val show=PendingIntent.getActivity(c,100+slot,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t,show),operation);p.edit().putLong("wall$slot",t).apply()
            }
        } catch (_: SecurityException) { }
    }
}
