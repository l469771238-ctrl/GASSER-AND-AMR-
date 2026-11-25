package com.gasser.fajr

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.*

/** Fajr time: Egyptian General Authority of Survey (19.5 deg), Kafr El-Sheikh. */
object Prayer {
    private const val LAT = 31.1107
    private const val LNG = 30.9388
    private const val ANGLE = 19.5
    val ZONE: ZoneId = ZoneId.of("Africa/Cairo")

    /** Epoch millis of Fajr on the given Cairo calendar date. */
    fun fajr(date: LocalDate): Long {
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
        val ang = Math.toRadians(ANGLE)
        val c = (-sin(ang) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
        val t = Math.toDegrees(acos(c)) / 15
        val mins = ceil((noon - t) * 60).toLong()
        return midnightUtc + mins * 60000L - (tz * 3600000).toLong()
    }
}
