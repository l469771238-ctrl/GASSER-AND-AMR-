package com.gasser.fajr

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
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

class ScanActivity : AppCompatActivity() {
    private var register=false
    @Volatile private var done=false
    private var started=false
    private var torchOn=false
    private var camera: Camera?=null
    private lateinit var preview: PreviewView
    private lateinit var frame: View
    private lateinit var msg: TextView
    private lateinit var stopBtn: Button
    private lateinit var flashBtn: Button
    private val scanner=BarcodeScanning.getClient()
    private val exec=Executors.newSingleThreadExecutor()
    private val changes=object: BroadcastReceiver(){override fun onReceive(context: Context?, intent: Intent?){if(!register){if(!RingService.active)finish() else setupMode()}}}
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b);register=intent.getBooleanExtra("register",false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true)}
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true){override fun handleOnBackPressed(){if(register)finish() else moveTaskToBack(true)}})
        val root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
        preview=PreviewView(this);root.addView(preview,FrameLayout.LayoutParams(-1,-1))
        frame=View(this).apply { background=GradientDrawable().apply { setColor(Color.TRANSPARENT);setStroke(3.dp(),GlassStyle.GOLD);cornerRadius=20.dp().toFloat() } }
        val side=minOf(260.dp(),resources.displayMetrics.widthPixels*3/4)
        root.addView(frame,FrameLayout.LayoutParams(side,side,Gravity.CENTER))
        msg=TextView(this).apply {
            setTextColor(GlassStyle.TEXT);textSize=17f;gravity=Gravity.CENTER;background=GlassStyle.highlight(this@ScanActivity)
            setPadding(16.dp(),18.dp(),16.dp(),18.dp())
        }
        root.addView(msg,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply{setMargins(20.dp(),0,20.dp(),32.dp())})
        stopBtn=Button(this).apply {
            text="إيقاف المنبّه";isAllCaps=false;textSize=16f;typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#261A0E"));background=GlassStyle.button(this@ScanActivity,true)
            setOnClickListener{isEnabled=false;RingService.dismiss(this@ScanActivity)}
        }
        root.addView(stopBtn,FrameLayout.LayoutParams(220.dp(),56.dp(),Gravity.CENTER))
        flashBtn=Button(this).apply {
            text="الفلاش غير متاح";isAllCaps=false;isEnabled=false;textSize=16f;typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#261A0E"));background=GlassStyle.button(this@ScanActivity,true);elevation=8.dp().toFloat()
            contentDescription="تشغيل أو إطفاء الفلاش"
            setOnClickListener { val cam=camera ?: return@setOnClickListener;torchOn=!torchOn;cam.cameraControl.enableTorch(torchOn);updateFlashLabel() }
        }
        root.addView(flashBtn,FrameLayout.LayoutParams(220.dp(),56.dp(),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin=32.dp() })
        setContentView(root);setupMode()
    }
    override fun onNewIntent(i: Intent) {super.onNewIntent(i);setIntent(i);register=i.getBooleanExtra("register",false);done=false;setupMode()}
    override fun onStart(){super.onStart();ContextCompat.registerReceiver(this,changes,IntentFilter(RingService.CHANGED),ContextCompat.RECEIVER_NOT_EXPORTED)}
    override fun onStop(){unregisterReceiver(changes);super.onStop()}
    private fun setupMode() {
        done=false;stopBtn.isEnabled=true
        val scan=register||RingService.requiresKey
        preview.visibility=if(scan)View.VISIBLE else View.GONE;frame.visibility=if(scan)View.VISIBLE else View.GONE
        flashBtn.visibility=if(scan)View.VISIBLE else View.GONE;stopBtn.visibility=if(scan)View.GONE else View.VISIBLE
        msg.text=if(register)"وجّه الكاميرا على باركودك" else if(scan)"امسح أي باركود محفوظ" else "${RingService.label} · اضغط إيقاف"
        if(!register && RingService.audioProblem)msg.text="تعذّر تشغيل الصوت · ${msg.text}"
        if(!scan){stopCamera();return}
        if(!register&&Store.keys(this).isEmpty()){stopBtn.visibility=View.VISIBLE;msg.text="لا يوجد باركود محفوظ";return}
        if(hasCam())startCamera() else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),2)
    }
    private fun updateFlashLabel(){flashBtn.text=if(!flashBtn.isEnabled)"الفلاش غير متاح" else if(torchOn)"◉  إطفاء الفلاش" else "◉  تشغيل الفلاش"}
    private fun stopCamera(){
        camera?.cameraControl?.enableTorch(false);torchOn=false;flashBtn.isEnabled=false;updateFlashLabel()
        try { val future=ProcessCameraProvider.getInstance(this);if(future.isDone)future.get().unbindAll() } catch (_: Exception) {}
        camera=null;started=false
    }
    private fun hasCam()=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
    override fun onRequestPermissionsResult(rc: Int,p: Array<out String>,r: IntArray){super.onRequestPermissionsResult(rc,p,r);if(rc==2){if(hasCam())startCamera() else if(register)finish() else {msg.text="الكاميرا غير متاحة";stopBtn.visibility=View.VISIBLE}}}
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun startCamera() {
        if(started)return;started=true
        val fut=ProcessCameraProvider.getInstance(this)
        fut.addListener({
            try {
                if(isFinishing||isDestroyed||(!register&&!RingService.requiresKey)){started=false;return@addListener}
                val provider=fut.get()
                val prev=Preview.Builder().build().also{it.setSurfaceProvider(preview.surfaceProvider)}
                @Suppress("DEPRECATION") val analysis=ImageAnalysis.Builder().setTargetResolution(Size(1280,720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(exec){ proxy ->
                    val img=proxy.image
                    if(img==null||done||(!register&&!RingService.requiresKey)){proxy.close()} else {
                        try {
                            scanner.process(InputImage.fromMediaImage(img,proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener { list -> list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let { onCode(it) } }
                                .addOnCompleteListener { proxy.close() }
                        } catch(_: Exception){proxy.close()}
                    }
                }
                provider.unbindAll();camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,prev,analysis)
                flashBtn.isEnabled=camera?.cameraInfo?.hasFlashUnit()==true;updateFlashLabel()
            } catch(_: Exception) { started=false;msg.text="تعذّر فتح الكاميرا";if(!register)stopBtn.visibility=View.VISIBLE }
        },ContextCompat.getMainExecutor(this))
    }
    private fun onCode(v: String) {
        if(done||v.isBlank()||(!register&&!RingService.requiresKey))return
        if(register){done=true;val added=Store.addKey(this,v);Toast.makeText(this,if(added)"تم حفظ الباركود" else "الباركود محفوظ بالفعل",Toast.LENGTH_SHORT).show();finish()}
        else if(Store.keys(this).contains(v)){done=true;RingService.dismiss(this)}
        else msg.text="ده مش باركود محفوظ"
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(!register && RingService.active && (event.keyCode==KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode==KeyEvent.KEYCODE_VOLUME_UP || event.keyCode==KeyEvent.KEYCODE_VOLUME_MUTE)) return true
        return super.dispatchKeyEvent(event)
    }
    override fun onDestroy(){stopCamera();scanner.close();exec.shutdown();super.onDestroy()}
}
