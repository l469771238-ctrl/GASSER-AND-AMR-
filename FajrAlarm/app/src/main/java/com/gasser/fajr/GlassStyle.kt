package com.gasser.fajr

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

/** Translucent layered glass-style surfaces (no camera or wallpaper blur). */
object GlassStyle {
    val TEXT=Color.parseColor("#F5F7FF")
    val MUTED=Color.parseColor("#B5C3D9")
    val GOLD=Color.parseColor("#F6C784")
    private fun px(c: Context,n:Int)=(n*c.resources.displayMetrics.density).toInt()
    fun backdrop()=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xff091326.toInt(),0xff14294a.toInt(),0xff17182e.toInt()))
    fun panel(c: Context,r:Int=20)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xa9445c7e.toInt(),0xb322304d.toInt(),0xad1b273d.toInt())).apply {
        cornerRadius=px(c,r).toFloat();setStroke(px(c,1),0x62b7c9e1)
    }
    fun button(c: Context,accent:Boolean=false):RippleDrawable {
        val shape=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            if(accent) intArrayOf(0xffffd598.toInt(),0xffdba862.toInt()) else intArrayOf(0xa7546d8d.toInt(),0xa52e405d.toInt())).apply {
            cornerRadius=px(c,14).toFloat();setStroke(px(c,1),if(accent) 0xaaffdfae.toInt() else 0x62bbd2ed)
        }
        return RippleDrawable(ColorStateList.valueOf(if(accent)0x55443322 else 0x55ffffff),shape,null)
    }
    fun highlight(c: Context)=GradientDrawable().apply {setColor(0x88111f36.toInt());cornerRadius=px(c,18).toFloat();setStroke(px(c,1),0x72b8cde7)}
}
