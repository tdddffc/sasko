package com.weatherfocus.app.ui.month

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherfocus.app.data.model.ConditionGroup
import com.weatherfocus.app.data.model.DayForecast
import com.weatherfocus.app.ui.WeatherFormat
import com.weatherfocus.app.ui.WeatherViewModel
import com.weatherfocus.app.ui.components.PremiumWeatherIcon
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(viewModel: WeatherViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val days = state.bundle?.monthDays.orEmpty()
    val useF = state.settings.useFahrenheit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Next 16 days & month") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)
        ) {
            item { SummaryCard(days.filterNot { it.isOutlookEstimate }, useF) }
            if (days.any { it.isOutlookEstimate }) {
                item {
                    Text(
                        "Days beyond ~16 are a historical outlook (average of the last 3 years for that date), not a real forecast.",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            item { Text("Tap a day for more details", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
            items(days) { day -> MonthDayRow(day, useF) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Headline numbers for the real-forecast days: extremes, totals and counts that matter in a cold, snowy climate. */
@Composable
private fun SummaryCard(real: List<DayForecast>, useF: Boolean) {
    if (real.isEmpty()) return
    val warmest = real.filter { it.maxTemp != null }.maxByOrNull { it.maxTemp!! }
    val coldest = real.filter { it.minTemp != null }.minByOrNull { it.minTemp!! }
    val wettest = real.filter { (it.precipMm ?: 0.0) > 0.0 }.maxByOrNull { it.precipMm!! }
    val windiest = real.filter { (it.gustKmh ?: it.windSpeedKmh) != null }.maxByOrNull { it.gustKmh ?: it.windSpeedKmh!! }
    val frostNights = real.count { (it.minTemp ?: 99.0) <= 0.0 }
    val snowDays = real.count { (it.snowCm ?: 0.0) >= 0.5 || it.conditionGroup == ConditionGroup.SNOW }
    val iceDays = real.count { isIceRisk(it) }
    val totalSnow = real.sumOf { it.snowCm ?: 0.0 }
    val totalPrecip = real.sumOf { it.precipMm ?: 0.0 }

    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Next ${real.size} days at a glance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            val lines = buildList {
                warmest?.let { add("☀️" to "Warmest: ${it.dayLabel} ${it.dateLabel}, ${WeatherFormat.temp(it.maxTemp, useF)}") }
                coldest?.let { add("🥶" to "Coldest: ${it.dayLabel} ${it.dateLabel}, ${WeatherFormat.temp(it.minTemp, useF)}") }
                wettest?.let { add("🌧️" to "Wettest: ${it.dayLabel} ${it.dateLabel}, ${fmt1(it.precipMm)} mm") }
                windiest?.let { add("💨" to "Windiest: ${it.dayLabel} ${it.dateLabel}, ${(it.gustKmh ?: it.windSpeedKmh)?.let { v -> (v / 3.6).roundToInt() }} m/s") }
                add("❄️" to "Frost nights (≤ 0°C): $frostNights")
                add("🧊" to "Ice-risk days (freeze–thaw or freezing rain): $iceDays")
                add("🌨️" to "Snow days: $snowDays" + if (totalSnow > 0) " · ${fmt1(totalSnow)} cm in total" else "")
                add("💧" to "Total precipitation: ${fmt1(totalPrecip)} mm")
            }
            lines.forEach { (emoji, text) -> InfoLine(emoji, text) }
        }
    }
}

@Composable
private fun InfoLine(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
        Text(text, fontSize = 14.sp)
    }
}

private fun isIceRisk(d: DayForecast): Boolean {
    val min = d.minTemp ?: return false
    val max = d.maxTemp ?: return false
    val freezeThaw = min <= 0.0 && max >= 0.0
    val wetNearFreezing = min <= 1.0 && d.conditionGroup == ConditionGroup.RAIN
    return freezeThaw || wetNearFreezing
}

private fun fmt1(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "--"

/** Small winter/hazard emoji shown on a collapsed row so risky days stand out without opening them. */
private fun dayTags(d: DayForecast): String = buildString {
    if (d.isOutlookEstimate) return@buildString
    if ((d.minTemp ?: 99.0) <= 0.0) append("❄️")
    if (isIceRisk(d)) append("🧊")
    if ((d.snowCm ?: 0.0) >= 1.0) append("🌨️")
    if ((d.gustKmh ?: d.windSpeedKmh ?: 0.0) >= 60.0) append("💨")
    if ((d.feelsMin ?: 99.0) <= -15.0) append("🥶")
}

@Composable
private fun MonthDayRow(day: DayForecast, useFahrenheit: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (day.isOutlookEstimate) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.4f)) {
                    Text("${day.dayLabel}, ${day.dateLabel}", fontWeight = FontWeight.SemiBold)
                    Text(
                        day.description + if (day.isOutlookEstimate) " · outlook" else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val tags = dayTags(day)
                    if (tags.isNotEmpty()) Text(tags, fontSize = 14.sp)
                }
                PremiumWeatherIcon(day.conditionGroup, size = 38.dp, modifier = Modifier.weight(0.6f))
                Text(
                    "${WeatherFormat.tempNoUnit(day.maxTemp, useFahrenheit)} / ${WeatherFormat.tempNoUnit(day.minTemp, useFahrenheit)}",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Row(modifier = Modifier.weight(0.6f), verticalAlignment = Alignment.CenterVertically) {
                    day.rainChancePercent?.let {
                        Text("💧", fontSize = 13.sp)
                        Text(" $it%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (day.isOutlookEstimate) {
                        Text(
                            "Typical conditions for this date over the last 3 years. Rain, wind and snow details are only available for the real forecast days.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val details = buildList {
                            if (day.feelsMax != null || day.feelsMin != null)
                                add("🌡️" to "Feels like ${WeatherFormat.tempNoUnit(day.feelsMax, useFahrenheit)} / ${WeatherFormat.tempNoUnit(day.feelsMin, useFahrenheit)}")
                            day.rainChancePercent?.let { add("💧" to "Precipitation chance $it%") }
                            day.precipMm?.let { add("🌧️" to "Precipitation ${fmt1(it)} mm") }
                            day.snowCm?.let { add("❄️" to "Snowfall ${fmt1(it)} cm") }
                            day.windSpeedKmh?.let { add("💨" to "Wind up to ${(it / 3.6).roundToInt()} m/s") }
                            day.gustKmh?.let { add("🌬️" to "Gusts up to ${(it / 3.6).roundToInt()} m/s") }
                            day.uvIndexMax?.let { add("🕶️" to "UV index ${fmt1(it)}") }
                            day.sunrise?.let { add("🌅" to "Sunrise $it") }
                            day.sunset?.let { add("🌇" to "Sunset $it") }
                            day.daylightHours?.let {
                                val h = it.toInt()
                                val m = ((it - h) * 60).roundToInt()
                                add("☀️" to "Daylight ${h}h ${m}m")
                            }
                        }
                        details.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth()) {
                                pair.forEach { (emoji, text) ->
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Text(emoji, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                        Text(text, fontSize = 12.5.sp)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        val warn = dayTags(day)
                        if (warn.isNotEmpty()) {
                            Text(
                                buildList {
                                    if ((day.minTemp ?: 99.0) <= 0.0) add("frost")
                                    if (isIceRisk(day)) add("ice risk")
                                    if ((day.snowCm ?: 0.0) >= 1.0) add("snow")
                                    if ((day.gustKmh ?: day.windSpeedKmh ?: 0.0) >= 60.0) add("strong wind")
                                    if ((day.feelsMin ?: 99.0) <= -15.0) add("severe wind chill")
                                }.joinToString(" · ", prefix = "$warn  "),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
