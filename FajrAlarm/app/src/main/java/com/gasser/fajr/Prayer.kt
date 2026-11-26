package com.gasser.fajr

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.*

/** Offline Egyptian-method estimate for Kafr El-Sheikh. Official daily values take priority. */
object Prayer {
    private const val LAT = 31.1107
    private const val LNG = 30.9388
    val ZONE: ZoneId = ZoneId.of("Africa/Cairo")
    val NAMES = arrayOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

    fun estimated(date: LocalDate): LongArray {
        val midnightUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val noonUtc = midnightUtc + 12 * 3600000L
        val d = noonUtc / 86400000.0 + 2440587.5 - 2451545.0
        val g = Math.toRadians((357.529 + 0.98560028 * d) % 360)
        val q = (280.459 + 0.98564736 * d) % 360
        val l = Math.toRadians((q + 1.915 * sin(g) + 0.020 * sin(2 * g)) % 360)
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = (Math.toDegrees(atan2(cos(e) * sin(l), cos(l))) / 15 + 24) % 24
        val eqt = (((q / 15 - ra + 12) % 24) + 24) % 24 - 12
        val decl = asin(sin(e) * sin(l))
        val tz = ZONE.rules.getOffset(Instant.ofEpochMilli(noonUtc)).totalSeconds / 3600.0
        val noon = 12 + tz - LNG / 15 - eqt
        val lat = Math.toRadians(LAT)
        fun hourAngle(altitude: Double): Double {
            val c = (sin(altitude) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
            return Math.toDegrees(acos(c.coerceIn(-1.0, 1.0))) / 15
        }
        val asrAltitude = atan(1 / (1 + tan(abs(lat - decl))))
        val hours = doubleArrayOf(noon - hourAngle(Math.toRadians(-19.5)), noon,
            noon + hourAngle(asrAltitude), noon + hourAngle(Math.toRadians(-0.833)),
            noon + hourAngle(Math.toRadians(-17.5)))
        return hours.map { midnightUtc + ceil(it * 60).toLong() * 60000L - (tz * 3600000).toLong() }.toLongArray()
    }
    fun forDate(c: android.content.Context, date: LocalDate): LongArray = OfficialPrayer.cached(c, date) ?: estimated(date)
}
