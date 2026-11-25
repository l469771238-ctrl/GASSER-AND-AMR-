package com.gasser.fajr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val slot = i.getIntExtra("slot", 0)
        Scheduler.scheduleAll(c)
        if (Store.on(c, slot)) RingService.start(c, Store.sound(c, slot))
    }
}
