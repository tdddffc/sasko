package com.weatherfocus.app.ui.today

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherfocus.app.data.model.ConditionGroup
import com.weatherfocus.app.data.model.CustomAlertMatch
import com.weatherfocus.app.data.repository.WeatherBundle
import com.weatherfocus.app.widget.HourSlot
import com.weatherfocus.app.widget.WidgetData
import com.weatherfocus.app.widget.WidgetExtras
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/* ===================== animated weather backdrop (rain, snow, sun, stars, clouds, fog) ===================== */

private class Particle(val x: Float, val y: Float, val speed: Int, val size: Float, val phase: Float)

@Composable
fun WeatherBackdrop(group: ConditionGroup, night: Boolean, modifier: Modifier = Modifier) {
    val particles = remember { val r = Random(11); List(70) { Particle(r.nextFloat(), r.nextFloat(), 1 + r.nextInt(3), 0.5f + r.nextFloat(), r.nextFloat()) } }
    val inf = rememberInfiniteTransition(label = "backdrop")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "t")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3500), RepeatMode.Reverse), label = "pulse")

    val (top, bottom) = when {
        group == ConditionGroup.CLEAR && night -> Color(0xFF0A1230) to Color(0xFF2B3E85)
        group == ConditionGroup.CLEAR -> Color(0xFF2FA8F0) to Color(0xFF8EE2FF)
        group == ConditionGroup.RAIN && night -> Color(0xFF0F1726) to Color(0xFF2B3C58)
        group == ConditionGroup.RAIN -> Color(0xFF3C5A86) to Color(0xFF7F9FC8)
        group == ConditionGroup.THUNDER -> if (night) Color(0xFF0D0A26) to Color(0xFF3A2A6B) else Color(0xFF2A2956) to Color(0xFF5A5292)
        group == ConditionGroup.SNOW -> if (night) Color(0xFF18213D) to Color(0xFF41587F) else Color(0xFF4AA8F5) to Color(0xFFC8EEFF)
        group == ConditionGroup.FOG -> if (night) Color(0xFF272D3B) to Color(0xFF4B5468) else Color(0xFF8FA1C2) to Color(0xFFD4DDED)
        else -> if (night) Color(0xFF1C2231) to Color(0xFF3B465C) else Color(0xFF6F89B3) to Color(0xFFB6C6E0)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Soft tinted sky; the content cards on top keep their own colours so text stays readable.
        drawRect(Brush.verticalGradient(listOf(top.copy(alpha = 0.55f), bottom.copy(alpha = 0.18f), Color.Transparent)))
        when (group) {
            ConditionGroup.RAIN, ConditionGroup.THUNDER -> {
                particles.forEach { p ->
                    val y = (p.y + t * p.speed) % 1f
                    val x = (p.x + y * 0.05f) % 1f
                    val len = 14f + 16f * p.size
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(x * w, y * h), Offset(x * w - len * 0.2f, y * h + len), strokeWidth = 1.6f, cap = StrokeCap.Round)
                }
                if (group == ConditionGroup.THUNDER && t in 0.60f..0.66f) drawRect(Color.White.copy(alpha = 0.25f))
            }
            ConditionGroup.SNOW -> particles.forEach { p ->
                val y = (p.y + t * p.speed * 0.5f) % 1f
                val x = (p.x + 0.03f * sin((y + p.phase) * 2f * PI.toFloat() * 2f)) % 1f
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 2f + 3f * p.size, center = Offset(x * w, y * h))
            }
            ConditionGroup.CLEAR -> if (night) {
                particles.take(45).forEach { p ->
                    val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin((pulse + p.phase) * 2f * PI.toFloat()))
                    drawCircle(Color.White.copy(alpha = tw), radius = 1.2f + 1.6f * p.size, center = Offset(p.x * w, p.y * h * 0.6f))
                }
            } else {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFF3B0).copy(alpha = 0.55f + 0.2f * pulse), Color(0xFFFFE070).copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(w * 0.88f, 0f), radius = w * 0.75f
                    ),
                    radius = w * 0.75f, center = Offset(w * 0.88f, 0f)
                )
            }
            ConditionGroup.FOG -> for (i in 0 until 5) {
                val y = h * (0.12f + i * 0.17f)
                val dx = (t * 2f + i * 0.2f) % 1f * w * 0.25f
                drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(-w * 0.1f + dx, y), androidx.compose.ui.geometry.Size(w * 0.9f, 22f), androidx.compose.ui.geometry.CornerRadius(11f))
            }
            else -> for (i in 0 until 4) { // cloudy
                val cx = ((t + i * 0.27f) % 1.3f - 0.15f) * w
                val cy = h * (0.08f + i * 0.14f)
                val a = if (night) 0.10f else 0.22f
                drawCircle(Color.White.copy(alpha = a), 38f, Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = a), 52f, Offset(cx + 46f, cy - 10f))
                drawCircle(Color.White.copy(alpha = a), 40f, Offset(cx + 100f, cy + 4f))
            }
        }
    }
}

/* ===================== next-48-hours chart ===================== */

@Composable
fun HourlyChartCard(slots: List<HourSlot>, useFahrenheit: Boolean) {
    val hours = slots.take(48)
    if (hours.size < 6) return
    val colW = 30.dp
    val timeFmt = remember { SimpleDateFormat("HH", Locale.getDefault()) }
    val temps = hours.mapNotNull { it.temp }
    if (temps.isEmpty()) return
    val tMin = temps.min()
    val tMax = temps.max()
    val lineColor = MaterialTheme.colorScheme.secondary
    val barColor = MaterialTheme.colorScheme.tertiary
    val zeroColor = MaterialTheme.colorScheme.outline

    fun disp(c: Double) = if (useFahrenheit) (c * 9 / 5 + 32).roundToInt() else c.roundToInt()

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Next 48 hours", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(
                "Temperature ${disp(tMin)}° … ${disp(tMax)}° · blue bars = rain/snow · swipe sideways",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())) {
                Column {
                    Canvas(modifier = Modifier.width(colW * hours.size).height(110.dp)) {
                        val cw = size.width / hours.size
                        val span = (tMax - tMin).let { if (it < 4.0) 4.0 else it }
                        fun yOf(t: Double) = (size.height * 0.62f * (1f - ((t - tMin) / span).toFloat())) + 8f
                        // 0 degC guide line
                        if (tMin < 0 && tMax > 0) {
                            val y0 = yOf(0.0)
                            drawLine(zeroColor, Offset(0f, y0), Offset(size.width, y0), strokeWidth = 1f)
                        }
                        // precipitation bars (mm + snow cm / 10) from the bottom
                        hours.forEachIndexed { i, s ->
                            val mm = (s.rainMm ?: 0.0) + (s.snowCm ?: 0.0)
                            if (mm > 0.05) {
                                val bh = (minOf(mm, 5.0) / 5.0 * size.height * 0.3).toFloat() + 3f
                                drawRect(barColor.copy(alpha = 0.85f), Offset(i * cw + cw * 0.25f, size.height - bh), androidx.compose.ui.geometry.Size(cw * 0.5f, bh))
                            }
                        }
                        val path = Path()
                        var started = false
                        hours.forEachIndexed { i, s ->
                            val t = s.temp ?: return@forEachIndexed
                            val p = Offset(i * cw + cw / 2, yOf(t))
                            if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
                        hours.forEachIndexed { i, s ->
                            val t = s.temp ?: return@forEachIndexed
                            if (i % 6 == 0) drawCircle(lineColor, 6f, Offset(i * cw + cw / 2, yOf(t)))
                        }
                    }
                    // temperature numbers every 3 h, then hour labels, then wind (m/s)
                    Row {
                        hours.forEachIndexed { i, s ->
                            Text(
                                if (i % 3 == 0) s.temp?.let { "${disp(it)}°" } ?: "" else "",
                                modifier = Modifier.width(colW), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Row {
                        hours.forEachIndexed { i, s ->
                            Text(
                                if (i % 3 == 0) timeFmt.format(Date(s.millis)) else "",
                                modifier = Modifier.width(colW), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row {
                        hours.forEachIndexed { i, s ->
                            Text(
                                if (i % 3 == 0) s.windSpeed?.let { "${(it / 3.6).roundToInt()}" } ?: "" else "",
                                modifier = Modifier.width(colW), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("wind m/s", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* ===================== "plan my day" ===================== */

object PlanMyDay {
    private fun wet(s: HourSlot): Boolean =
        (s.rainMm ?: 0.0) >= 0.1 || (s.snowCm ?: 0.0) >= 0.1 ||
            (s.code != null && s.code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99))

    private fun hour(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
    private fun clock(ms: Long) = String.format(Locale.US, "%02d:00", hour(ms))

    fun build(bundle: WeatherBundle?, alerts: List<CustomAlertMatch>, now: Long = System.currentTimeMillis()): List<Pair<String, String>> {
        bundle ?: return emptyList()
        val up = WidgetData.upcoming(WidgetData.slots(bundle.hourlyForAlerts), now)
        if (up.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<String, String>>()

        val wear = WidgetExtras.wearIcons(bundle.current.feelsLike, up, 5)
        if (wear.isNotBlank()) out += "🧥" to "Wear: $wear"

        val next = WidgetExtras.nextPrecip(up, now)
        if (!next.empty) out += (next.emoji ?: "☔") to "${next.label ?: "Precipitation"} ${next.value}"
        else out += "☀️" to "No rain or snow expected in the next 36 hours"

        // Best window for being outside / driving: the longest dry, frost-free run in the next 14 daytime hours.
        val day = up.take(14).filter { hour(it.millis) in 7..21 }
        var best: List<HourSlot> = emptyList()
        var cur = mutableListOf<HourSlot>()
        for (s in day) {
            val ok = !wet(s) && (s.temp ?: 5.0) > 1.0
            if (ok && (cur.isEmpty() || s.millis - cur.last().millis <= 3_600_000L)) cur.add(s) else { if (cur.size > best.size) best = cur; cur = if (ok) mutableListOf(s) else mutableListOf() }
        }
        if (cur.size > best.size) best = cur
        out += if (best.size >= 2) "🚗" to "Best window to drive or be outside: ${clock(best.first().millis)}–${clock(best.last().millis + 3_600_000L)}"
        else "🚗" to "No dry, ice-free window in the next 14 h – take care on the road"

        // Tomorrow's commute (06-09).
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, if (hour(now) >= 9) 1 else 0) }
        val ymd = SimpleDateFormat("yyyyMMdd", Locale.US)
        val commute = up.filter { ymd.format(Date(it.millis)) == ymd.format(cal.time) && hour(it.millis) in 6..9 }
        val prevWet = up.any { wet(it) && it.millis < (commute.firstOrNull()?.millis ?: 0L) && (commute.firstOrNull()?.millis ?: 0L) - it.millis <= 8 * 3_600_000L }
        val icy = commute.firstOrNull { (it.temp ?: 9.0) <= 1.0 && (wet(it) || prevWet) }
        val label = if (hour(now) >= 9) "Tomorrow's commute" else "This morning's commute"
        out += if (icy != null) "⚠️" to "$label may be icy (${icy.temp?.roundToInt()}°C at ${clock(icy.millis)}) – leave earlier"
        else "✅" to "$label looks fine"

        val frost = WidgetExtras.frostInfo(up, now)
        if (frost.pct >= 50) out += "🧊" to "Ice on the car ${if (frost.tomorrow) "tomorrow morning" else "this morning"}: ${frost.pct}% – get up 10–15 min earlier"

        val worst = alerts.maxByOrNull { it.severity.ordinal }
        if (worst != null) out += "🚨" to "${alerts.size} active alert${if (alerts.size > 1) "s" else ""}, worst: ${worst.label}"
        return out
    }
}

@Composable
fun PlanMyDayCard(lines: List<Pair<String, String>>) {
    if (lines.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Plan my day", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            lines.forEach { (emoji, text) ->
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(emoji, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
