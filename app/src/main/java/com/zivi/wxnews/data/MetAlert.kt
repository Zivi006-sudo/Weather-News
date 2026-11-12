package com.zivi.wxnews.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.coroutines.resume

val MVT: ZoneId = ZoneId.of("Indian/Maldives")

enum class AlertLevel(val label: String, val rank: Int) {
    WHITE("White Alert", 1), YELLOW("Yellow Advisory", 2), ORANGE("Orange Warning", 3), RED("Red Warning", 4);

    companion object {
        fun of(s: String): AlertLevel? = when (s.lowercase(Locale.US)) {
            "white" -> WHITE
            "yellow" -> YELLOW
            "orange" -> ORANGE
            "red" -> RED
            else -> null
        }
    }
}

class MetAlert(
    val level: AlertLevel,
    val text: String,
    val area: String,
    val timeText: String,
    val from: ZonedDateTime?,
    val to: ZonedDateTime?,
) {
    val atolls: Set<Int> = Atolls.inArea(area)

    /** In effect now (or the times couldn't be read, in which case it is treated as current). */
    fun activeAt(now: ZonedDateTime): Boolean {
        val t = to ?: return true
        val f = from
        return !now.isAfter(t) && (f == null || !now.isBefore(f.minusHours(1)))
    }

    fun toJson(): String = JSONObject()
        .put("level", level.name).put("text", text).put("area", area).put("time", timeText)
        .put("from", from?.toString() ?: "").put("to", to?.toString() ?: "")
        .toString()

    companion object {
        fun fromJson(s: String): MetAlert? = try {
            val o = JSONObject(s)
            MetAlert(
                AlertLevel.valueOf(o.getString("level")), o.optString("text"), o.optString("area"), o.optString("time"),
                o.optString("from").takeIf { it.isNotEmpty() }?.let { ZonedDateTime.parse(it) },
                o.optString("to").takeIf { it.isNotEmpty() }?.let { ZonedDateTime.parse(it) },
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Reads the current alert from the Maldives Meteorological Service website. Its pages fill themselves in with
 * scripts, so the page is opened in an invisible browser view and its text is read. The site shows the alert
 * as: "<level> Alert", the message, "Area" and the atolls, "Time" and "21 Oct 20:30 — 22 Oct 01:30".
 */
object MetOffice {
    const val SITE = "https://www.meteorology.gov.mv/"

    class Result(val alert: MetAlert?, val pageRead: Boolean)

    private val ALERT = Regex(
        "(?is)\\b(white|yellow|orange|red)\\s+(?:alert|advisory|warning)\\b\\s*(.{3,500}?)\\s*\\bArea\\b\\s*:?\\s*(.{2,240}?)" +
            "\\s*\\bTime\\b\\s*:?\\s*(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{1,2}:\\d{2})\\s*(?:[—–-]+|to)\\s*(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{1,2}:\\d{2})"
    )

    suspend fun read(ctx: Context): Result {
        val text = withTimeoutOrNull(40_000L) { pageText(ctx.applicationContext) } ?: ""
        Log.i("WxNews", "Met page text: ${text.length} chars: " + text.take(1500).replace('\n', '|'))
        if (text.length < 200) return Result(null, false)
        return Result(parse(text, ZonedDateTime.now(MVT)), true)
    }

    fun parse(text: String, now: ZonedDateTime): MetAlert? {
        val m = ALERT.find(text) ?: return null
        val level = AlertLevel.of(m.groupValues[1]) ?: return null
        val msg = m.groupValues[2].replace(Regex("\\s+"), " ").trim()
        val area = m.groupValues[3].replace(Regex("\\s+"), " ").trim()
        val f = m.groupValues[4]
        val t = m.groupValues[5]
        val from = parseTime(f, now)
        var to = parseTime(t, now)
        if (from != null && to != null && to.isBefore(from)) to = to.plusYears(1)
        return MetAlert(level, msg, area, "$f – $t", from, to)
    }

    /** "21 Oct 20:30" in Maldives time, in the year that puts it nearest to now. */
    private fun parseTime(s: String, now: ZonedDateTime): ZonedDateTime? = try {
        val p = s.trim().split(Regex("\\s+"))
        val day = p[0].toInt()
        val mon = month(p[1]) ?: throw IllegalArgumentException("month")
        val (h, mi) = p[2].split(":").map { it.toInt() }
        val candidates = listOf(now.year - 1, now.year, now.year + 1).map {
            ZonedDateTime.of(LocalDateTime.of(it, mon, day, h, mi), MVT)
        }
        candidates.minByOrNull { Math.abs(java.time.Duration.between(now, it).toMinutes()) }
    } catch (e: Exception) {
        null
    }

    private fun month(s: String): Int? {
        val k = s.take(3).lowercase(Locale.US)
        val i = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(k)
        return if (i >= 0) i + 1 else null
    }

    private const val JS = "(function(){try{return (document.body&&document.body.innerText)||'';}catch(e){return '';}})()"

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun pageText(ctx: Context): String = withContext(Dispatchers.Main) {
        val web = try { WebView(ctx) } catch (t: Throwable) { return@withContext "" }
        try {
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.blockNetworkImage = true
            web.webViewClient = WebViewClient()
            web.layout(0, 0, 1080, 1920)
            web.loadUrl(SITE)
            var best = ""
            var steady = 0
            var tries = 0
            // Look once a second until the alert shows up, or the page stops changing.
            while (tries < 30 && steady < 4) {
                delay(1000)
                tries++
                val t = evaluate(web)
                if (ALERT.containsMatchIn(t)) return@withContext t
                if (t.length > 200 && t.length == best.length) steady++ else steady = 0
                if (t.length >= best.length) best = t
            }
            best
        } finally {
            try { web.stopLoading(); web.destroy() } catch (t: Throwable) { }
        }
    }

    private suspend fun evaluate(web: WebView): String = suspendCancellableCoroutine { cont ->
        try {
            web.evaluateJavascript(JS) { v ->
                val text = try { JSONTokener(v ?: "null").nextValue() as? String ?: "" } catch (e: Exception) { "" }
                if (cont.isActive) cont.resume(text)
            }
        } catch (t: Throwable) {
            if (cont.isActive) cont.resume("")
        }
    }
}
