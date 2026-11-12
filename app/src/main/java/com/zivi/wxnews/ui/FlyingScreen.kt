package com.zivi.wxnews.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivi.wxnews.data.Maldives
import com.zivi.wxnews.data.Sigmet

@Composable
fun FlyingScreen(vm: AppViewModel, pad: PaddingValues) {
    val active = activeAlert(vm)
    val regions = vm.regions.data
    val all = vm.sigmets.data ?: emptyList()
    val local = all.filter { it.fir.equals("VRMF", true) }
    val world = all.filter { !it.fir.equals("VRMF", true) && it.hazard.uppercase() in setOf("TC", "VA") }
        .sortedWith(compareBy<Sigmet> { if (it.hazard.equals("TC", true)) 0 else 1 }.thenBy { !nearIndianOcean(it) }.thenBy { it.fir })

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- airports under the Met Office alert
        item {
            SectionTitle("Airports in the alert area")
            if (active == null) {
                Card {
                    Pill("NONE", Good)
                    Note(if (vm.alertPageRead) "No Met Office alert is in effect." else "Met Office alert not known yet.")
                }
            } else {
                val hit = Maldives.airports.filter { it.atoll in active.atolls }
                Card(edge = alertColor(active.level)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(active.level.label.uppercase(), alertColor(active.level))
                        Spacer(Modifier.width(8.dp))
                        Text(active.timeText, fontSize = 13.sp, color = Muted)
                    }
                    Text(active.text, fontSize = 15.sp)
                    if (hit.isEmpty()) {
                        Note("Area: ${active.area}. Couldn't match it to airports; check the area above.")
                    } else {
                        Text(hit.joinToString("  ") { it.icao }, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Note(hit.joinToString(", ") { it.name })
                    }
                }
            }
        }

        // ---- thunderstorms, gusts and visibility by region
        item { SectionTitle("Next 12 hours by region") }
        if (regions == null) {
            item { Note(if (vm.regions.loading) "Loading the forecast…" else "No forecast yet.") }
        } else {
            items(Maldives.regions.indices.toList()) { i ->
                val w = regions.getOrNull(i) ?: return@items
                val r = Maldives.regions[i]
                val o = outlook(w)
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        when {
                            o.storms -> Pill("TS LIKELY", Color(0xFFFF8C2B))
                            o.stormRisk -> Pill("TS RISK", Amber)
                            else -> Pill("NO TS", Good)
                        }
                    }
                    Text(r.airports.joinToString(" ") { it.icao }, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Muted)
                    Text(
                        "Max gust ${Fmt.kt(o.maxGust)} · min vis ${Fmt.vis(o.minVis)} · max rain chance " +
                            (if (o.maxRain.isNaN()) "–" else "${Math.round(o.maxRain)}%"),
                        fontSize = 14.sp,
                    )
                    o.firstStorm?.let { Note("Thunderstorms in the forecast from about ${Fmt.local(it)} local.") }
                }
            }
        }

        // ---- SIGMETs
        item {
            SectionTitle("SIGMETs · Malé FIR (VRMF)")
            if (vm.sigmets.data == null) Note(if (vm.sigmets.loading) "Loading SIGMETs…" else "No SIGMETs yet" + (vm.sigmets.error?.let { ": $it." } ?: "."))
            else if (vm.sigmets.error != null) Note("Couldn't update (${vm.sigmets.error}). Showing SIGMETs from ${Fmt.ago(vm.sigmets.at)}.", Amber)
            if (vm.sigmets.data != null && local.isEmpty()) Note("No SIGMETs in force for the Malé FIR.")
        }
        items(local, key = { "l" + it.raw.hashCode() }) { SigmetCard(it) }
        item {
            SectionTitle("Tropical cyclone and volcanic ash SIGMETs · worldwide")
            if (vm.sigmets.data != null && world.isEmpty()) Note("None in force.")
        }
        items(world, key = { "w" + it.raw.hashCode() }) { SigmetCard(it) }
        item {
            Note(
                "Thunderstorm risk is worked out by this app from the model forecast (thunderstorm codes, CAPE and rain chance). " +
                    "SIGMETs from the Aviation Weather Center. For awareness only, not an official briefing."
            )
        }
    }
}

private fun nearIndianOcean(s: Sigmet): Boolean {
    val la = s.lat ?: return false
    val lo = s.lon ?: return false
    return la in -50.0..32.0 && lo in 25.0..120.0
}

@Composable
fun SigmetCard(s: Sigmet) {
    var open by rememberSaveable(s.raw) { mutableStateOf(false) }
    val color = when (s.hazard.uppercase()) {
        "TC" -> Color(0xFFFF4D4D)
        "VA" -> Color(0xFFB98CFF)
        "TS" -> Color(0xFFFF8C2B)
        else -> Amber
    }
    Card(Modifier.animateContentSize().clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(s.hazardName.uppercase(), color)
            Spacer(Modifier.width(8.dp))
            Text(s.firName.ifEmpty { s.fir }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        }
        val lv = when {
            s.baseFt != null && s.topFt != null -> " · FL${s.baseFt / 100}–FL${s.topFt / 100}"
            s.topFt != null -> " · up to FL${s.topFt / 100}"
            else -> ""
        }
        Text(
            (if (s.qualifier.isNotEmpty()) s.qualifier.lowercase().replaceFirstChar { it.uppercase() } + " · " else "") +
                "valid ${Fmt.zulu(s.from)} – ${Fmt.zulu(s.to)}$lv",
            fontSize = 13.sp, color = Muted,
        )
        if (open) {
            Text(s.raw, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        } else {
            Text("Tap for the full text", fontSize = 12.sp, color = Muted)
        }
    }
}
