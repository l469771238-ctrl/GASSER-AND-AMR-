package com.gasser.fajr

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class PrayerTest {
    private val date=LocalDate.of(2026,11,26)
    private val row="""<table><tr><td>كفر الشيخ</td><td>2026-11-26</td><td>هجري</td><td>5:0 ص</td><td>6:33 ص</td><td>11:44 ص</td><td>2:35 م</td><td>4:54 م</td><td>6:17 م</td></tr></table>"""
    private fun clock(t: Long)=Instant.ofEpochMilli(t).atZone(Prayer.ZONE).format(DateTimeFormatter.ofPattern("HH:mm",Locale.US))
    @Test fun readsOfficialRowAndConvertsAfternoonCorrectly(){
        val result=OfficialPrayer.parse(row,date)!!
        assertEquals(listOf("05:00","11:44","14:35","16:54","18:17"),result.map(::clock))
    }
    @Test fun refusesStaleAndBrokenRows(){
        assertNull(OfficialPrayer.parse(row,date.plusDays(1)))
        assertNull(OfficialPrayer.parse(row.replace("2:35 م","no time"),date))
        assertNull(OfficialPrayer.parse(row.replace("كفر الشيخ","طنطا"),date))
    }
    @Test fun estimatedTimesRemainOrderedAndCloseToOfficialExample(){
        val estimate=Prayer.estimated(date)
        assertEquals(5,estimate.size)
        assertTrue(estimate.toList().zipWithNext().all{it.first<it.second})
        val official=OfficialPrayer.parse(row,date)!!
        assertTrue(estimate.indices.all { kotlin.math.abs(estimate[it]-official[it])<=120000 })
    }
    @Test fun midnightAndDaylightTransitionsProduceFutureManualTime(){
        val z=Prayer.ZONE
        val before=LocalDate.of(2026,4,23).atTime(23,59).atZone(z).toInstant().toEpochMilli()
        val result=Scheduler.selectedCustomAt(5,10,before)
        val expected=LocalDate.of(2026,4,24).atTime(5,10).atZone(z).toInstant().toEpochMilli()
        assertEquals(expected,result)
    }
}
