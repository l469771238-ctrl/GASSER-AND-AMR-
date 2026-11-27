package com.gasser.fajr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class RingService : Service() {
    companion object {
        @Volatile var active=false
        @Volatile var requiresKey=true
        @Volatile var label="المنبّه"
        @Volatile var audioProblem=false
        const val CHANGED="com.gasser.fajr.RING_CHANGED"
        private const val START="com.gasser.fajr.START_RING"
        private const val DISMISS="com.gasser.fajr.DISMISS_RING"
        fun start(c: Context, sound: Int, needsKey: Boolean, label: String) {
            ContextCompat.startForegroundService(c,Intent(c,RingService::class.java).setAction(START).putExtra("sound",sound).putExtra("needsKey",needsKey).putExtra("label",label))
        }
        fun dismiss(c: Context) { c.startService(Intent(c,RingService::class.java).setAction(DISMISS)) }
    }
    private var player: SoundPlayer?=null
    private var wl: PowerManager.WakeLock?=null
    private var vib: Vibrator?=null
    private val h=Handler(Looper.getMainLooper())
    private val autoStop=Runnable { stopAll() }
    private val keepVolume=object: Runnable {
        override fun run() {
            if(active) { VolumeGuard.enforce(this@RingService); h.postDelayed(this,250) }
        }
    }
    private fun events(): JSONArray = try { JSONArray(Store.events(this)) } catch (_: Exception) { JSONArray() }
    private fun save(q: JSONArray) = Store.saveEvents(this,q.toString())
    private fun changed() { sendBroadcast(Intent(CHANGED).setPackage(packageName)) }
    override fun onBind(i: Intent?): IBinder?=null
    private fun notification(): android.app.Notification {
        val nm=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("ring","المنبّه",NotificationManager.IMPORTANCE_HIGH).apply { setSound(null,null);enableVibration(false);lockscreenVisibility=android.app.Notification.VISIBILITY_PUBLIC })
        val open=Intent(this,ScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,"ring").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(label)
            .setContentText(if(audioProblem)"تعذّر تشغيل صوت المنبّه" else if(requiresKey)"امسح الباركود لإيقاف المنبّه" else "اضغط لإيقاف المنبّه")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true)
            .setContentIntent(pi).setFullScreenIntent(pi,true).build()
    }
    private fun foreground() {
        if(Build.VERSION.SDK_INT>=29)startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(1,notification())
    }
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        var q=events()
        val deadline=Store.ringDeadline(this)
        if(q.length()>0 && deadline>0 && RingDeadline.remaining(deadline,SystemClock.elapsedRealtime())==0L) {
            clearSession();q=JSONArray()
        }
        if(i?.action==START) {
            if(q.length()==0) Store.setRingDeadline(this,RingDeadline.start(SystemClock.elapsedRealtime()))
            q.put(JSONObject().put("sound",i.getIntExtra("sound",0)).put("key",i.getBooleanExtra("needsKey",true)).put("label",i.getStringExtra("label")?:"المنبّه"));save(q)
        }
        // Publish promptly even if restarted by Android or asked to dismiss.
        foreground()
        if(i?.action==DISMISS){advance();return if(active) START_STICKY else START_NOT_STICKY}
        if(!active && q.length()>0) {
            if(Store.ringDeadline(this)==0L)Store.setRingDeadline(this,RingDeadline.start(SystemClock.elapsedRealtime()))
            playFirst(q)
        } else if(q.length()==0){stopSelf();return START_NOT_STICKY}
        return START_STICKY
    }
    @Suppress("DEPRECATION")
    private fun playFirst(q: JSONArray) {
        val first=q.optJSONObject(0) ?: run { stopSelf();return }
        label=first.optString("label","المنبّه");requiresKey=first.optBoolean("key",true);active=true
        foreground()
        val remaining=RingDeadline.remaining(Store.ringDeadline(this),SystemClock.elapsedRealtime())
        if(remaining==0L){stopAll();return}
        try { if(wl?.isHeld==true) wl?.release() } catch (_: Exception) {}
        wl=(getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"fajr:ring").apply { acquire(remaining+60000L) }
        h.removeCallbacks(keepVolume);VolumeGuard.enforce(this);h.postDelayed(keepVolume,250)
        player?.stop();audioProblem=false
        try { player=SoundPlayer(this).also { it.start(first.optInt("sound",0).coerceIn(0,Synth.NAMES.lastIndex),Store.volume(this)/100f,Store.boost(this),Store.ramp(this)) } }
        catch (_: Exception) { player?.stop();player=null;audioProblem=true }
        foreground()
        vib=getSystemService(Context.VIBRATOR_SERVICE) as Vibrator;vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,700,300),0))
        h.removeCallbacks(autoStop);h.postDelayed(autoStop,remaining)
        changed()
        try { startActivity(Intent(this,ScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) } catch (_: Exception) { }
    }
    private fun advance() {
        val old=events();val next=JSONArray();for(i in 1 until old.length())next.put(old.get(i));save(next)
        h.removeCallbacks(autoStop);player?.stop();player=null;vib?.cancel();active=false
        if(next.length()>0)playFirst(next) else stopAll()
    }
    private fun clearSession() {
        save(JSONArray());Store.setRingDeadline(this,0L);h.removeCallbacks(autoStop);h.removeCallbacks(keepVolume)
        player?.stop();player=null;vib?.cancel();active=false;audioProblem=false;VolumeGuard.restore(this)
    }
    private fun stopAll() { clearSession();changed();stopSelf() }
    override fun onDestroy(){h.removeCallbacks(autoStop);h.removeCallbacks(keepVolume);player?.stop();vib?.cancel();try{if(wl?.isHeld==true)wl?.release()}catch(_:Exception){};wl=null;active=false;VolumeGuard.restore(this);changed();super.onDestroy()}
}
