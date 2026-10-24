package com.weatherfocus.app.widget

import com.weatherfocus.app.data.model.ConditionGroup
import com.weatherfocus.app.data.model.OpenMeteoHourly
import com.weatherfocus.app.data.repository.WeatherCodeMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One hour of forecast, parsed out of Open-Meteo's parallel hourly arrays. */
data class HourSlot(
    val millis: Long,
    val temp: Double?,
    val code: Int?,
    val rainChance: Int?,
    val gust: Double?,
    val uv: Double?,
    val snowCm: Double?,
    val rainMm: Double?,
    val visibilityM: Double?,
    val feelsLike: Double?,
    val cloudPercent: Int?,
    val windDirDeg: Int?,
    val dewPoint: Double?,
    val windSpeed: Double? = null,
    val snowDepthM: Double? = null
)

object WidgetData {
    private val timeParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)

    fun slots(h: OpenMeteoHourly?): List<HourSlot> {
        val times = h?.time ?: return emptyList()
        return times.indices.mapNotNull { i ->
            val millis = runCatching { timeParser.parse(times[i])?.time }.getOrNull() ?: return@mapNotNull null
            HourSlot(
                millis = millis,
                temp = h.temperature_2m?.getOrNull(i),
                code = h.weather_code?.getOrNull(i),
                rainChance = h.precipitation_probability?.getOrNull(i),
                gust = h.wind_gusts_10m?.getOrNull(i),
                uv = h.uv_index?.getOrNull(i),
                snowCm = h.snowfall?.getOrNull(i),
                rainMm = h.rain?.getOrNull(i),
                visibilityM = h.visibility?.getOrNull(i),
                feelsLike = h.apparent_temperature?.getOrNull(i),
                cloudPercent = h.cloud_cover?.getOrNull(i),
                windDirDeg = h.wind_direction_10m?.getOrNull(i),
                dewPoint = h.dew_point_2m?.getOrNull(i),
                windSpeed = h.wind_speed_10m?.getOrNull(i),
                snowDepthM = h.snow_depth?.getOrNull(i)
            )
        }.sortedBy { it.millis }
    }

    /** Hours starting with the one we are currently in. */
    fun upcoming(all: List<HourSlot>, nowMillis: Long): List<HourSlot> =
        all.filter { it.millis >= nowMillis - 59 * 60_000L }

    private fun minutesOf(clock: String?): Int? {
        val parts = clock?.split(":") ?: return null
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }

    fun isNight(millis: Long, sunrise: String?, sunset: String?): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val sr = minutesOf(sunrise)
        val ss = minutesOf(sunset)
        return if (sr != null && ss != null) minute < sr || minute >= ss
        else cal.get(Calendar.HOUR_OF_DAY).let { it < 6 || it >= 21 }
    }

    fun daylightText(sunrise: String?, sunset: String?): String? {
        val sr = minutesOf(sunrise) ?: return null
        val ss = minutesOf(sunset) ?: return null
        val d = ss - sr
        if (d <= 0) return null
        return "${d / 60}h ${d % 60}m"
    }

    fun emojiForCode(code: Int?, night: Boolean): String = when (code) {
        0 -> if (night) "🌙" else "☀️"
        1 -> if (night) "🌙" else "🌤️"
        2 -> if (night) "☁️" else "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 80, 81, 82 -> "🌦️"
        56, 57, 66, 67 -> "🧊"
        61, 63, 65 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌤️"
    }

    fun emojiForGroup(group: ConditionGroup, night: Boolean): String = when (group) {
        ConditionGroup.CLEAR -> if (night) "🌙" else "☀️"
        ConditionGroup.CLOUDY -> "⛅"
        ConditionGroup.RAIN -> "🌧️"
        ConditionGroup.SNOW -> "🌨️"
        ConditionGroup.THUNDER -> "⛈️"
        ConditionGroup.FOG -> "🌫️"
        ConditionGroup.UNKNOWN -> "🌤️"
    }

    /** Higher = nastier. Used to colour the widget by the worst weather in the look-ahead window. */
    fun severity(g: ConditionGroup): Int = when (g) {
        ConditionGroup.THUNDER -> 6
        ConditionGroup.SNOW -> 5
        ConditionGroup.RAIN -> 4
        ConditionGroup.FOG -> 3
        ConditionGroup.CLOUDY -> 2
        ConditionGroup.CLEAR -> 1
        ConditionGroup.UNKNOWN -> 0
    }

    fun groupOfCode(code: Int?): ConditionGroup = WeatherCodeMapper.groupOf(code)

    /** "🧊 Black ice risk" -> ("🧊", "Black ice risk"); labels without a leading emoji get a warning sign. */
    fun splitEmoji(label: String): Pair<String, String> {
        val i = label.indexOf(' ')
        return if (i > 0 && label[0].code > 127) label.substring(0, i) to label.substring(i + 1).trim()
        else "⚠️" to label
    }

    /** Compass point the wind blows FROM, e.g. 315 -> "NW". */
    fun compass(deg: Int): String {
        val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[(((deg % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8]
    }
}
