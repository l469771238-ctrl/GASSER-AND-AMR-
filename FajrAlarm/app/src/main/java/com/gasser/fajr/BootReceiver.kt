package com.gasser.fajr
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if(i.action==Intent.ACTION_BOOT_COMPLETED){Store.rebaseQuickAfterBoot(c);Store.saveEvents(c,"[]")}
        Scheduler.cancelLegacy(c);Scheduler.scheduleAll(c);OfficialPrayer.refresh(c)
    }
}
