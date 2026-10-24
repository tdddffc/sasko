package com.weatherfocus.app.data.repository

import com.google.gson.JsonElement
import com.weatherfocus.app.data.model.AlertRuleType
import com.weatherfocus.app.data.model.AlertSeverity
import com.weatherfocus.app.data.model.CustomAlertMatch
import com.weatherfocus.app.data.model.MetNoResponse
import com.weatherfocus.app.data.model.MetNoTimeseries
import com.weatherfocus.app.data.model.OpenMeteoHourly
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * yr.no (MET Norway) is the app's primary forecast. Open-Meteo stays underneath only as a filler for what yr.no
 * does not provide (16-day daily forecast, sunrise/sunset, snow depth, visibility) and as a fallback if yr.no is down.
 *
 * [mergeHourly] takes Open-Meteo's hourly arrays and overwrites every hour that yr.no covers with yr.no's values.
 * Keys are the local wall-clock strings Open-Meteo uses ("yyyy-MM-dd'T'HH:mm"), formatted in the phone's time zone,
 * which is the same assumption the rest of the app already makes.
 */
object YrMerger {

    private val utcIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parse(t: String?): Long? = t?.let { runCatching { synchronized(utcIso) { utcIso.parse(it)?.time } }.getOrNull() }

    private fun key(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(Date(millis))

    /** yr.no symbol_code ("heavyrainshowers_day") -> the WMO code the rest of the app understands. */
    fun wmoFromSymbol(symbol: String?): Int? {
        if (symbol == null) return null
        val s = symbol.substringBefore('_')
        return when {
            s.contains("thunder") -> 95
            s == "clearsky" -> 0
            s == "fair" -> 1
            s == "partlycloudy" -> 2
            s == "cloudy" -> 3
            s == "fog" -> 45
            s.startsWith("heavysnowshowers") -> 86
            s.contains("snowshowers") -> 85
            s.startsWith("heavysnow") -> 75
            s.startsWith("lightsnow") -> 71
            s.contains("snow") && !s.contains("sleet") -> 73
            s.contains("sleet") -> 63          // rain/snow mix: treated as wet, not as "freezing rain"
            s.startsWith("heavyrainshowers") -> 82
            s.contains("rainshowers") -> 80
            s.startsWith("heavyrain") -> 65
            s.startsWith("lightrain") -> 61
            s.contains("rain") -> 63
            else -> null
        }
    }

    private fun isSnowSymbol(symbol: String?) = symbol?.substringBefore('_')?.let { it.contains("snow") } == true

    /** Wind chill ("feels like") from air temperature (degC) and wind (km/h), Environment Canada formula. */
    fun feelsLike(tempC: Double, windKmh: Double): Double {
        if (tempC > 10.0 || windKmh <= 4.8) return tempC
        val v = windKmh.pow(0.16)
        return 13.12 + 0.6215 * tempC - 11.37 * v + 0.3965 * tempC * v
    }

    /** Hourly entries only (those that carry a next_1_hours block), as millis -> entry. */
    private fun hourlyEntries(yr: MetNoResponse?): List<Pair<Long, MetNoTimeseries>> =
        yr?.properties?.timeseries.orEmpty().mapNotNull { ts ->
            val m = parse(ts.time) ?: return@mapNotNull null
            if (ts.data?.next_1_hours == null || ts.data.instant?.details == null) null else m to ts
        }

    fun mergeHourly(om: OpenMeteoHourly?, yr: MetNoResponse?): OpenMeteoHourly? {
        val entries = hourlyEntries(yr)
        if (entries.isEmpty()) return om

        // Base timeline: Open-Meteo's if we have it, otherwise yr.no's own hours.
        val times: List<String> = om?.time ?: entries.map { key(it.first) }
        val n = times.size
        val index = HashMap<String, Int>(n * 2).also { m -> times.forEachIndexed { i, t -> m[t] = i } }

        fun <T> copyOf(src: List<T?>?): MutableList<T?> =
            MutableList(n) { i -> src?.getOrNull(i) }

        val temp = copyOf(om?.temperature_2m)
        val pop = copyOf(om?.precipitation_probability)
        val code = copyOf(om?.weather_code)
        val wind = copyOf(om?.wind_speed_10m)
        val uv = copyOf(om?.uv_index)
        val cloud = copyOf(om?.cloud_cover)
        val dir = copyOf(om?.wind_direction_10m)
        val dew = copyOf(om?.dew_point_2m)
        val app = copyOf(om?.apparent_temperature)
        val hum = copyOf(om?.relative_humidity_2m)
        val rain = copyOf(om?.rain)
        val snow = copyOf(om?.snowfall)
        val vis = copyOf(om?.visibility)
        val gust = copyOf(om?.wind_gusts_10m)
        val depth = copyOf(om?.snow_depth)

        for ((millis, ts) in entries) {
            val i = index[key(millis)] ?: continue
            val d = ts.data!!.instant!!.details!!
            val next = ts.data.next_1_hours!!
            val symbol = next.summary?.symbol_code

            d.air_temperature?.let { temp[i] = it }
            d.wind_speed?.let { wind[i] = it * 3.6 }
            d.wind_speed_of_gust?.let { gust[i] = it * 3.6 }
            d.wind_from_direction?.let { dir[i] = it.roundToInt() }
            d.dew_point_temperature?.let { dew[i] = it }
            d.relative_humidity?.let { hum[i] = it.roundToInt() }
            d.cloud_area_fraction?.let { cloud[i] = it.roundToInt() }
            wmoFromSymbol(symbol)?.let { code[i] = it }
            next.details?.probability_of_precipitation?.let { pop[i] = it.roundToInt() }
            val t = temp[i]
            val w = wind[i]
            if (t != null && w != null) app[i] = feelsLike(t, w)
            val fog = d.fog_area_fraction
            if (fog != null && fog >= 20.0) vis[i] = if (fog >= 50.0) 800.0 else 3000.0
            d.ultraviolet_index_clear_sky?.let { clear ->
                val c = (d.cloud_area_fraction ?: 0.0) / 100.0
                uv[i] = (clear * (1.0 - 0.75 * c.pow(3.4)) * 10.0).roundToInt() / 10.0
            }
            // Precipitation is "during the next hour": Open-Meteo reports "during the preceding hour", so shift by one.
            val mm = next.details?.precipitation_amount
            val j = index[key(millis + 3_600_000L)]
            if (mm != null && j != null) {
                if (isSnowSymbol(symbol)) { snow[j] = (mm * 1.2 * 10).roundToInt() / 10.0; rain[j] = 0.0 }
                else { rain[j] = mm; snow[j] = 0.0 }
            }
        }

        return OpenMeteoHourly(
            time = times,
            temperature_2m = temp, precipitation_probability = pop, weather_code = code, wind_speed_10m = wind,
            uv_index = uv, cloud_cover = cloud, wind_direction_10m = dir, dew_point_2m = dew,
            apparent_temperature = app, relative_humidity_2m = hum, rain = rain, snowfall = snow,
            visibility = vis, wind_gusts_10m = gust, snow_depth = depth
        )
    }

    /** What yr.no says right now (first entry), as a small value object for the "current conditions" card. */
    data class YrNow(
        val temp: Double, val windKmh: Double?, val humidity: Int?, val pressure: Int?, val symbol: String?
    )

    fun now(yr: MetNoResponse?): YrNow? {
        val first = yr?.properties?.timeseries?.firstOrNull()?.data ?: return null
        val d = first.instant?.details ?: return null
        val t = d.air_temperature ?: return null
        val symbol = first.next_1_hours?.summary?.symbol_code ?: first.next_6_hours?.summary?.symbol_code
        return YrNow(t, d.wind_speed?.let { it * 3.6 }, d.relative_humidity?.roundToInt(), d.air_pressure_at_sea_level?.roundToInt(), symbol)
    }

    /** MET Norway's official warnings (metalerts GeoJSON) as alerts. Tolerant parsing: anything unexpected is skipped. */
    fun parseOfficialAlerts(root: JsonElement?, nowMillis: Long = System.currentTimeMillis()): List<CustomAlertMatch> {
        val features = runCatching { root?.asJsonObject?.getAsJsonArray("features") }.getOrNull() ?: return emptyList()
        val out = mutableListOf<CustomAlertMatch>()
        for (f in features) {
            val obj = runCatching { f.asJsonObject }.getOrNull() ?: continue
            val props = obj.getAsJsonObject("properties") ?: continue
            fun str(k: String) = props.get(k)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
            if (str("geographicDomain").equals("marine", ignoreCase = true)) continue
            val interval = runCatching { obj.getAsJsonObject("when")?.getAsJsonArray("interval") }.getOrNull()
            val startMs = interval?.get(0)?.asString?.let { parseIsoOffset(it) }
            val endMs = interval?.get(1)?.asString?.let { parseIsoOffset(it) } ?: str("eventEndingTime")?.let { parseIsoOffset(it) }
            if (endMs != null && endMs < nowMillis) continue
            val level = str("awareness_level")?.lowercase().orEmpty()
            val severity = when {
                "red" in level -> AlertSeverity.RED
                "orange" in level -> AlertSeverity.ORANGE
                else -> AlertSeverity.YELLOW
            }
            val name = str("eventAwarenessName") ?: str("event") ?: "Weather warning"
            val color = when (severity) { AlertSeverity.RED -> "red"; AlertSeverity.ORANGE -> "orange"; AlertSeverity.YELLOW -> "yellow" }
            val detail = listOfNotNull(str("description"), str("instruction")).joinToString(" ").trim().ifEmpty { str("title") ?: "" }
            val area = str("area")
            val emoji = when (severity) { AlertSeverity.RED -> "🔴"; AlertSeverity.ORANGE -> "🟠"; AlertSeverity.YELLOW -> "🟡" }
            val trigger = startMs ?: nowMillis
            out += CustomAlertMatch(
                type = AlertRuleType.OFFICIAL,
                label = "$emoji $name ($color warning)" + (area?.let { " – $it" } ?: ""),
                detail = detail.take(300),
                dayLabel = "Today",
                triggerAtMillis = trigger,
                leadWarning = true,
                severity = severity,
                official = true
            )
        }
        return out
    }

    private fun parseIsoOffset(s: String): Long? = runCatching {
        // "2024-03-05T15:00:00+00:00" -> strip the colon in the offset so SimpleDateFormat's XXX-less pattern works everywhere.
        val fixed = if (s.length > 5 && s[s.length - 3] == ':' && (s[s.length - 6] == '+' || s[s.length - 6] == '-'))
            s.substring(0, s.length - 3) + s.substring(s.length - 2) else s.replace("Z", "+0000")
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(fixed)?.time
    }.getOrNull()
}
