package com.gasser.fajr

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
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
import android.os.SystemClock
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val NIGHT=Color.parseColor("#0F1733");private val PANEL=Color.parseColor("#18234B");private val LINE=Color.parseColor("#2A376B")
    private val DAWN=Color.parseColor("#F2B45C");private val TEXT=Color.parseColor("#EAEDFB");private val MUTED=Color.parseColor("#9099C4")
    private lateinit var scroll: ScrollView;private lateinit var root: LinearLayout;private lateinit var timeTv: TextView;private lateinit var leftTv: TextView;private lateinit var statusTv: TextView
    private val times=mutableMapOf<Int,TextView>();private lateinit var quickStatus: TextView
    private lateinit var player: SoundPlayer
    private val h=Handler(Looper.getMainLooper());private val stopTest=Runnable{player.stop()}
    private var quickMinutes=5
    private val ticker=object: Runnable{override fun run(){refresh();h.postDelayed(this,1000)}}
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
    override fun onCreate(s: Bundle?) {
        super.onCreate(s);window.decorView.layoutDirection=View.LAYOUT_DIRECTION_RTL;window.statusBarColor=NIGHT;window.navigationBarColor=NIGHT
        player=SoundPlayer(this);Scheduler.cancelLegacy(this);render()
        val perms=mutableListOf(Manifest.permission.CAMERA);if(Build.VERSION.SDK_INT>=33)perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this,perms.toTypedArray(),1)
        OfficialPrayer.refresh(this)
    }
    override fun onResume(){super.onResume();if(RingService.active)startActivity(Intent(this,ScanActivity::class.java));Scheduler.scheduleAll(this,true);OfficialPrayer.refresh(this);render();h.post(ticker)}
    override fun onPause(){h.removeCallbacks(ticker);h.removeCallbacks(stopTest);player.stop();super.onPause()}
    private fun rounded(c:Int,r:Int=14)=GradientDrawable().apply{setColor(c);cornerRadius=r.dp().toFloat()}
    private fun text(t:String,size:Float=16f,color:Int=TEXT)=TextView(this).apply{text=t;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL}
    private fun btn(t:String,action:()->Unit)=Button(this).apply{text=t;isAllCaps=false;setTextColor(TEXT);background=rounded(LINE,10);minWidth=0;minimumWidth=0;setOnClickListener{action()}}
    private fun heading(t:String){root.addView(text(t,17f,DAWN).apply{typeface=Typeface.DEFAULT_BOLD;setPadding(0,20.dp(),0,8.dp())})}
    private fun card():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=rounded(PANEL);setPadding(14.dp(),10.dp(),14.dp(),10.dp());root.addView(this,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=8.dp()})}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun addWeighted(parent:LinearLayout,v:View){parent.addView(v,LinearLayout.LayoutParams(0,-2,1f))}
    private fun rowButton(parent:LinearLayout,label:String,action:()->Unit){val b=btn(label,action);parent.addView(b,LinearLayout.LayoutParams(0,46.dp(),1f).apply{marginStart=4.dp();marginEnd=4.dp()})}
    private fun render(){
        val old=if(::scroll.isInitialized)scroll.scrollY else 0
        times.clear();root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20.dp(),28.dp(),20.dp(),32.dp())}
        scroll=ScrollView(this).apply{setBackgroundColor(NIGHT);isFillViewport=true;addView(root)}
        timeTv=text("--:--",52f,DAWN).apply{typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;textDirection=View.TEXT_DIRECTION_LTR}
        leftTv=text("",14f,MUTED).apply{gravity=Gravity.CENTER};statusTv=text("",12f,MUTED).apply{gravity=Gravity.CENTER}
        root.addView(text("الصلاة القادمة · كفر الشيخ",14f,MUTED).apply{gravity=Gravity.CENTER});root.addView(timeTv);root.addView(leftTv);root.addView(statusTv)
        heading("الصلوات الخمس")
        for(slot in 0..4)prayerCard(slot)
        heading("منبّه خاص");customCard()
        heading("منبّه سريع · مرة واحدة");quickCard()
        heading("إعدادات الصوت")
        root.addView(slider({"الصوت $it%"},30,100,Store.volume(this)){Store.setVolume(this,it)})
        root.addView(slider({"تضخيم +$it dB"},0,18,Store.boost(this)){Store.setBoost(this,it)})
        root.addView(slider({if(it==0)"أقصى صوت فوراً" else "أقصى صوت بعد $it ث"},0,60,Store.ramp(this)){Store.setRamp(this,it)})
        root.addView(text("التضخيم العالي قد يضر السمع والسماعة. ارفع صوت المنبّه من إعدادات الجهاز.",12f,MUTED).apply{setPadding(0,8.dp(),0,6.dp())})
        val actions=row();rowButton(actions,"الباركودات (${Store.keys(this).size})"){manageKeys()};rowButton(actions,"تجربة الصوت"){previewSound(0)};rowButton(actions,"تهيئة"){showSetup()};root.addView(actions)
        setContentView(scroll);scroll.post{scroll.scrollTo(0,old)};refresh()
    }
    private fun soundButton(slot:Int):Button=btn(Synth.NAMES[Store.sound(this,slot)]) {
        AlertDialog.Builder(this).setTitle("النغمة").setSingleChoiceItems(Synth.NAMES,Store.sound(this,slot)){d,which->Store.setSound(this,slot,which);previewSound(slot);d.dismiss();render()}.show()
    }
    private fun previewSound(slot:Int){h.removeCallbacks(stopTest);try{player.start(Store.sound(this,slot),Store.volume(this)/100f,Store.boost(this),1);h.postDelayed(stopTest,3000)}catch(_:Exception){toast("تعذّر تشغيل الصوت")}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun readyFor(slot:Int):Boolean {
        if(Store.needsKey(this,slot)&&Store.keys(this).isEmpty()){toast("سجّل باركودًا أولاً");manageKeys();return false}
        if(!Scheduler.canSchedule(this)){toast("فعّل المنبّهات الدقيقة من التهيئة");if(Build.VERSION.SDK_INT>=31)launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")));return false}
        return true
    }
    private fun alarmSwitch(slot:Int):SwitchCompat=SwitchCompat(this).apply{
        isChecked=Store.on(this@MainActivity,slot);contentDescription="تشغيل المنبّه"
        setOnCheckedChangeListener{_,checked->
            if(checked && !readyFor(slot)){isChecked=false;return@setOnCheckedChangeListener}
            if(checked && slot==5 && Store.customAt(this@MainActivity)==0L){isChecked=false;chooseCustom();return@setOnCheckedChangeListener}
            if(checked && slot==5 && !Store.customDaily(this@MainActivity))Store.setCustom(this@MainActivity,Store.customHour(this@MainActivity),Store.customMinute(this@MainActivity),false,Scheduler.selectedCustomAt(Store.customHour(this@MainActivity),Store.customMinute(this@MainActivity)))
            Store.setOn(this@MainActivity,slot,checked);Scheduler.schedule(this@MainActivity,slot);refresh()
        }
    }
    private fun prayerCard(slot:Int){
        val card=card();val header=row();addWeighted(header,text(Prayer.NAMES[slot],17f,TEXT).apply{typeface=Typeface.DEFAULT_BOLD});header.addView(alarmSwitch(slot));card.addView(header)
        val second=row();val t=text("--:--",16f,MUTED);times[slot]=t;addWeighted(second,t);second.addView(soundButton(slot),LinearLayout.LayoutParams(125.dp(),46.dp()));card.addView(second)
    }
    private fun keyChoice(card:LinearLayout,slot:Int){val r=row();addWeighted(r,text("الإيقاف بالباركود",14f,MUTED));val sw=SwitchCompat(this);sw.isChecked=Store.needsKey(this,slot);sw.setOnCheckedChangeListener{_,v->
        if(v&&Store.keys(this).isEmpty()){sw.isChecked=false;toast("سجّل باركودًا أولاً");return@setOnCheckedChangeListener}
        Store.setNeedsKey(this,slot,v);if(Store.on(this,slot))Scheduler.schedule(this,slot)
    };r.addView(sw);card.addView(r)}
    private fun customCard(){
        val card=card();val header=row();addWeighted(header,text("المنبّه السادس",17f,TEXT));header.addView(alarmSwitch(5));card.addView(header)
        val line=row();val t=text(if(Store.customAt(this)==0L)"حدد الوقت" else "${formatClock(Store.customHour(this),Store.customMinute(this))} · ${if(Store.customDaily(this))"يوميًا" else "مرة واحدة"}",15f,MUTED)
        t.setOnClickListener{chooseCustom()};addWeighted(line,t);line.addView(btn("ضبط الوقت"){chooseCustom()},LinearLayout.LayoutParams(110.dp(),46.dp()));card.addView(line)
        keyChoice(card,5);val snd=row();addWeighted(snd,text("النغمة",14f,MUTED));snd.addView(soundButton(5),LinearLayout.LayoutParams(125.dp(),46.dp()));card.addView(snd)
    }
    private fun chooseCustom(){
        TimePickerDialog(this,android.R.style.Theme_Material_Dialog_Alert,{_,hour,minute->
            AlertDialog.Builder(this).setTitle("التكرار").setItems(arrayOf("مرة واحدة","كل يوم")){_,which->
                val at=Scheduler.selectedCustomAt(hour,minute);Store.setCustom(this,hour,minute,which==1,at)
                if(readyFor(5)){Store.setOn(this,5,true);Scheduler.schedule(this,5)};render()
            }.show()
        },Store.customHour(this),Store.customMinute(this),true).show()
    }
    private fun quickCard(){
        val card=card();quickStatus=text("",18f,TEXT).apply{gravity=Gravity.CENTER};card.addView(quickStatus)
        val controls=row();rowButton(controls,"−٥"){quickMinutes=(quickMinutes-5).coerceAtLeast(5);refresh()};rowButton(controls,"+٥"){quickMinutes=(quickMinutes+5).coerceAtMost(1440);refresh()};rowButton(controls,"+١٠"){quickMinutes=(quickMinutes+10).coerceAtMost(1440);refresh()};card.addView(controls)
        keyChoice(card,6);val sound=row();addWeighted(sound,text("النغمة",14f,MUTED));sound.addView(soundButton(6),LinearLayout.LayoutParams(125.dp(),46.dp()));card.addView(sound)
        val actions=row();rowButton(actions,if(Store.on(this,6))"إعادة التشغيل" else "ابدأ"){if(readyFor(6)){val duration=quickMinutes*60000L;Store.setQuick(this,System.currentTimeMillis()+duration,SystemClock.elapsedRealtime()+duration);Store.setOn(this,6,true);Scheduler.schedule(this,6);render()}}
        if(Store.on(this,6))rowButton(actions,"إلغاء"){Store.setOn(this,6,false);Scheduler.schedule(this,6);render()};card.addView(actions)
    }
    private fun manageKeys(){
        val keys=Store.keys(this);val items=(listOf("+ إضافة باركود")+keys.indices.map{"باركود ${it+1} · حذف"}).toTypedArray()
        AlertDialog.Builder(this).setTitle("الباركودات المحفوظة").setItems(items){_,which->
            if(which==0)startActivity(Intent(this,ScanActivity::class.java).putExtra("register",true))
            else AlertDialog.Builder(this).setMessage("حذف باركود ${which}؟").setNegativeButton("رجوع",null).setPositiveButton("حذف"){_,_->
                Store.removeKey(this,keys[which-1]);if(Store.keys(this).isEmpty())for(slot in 0..6)if(Store.needsKey(this,slot)){Store.setOn(this,slot,false);Scheduler.schedule(this,slot)}
                render()
            }.show()
        }.show()
    }
    private fun slider(fmt:(Int)->String,min:Int,max:Int,value:Int,onChange:(Int)->Unit):View {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(2.dp(),12.dp(),2.dp(),0)};val label=text(fmt(value),15f,TEXT)
        val bar=SeekBar(this).apply{this.min=min;this.max=max;progress=value;progressTintList=ColorStateList.valueOf(DAWN);thumbTintList=ColorStateList.valueOf(DAWN)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){label.text=fmt(p);if(u)onChange(p)};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}})}
        box.addView(label);box.addView(bar);return box
    }
    private fun formatClock(hour:Int,minute:Int)=String.format(Locale.US,"%02d:%02d",hour,minute)
    private fun formatTime(ms:Long)=Instant.ofEpochMilli(ms).atZone(Prayer.ZONE).format(DateTimeFormatter.ofPattern("h:mm a",Locale.US))
    private fun refresh(){
        if(!::timeTv.isInitialized)return
        val now=System.currentTimeMillis();val next=(0..4).map{it to Scheduler.nextPrayer(this,it,now)}.minByOrNull{it.second}!!
        timeTv.text=formatTime(next.second);leftTv.text=Prayer.NAMES[next.first]+" · باقي "+((next.second-now)/60000)+" دقيقة"
        val today=LocalDate.now(Prayer.ZONE);statusTv.text=if(OfficialPrayer.cached(this,today)!=null)"مواقيت الهيئة المصرية للمساحة · كفر الشيخ" else "توقيت تقريبي · الاتصال لتحديث مواقيت الهيئة"
        for(slot in 0..4)times[slot]?.text=formatTime(Scheduler.nextPrayer(this,slot,now))
        if(::quickStatus.isInitialized){if(Store.on(this,6)){val remaining=((Store.quickElapsed(this)-SystemClock.elapsedRealtime()).coerceAtLeast(0)+999)/1000;quickStatus.text=String.format(Locale.US,"باقي %02d:%02d:%02d",remaining/3600,(remaining%3600)/60,remaining%60)}else quickStatus.text="$quickMinutes دقيقة"}
    }
    private fun launch(i:Intent){try{startActivity(i)}catch(_:Exception){try{startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}catch(_:Exception){}}}
    private fun showSetup(){val items=arrayOf("البطارية: بدون قيود","التشغيل التلقائي","النوافذ وشاشة القفل","المنبّهات الدقيقة","الإشعارات")
        AlertDialog.Builder(this).setItems(items){_,w->when(w){
            0->launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName")))
            1->launch(Intent().setComponent(ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity")))
            2->launch(Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter","com.miui.permcenter.permissions.PermissionsEditorActivity").putExtra("extra_pkgname",packageName))
            3->if(Build.VERSION.SDK_INT>=31)launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName"))) else launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
            else->launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
        }}.show()}
}
