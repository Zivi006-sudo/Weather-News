package com.zivi.wxnews.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tab can be chosen when opening the app (used by the automatic screenshot test).
        val start = intent?.getIntExtra("tab", 0)?.coerceIn(0, 3) ?: 0
        setContent {
            AppTheme { AppRoot(vm, start) }
        }
    }
}

private val TABS = listOf("Maldives", "World", "Flying", "Settings")

@Composable
fun AppRoot(vm: AppViewModel, start: Int) {
    var tab by rememberSaveable { mutableIntStateOf(start) }
    // Ticks once a minute so "updated N min ago" stays right.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (tab == 0) "Weather News" else TABS[tab], fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (vm.busy) "Updating…" else "Updated ${Fmt.ago(vm.updatedAt, now)}",
                        fontSize = 13.sp, color = Muted,
                    )
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val icons = listOf(Icons.Filled.Home, Icons.Filled.List, Icons.Filled.Send, Icons.Filled.Settings)
                TABS.forEachIndexed { i, name ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], contentDescription = name) },
                        label = { Text(name) },
                    )
                }
            }
        },
    ) { pad ->
        when (tab) {
            0 -> MaldivesScreen(vm, pad)
            1 -> WorldScreen(vm, pad)
            2 -> FlyingScreen(vm, pad)
            else -> SettingsScreen(vm, pad)
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle("Data")
            Card {
                Text("Refresh everything now", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.refresh() }.padding(vertical = 4.dp))
                Note("The app also refreshes by itself every 15 minutes while it is open.")
                Status("Met Office alert", vm.alert)
                Status("Maldives forecast", vm.regions)
                Status("World events", vm.world)
                Status("SIGMETs", vm.sigmets)
            }
        }
        item {
            SectionTitle("Sources")
            Card {
                Note(
                    "Maldives alerts: Maldives Meteorological Service website (meteorology.gov.mv).\n" +
                        "Maldives weather, waves, sea temperature and tides: Open-Meteo forecast and marine models.\n" +
                        "World events: GDACS, Global Disaster Alert and Coordination System.\n" +
                        "SIGMETs: Aviation Weather Center (aviationweather.gov).\n" +
                        "All free sources, no accounts or keys.", Color.Unspecified,
                )
            }
        }
        item {
            SectionTitle("About")
            Card {
                Text("Weather News $version", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Note("For awareness only. Not an official forecast, briefing or navigation aid.", Amber)
                Text("All versions (to reinstall an older one)", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { openLink(ctx, "https://github.com/Zivi006-sudo/weather-news/releases") }.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun <T> Status(name: String, f: Feed<T>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val c = when {
            f.loading -> Muted
            f.error != null -> Amber
            f.at > 0 -> Good
            else -> Muted
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            "$name: " + when {
                f.loading -> "updating…"
                f.error != null -> "failed (${f.error})" + (if (f.at > 0) ", last good ${Fmt.ago(f.at)}" else "")
                f.at > 0 -> "updated ${Fmt.ago(f.at)}"
                else -> "not loaded"
            },
            fontSize = 13.sp, color = c,
        )
    }
}
