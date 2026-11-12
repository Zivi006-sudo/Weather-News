package com.zivi.wxnews.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

/** Hourly model forecast for one region (Open-Meteo), with sea state and tide when available. */
class RegionWx(
    val times: LongArray,              // epoch seconds
    val temp: FloatArray,
    val rainProb: FloatArray,
    val rain: FloatArray,              // mm in the hour
    val code: IntArray,                // WMO weather code
    val wind: FloatArray,              // kt
    val windDir: FloatArray,
    val gust: FloatArray,              // kt
    val vis: FloatArray,               // metres
    val cape: FloatArray,
    val sea: Sea?,
) {
    /** Index of the hour containing [now], or the first hour. */
    fun nowIndex(now: Long): Int {
        var i = 0
        while (i + 1 < times.size && times[i + 1] <= now) i++
        return i
    }

    /** Hours from now to [hours] ahead. */
    fun ahead(now: Long, hours: Int): IntRange {
        val i = nowIndex(now)
        return i..minOf(times.size - 1, i + hours)
    }
}

class Sea(
    val times: LongArray,
    val wave: FloatArray,
    val waveDir: FloatArray,
    val wavePeriod: FloatArray,
    val swell: FloatArray,
    val swellDir: FloatArray,
    val swellPeriod: FloatArray,
    val sst: FloatArray,
    val level: FloatArray,
) {
    fun at(now: Long): Int {
        var i = 0
        while (i + 1 < times.size && times[i + 1] <= now) i++
        return i
    }

    class Tide(val time: Instant, val high: Boolean, val height: Float)

    /** High and low tides from the sea level forecast, from a little before [now] onwards. */
    fun tides(now: Long): List<Tide> {
        val out = ArrayList<Tide>()
        val v = level
        for (i in 1 until v.size - 1) {
            if (v[i].isNaN() || v[i - 1].isNaN() || v[i + 1].isNaN()) continue
            val high = v[i] > v[i - 1] && v[i] >= v[i + 1]
            val low = v[i] < v[i - 1] && v[i] <= v[i + 1]
            if (!high && !low) continue
            // The turning point between the hourly values.
            val denom = v[i - 1] - 2 * v[i] + v[i + 1]
            val off = if (denom != 0f) ((v[i - 1] - v[i + 1]) / (2 * denom)).coerceIn(-0.5f, 0.5f) else 0f
            val t = times[i] + (off * 3600).toLong()
            val h = v[i] - 0.25f * (v[i - 1] - v[i + 1]) * off
            if (t >= now - 3 * 3600) out += Tide(Instant.ofEpochSecond(t), high, h)
        }
        return out
    }
}

object OpenMeteo {
    private fun coords(): Pair<String, String> {
        val us = Locale.US
        val lat = Maldives.regions.joinToString(",") { "%.2f".format(us, it.lat) }
        val lon = Maldives.regions.joinToString(",") { "%.2f".format(us, it.lon) }
        return lat to lon
    }

    fun forecastUrl(): String {
        val (lat, lon) = coords()
        return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m," +
            "wind_gusts_10m,visibility,cape&wind_speed_unit=kn&timezone=GMT&timeformat=unixtime&forecast_days=3"
    }

    fun marineUrl(): String {
        val (lat, lon) = coords()
        return "https://marine-api.open-meteo.com/v1/marine?latitude=$lat&longitude=$lon" +
            "&hourly=wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction,swell_wave_period," +
            "sea_surface_temperature,sea_level_height_msl&timezone=GMT&timeformat=unixtime&forecast_days=3"
    }

    private fun arr(text: String): List<JSONObject> {
        val t = text.trim()
        return if (t.startsWith("[")) {
            val a = JSONArray(t)
            (0 until a.length()).map { a.getJSONObject(it) }
        } else listOf(JSONObject(t))
    }

    private fun floats(h: JSONObject, name: String, n: Int): FloatArray {
        val a = h.optJSONArray(name) ?: return FloatArray(n) { Float.NaN }
        return FloatArray(n) { i -> if (i < a.length() && !a.isNull(i)) a.optDouble(i).toFloat() else Float.NaN }
    }

    private fun longs(h: JSONObject): LongArray {
        val a = h.getJSONArray("time")
        return LongArray(a.length()) { a.optLong(it) }
    }

    fun parseSea(text: String): List<Sea?> = arr(text).map { o ->
        try {
            val h = o.getJSONObject("hourly")
            val t = longs(h)
            val n = t.size
            Sea(
                t, floats(h, "wave_height", n), floats(h, "wave_direction", n), floats(h, "wave_period", n),
                floats(h, "swell_wave_height", n), floats(h, "swell_wave_direction", n), floats(h, "swell_wave_period", n),
                floats(h, "sea_surface_temperature", n), floats(h, "sea_level_height_msl", n),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parse(text: String, seas: List<Sea?>): List<RegionWx> = arr(text).mapIndexed { idx, o ->
        val h = o.getJSONObject("hourly")
        val t = longs(h)
        val n = t.size
        val codeF = floats(h, "weather_code", n)
        RegionWx(
            t, floats(h, "temperature_2m", n), floats(h, "precipitation_probability", n), floats(h, "precipitation", n),
            IntArray(n) { if (codeF[it].isNaN()) -1 else codeF[it].toInt() },
            floats(h, "wind_speed_10m", n), floats(h, "wind_direction_10m", n), floats(h, "wind_gusts_10m", n),
            floats(h, "visibility", n), floats(h, "cape", n), seas.getOrNull(idx),
        )
    }
}

object Wmo {
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80 -> "Light showers"
        81 -> "Showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorms"
        96, 99 -> "Thunderstorms with hail"
        else -> "—"
    }

    fun isStorm(code: Int) = code in 95..99
}

/** Compass point for a direction in degrees. */
fun compass(deg: Float): String {
    if (deg.isNaN()) return ""
    val pts = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return pts[(((deg % 360) + 360) % 360 / 45f + 0.5f).toInt() % 8]
}
