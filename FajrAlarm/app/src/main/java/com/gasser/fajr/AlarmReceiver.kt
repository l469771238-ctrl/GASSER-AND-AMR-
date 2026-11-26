package com.gasser.fajr
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val slot=i.getIntExtra("slot",-1)
        if(slot !in 0..6 || !Store.on(c,slot))return
        val sound=Store.sound(c,slot);val needsKey=Store.needsKey(c,slot)
        val label=if(slot<5)Prayer.NAMES[slot] else if(slot==5)"المنبّه الخاص" else "المنبّه السريع"
        if(slot==6 || (slot==5&&!Store.customDaily(c)))Store.setOn(c,slot,false)
        Scheduler.schedule(c,slot)
        RingService.start(c,sound,needsKey,label)
    }
}
