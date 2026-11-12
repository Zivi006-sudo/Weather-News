package com.zivi.wxnews.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivi.wxnews.data.AlertLevel
import com.zivi.wxnews.data.MVT
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AppColors = darkColorScheme(
    primary = Color(0xFF5CC8FF),
    onPrimary = Color(0xFF00283A),
    primaryContainer = Color(0xFF0E3A52),
    onPrimaryContainer = Color(0xFFCDEBFF),
    secondaryContainer = Color(0xFF1C3A44),
    onSecondaryContainer = Color(0xFFD6F1FF),
    background = Color(0xFF0A0E13),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0A0E13),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF151C25),
    onSurfaceVariant = Color(0xFF9BA8B6),
    surfaceContainer = Color(0xFF10161D),
    outline = Color(0xFF3A4656),
    outlineVariant = Color(0xFF232D39),
)

val CardColor = Color(0xFF131A22)
val Muted = Color(0xFF9BA8B6)
val Amber = Color(0xFFFFB84D)
val Good = Color(0xFF34C759)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}

fun alertColor(l: AlertLevel): Color = when (l) {
    AlertLevel.WHITE -> Color(0xFFE8EEF4)
    AlertLevel.YELLOW -> Color(0xFFFFD43B)
    AlertLevel.ORANGE -> Color(0xFFFF8C2B)
    AlertLevel.RED -> Color(0xFFFF4D4D)
}

fun gdacsColor(level: String): Color = when (level.lowercase(Locale.US)) {
    "red" -> Color(0xFFFF4D4D)
    "orange" -> Color(0xFFFF8C2B)
    else -> Color(0xFF34C759)
}

@Composable
fun Card(modifier: Modifier = Modifier, edge: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    var m = modifier.fillMaxWidth().background(CardColor, shape)
    if (edge != null) m = m.border(1.5.dp, edge, shape)
    Column(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.US), modifier = modifier.padding(top = 14.dp, bottom = 2.dp),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Muted,
    )
}

@Composable
fun Pill(text: String, color: Color, dark: Boolean = true) {
    Box(Modifier.background(color, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, color = if (dark) Color(0xFF06090C) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun Note(text: String, color: Color = Muted) {
    Text(text, fontSize = 13.sp, color = color, lineHeight = 18.sp)
}

fun openLink(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // No browser: nothing to do.
    }
}

object Fmt {
    private val HM = DateTimeFormatter.ofPattern("HH:mm").withZone(MVT)
    private val DAY_HM = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US).withZone(MVT)
    private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.US).withZone(MVT)
    private val Z = DateTimeFormatter.ofPattern("dd HHmm'Z'").withZone(ZoneOffset.UTC)

    fun local(t: Instant): String = HM.format(t)
    fun localDay(t: Instant): String = DAY_HM.format(t)
    fun day(t: Instant): String = DAY.format(t)
    fun zulu(epochSec: Long): String = Z.format(Instant.ofEpochSecond(epochSec))

    fun ago(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (thenMs <= 0) return "never"
        val min = ((nowMs - thenMs) / 60000L).coerceAtLeast(0)
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 48 * 60 -> "${min / 60} h ago"
            else -> "${min / 1440} days ago"
        }
    }

    fun kt(v: Float): String = if (v.isNaN()) "–" else "${Math.round(v)} kt"
    fun m(v: Float, d: Int = 1): String = if (v.isNaN()) "–" else "%.${d}f m".format(Locale.US, v)
    fun vis(metres: Float): String = when {
        metres.isNaN() -> "–"
        metres >= 10_000 -> "10+ km"
        metres >= 1_000 -> "%.0f km".format(Locale.US, metres / 1000)
        else -> "${(Math.round(metres / 100f) * 100)} m"
    }
}
