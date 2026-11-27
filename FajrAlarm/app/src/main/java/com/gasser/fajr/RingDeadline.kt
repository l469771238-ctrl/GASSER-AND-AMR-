package com.gasser.fajr

/** A single uninterrupted ringing session has a hard 20-minute cap, even with queued alarms. */
object RingDeadline {
    const val LIMIT_MS = 20 * 60 * 1000L
    fun start(now: Long): Long = now + LIMIT_MS
    fun remaining(deadline: Long, now: Long): Long = (deadline - now).coerceAtLeast(0L)
}
