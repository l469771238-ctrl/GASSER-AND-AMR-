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
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class RingService : Service() {
    companion object {
        @Volatile var active = false
        private const val AUTO_STOP_MS = 15 * 60 * 1000L

        fun start(c: Context, sound: Int) {
            ContextCompat.startForegroundService(
                c, Intent(c, RingService::class.java).putExtra("sound", sound)
            )
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, RingService::class.java))
        }
    }

    private var player: SoundPlayer? = null
    private var wl: PowerManager.WakeLock? = null
    private var vib: Vibrator? = null
    private val h = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopSelf() }

    override fun onBind(i: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val sound = i?.getIntExtra("sound", 0) ?: 0
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("ring", "alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        val open = Intent(this, ScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, "ring")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("الفجر")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)

        active = true
        if (wl == null) {
            wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fajr:ring")
                .apply { acquire(AUTO_STOP_MS + 60000L) }
        }
        player?.stop()
        player = SoundPlayer(this).also {
            it.start(sound, Store.volume(this) / 100f, Store.boost(this), Store.ramp(this))
        }
        vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0))

        h.removeCallbacks(autoStop)
        h.postDelayed(autoStop, AUTO_STOP_MS)
        try { startActivity(open) } catch (_: Exception) {}
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(autoStop)
        player?.stop()
        vib?.cancel()
        try { wl?.release() } catch (_: Exception) {}
        wl = null
        active = false
        super.onDestroy()
    }
}
