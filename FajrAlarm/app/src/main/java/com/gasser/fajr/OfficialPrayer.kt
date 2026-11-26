package com.gasser.fajr

import android.content.Context
import androidx.work.*
import org.jsoup.Jsoup
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Official ESA site publishes a daily city-wide Kafr El-Sheikh row, not a district-specific row. */
object OfficialPrayer {
    private const val URL_STRING = "https://www.esa.gov.eg/praytimes.aspx"
    private fun prefs(c: Context) = c.getSharedPreferences("official_prayers", Context.MODE_PRIVATE)
    fun cached(c: Context, date: LocalDate): LongArray? {
        val s = prefs(c).getString(date.toString(), null) ?: return null
        return try { val a=JSONArray(s); if(a.length()!=5) null else LongArray(5) { a.getLong(it) } } catch (_: Exception) { null }
    }
    fun save(c: Context, date: LocalDate, values: LongArray): Boolean {
        val value=JSONArray(values.toList()).toString(); val key=date.toString()
        if(prefs(c).getString(key,null)==value) return false
        prefs(c).edit().putString(key,value).apply();return true
    }
    fun parse(html: String, date: LocalDate): LongArray? {
        val rows = Jsoup.parse(html).select("tr")
        for (row in rows) {
            val cells=row.select("td")
            if(cells.size < 9 || cells[0].text().replace('\u00a0',' ').trim() != "كفر الشيخ" || cells[1].text().trim()!=date.toString()) continue
            val indexes=intArrayOf(3,5,6,7,8)
            val values = LongArray(5)
            for(i in indexes.indices) {
                val text=cells[indexes[i]].text().map { ch -> Character.digit(ch,10).let { if(it>=0) ('0'.code+it).toChar() else ch } }.joinToString("")
                val match=Regex("(\\d{1,2}):(\\d{1,2})\\s*([صم])").find(text) ?: return null
                val hour=match.groupValues[1].toInt();val minute=match.groupValues[2].toInt()
                if(hour !in 1..12 || minute !in 0..59) return null
                val h=(hour % 12)+if(match.groupValues[3]=="م")12 else 0
                values[i]=date.atTime(h,minute).atZone(Prayer.ZONE).toInstant().toEpochMilli()
            }
            if(values.toList().zipWithNext().any { it.first >= it.second }) return null
            return values
        }
        return null
    }
    fun refresh(c: Context) {
        val wm=WorkManager.getInstance(c)
        val constraint=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val now=OneTimeWorkRequestBuilder<PrayerSyncWorker>().setConstraints(constraint).build()
        val periodic=PeriodicWorkRequestBuilder<PrayerSyncWorker>(3,TimeUnit.HOURS).setConstraints(constraint).build()
        wm.enqueueUniqueWork("prayer-sync-now",ExistingWorkPolicy.KEEP,now)
        wm.enqueueUniquePeriodicWork("prayer-sync-periodic",ExistingPeriodicWorkPolicy.KEEP,periodic)
    }
    fun download(): String {
        val con=URL(URL_STRING).openConnection() as HttpURLConnection
        con.connectTimeout=15000;con.readTimeout=15000;con.setRequestProperty("User-Agent","FajrAlarm/2.0")
        try {
            if(con.responseCode!=200) throw IllegalStateException("ESA HTTP ${con.responseCode}")
            return con.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                val out=StringBuilder();val buf=CharArray(8192)
                while(true){val n=r.read(buf);if(n<0)break;if(out.length+n>2_000_000)throw IllegalStateException("ESA response too large");out.append(buf,0,n)}
                out.toString()
            }
        } finally { con.disconnect() }
    }
}
class PrayerSyncWorker(c: Context, params: WorkerParameters): Worker(c,params) {
    override fun doWork(): Result {
        return try {
            val date=LocalDate.now(Prayer.ZONE)
            val times=OfficialPrayer.parse(OfficialPrayer.download(),date) ?: return Result.retry()
            if(OfficialPrayer.save(applicationContext,date,times)) Scheduler.scheduleAll(applicationContext, preserveImminent=true)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
