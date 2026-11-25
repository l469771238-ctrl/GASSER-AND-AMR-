package com.gasser.fajr

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/** One-second seamless loops (integer cycles) for each alarm sound. */
object Synth {
    const val SR = 44100
    val NAMES = arrayOf("سيرينة", "إنذار حريق", "صفير", "جرس", "هجوم", "ثلاثي",
        "حريق سريع", "رباعي", "ثلاثي سريع", "ثلاثي حاد", "حريق حاد", "ثلاثي هابط")

    private fun frac(p: Double) = p - floor(p)
    private fun saw(p: Double) = frac(p) * 2 - 1
    private fun sq(p: Double) = if (frac(p) < 0.5) 1.0 else -1.0

    fun gen(kind: Int): ShortArray {
        val out = ShortArray(SR)
        val tri = doubleArrayOf(1200.0, 1500.0, 2100.0)
        val quad = doubleArrayOf(800.0, 1200.0, 1600.0, 2000.0)
        val hi = doubleArrayOf(1800.0, 2400.0, 3000.0)
        val desc = doubleArrayOf(2100.0, 1500.0, 1200.0)
        for (i in 0 until SR) {
            val t = i.toDouble() / SR
            val v = when (kind) {
                0 -> saw(950 * t + 450 / (2 * PI) * (1 - cos(2 * PI * t)))
                1 -> sq((if ((t * 4).toInt() % 2 == 0) 960.0 else 768.0) * t)
                2 -> if (frac(t * 6) < 0.5) sq(2900 * t) else 0.0
                3 -> if ((t % 0.5) < 0.4 && frac(t * 25) < 0.5) sq(1500 * t) else 0.0
                4 -> saw(300 * t + 850 * t * t)
                6 -> sq((if ((t * 8).toInt() % 2 == 0) 1000.0 else 1504.0) * t)
                7 -> sq(quad[(t * 4).toInt().coerceAtMost(3)] * t)
                8 -> sq(tri[(t * 6).toInt() % 3] * t)
                9 -> sq(hi[(t * 3).toInt().coerceAtMost(2)] * t)
                10 -> sq((if ((t * 6).toInt() % 2 == 0) 1380.0 else 1800.0) * t)
                11 -> sq(desc[(t * 3).toInt().coerceAtMost(2)] * t)
                else -> sq(tri[(t * 3).toInt().coerceAtMost(2)] * t)
            }
            out[i] = (v * 0.95 * 32767).toInt().toShort()
        }
        return out
    }
}
