package com.gasser.fajr

import android.content.Context

object Store {
    private fun p(c: Context) = c.getSharedPreferences("fajr", Context.MODE_PRIVATE)

    fun volume(c: Context) = p(c).getInt("vol", 100)
    fun setVolume(c: Context, v: Int) = p(c).edit().putInt("vol", v).apply()
    fun boost(c: Context) = p(c).getInt("boost", 6)
    fun setBoost(c: Context, v: Int) = p(c).edit().putInt("boost", v).apply()
    fun ramp(c: Context) = p(c).getInt("ramp", 15)
    fun setRamp(c: Context, v: Int) = p(c).edit().putInt("ramp", v).apply()

    fun on(c: Context, s: Int) = p(c).getBoolean("on$s", false)
    fun setOn(c: Context, s: Int, v: Boolean) = p(c).edit().putBoolean("on$s", v).apply()
    fun offset(c: Context, s: Int) = p(c).getInt("off$s", if (s == 0) 0 else 10)
    fun setOffset(c: Context, s: Int, v: Int) = p(c).edit().putInt("off$s", v).apply()
    fun sound(c: Context, s: Int) = p(c).getInt("snd$s", s)
    fun setSound(c: Context, s: Int, v: Int) = p(c).edit().putInt("snd$s", v).apply()

    fun key(c: Context): String? = p(c).getString("key", null)
    fun setKey(c: Context, v: String) = p(c).edit().putString("key", v).apply()
}
