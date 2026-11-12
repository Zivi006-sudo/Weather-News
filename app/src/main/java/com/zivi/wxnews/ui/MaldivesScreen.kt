package com.zivi.wxnews.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivi.wxnews.data.MVT
import com.zivi.wxnews.data.MetAlert
import com.zivi.wxnews.data.MetOffice
import com.zivi.wxnews.data.Maldives
import com.zivi.wxnews.data.Region
import com.zivi.wxnews.data.RegionWx
import com.zivi.wxnews.data.Wmo
import com.zivi.wxnews.data.compass
import java.time.Instant
import java.time.ZonedDateTime

/** The alert, if one is in effect now. */
fun activeAlert(vm: AppViewModel): MetAlert? = vm.alert.data?.takeIf { it.activeAt(ZonedDateTime.now(MVT)) }

/** Things worth flagging in a region over the next [hours] hours. */
class Outlook(val storms: Boolean, val stormRisk: Boolean, val maxGust: Float, val minVis: Float, val maxRain: Float, val firstStorm: Instant?)

fun outlook(w: RegionWx, hours: Int = 12): Outlook {
    val now = System.currentTimeMillis() / 1000
    var storms = false
    var risk = false
    var gust = Float.NaN
    var vis = Float.NaN
    var rain = Float.NaN
    var first: Instant? = null
    for (i in w.ahead(now, hours)) {
        if (Wmo.isStorm(w.code[i])) {
            storms = true
            if (first == null) first = Instant.ofEpochSecond(w.times[i])
        }
        if (!w.cape[i].isNaN() && w.cape[i] >= 1500 && !w.rainProb[i].isNaN() && w.rainProb[i] >= 40) risk = true
        if (!w.gust[i].isNaN() && (gust.isNaN() || w.gust[i] > gust)) gust = w.gust[i]
        if (!w.vis[i].isNaN() && (vis.isNaN() || w.vis[i] < vis)) vis = w.vis[i]
        if (!w.rainProb[i].isNaN() && (rain.isNaN() || w.rainProb[i] > rain)) rain = w.rainProb[i]
    }
    return Outlook(storms, risk || storms, gust, vis, rain, first)
}

/** Short warnings for a region card, most important first, with the colour to show them in. */
fun flags(w: RegionWx): List<Pair<String, Color>> {
    val o = outlook(w)
    val out = ArrayList<Pair<String, Color>>()
    if (o.storms) out += "Thunderstorms likely" to Color(0xFFFF8C2B)
    else if (o.stormRisk) out += "Thunderstorm risk" to Amber
    if (!o.maxGust.isNaN() && o.maxGust >= 34) out += "Gusts ${Math.round(o.maxGust)} kt" to Color(0xFFFF8C2B)
    else if (!o.maxGust.isNaN() && o.maxGust >= 25) out += "Gusts ${Math.round(o.maxGust)} kt" to Amber
    val sea = w.sea
    if (sea != null) {
        val now = System.currentTimeMillis() / 1000
        val i = sea.at(now)
        var maxWave = Float.NaN
        for (k in i..minOf(sea.times.size - 1, i + 12)) if (!sea.wave[k].isNaN() && (maxWave.isNaN() || sea.wave[k] > maxWave)) maxWave = sea.wave[k]
        if (!maxWave.isNaN() && maxWave >= 2.5f) out += "Very rough seas" to Color(0xFFFF8C2B)
        else if (!maxWave.isNaN() && maxWave >= 1.25f) out += "Rough seas" to Amber
    }
    if (!o.minVis.isNaN() && o.minVis < 3000) out += "Low visibility" to Amber
    return out
}

@Composable
fun MaldivesScreen(vm: AppViewModel, pad: PaddingValues) {
    val list = vm.regions.data
    val active = activeAlert(vm)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AlertCard(vm) }
        item {
            SectionTitle("Weather and sea by region")
            if (list == null) {
                Note(if (vm.regions.loading) "Loading the forecast…" else "No forecast yet" + (vm.regions.error?.let { ": $it." } ?: "."))
            } else if (vm.regions.error != null) {
                Note("Couldn't update (${vm.regions.error}). Showing the forecast from ${Fmt.ago(vm.regions.at)}.", Amber)
            }
        }
        if (list != null) {
            items(Maldives.regions.indices.toList()) { i ->
                val w = list.getOrNull(i)
                if (w != null) RegionCard(Maldives.regions[i], w, active)
            }
            item {
                Note(
                    "Model forecast from Open-Meteo. Tide times come from an ocean model: good for a general idea, " +
                        "not for navigation."
                )
            }
        }
    }
}

@Composable
fun AlertCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    val a = vm.alert.data
    val now = ZonedDateTime.now(MVT)
    when {
        a != null && a.activeAt(now) -> Card(edge = alertColor(a.level)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(a.level.label.uppercase(), alertColor(a.level))
                Spacer(Modifier.width(8.dp))
                Text("Met Office · in effect", fontSize = 13.sp, color = Muted)
            }
            Text(a.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
            Text("Area: ${a.area}", fontSize = 15.sp)
            Text("Time: ${a.timeText}", fontSize = 15.sp)
            if (a.atolls.isNotEmpty()) {
                val regs = Maldives.regions.filter { r -> r.atolls.any { it in a.atolls } }.joinToString(", ") { it.name }
                Note("Regions affected: $regs")
            }
            Note("Checked ${Fmt.ago(vm.alert.at)}", Muted)
        }
        a != null -> Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("NO ALERT IN EFFECT", Good)
            }
            Note("Last alert: ${a.level.label} for ${a.area}, ${a.timeText} (ended). ${a.text}")
            Note("Checked ${Fmt.ago(vm.alert.at)}")
        }
        vm.alertPageRead -> Card {
            Pill("NO ALERT IN EFFECT", Good)
            Note("No weather alert is shown on the Met Office website. Checked ${Fmt.ago(vm.alert.at)}.")
        }
        vm.alert.loading -> Card { Note("Checking the Met Office for alerts…") }
        else -> Card {
            Text("Met Office alert unknown", fontWeight = FontWeight.SemiBold)
            Note("Couldn't read the Met Office website" + (vm.alert.error?.let { " ($it)" } ?: "") + ".", Amber)
        }
    }
    if (vm.alert.error != null && vm.alertPageRead) {
        Note("Couldn't check just now (${vm.alert.error}); showing the last check.", Amber)
    }
    Text(
        "Open Met Office website", color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { openLink(ctx, MetOffice.SITE) }.padding(4.dp),
    )
}

@Composable
fun RegionCard(r: Region, w: RegionWx, alert: MetAlert?) {
    var open by rememberSaveable(r.name) { mutableStateOf(false) }
    val now = System.currentTimeMillis() / 1000
    val i = w.nowIndex(now)
    val inAlert = alert != null && r.atolls.any { it in alert.atolls }
    val fl = remember(w) { flags(w) }
    Card(Modifier.animateContentSize().clickable { open = !open }, edge = if (inAlert) alertColor(alert!!.level) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(r.atollNames, fontSize = 12.sp, color = Muted, maxLines = 1)
            }
            if (!w.temp[i].isNaN()) Text("${Math.round(w.temp[i])}°", fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
        if (inAlert) Pill("IN ${alert!!.level.label.uppercase()} AREA", alertColor(alert.level))
        Text(
            Wmo.describe(w.code[i]) + (if (!w.rainProb[i].isNaN()) " · rain ${Math.round(w.rainProb[i])}%" else ""),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Wind ${compass(w.windDir[i])} ${Fmt.kt(w.wind[i])}, gusts ${Fmt.kt(w.gust[i])} · Vis ${Fmt.vis(w.vis[i])}",
            fontSize = 14.sp,
        )
        val sea = w.sea
        if (sea != null) {
            val k = sea.at(now)
            val swell = if (!sea.swell[k].isNaN())
                " · swell ${Fmt.m(sea.swell[k])} ${compass(sea.swellDir[k])}" +
                    (if (!sea.swellPeriod[k].isNaN()) " ${Math.round(sea.swellPeriod[k])} s" else "") else ""
            Text("Waves ${Fmt.m(sea.wave[k])}$swell" + (if (!sea.sst[k].isNaN()) " · sea ${Math.round(sea.sst[k])}°C" else ""), fontSize = 14.sp)
            val tides = sea.tides(now).filter { it.time.epochSecond >= now - 1800 }.take(4)
            if (tides.isNotEmpty()) {
                Text(
                    "Tides: " + tides.joinToString("  ") { (if (it.high) "High " else "Low ") + Fmt.local(it.time) },
                    fontSize = 14.sp, color = Color(0xFF8BD3C7),
                )
            }
        }
        if (fl.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp).horizontalScroll(rememberScrollState())) {
                fl.take(3).forEach { (t, c) -> Pill(t, c) }
            }
        }
        if (open) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFF232D39))
            Text("Next 12 hours", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Bold)
            for (h in w.ahead(now, 12)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(Fmt.local(Instant.ofEpochSecond(w.times[h])), Modifier.width(52.dp), fontSize = 13.sp, color = Muted)
                    Text(Wmo.describe(w.code[h]), Modifier.weight(1f), fontSize = 13.sp)
                    Text(if (w.rainProb[h].isNaN()) "" else "${Math.round(w.rainProb[h])}%", Modifier.width(44.dp), fontSize = 13.sp)
                    Text("${compass(w.windDir[h])} ${Fmt.kt(w.wind[h])}/${Fmt.kt(w.gust[h]).removeSuffix(" kt")}", fontSize = 13.sp)
                }
            }
            if (sea != null) {
                val all = sea.tides(now).take(6)
                if (all.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Tides", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Bold)
                    for (t in all) {
                        Text(
                            "${Fmt.localDay(t.time)}  ${if (t.high) "High" else "Low"}  ${"%+.2f".format(java.util.Locale.US, t.height)} m",
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            Note("Airports: " + r.airports.joinToString(", ") { "${it.icao} ${it.name}" })
        } else {
            Text("Tap for hourly forecast and tides", fontSize = 12.sp, color = Muted)
        }
    }
}
