package com.gasser.fajr

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private val NIGHT = Color.parseColor("#0F1733")
    private val PANEL = Color.parseColor("#18234B")
    private val LINE = Color.parseColor("#2A376B")
    private val DAWN = Color.parseColor("#F2B45C")
    private val TEXT = Color.parseColor("#EAEDFB")
    private val MUTED = Color.parseColor("#9099C4")

    private lateinit var timeTv: TextView
    private lateinit var leftTv: TextView
    private lateinit var keyBtn: Button
    private lateinit var player: SoundPlayer
    private val h = Handler(Looper.getMainLooper())
    private val stopTest = Runnable { player.stop() }
    private val ticker = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 1000) }
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        window.statusBarColor = NIGHT
        window.navigationBarColor = NIGHT
        player = SoundPlayer(this)
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
        setContentView(build())
        if (!Scheduler.scheduleAll(this))
            Toast.makeText(this, "فعّل المنبّهات الدقيقة من التهيئة", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        if (RingService.active) startActivity(Intent(this, ScanActivity::class.java))
        Scheduler.scheduleAll(this)
        refresh()
        h.post(ticker)
    }

    override fun onPause() {
        h.removeCallbacks(ticker)
        super.onPause()
    }

    private fun rounded(c: Int, r: Int = 14) = GradientDrawable().apply {
        setColor(c); cornerRadius = r.dp().toFloat()
    }

    private fun btn(t: String, w: Int = -2, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(TEXT); background = rounded(LINE, 12)
        minWidth = 0; minimumWidth = 0
        layoutParams = LinearLayout.LayoutParams(w, 46.dp())
        setOnClickListener { onClick() }
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 36.dp(), 20.dp(), 28.dp())
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(NIGHT); isFillViewport = true; addView(root)
        }
        timeTv = TextView(this).apply {
            textSize = 72f; setTextColor(DAWN); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_LTR
        }
        leftTv = TextView(this).apply {
            textSize = 18f; setTextColor(MUTED); gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
        }
        root.addView(timeTv)
        root.addView(leftTv)

        for (slot in 0..1) root.addView(alarmRow(slot), rowParams(18))

        root.addView(slider({ "الصوت $it%" }, 30, 100, Store.volume(this)) { Store.setVolume(this, it) })
        root.addView(slider({ "تضخيم +$it dB" }, 0, 18, Store.boost(this)) { Store.setBoost(this, it) })
        root.addView(slider({ if (it == 0) "أقصى صوت فوراً" else "أقصى صوت بعد $it ث" }, 0, 60, Store.ramp(this)) { Store.setRamp(this, it) })
        root.addView(TextView(this).apply {
            text = "التضخيم العالي قد يضر السمع والسماعة. ارفع صوت المنبّه من إعدادات الجهاز."
            textSize = 12f; setTextColor(MUTED); setPadding(0, 10.dp(), 0, 0)
        })

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        keyBtn = btn("", 0) { startActivity(Intent(this, ScanActivity::class.java).putExtra("register", true)) }
        val test = btn("تجربة", 0) {
            h.removeCallbacks(stopTest)
            player.start(Store.sound(this, 0), Store.volume(this) / 100f, Store.boost(this), 1)
            h.postDelayed(stopTest, 3000)
        }
        val setup = btn("تهيئة", 0) { showSetup() }
        for (v in listOf(keyBtn, test, setup)) {
            (v.layoutParams as LinearLayout.LayoutParams).apply { weight = 1f; marginEnd = 6.dp(); marginStart = 6.dp() }
            bar.addView(v)
        }
        root.addView(bar, rowParams(26))
        return scroll
    }

    private fun rowParams(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top.dp() }

    private fun offsetText(o: Int) = when {
        o == 0 -> "الفجر"
        o > 0 -> "الفجر +$o د"
        else -> "الفجر −${abs(o)} د"
    }

    private fun alarmRow(slot: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(PANEL); setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
        }
        val sw = SwitchCompat(this).apply {
            isChecked = Store.on(this@MainActivity, slot)
            setOnCheckedChangeListener { _, v ->
                if (v && Store.key(this@MainActivity).isNullOrEmpty()) {
                    isChecked = false
                    Toast.makeText(this@MainActivity, "سجّل باركودك الأول", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                Store.setOn(this@MainActivity, slot, v)
                Scheduler.scheduleAll(this@MainActivity)
                refresh()
            }
        }
        val label = TextView(this).apply {
            setTextColor(TEXT); textSize = 16f; gravity = Gravity.CENTER
            text = offsetText(Store.offset(this@MainActivity, slot))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        fun change(d: Int) {
            val o = (Store.offset(this, slot) + d).coerceIn(-120, 120)
            Store.setOffset(this, slot, o)
            label.text = offsetText(o)
            Scheduler.scheduleAll(this)
            refresh()
        }
        val minus = btn("−", 44.dp()) { change(-5) }
        val plus = btn("+", 44.dp()) { change(5) }
        val snd = btn(Synth.NAMES[Store.sound(this, slot)]) {}
        snd.setOnClickListener {
            AlertDialog.Builder(this)
                .setSingleChoiceItems(Synth.NAMES, Store.sound(this, slot)) { d, which ->
                    Store.setSound(this, slot, which)
                    snd.text = Synth.NAMES[which]
                    h.removeCallbacks(stopTest)
                    player.start(which, Store.volume(this) / 100f, Store.boost(this), 1)
                    h.postDelayed(stopTest, 2500)
                    d.dismiss()
                }.show()
        }
        row.addView(sw)
        row.addView(minus)
        row.addView(label)
        row.addView(plus)
        row.addView(snd, LinearLayout.LayoutParams(-2, 46.dp()).apply { marginStart = 8.dp() })
        return row
    }

    private fun slider(fmt: (Int) -> String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(2.dp(), 18.dp(), 2.dp(), 0)
        }
        val tv = TextView(this).apply { setTextColor(TEXT); textSize = 15f; text = fmt(value) }
        val sb = SeekBar(this).apply {
            this.min = min; this.max = max; progress = value
            progressTintList = ColorStateList.valueOf(DAWN)
            thumbTintList = ColorStateList.valueOf(DAWN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { tv.text = fmt(p); onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        box.addView(tv); box.addView(sb)
        return box
    }

    private fun refresh() {
        val f = Scheduler.nextTrigger(0)
        val z = Instant.ofEpochMilli(f).atZone(Prayer.ZONE)
        val h12 = if (z.hour % 12 == 0) 12 else z.hour % 12
        timeTv.text = String.format(Locale.US, "%d:%02d %s", h12, z.minute, if (z.hour < 12) "ص" else "م")
        var best = Long.MAX_VALUE
        for (s in 0..1) if (Store.on(this, s)) best = minOf(best, Scheduler.nextTrigger(Store.offset(this, s)))
        leftTv.text = if (best == Long.MAX_VALUE) "—" else {
            val d = (best - System.currentTimeMillis()) / 1000
            String.format(Locale.US, "%d:%02d:%02d", d / 3600, (d % 3600) / 60, d % 60)
        }
        keyBtn.text = if (Store.key(this) == null) "سجّل باركودك" else "باركودي ✓"
    }

    private fun launch(i: Intent) {
        try { startActivity(i) } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        }
    }

    private fun showSetup() {
        val items = arrayOf("البطارية: بدون قيود", "التشغيل التلقائي", "النوافذ وشاشة القفل", "المنبّهات الدقيقة")
        AlertDialog.Builder(this).setItems(items) { _, w ->
            when (w) {
                0 -> launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                1 -> launch(Intent().setComponent(ComponentName(
                    "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                2 -> launch(Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", packageName))
                else -> if (Build.VERSION.SDK_INT >= 31)
                    launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                else launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }.show()
    }
}
