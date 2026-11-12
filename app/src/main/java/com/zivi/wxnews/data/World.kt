package com.zivi.wxnews.data

import android.util.Xml
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One disaster event from GDACS (Global Disaster Alert and Coordination System). */
class GdacsEvent(
    val type: String,          // TC, FL, EQ, VO, DR, WF, TS
    val level: String,         // Green, Orange, Red
    val title: String,
    val name: String,
    val description: String,
    val country: String,
    val severity: String,
    val link: String,
    val from: Instant?,
    val to: Instant?,
    val published: Instant?,
    val lat: Double?,
    val lon: Double?,
    val current: Boolean,
) {
    val typeName: String
        get() = when (type) {
            "TC" -> "Tropical cyclone"
            "FL" -> "Flood"
            "EQ" -> "Earthquake"
            "VO" -> "Volcano"
            "DR" -> "Drought"
            "WF" -> "Wildfire"
            "TS" -> "Tsunami"
            else -> type
        }

    /** Indian Ocean and the lands around it. */
    val indianOcean: Boolean
        get() {
            val la = lat ?: return false
            val lo = lon ?: return false
            return la in -50.0..32.0 && lo in 25.0..120.0
        }

    val levelRank: Int get() = when (level.lowercase(Locale.US)) { "red" -> 3; "orange" -> 2; else -> 1 }

    /** Newest information first. */
    val sortTime: Long get() = (published ?: to ?: from)?.epochSecond ?: 0L
}

object Gdacs {
    const val URL = "https://www.gdacs.org/xml/rss.xml"

    private val RFC = DateTimeFormatter.RFC_1123_DATE_TIME

    private fun time(s: String?): Instant? = try {
        if (s.isNullOrBlank()) null else ZonedDateTime.parse(s.trim(), RFC).toInstant()
    } catch (e: Exception) {
        null
    }

    private fun clean(s: String): String =
        s.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">").replace(Regex("\\s+"), " ").trim()

    fun parse(xml: String): List<GdacsEvent> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        // The feed starts with an invisible byte-order mark, which the parser rejects.
        p.setInput(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t')))
        val out = ArrayList<GdacsEvent>()
        var f: HashMap<String, String>? = null
        var tag = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") f = HashMap()
                    // Some values are given as attributes.
                    if (f != null && tag == "gdacs:severity") p.getAttributeValue(null, "value")?.let { f!!["sevValue"] = it }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val m = f
                    if (m != null && tag.isNotEmpty()) {
                        val t = p.text ?: ""
                        if (t.isNotBlank()) m[tag] = (m[tag] ?: "") + t
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item") {
                        f?.let { out += event(it) }
                        f = null
                    }
                    tag = ""
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun event(m: Map<String, String>): GdacsEvent {
        val pt = m["georss:point"]?.trim()?.split(Regex("\\s+"))
        return GdacsEvent(
            type = m["gdacs:eventtype"]?.trim() ?: "",
            level = m["gdacs:alertlevel"]?.trim() ?: "Green",
            title = clean(m["title"] ?: ""),
            name = clean(m["gdacs:eventname"] ?: ""),
            description = clean(m["description"] ?: ""),
            country = clean(m["gdacs:country"] ?: ""),
            severity = clean(m["gdacs:severity"] ?: ""),
            link = m["link"]?.trim() ?: "",
            from = time(m["gdacs:fromdate"]),
            to = time(m["gdacs:todate"]),
            published = time(m["pubDate"]),
            lat = pt?.getOrNull(0)?.toDoubleOrNull(),
            lon = pt?.getOrNull(1)?.toDoubleOrNull(),
            current = m["gdacs:iscurrent"]?.trim()?.lowercase(Locale.US) == "true",
        )
    }
}

class Sigmet(
    val fir: String,
    val firName: String,
    val hazard: String,
    val qualifier: String,
    val from: Long,
    val to: Long,
    val raw: String,
    val baseFt: Int?,
    val topFt: Int?,
    val lat: Double?,
    val lon: Double?,
) {
    val hazardName: String
        get() = when (hazard.uppercase(Locale.US)) {
            "TC" -> "Tropical cyclone"
            "VA" -> "Volcanic ash"
            "TS" -> "Thunderstorms"
            "TURB" -> "Turbulence"
            "ICE" -> "Icing"
            "MTW" -> "Mountain waves"
            "DS" -> "Dust storm"
            "SS" -> "Sandstorm"
            "RDOACT" -> "Radioactive cloud"
            else -> hazard
        }
}

object Sigmets {
    const val URL = "https://aviationweather.gov/api/data/isigmet?format=json"

    fun parse(text: String): List<Sigmet> {
        if (text.isBlank()) return emptyList()
        val arr = JSONArray(text)
        val now = System.currentTimeMillis() / 1000
        val out = ArrayList<Sigmet>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val to = o.optLong("validTimeTo", 0L)
            if (to in 1 until now) continue
            var la = 0.0
            var lo = 0.0
            var n = 0
            o.optJSONArray("coords")?.let { cs ->
                for (k in 0 until cs.length()) {
                    val c = cs.optJSONObject(k) ?: continue
                    val a = c.optDouble("lat", Double.NaN)
                    val b = c.optDouble("lon", Double.NaN)
                    if (!a.isNaN() && !b.isNaN()) { la += a; lo += b; n++ }
                }
            }
            out += Sigmet(
                fir = o.optString("firId", ""),
                firName = o.optString("firName", ""),
                hazard = o.optString("hazard", ""),
                qualifier = o.optString("qualifier", ""),
                from = o.optLong("validTimeFrom", 0L),
                to = to,
                raw = o.optString("rawSigmet", ""),
                baseFt = if (o.has("base") && !o.isNull("base")) o.optInt("base") else null,
                topFt = if (o.has("top") && !o.isNull("top")) o.optInt("top") else null,
                lat = if (n > 0) la / n else null,
                lon = if (n > 0) lo / n else null,
            )
        }
        return out
    }
}
