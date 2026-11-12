package com.zivi.wxnews.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivi.wxnews.data.GdacsEvent

private val FILTERS = listOf(
    "All" to "",
    "Cyclones" to "TC",
    "Floods" to "FL",
    "Earthquakes" to "EQ",
    "Volcanoes" to "VO",
    "Wildfires" to "WF",
    "Droughts" to "DR",
)

@Composable
fun WorldScreen(vm: AppViewModel, pad: PaddingValues) {
    var filter by rememberSaveable { mutableStateOf("") }
    val all = vm.world.data
    val shown = remember(all, filter) {
        (all ?: emptyList()).filter { filter.isEmpty() || it.type == filter }
            .sortedWith(compareByDescending<GdacsEvent> { it.sortTime })
    }
    val indian = shown.filter { it.indianOcean }
    val rest = shown.filter { !it.indianOcean }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILTERS.forEach { (label, code) ->
                    FilterChip(selected = filter == code, onClick = { filter = code }, label = { Text(label) })
                }
            }
            if (all == null) {
                Note(if (vm.world.loading) "Loading world events…" else "No events yet" + (vm.world.error?.let { ": $it." } ?: "."))
            } else if (vm.world.error != null) {
                Note("Couldn't update (${vm.world.error}). Showing events from ${Fmt.ago(vm.world.at)}.", Amber)
            }
        }
        if (all != null) {
            item { SectionTitle("Indian Ocean region · ${indian.size}") }
            if (indian.isEmpty()) item { Note("Nothing reported in the Indian Ocean region.") }
            items(indian, key = { "i" + it.link + it.title }) { EventCard(it) }
            item { SectionTitle("Rest of the world · ${rest.size}") }
            if (rest.isEmpty()) item { Note("Nothing reported.") }
            items(rest, key = { "r" + it.link + it.title }) { EventCard(it) }
            item {
                Note(
                    "From GDACS, the Global Disaster Alert and Coordination System (European Commission and United Nations). " +
                        "Green, orange and red show the expected impact on people."
                )
            }
        }
    }
}

@Composable
fun EventCard(e: GdacsEvent) {
    val ctx = LocalContext.current
    var open by rememberSaveable(e.link + e.title) { mutableStateOf(false) }
    Card(Modifier.animateContentSize().clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(gdacsColor(e.level)))
            Spacer(Modifier.width(8.dp))
            Text(
                e.typeName.uppercase() + (if (e.current) "" else " · ENDED"),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = gdacsColor(e.level), letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.weight(1f))
            e.published?.let { Text(Fmt.ago(it.toEpochMilli()), fontSize = 12.sp, color = Muted) }
        }
        Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, maxLines = if (open) 6 else 3, overflow = TextOverflow.Ellipsis)
        if (e.country.isNotEmpty()) Text(e.country, fontSize = 14.sp, color = Muted, maxLines = if (open) 4 else 1, overflow = TextOverflow.Ellipsis)
        if (open) {
            if (e.severity.isNotEmpty()) Text(e.severity, fontSize = 14.sp)
            val dates = listOfNotNull(e.from?.let { Fmt.day(it) }, e.to?.let { Fmt.day(it) }).distinct().joinToString(" – ")
            if (dates.isNotEmpty()) Note("Dates: $dates")
            if (e.description.isNotEmpty()) Text(e.description, fontSize = 14.sp, lineHeight = 20.sp)
            if (e.link.isNotEmpty()) {
                Text(
                    "Open full report", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { openLink(ctx, e.link) }.padding(vertical = 6.dp),
                )
            }
        }
    }
}
