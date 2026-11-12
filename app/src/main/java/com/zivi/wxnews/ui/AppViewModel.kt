package com.zivi.wxnews.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zivi.wxnews.data.Disk
import com.zivi.wxnews.data.GdacsEvent
import com.zivi.wxnews.data.Gdacs
import com.zivi.wxnews.data.MetAlert
import com.zivi.wxnews.data.MetOffice
import com.zivi.wxnews.data.Net
import com.zivi.wxnews.data.OpenMeteo
import com.zivi.wxnews.data.RegionWx
import com.zivi.wxnews.data.Sigmet
import com.zivi.wxnews.data.Sigmets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What one source has: its data, when it was fetched, and why the last try failed (if it did). */
class Feed<T>(val data: T?, val at: Long, val error: String?, val loading: Boolean)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var alert by mutableStateOf(Feed<MetAlert?>(null, 0L, null, false))
        private set
    /** True once the Met page has been read at least once (so "no alert" is a real answer). */
    var alertPageRead by mutableStateOf(false)
        private set
    var regions by mutableStateOf(Feed<List<RegionWx>>(null, 0L, null, false))
        private set
    var world by mutableStateOf(Feed<List<GdacsEvent>>(null, 0L, null, false))
        private set
    var sigmets by mutableStateOf(Feed<List<Sigmet>>(null, 0L, null, false))
        private set

    private var job: Job? = null

    init {
        viewModelScope.launch {
            loadSaved()
            refresh()
            // Keep it current while the app is open.
            while (true) {
                delay(15 * 60_000L)
                refresh()
            }
        }
    }

    val busy: Boolean get() = alert.loading || regions.loading || world.loading || sigmets.loading

    /** Newest time any source was updated. */
    val updatedAt: Long get() = maxOf(alert.at, regions.at, world.at, sigmets.at)

    private suspend fun loadSaved() = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        try {
            Disk.load(ctx, "alert.json")?.let { (t, at) ->
                val a = if (t == "none") null else MetAlert.fromJson(t)
                alert = Feed(a, at, null, false)
                alertPageRead = true
            }
            Disk.load(ctx, "forecast.json")?.let { (t, at) ->
                val sea = Disk.load(ctx, "marine.json")?.first?.let { runCatching { OpenMeteo.parseSea(it) }.getOrNull() } ?: emptyList()
                regions = Feed(OpenMeteo.parse(t, sea), at, null, false)
            }
            Disk.load(ctx, "gdacs.xml")?.let { (t, at) -> world = Feed(Gdacs.parse(t), at, null, false) }
            Disk.load(ctx, "sigmet.json")?.let { (t, at) -> sigmets = Feed(Sigmets.parse(t), at, null, false) }
        } catch (e: Exception) {
            Log.w("WxNews", "saved data unreadable", e)
        }
    }

    fun refresh() {
        if (job?.isActive == true) return
        val ctx = getApplication<Application>()
        job = viewModelScope.launch {
            alert = Feed(alert.data, alert.at, null, true)
            regions = Feed(regions.data, regions.at, null, true)
            world = Feed(world.data, world.at, null, true)
            sigmets = Feed(sigmets.data, sigmets.at, null, true)

            val a = async {
                try {
                    val r = MetOffice.read(ctx)
                    if (r.pageRead) {
                        alertPageRead = true
                        alert = Feed(r.alert, System.currentTimeMillis(), null, false)
                        withContext(Dispatchers.IO) { Disk.save(ctx, "alert.json", r.alert?.toJson() ?: "none") }
                    } else {
                        alert = Feed(alert.data, alert.at, "the page didn't load", false)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("WxNews", "alert", e)
                    alert = Feed(alert.data, alert.at, Net.reason(e), false)
                }
            }
            val f = async(Dispatchers.IO) {
                try {
                    val sea = try {
                        val t = Net.getText(OpenMeteo.marineUrl())
                        val s = OpenMeteo.parseSea(t)
                        Disk.save(ctx, "marine.json", t)
                        s
                    } catch (e: Exception) {
                        Log.w("WxNews", "marine", e)
                        regions.data?.map { it.sea } ?: emptyList()
                    }
                    val t = Net.getText(OpenMeteo.forecastUrl())
                    val list = OpenMeteo.parse(t, sea)
                    Disk.save(ctx, "forecast.json", t)
                    regions = Feed(list, System.currentTimeMillis(), null, false)
                } catch (e: Exception) {
                    Log.w("WxNews", "forecast", e)
                    regions = Feed(regions.data, regions.at, Net.reason(e), false)
                }
            }
            val g = async(Dispatchers.IO) {
                try {
                    val t = Net.getText(Gdacs.URL)
                    val list = Gdacs.parse(t)
                    Disk.save(ctx, "gdacs.xml", t)
                    world = Feed(list, System.currentTimeMillis(), null, false)
                } catch (e: Exception) {
                    Log.w("WxNews", "gdacs", e)
                    world = Feed(world.data, world.at, Net.reason(e), false)
                }
            }
            val s = async(Dispatchers.IO) {
                try {
                    val t = Net.getText(Sigmets.URL)
                    val list = Sigmets.parse(t)
                    Disk.save(ctx, "sigmet.json", t)
                    sigmets = Feed(list, System.currentTimeMillis(), null, false)
                } catch (e: Exception) {
                    Log.w("WxNews", "sigmet", e)
                    sigmets = Feed(sigmets.data, sigmets.at, Net.reason(e), false)
                }
            }
            a.await(); f.await(); g.await(); s.await()
        }
    }
}
