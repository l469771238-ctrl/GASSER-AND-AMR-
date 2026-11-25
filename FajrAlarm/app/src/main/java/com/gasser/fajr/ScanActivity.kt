package com.gasser.fajr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** Ring mode: scan the saved barcode to stop the alarm. Register mode: scan once to save the key. */
class ScanActivity : AppCompatActivity() {
    private var register = false
    private var done = false
    private lateinit var preview: PreviewView
    private lateinit var msg: TextView
    private lateinit var stopBtn: Button
    private val exec = Executors.newSingleThreadExecutor()

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        register = intent.getBooleanExtra("register", false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!register) {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true); setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            })
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        msg = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B8160A"))
            setPadding(16.dp(), 18.dp(), 16.dp(), 18.dp())
            text = if (register) "وجّه الكاميرا على باركودك" else "امسح الباركود"
        }
        root.addView(msg, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        stopBtn = Button(this).apply {
            text = "إيقاف"; visibility = View.GONE
            setOnClickListener { RingService.stop(this@ScanActivity); finish() }
        }
        root.addView(stopBtn, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = 48.dp()
        })
        setContentView(root)

        if (!register && Store.key(this) == null) {
            stopBtn.visibility = View.VISIBLE
            return
        }
        if (hasCam()) startCamera()
        else if (register) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 2)
        else stopBtn.visibility = View.VISIBLE
    }

    private fun hasCam() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (hasCam()) startCamera() else if (register) finish() else stopBtn.visibility = View.VISIBLE
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun startCamera() {
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val provider = fut.get()
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val scanner = BarcodeScanning.getClient()
            analysis.setAnalyzer(exec) { proxy ->
                val img = proxy.image
                if (img == null || done) {
                    proxy.close()
                } else {
                    val input = InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { list ->
                            for (code in list) code.rawValue?.let { onCode(it) }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, prev, analysis)
            } catch (_: Exception) {
                if (!register) stopBtn.visibility = View.VISIBLE
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCode(v: String) {
        if (done) return
        if (register) {
            done = true
            Store.setKey(this, v)
            Toast.makeText(this, "تم", Toast.LENGTH_SHORT).show()
            finish()
        } else if (v == Store.key(this)) {
            done = true
            RingService.stop(this)
            finish()
        } else {
            msg.text = "ده مش باركودك"
        }
    }

    override fun onDestroy() {
        exec.shutdown()
        super.onDestroy()
    }
}
