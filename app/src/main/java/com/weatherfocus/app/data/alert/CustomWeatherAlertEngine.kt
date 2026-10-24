package com.weatherfocus.app.data.alert

import com.weatherfocus.app.data.model.AlertRuleType
import com.weatherfocus.app.data.model.CustomAlertMatch
import com.weatherfocus.app.data.model.CustomAlertRules
import com.weatherfocus.app.data.model.OpenMeteoHourly
import com.weatherfocus.app.data.repository.WeatherCodeMapper
import com.weatherfocus.app.widget.WidgetData
import com.weatherfocus.app.widget.WidgetExtras
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Evaluates the user's custom weather alert rules (Settings > Custom Weather Alert Rules)
 * against Open-Meteo's free hourly forecast. For each enabled rule this scans forward from
 * "now" out to [CustomAlertRules.horizonHours] and reports the first hour the threshold is
 * met, flagged [CustomAlertMatch.leadWarning] once it's inside the configured lead time.
 */
object CustomWeatherAlertEngine {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val weekdayDateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    private val hourlyTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)

    private data class Point(val atMillis: Long, val temp: Double?, val windSpeed: Double?, val uvi: Double?, val pop: Int?, val code: Int?,
        val feelsLike: Double? = null, val humidity: Int? = null, val rain: Double? = null,
        val snowCm: Double? = null, val visibility: Double? = null, val gust: Double? = null)

    fun evaluate(
        hourly: OpenMeteoHourly?, rules: CustomAlertRules, nowMillis: Long = System.currentTimeMillis(),
        flood: com.weatherfocus.app.data.model.FloodContext? = null
    ): List<CustomAlertMatch> {
        if (!rules.enabled || hourly?.time == null) return emptyList()

        val horizonMillis = TimeUnit.HOURS.toMillis(rules.horizonHours.coerceIn(1, 86).toLong())
        val leadMillis = TimeUnit.HOURS.toMillis(rules.leadTimeHours.coerceIn(1, 48).toLong())
        val windowEnd = nowMillis + horizonMillis

        val parsedPoints = hourly.time.indices.mapNotNull { i ->
            val millis = runCatching { hourlyTimeFormat.parse(hourly.time[i])?.time }.getOrNull() ?: return@mapNotNull null
            Point(
                atMillis = millis,
                temp = hourly.temperature_2m?.getOrNull(i),
                windSpeed = hourly.wind_speed_10m?.getOrNull(i),
                uvi = hourly.uv_index?.getOrNull(i),
                pop = hourly.precipitation_probability?.getOrNull(i),
                code = hourly.weather_code?.getOrNull(i),
                feelsLike = hourly.apparent_temperature?.getOrNull(i),
                humidity = hourly.relative_humidity_2m?.getOrNull(i),
                rain = hourly.rain?.getOrNull(i),
                snowCm = hourly.snowfall?.getOrNull(i),
                visibility = hourly.visibility?.getOrNull(i),
                gust = hourly.wind_gusts_10m?.getOrNull(i)
            )
        }.sortedBy { it.atMillis }
        // The user-chosen horizon applies to the general rules; the car rules below always look at the coming night/morning.
        val allPoints = parsedPoints.filter { it.atMillis <= windowEnd }
        // Only hours from "now" onward can trigger; earlier ones are kept purely as "previous hour"
        // context for the transition rules (frost arriving, refreezing, rapid drop).
        val points = allPoints.filter { it.atMillis >= nowMillis }

        val results = mutableListOf<CustomAlertMatch>()
        fun isImminent(atMillis: Long) = atMillis - nowMillis <= leadMillis
        fun whenText(atMillis: Long) = "${dayLabel(atMillis, nowMillis)}, ${timeFormat.format(Date(atMillis))}"

        if (rules.tempAboveEnabled) {
            points.firstOrNull { (it.temp ?: Double.MIN_VALUE) >= rules.tempAboveValue }?.let { p ->
                results += CustomAlertMatch(
                    AlertRuleType.TEMP_ABOVE, "Temperature above ${fmt(rules.tempAboveValue)}\u00B0C",
                    "${whenText(p.atMillis)} \u2014 Forecast ${p.temp?.let { fmt(it) } ?: "\u2014"}\u00B0C",
                    dayLabel(p.atMillis, nowMillis), p.atMillis, isImminent(p.atMillis)
                )
            }
        }
        if (rules.tempBelowEnabled) {
            points.firstOrNull { (it.temp ?: Double.MAX_VALUE) <= rules.tempBelowValue }?.let { p ->
                results += CustomAlertMatch(
                    AlertRuleType.TEMP_BELOW, "Temperature below ${fmt(rules.tempBelowValue)}\u00B0C",
                    "${whenText(p.atMillis)} \u2014 Forecast ${p.temp?.let { fmt(it) } ?: "\u2014"}\u00B0C",
                    dayLabel(p.atMillis, nowMillis), p.atMillis, isImminent(p.atMillis)
                )
            }
        }
        if (rules.uvIndexEnabled) {
            points.firstOrNull { (it.uvi ?: -1.0) >= rules.uvIndexValue }?.let { p ->
                results += CustomAlertMatch(
                    AlertRuleType.UV_INDEX, "UV index above ${fmt(rules.uvIndexValue)}",
                    "${whenText(p.atMillis)} \u2014 Forecast UV ${p.uvi?.let { fmt(it) } ?: "\u2014"}",
                    dayLabel(p.atMillis, nowMillis), p.atMillis, isImminent(p.atMillis)
                )
            }
        }
        if (rules.windSpeedEnabled) {
            points.firstOrNull { (it.windSpeed ?: -1.0) >= rules.windSpeedValue || (it.gust ?: -1.0) >= rules.windGustValue }?.let { p ->
                results += CustomAlertMatch(
                    AlertRuleType.WIND_SPEED, "\uD83D\uDCA8 Strong wind (\u2265 ${ms(rules.windSpeedValue)} m/s, gusts \u2265 ${ms(rules.windGustValue)} m/s)",
                    "${whenText(p.atMillis)} \u2014 Wind ${p.windSpeed?.let { ms(it) } ?: "\u2014"} m/s" +
                        (p.gust?.let { ", gusts ${ms(it)} m/s" } ?: ""),
                    dayLabel(p.atMillis, nowMillis), p.atMillis, isImminent(p.atMillis),
                    severity = if ((p.windSpeed ?: 0.0) >= rules.windSpeedValue * 1.4 || (p.gust ?: 0.0) >= rules.windGustValue * 1.4)
                        com.weatherfocus.app.data.model.AlertSeverity.RED else com.weatherfocus.app.data.model.AlertSeverity.ORANGE
                )
            }
        }
        if (rules.rainProbEnabled) {
            points.firstOrNull { (it.pop ?: -1) >= rules.rainProbValue }?.let { p ->
                results += CustomAlertMatch(
                    AlertRuleType.RAIN_PROBABILITY, "\uD83C\uDF27\uFE0F Rain likely (\u2265 ${rules.rainProbValue}%)",
                    "${whenText(p.atMillis)} \u2014 Rain chance ${p.pop ?: 0}%",
                    dayLabel(p.atMillis, nowMillis), p.atMillis, isImminent(p.atMillis)
                )
            }
        }
        if (rules.thunderstormEnabled) {
            points.firstOrNull { WeatherCodeMapper.isThunder(it.code) }?.let { p ->
                results += CustomAlertMatch(
                    AlertRuleType.THUNDERSTORM, "Thunderstorm expected",
                    "Expected ${whenText(p.atMillis)}", dayLabel(p.atMillis, nowMillis), p.atMillis, isImminent(p.atMillis)
                )
            }
        }
        // ---------------- Winter / Nordic rules ----------------
        fun add(type: AlertRuleType, label: String, detail: String, p: Point) {
            results += CustomAlertMatch(
                type, label, "${whenText(p.atMillis)} \u2014 $detail",
                dayLabel(p.atMillis, nowMillis), p.atMillis, isImminent(p.atMillis)
            )
        }
        fun previousOf(p: Point): Point? = allPoints.lastOrNull { it.atMillis < p.atMillis }
        fun isWet(p: Point): Boolean =
            (p.rain ?: 0.0) > 0.1 || (p.code != null && p.code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82))
        fun isSnowing(p: Point): Boolean = (p.snowCm ?: 0.0) > 0.0 || WeatherCodeMapper.isSnow(p.code)

        if (rules.freezingRainEnabled) {
            points.firstOrNull { p ->
                val t = p.temp ?: return@firstOrNull false
                (p.code != null && p.code in listOf(56, 57, 66, 67)) || (t <= rules.freezingRainMaxTemp && isWet(p))
            }?.let { p ->
                add(AlertRuleType.FREEZING_RAIN, "\uD83E\uDDCA Freezing rain \u2013 extremely slippery",
                    "Rain at ${p.temp?.let { fmt(it) } ?: "\u2014"}\u00B0C, expect glaze ice", p)
            }
        }
        if (rules.blackIceEnabled) {
            // One "ice on the road" alert: wet surfaces (rain/sleet/melt within the last 6 h or now) with the air at or below
            // the threshold - including a big minus after rain - or wet air refreezing through 0.
            points.firstOrNull { p ->
                val t = p.temp ?: return@firstOrNull false
                val prev = previousOf(p)
                val prevT = prev?.temp
                val wetRecent = parsedPoints.any { it.atMillis <= p.atMillis && p.atMillis - it.atMillis <= TimeUnit.HOURS.toMillis(6) && isWet(it) }
                val cold = t <= rules.blackIceMaxTemp && wetRecent
                val refreeze = prev != null && prevT != null && prevT > 0.0 && t <= 0.0 &&
                    (isWet(prev) || isWet(p) || (p.humidity ?: 0) >= 80)
                cold || refreeze
            }?.let { p ->
                add(AlertRuleType.BLACK_ICE, "\u26A0\uFE0F Ice on the road",
                    "${p.temp?.let { fmt(it) } ?: "\u2014"}\u00B0C after rain/wet roads \u2013 slippery roads and paths", p)
            }
        }
        if (rules.heavySnowEnabled) {
            points.firstOrNull { (it.snowCm ?: 0.0) >= rules.heavySnowValue }?.let { p ->
                add(AlertRuleType.HEAVY_SNOW, "\uD83C\uDF28\uFE0F Heavy snowfall (\u2265 ${fmt(rules.heavySnowValue)} cm/h)",
                    "About ${p.snowCm?.let { fmt(it) } ?: "\u2014"} cm of snow this hour", p)
            }
        }
        if (rules.snowstormEnabled) {
            points.firstOrNull { p ->
                isSnowing(p) && maxOf(p.windSpeed ?: 0.0, (p.gust ?: 0.0) * 0.7) >= rules.snowstormWindValue
            }?.let { p ->
                add(AlertRuleType.SNOWSTORM, "\uD83C\uDF2C\uFE0F Snowstorm / drifting snow",
                    "Snow with wind ${p.windSpeed?.let { ms(it) } ?: "\u2014"} m/s" +
                        (p.gust?.let { ", gusts ${ms(it)} m/s" } ?: "") + " \u2013 poor visibility", p)
            }
        }
        if (rules.windChillEnabled) {
            points.firstOrNull { (it.feelsLike ?: Double.MAX_VALUE) <= rules.windChillValue }?.let { p ->
                add(AlertRuleType.WIND_CHILL, "\uD83E\uDD76 Wind chill \u2264 ${fmt(rules.windChillValue)}\u00B0C",
                    "Feels like ${p.feelsLike?.let { fmt(it) } ?: "\u2014"}\u00B0C (air ${p.temp?.let { fmt(it) } ?: "\u2014"}\u00B0C)", p)
            }
        }
        if (rules.lowVisibilityEnabled) {
            points.firstOrNull { (it.visibility ?: Double.MAX_VALUE) <= rules.lowVisibilityValue }?.let { p ->
                add(AlertRuleType.LOW_VISIBILITY, "\uD83C\uDF2B\uFE0F Low visibility (\u2264 ${fmt(rules.lowVisibilityValue)} m)",
                    "Visibility about ${p.visibility?.let { fmt(it) } ?: "\u2014"} m \u2013 drive carefully", p)
            }
        }
        if (rules.rapidDropEnabled) {
            points.firstOrNull { p ->
                val t = p.temp ?: return@firstOrNull false
                val ref = allPoints.filter { it.atMillis < p.atMillis && p.atMillis - it.atMillis <= TimeUnit.HOURS.toMillis(6) }
                    .mapNotNull { it.temp }.maxOrNull() ?: return@firstOrNull false
                ref - t >= rules.rapidDropValue
            }?.let { p ->
                add(AlertRuleType.RAPID_TEMP_DROP, "\uD83D\uDCC9 Rapid temperature drop",
                    "Falling \u2265 ${fmt(rules.rapidDropValue)}\u00B0C within 6h, down to ${p.temp?.let { fmt(it) } ?: "\u2014"}\u00B0C", p)
            }
        }

        // ---------------- Car & road ----------------
        val morningEnd = nextMorningEnd(nowMillis)               // 09:00 of the coming morning
        val morningStart = morningEnd - TimeUnit.HOURS.toMillis(6) // 03:00
        val notifyFrom = morningEnd - TimeUnit.HOURS.toMillis(15) // 18:00 the evening before: time to plan / set the alarm
        val carNotifyNow = nowMillis >= notifyFrom
        val tomorrowMorning = !sameDay(morningEnd, nowMillis)
        val morningWord = if (tomorrowMorning) "tomorrow morning" else "this morning"
        val morningTrigger = morningStart + TimeUnit.HOURS.toMillis(3) // ~06:00

        if (rules.carIceEnabled) {
            val slots = WidgetData.upcoming(WidgetData.slots(hourly), nowMillis)
            val f = WidgetExtras.frostInfo(slots, nowMillis)
            if (f.pct >= rules.carIceChance) {
                val low = f.minTemp?.let { ", low ${fmt(it)}\u00B0C" } ?: ""
                val rainy = parsedPoints.any { it.atMillis in morningStart until morningEnd && (it.rain ?: 0.0) >= 0.1 && (it.temp ?: 9.0) <= 0.5 }
                results += CustomAlertMatch(
                    AlertRuleType.CAR_ICE,
                    "\uD83E\uDDCA Ice on the car $morningWord",
                    (if (rainy) "Freezing rain/glaze likely on the glass" else "${f.pct}% chance of frost on the windscreen$low") +
                        " \u2013 get up 10\u201315 min earlier to scrape (or cover it tonight)",
                    dayLabel(morningTrigger, nowMillis), morningTrigger, carNotifyNow,
                    severity = if (rainy) com.weatherfocus.app.data.model.AlertSeverity.ORANGE else com.weatherfocus.app.data.model.AlertSeverity.YELLOW
                )
            }
        }
        if (rules.carSnowEnabled) {
            val from = maxOf(nowMillis, morningEnd - TimeUnit.HOURS.toMillis(15))
            val snowSum = parsedPoints.filter { it.atMillis in from until morningEnd }.sumOf { it.snowCm ?: 0.0 }
            if (snowSum >= rules.carSnowValue) {
                results += CustomAlertMatch(
                    AlertRuleType.CAR_SNOW,
                    "\u2744\uFE0F Snow on the car $morningWord",
                    "About ${fmt(snowSum)} cm of snow overnight \u2013 clean the car and leave earlier if you need to drive",
                    dayLabel(morningTrigger, nowMillis), morningTrigger, carNotifyNow
                )
            }
        }
        // ---------------- Flood risk ----------------
        if (rules.floodEnabled) {
            val next48 = parsedPoints.filter { it.atMillis in nowMillis..(nowMillis + TimeUnit.HOURS.toMillis(48)) }
                .sumOf { (it.rain ?: 0.0) + (it.snowCm ?: 0.0) * 0.1 }
            val past = flood?.rainPast5dMm ?: 0.0
            val total = past + next48
            val riverRising = flood?.riverPeakM3s != null && flood.riverMeanM3s != null && flood.riverMeanM3s > 0.5 &&
                flood.riverPeakM3s >= flood.riverMeanM3s * 1.8
            val rainTrigger = total >= rules.floodMm && next48 >= 5.0
            if (rainTrigger || (riverRising && total >= rules.floodMm * 0.5)) {
                val startOfDay = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val river = if (riverRising) " River flow is forecast to rise to about ${fmt((flood!!.riverPeakM3s!! / flood.riverMeanM3s!!))}\u00D7 its 30-day average." else ""
                results += CustomAlertMatch(
                    AlertRuleType.FLOOD, "\uD83C\uDF0A Flood risk \u2013 saturated ground",
                    "${fmt(past)} mm of rain in the last 5 days + about ${fmt(next48)} mm in the next 48 h.$river Watch low ground, drains, cellars and rivers.",
                    "Today", startOfDay, true,
                    severity = if (total >= rules.floodMm * 1.5) com.weatherfocus.app.data.model.AlertSeverity.RED
                    else com.weatherfocus.app.data.model.AlertSeverity.ORANGE
                )
            }
        }

        // Several alerts describing the same hazard would just be noise: freezing rain already says "ice on the road".
        if (results.any { it.type == AlertRuleType.FREEZING_RAIN }) results.removeAll { it.type == AlertRuleType.BLACK_ICE }

        return results.sortedBy { it.triggerAtMillis }
    }

    /** Everything the user should see: their own rules plus (if enabled) MET Norway's official warnings. */
    fun evaluateAll(
        hourly: OpenMeteoHourly?, rules: CustomAlertRules,
        flood: com.weatherfocus.app.data.model.FloodContext? = null,
        official: List<CustomAlertMatch> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<CustomAlertMatch> {
        val own = evaluate(hourly, rules, nowMillis, flood)
        if (!rules.enabled || !rules.officialEnabled) return own
        return (official + own).sortedWith(compareByDescending<CustomAlertMatch> { it.severity.ordinal }.thenBy { it.triggerAtMillis })
    }

    private fun nextMorningEnd(nowMillis: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= nowMillis) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun dayLabel(triggerMillis: Long, nowMillis: Long): String {
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diffDays = ((triggerMillis - startOfToday) / TimeUnit.DAYS.toMillis(1)).toInt()
        return when {
            diffDays <= 0 -> "Today"
            diffDays == 1 -> "Tomorrow"
            diffDays in 2..6 -> "In $diffDays days"
            else -> weekdayDateFormat.format(Date(triggerMillis))
        }
    }

    private fun ms(kmh: Double): String = fmt(Math.round(kmh / 3.6 * 10) / 10.0)

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
}
