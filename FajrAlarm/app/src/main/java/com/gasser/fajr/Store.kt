package com.gasser.fajr

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray

object Store {
    private fun p(c: Context) = c.getSharedPreferences("fajr", Context.MODE_PRIVATE)
    fun volume(c: Context) = p(c).getInt("vol", 100)
    fun setVolume(c: Context, v: Int) = p(c).edit().putInt("vol", v.coerceIn(30,100)).apply()
    fun boost(c: Context) = p(c).getInt("boost", 6)
    fun setBoost(c: Context, v: Int) = p(c).edit().putInt("boost", v.coerceIn(0,18)).apply()
    fun ramp(c: Context) = p(c).getInt("ramp", 15)
    fun setRamp(c: Context, v: Int) = p(c).edit().putInt("ramp", v.coerceIn(0,60)).apply()
    fun on(c: Context, s: Int) = p(c).getBoolean("v2_on$s", false)
    fun setOn(c: Context, s: Int, v: Boolean) = p(c).edit().putBoolean("v2_on$s", v).apply()
    fun sound(c: Context, s: Int) = p(c).getInt("v2_snd$s", 0).coerceIn(0, Synth.NAMES.lastIndex)
    fun setSound(c: Context, s: Int, v: Int) = p(c).edit().putInt("v2_snd$s", v).apply()
    fun needsKey(c: Context, slot: Int) = slot < 5 || p(c).getBoolean("v2_key$slot", true)
    fun setNeedsKey(c: Context, slot: Int, v: Boolean) = p(c).edit().putBoolean("v2_key$slot", v).apply()
    fun customHour(c: Context) = p(c).getInt("custom_hour", 5)
    fun customMinute(c: Context) = p(c).getInt("custom_min", 0)
    fun customDaily(c: Context) = p(c).getBoolean("custom_daily", false)
    fun customAt(c: Context) = p(c).getLong("custom_at", 0L)
    fun setCustom(c: Context, h: Int, m: Int, daily: Boolean, at: Long) = p(c).edit().putInt("custom_hour",h).putInt("custom_min",m).putBoolean("custom_daily",daily).putLong("custom_at",at).apply()
    fun quickWall(c: Context) = p(c).getLong("quick_wall", 0L)
    fun quickElapsed(c: Context) = p(c).getLong("quick_elapsed", 0L)
    fun setQuick(c: Context, wall: Long, elapsed: Long) = p(c).edit().putLong("quick_wall",wall).putLong("quick_elapsed",elapsed).apply()
    fun rebaseQuickAfterBoot(c: Context) {
        if (on(c,6)) setQuick(c, quickWall(c), SystemClock.elapsedRealtime() + (quickWall(c)-System.currentTimeMillis()).coerceAtLeast(2000L))
    }
    fun keys(c: Context): List<String> {
        val result = linkedSetOf<String>()
        val json = p(c).getString("keys_v2", "[]") ?: "[]"
        try { val a=JSONArray(json); for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { result.add(it) } } catch (_: Exception) {}
        p(c).getString("key", null)?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        return result.toList()
    }
    private fun saveKeys(c: Context, keys: Collection<String>) { p(c).edit().putString("keys_v2",JSONArray(keys).toString()).remove("key").apply() }
    fun addKey(c: Context, value: String): Boolean { if (value.isBlank() || keys(c).contains(value)) return false; saveKeys(c,keys(c)+value);return true }
    fun removeKey(c: Context, value: String) { saveKeys(c,keys(c).filterNot { it == value }) }
    fun events(c: Context) = p(c).getString("ring_events", "[]") ?: "[]"
    fun saveEvents(c: Context, events: String) { p(c).edit().putString("ring_events",events).commit() }
}
