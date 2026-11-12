package com.zivi.wxnews.data

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Net {
    private const val UA = "WeatherNewsApp/1.0 (personal Android app)"

    fun getText(url: String, maxBytes: Int = 6_000_000, timeoutMs: Int = 25_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "*/*")
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total >= maxBytes) break
                }
                return String(out.toByteArray(), Charsets.UTF_8)
            }
        } finally {
            c.disconnect()
        }
    }

    /** A plain-words reason for a failed download. */
    fun reason(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "no internet connection"
        is java.net.SocketTimeoutException -> "the server didn't answer in time"
        is java.net.ConnectException -> "couldn't reach the server"
        else -> e.message ?: e.javaClass.simpleName
    }
}

/** Last good copy of each source, so the app opens with something to read even offline. */
object Disk {
    private fun dir(ctx: Context) = File(ctx.filesDir, "saved").apply { mkdirs() }

    fun save(ctx: Context, name: String, text: String) {
        try {
            File(dir(ctx), name).writeText(text)
        } catch (e: Exception) {
            Log.w("WxNews", "save $name failed", e)
        }
    }

    /** Text and when it was saved, or null. */
    fun load(ctx: Context, name: String): Pair<String, Long>? = try {
        val f = File(dir(ctx), name)
        if (f.exists() && f.length() > 0) f.readText() to f.lastModified() else null
    } catch (e: Exception) {
        null
    }
}
