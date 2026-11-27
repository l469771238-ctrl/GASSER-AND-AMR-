package com.gasser.fajr

import android.content.Context
import android.media.AudioManager

/** Restore only the alarm stream setting after a ringing session, not media or ringer volume. */
object VolumeGuard {
    private fun prefs(c: Context) = c.getSharedPreferences("ring_volume", Context.MODE_PRIVATE)
    fun enforce(c: Context) {
        val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            if (!prefs(c).getBoolean("saved",false)) {
                // Persist before changing the volume so a service restart keeps the original value.
                prefs(c).edit().putInt("original",current).putInt("max",max).putBoolean("saved",true).commit()
            }
            if (current < max) audio.setStreamVolume(AudioManager.STREAM_ALARM,max,0)
        } catch (_: SecurityException) { /* DND or a device policy can prevent volume changes. */ }
        catch (_: IllegalArgumentException) { }
    }
    fun restore(c: Context) {
        val p=prefs(c)
        if (!p.getBoolean("saved",false)) return
        val audio=c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            val current=audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val oldMax=p.getInt("max",-1)
            val max=audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (current==oldMax) audio.setStreamVolume(AudioManager.STREAM_ALARM,p.getInt("original",current).coerceIn(0,max),0)
        } catch (_: SecurityException) { }
        catch (_: IllegalArgumentException) { }
        finally { p.edit().clear().commit() }
    }
}
