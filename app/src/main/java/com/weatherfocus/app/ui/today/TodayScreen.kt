package com.weatherfocus.app.ui.today

import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherfocus.app.data.model.ConditionGroup
import com.weatherfocus.app.data.model.CurrentWeather
import com.weatherfocus.app.data.model.CustomAlertMatch
import com.weatherfocus.app.data.model.DayForecast
import com.weatherfocus.app.data.model.DayPart
import com.weatherfocus.app.ui.ClothingAdvisor
import com.weatherfocus.app.ui.WeatherFormat
import com.weatherfocus.app.ui.WeatherViewModel
import com.weatherfocus.app.ui.components.PremiumWeatherIcon
import com.weatherfocus.app.ui.components.StatIcon3D
import com.weatherfocus.app.ui.components.StatIconType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A notification bell that continuously wiggles/vibrates side to side, so a triggered weather alert visibly
 *  catches the eye instead of just sitting still. */
@Composable
private fun ShakingBellIcon(contentDescription: String?, tint: androidx.compose.ui.graphics.Color, size: Dp) {
    val infinite = rememberInfiniteTransition(label = "bellShake")
    val angle by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bellAngle"
    )
    Icon(
        Icons.Filled.NotificationsActive,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(size)
            .rotate(angle * 16f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: WeatherViewModel,
    onOpenMonth: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRadar: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // The alert popup's visibility: reopens automatically whenever a genuinely new set of alerts arrives,
    // but stays remembered (open or closed) across recompositions/refreshes with the same alerts.
    val alertsKey = state.activeAlerts.joinToString("|") { "${it.type}_${it.triggerAtMillis}" }
    var alertPopupVisible by remember { mutableStateOf(true) }
    var lastAlertsKey by remember { mutableStateOf(alertsKey) }
    if (alertsKey != lastAlertsKey) {
        lastAlertsKey = alertsKey
        if (alertsKey.isNotEmpty()) alertPopupVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.settings.cityName, fontWeight = FontWeight.SemiBold)
                            if (state.activeAlerts.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            alertPopupVisible = !alertPopupVisible
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ShakingBellIcon(
                                        contentDescription = "${state.activeAlerts.size} weather alert(s) active - tap to " +
                                            if (alertPopupVisible) "hide" else "show",
                                        tint = MaterialTheme.colorScheme.error,
                                        size = 18.dp
                                    )
                                }
                            }
                        }
                        state.lastUpdatedMillis?.let { updated ->
                            Text(
                                "Updated ${timeFormat.format(Date(updated))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenRadar) {
                        Icon(Icons.Filled.Map, contentDescription = "Rain radar")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            state.bundle?.current?.takeIf { it.available }?.let { c ->
                WeatherBackdrop(
                    group = c.conditionGroup,
                    night = com.weatherfocus.app.widget.WidgetData.isNight(System.currentTimeMillis(), c.sunrise, c.sunset)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.isLoading && state.bundle == null) {
                    Spacer(Modifier.height(80.dp))
                    CircularProgressIndicator()
                } else {
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (state.activeAlerts.isNotEmpty()) {
                        // Dismissible popup: X shrinks it away toward the small bell icon next to the city name;
                        // tapping that bell (in the top bar above) brings it right back.
                        AnimatedVisibility(
                            visible = alertPopupVisible,
                            enter = scaleIn(
                                animationSpec = tween(220),
                                transformOrigin = TransformOrigin(1f, 0f)
                            ) + fadeIn(tween(180)),
                            exit = scaleOut(
                                animationSpec = tween(220),
                                transformOrigin = TransformOrigin(1f, 0f)
                            ) + fadeOut(tween(160))
                        ) {
                            Column {
                                AlertSection(alerts = state.activeAlerts, onClose = { alertPopupVisible = false })
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                    state.bundle?.current?.let { current ->
                        TodaySection(
                            current = current,
                            useFahrenheit = state.settings.useFahrenheit,
                            todayParts = state.bundle?.todayParts.orEmpty()
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    PlanMyDayCard(remember(state.bundle, state.activeAlerts) { PlanMyDay.build(state.bundle, state.activeAlerts) })
                    Spacer(Modifier.height(14.dp))
                    HourlyChartCard(
                        slots = remember(state.bundle) {
                            com.weatherfocus.app.widget.WidgetData.upcoming(
                                com.weatherfocus.app.widget.WidgetData.slots(state.bundle?.hourlyForAlerts), System.currentTimeMillis()
                            )
                        },
                        useFahrenheit = state.settings.useFahrenheit
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Tomorrow",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    state.bundle?.tomorrow?.let { tomorrow ->
                        TomorrowSection(
                            tomorrow = tomorrow,
                            parts = state.bundle?.tomorrowParts.orEmpty(),
                            useFahrenheit = state.settings.useFahrenheit
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    state.bundle?.next3Days?.let { days ->
                        ThreeDayStrip(days = days, useFahrenheit = state.settings.useFahrenheit, onClick = onOpenMonth)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Shown as its own dismissible popup card directly under the city name whenever a custom weather alert is
 * currently active. The X in the top-right corner closes it (it collapses away toward the small bell icon
 * next to the city name); tapping that bell icon brings it back.
 */
@Composable
private fun AlertSection(alerts: List<CustomAlertMatch>, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShakingBellIcon(
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    size = 24.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${alerts.size} weather alert${if (alerts.size > 1) "s" else ""}",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss alert",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            alerts.forEach { match ->
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        match.label,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        match.detail,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun TodaySection(current: CurrentWeather, useFahrenheit: Boolean, todayParts: List<DayPart>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PremiumWeatherIcon(current.conditionGroup, size = 60.dp)
                Text(
                    WeatherFormat.temp(current.temp, useFahrenheit),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    current.description ?: "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "Feels like ${WeatherFormat.temp(current.feelsLike, useFahrenheit)} \u00B7 H:${WeatherFormat.tempNoUnit(current.tempMax, useFahrenheit)} L:${WeatherFormat.tempNoUnit(current.tempMin, useFahrenheit)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 1.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn(StatIconType.HUMIDITY, "Humidity", current.humidity?.let { "$it%" } ?: "--")
                    StatColumn(StatIconType.WIND, "Wind", current.windSpeedKmh?.let { "${(it / 3.6).roundToInt()} m/s" } ?: "--")
                    StatColumn(StatIconType.PRESSURE, "Pressure", current.pressure?.let { "$it hPa" } ?: "--")
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatColumn(StatIconType.SUNRISE, "Sunrise", current.sunrise ?: "--")
                    StatColumn(StatIconType.SUNSET, "Sunset", current.sunset ?: "--")
                }

                if (todayParts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(vertical = 6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            todayParts.forEach { part -> DayPartColumn(part, useFahrenheit) }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                ClothingTip(current, useFahrenheit)

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Live",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/** Colourful emoji "what to wear" icons matching the condition/temperature, e.g. hat+gloves+scarf for
 *  snow/cold, umbrella+boots for rain, sunglasses for hot & clear - continuing the same idea for every
 *  weather type instead of one generic clothing icon. */
private fun clothingEmojis(tempC: Double?, group: ConditionGroup, windKmh: Double?): List<String> {
    if (tempC == null) return listOf("\uD83D\uDC55") // 👕 fallback
    val icons = mutableListOf<String>()
    when {
        tempC <= 0 -> icons += listOf("\uD83E\uDDE3", "\uD83E\uDDE4", "\uD83E\uDDE2") // 🧣🧤🧢 scarf, gloves, cap
        tempC <= 10 -> icons += "\uD83E\uDDE5" // 🧥 jacket
        tempC <= 18 -> icons += "\uD83D\uDC55" // 👕 sweater/shirt layer
        tempC <= 24 -> icons += "\uD83D\uDC55" // 👕 t-shirt
        else -> icons += listOf("\uD83E\uDD73", "\uD83D\uDD76\uFE0F") // 🥳🕶️ shorts vibe + sunglasses
    }
    when (group) {
        ConditionGroup.RAIN -> icons += listOf("\u2602\uFE0F", "\uD83D\uDC62") // ☂️👢 umbrella, boots
        ConditionGroup.SNOW -> icons += listOf("\u2603\uFE0F", "\uD83D\uDC62") // ☃️👢 snowman, boots
        ConditionGroup.THUNDER -> icons += "\u26A1" // ⚡
        ConditionGroup.CLEAR -> if (tempC > 20) icons += "\u2600\uFE0F" // ☀️
        else -> {}
    }
    if (windKmh != null && windKmh >= 35) icons += "\uD83D\uDCA8" // 💨 windbreaker
    return icons.distinct()
}

@Composable
private fun ClothingTip(current: CurrentWeather, useFahrenheit: Boolean) {
    val text = ClothingAdvisor.recommend(current.temp, current.conditionGroup, current.windSpeedKmh)
    val emojis = clothingEmojis(current.temp, current.conditionGroup, current.windSpeedKmh)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            emojis.forEach { emoji ->
                Text(emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 2.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DayPartColumn(part: DayPart, useFahrenheit: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(part.label, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), fontSize = 12.sp)
        PremiumWeatherIcon(part.conditionGroup, size = 30.dp)
        Text(
            WeatherFormat.tempNoUnit(part.temp, useFahrenheit),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        part.rainChancePercent?.takeIf { it > 0 }?.let {
            Text("$it%", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun StatColumn(icon: StatIconType, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StatIcon3D(icon, size = 22.dp)
        Spacer(Modifier.height(1.dp))
        Text(value, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
        Text(label, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

/** Detailed forecast for tomorrow, plus its own morning/afternoon/evening breakdown - shown under the "Tomorrow" title. */
@Composable
private fun TomorrowSection(tomorrow: DayForecast, parts: List<DayPart>, useFahrenheit: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumWeatherIcon(tomorrow.conditionGroup, size = 50.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${tomorrow.dayLabel}, ${tomorrow.dateLabel}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text(tomorrow.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${WeatherFormat.tempNoUnit(tomorrow.maxTemp, useFahrenheit)} / ${WeatherFormat.tempNoUnit(tomorrow.minTemp, useFahrenheit)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Extra detail: rain chance, wind, UV, sunrise & sunset for tomorrow specifically.
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStat(StatIconType.RAIN, tomorrow.rainChancePercent?.let { "$it%" } ?: "--", "Rain")
                MiniStat(StatIconType.WIND, tomorrow.windSpeedKmh?.let { "${(it / 3.6).roundToInt()} m/s" } ?: "--", "Wind")
                MiniStat(StatIconType.UV, tomorrow.uvIndexMax?.let { "${it.toInt()}" } ?: "--", "UV")
                MiniStat(StatIconType.SUNRISE, tomorrow.sunrise ?: "--", "Sunrise")
                MiniStat(StatIconType.SUNSET, tomorrow.sunset ?: "--", "Sunset")
            }

            if (parts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    parts.forEach { part ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(part.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            PremiumWeatherIcon(part.conditionGroup, size = 38.dp)
                            Spacer(Modifier.height(2.dp))
                            Text(WeatherFormat.tempNoUnit(part.temp, useFahrenheit), fontWeight = FontWeight.SemiBold)
                            part.rainChancePercent?.takeIf { it > 0 }?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.WaterDrop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(" $it%", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(icon: StatIconType, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StatIcon3D(icon, size = 20.dp)
        Spacer(Modifier.height(1.dp))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThreeDayStrip(days: List<DayForecast>, useFahrenheit: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            days.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(day.dayLabel, fontWeight = FontWeight.SemiBold)
                    Text(day.dateLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    PremiumWeatherIcon(day.conditionGroup, size = 42.dp)
                    Spacer(Modifier.height(4.dp))
                    Text("${WeatherFormat.tempNoUnit(day.maxTemp, useFahrenheit)} / ${WeatherFormat.tempNoUnit(day.minTemp, useFahrenheit)}")
                    day.rainChancePercent?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.WaterDrop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text("$it%", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "See next 16 days & month",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
