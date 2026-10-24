package com.weatherfocus.app.data.repository

import com.google.gson.JsonElement
import com.weatherfocus.app.data.model.AppSettings
import com.weatherfocus.app.data.model.ConditionGroup
import com.weatherfocus.app.data.model.CurrentWeather
import com.weatherfocus.app.data.model.DayForecast
import com.weatherfocus.app.data.model.DayPart
import com.weatherfocus.app.data.model.GeoPlace
import com.weatherfocus.app.data.model.KpPoint
import com.weatherfocus.app.data.model.LastYearDay
import com.weatherfocus.app.data.model.OpenMeteoHourly
import com.weatherfocus.app.data.model.OpenMeteoMultiModelHourly
import com.weatherfocus.app.data.model.SourceReading
import com.weatherfocus.app.data.remote.NetworkModule
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything fetched/derived for the app's main screens: today's live consensus reading, day-part breakdowns, tomorrow, the 3-day strip, and the month view. */
data class WeatherBundle(
    val current: CurrentWeather,
    val todayParts: List<DayPart> = emptyList(),
    val tomorrow: DayForecast? = null,
    val tomorrowParts: List<DayPart> = emptyList(),
    val next3Days: List<DayForecast> = emptyList(),
    val monthDays: List<DayForecast> = emptyList(),
    val hourlyForAlerts: OpenMeteoHourly? = null,
    /** NOAA 3-hourly Kp forecast, used by the aurora tile (empty if the feed could not be reached). */
    val kpForecast: List<KpPoint> = emptyList(),
    /** Same calendar date one year ago, used by the "Last year today" tile. */
    val lastYearToday: LastYearDay? = null,
    /** Rain history + river trend for the flood-risk alert / tile (null if it could not be loaded). */
    val floodContext: com.weatherfocus.app.data.model.FloodContext? = null,
    /** Warnings issued by MET Norway for this location. */
    val officialAlerts: List<com.weatherfocus.app.data.model.CustomAlertMatch> = emptyList()
)

class WeatherRepository {

    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val hourlyTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)

    suspend fun searchCities(query: String): List<GeoPlace> {
        if (query.length < 2) return emptyList()
        return runCatching {
            NetworkModule.openMeteoGeocodingApi.search(name = query).results.orEmpty()
                .map {
                    GeoPlace(
                        name = it.name,
                        admin1 = it.admin1,
                        country = it.country,
                        countryCode = it.country_code,
                        latitude = it.latitude,
                        longitude = it.longitude
                    )
                }
        }.getOrElse { emptyList() }
    }

    /** Open-Meteo fills what yr.no lacks (16-day daily forecast, sunrise/sunset, snow depth, visibility) and is the fallback if yr.no is down. */
    private suspend fun fetchOpenMeteoWithRetry(settings: AppSettings): com.weatherfocus.app.data.model.OpenMeteoForecastResponse? {
        repeat(3) { attempt ->
            val r = runCatching { NetworkModule.openMeteoApi.getForecast(settings.latitude, settings.longitude) }.getOrNull()
            if (r?.current?.temperature_2m != null) return r
            kotlinx.coroutines.delay(1_500L * (attempt + 1))
        }
        return null
    }

    /** yr.no (MET Norway): the primary source. */
    private suspend fun fetchYrWithRetry(settings: AppSettings): com.weatherfocus.app.data.model.MetNoResponse? {
        repeat(3) { attempt ->
            val r = withTimeoutOrNull(12_000) {
                runCatching { NetworkModule.metNoApi.getForecast(settings.latitude, settings.longitude) }.getOrNull()
            }
            if (r?.properties?.timeseries?.isNotEmpty() == true) return r
            kotlinx.coroutines.delay(1_000L * (attempt + 1))
        }
        return null
    }

    /** MET Norway's official warnings for the location (empty outside Norway or on any error). */
    suspend fun loadOfficialAlerts(settings: AppSettings): List<com.weatherfocus.app.data.model.CustomAlertMatch> {
        if (!settings.customAlertRules.officialEnabled) return emptyList()
        val root = withTimeoutOrNull(10_000) {
            runCatching { NetworkModule.metNoApi.getAlerts(settings.latitude, settings.longitude) }.getOrNull()
        }
        return YrMerger.parseOfficialAlerts(root)
    }

    /** Rain that fell over the last 5 days and the river-discharge trend; any part may be missing without failing the rest. */
    suspend fun loadFloodContext(settings: AppSettings): com.weatherfocus.app.data.model.FloodContext? = coroutineScope {
        val rainD = async {
            withTimeoutOrNull(12_000) {
                runCatching {
                    val list = NetworkModule.openMeteoApi.getRainHistory(settings.latitude, settings.longitude).daily?.precipitation_sum.orEmpty()
                    // 7 past days + today: the last 6 entries are the 5 days before today plus today so far.
                    list.takeLast(6).sumOf { it ?: 0.0 }.takeIf { list.isNotEmpty() }
                }.getOrNull()
            }
        }
        val riverD = async {
            withTimeoutOrNull(12_000) {
                runCatching {
                    val q = NetworkModule.floodApi.getDischarge(settings.latitude, settings.longitude).daily?.river_discharge.orEmpty()
                    if (q.size < 20) null else {
                        val pastN = q.size - 7
                        val past = q.take(pastN).filterNotNull()
                        val future = q.drop(pastN).filterNotNull()
                        Triple(past.lastOrNull(), future.maxOrNull(), past.takeIf { it.isNotEmpty() }?.average())
                    }
                }.getOrNull()
            }
        }
        val rain = rainD.await()
        val river = riverD.await()
        if (rain == null && river == null) null
        else com.weatherfocus.app.data.model.FloodContext(rain ?: 0.0, river?.first, river?.second, river?.third)
    }

    /** Lightweight fetch used by the background alert check: hourly forecast only (yr.no merged over Open-Meteo). */
    suspend fun loadAlertForecast(settings: AppSettings): OpenMeteoHourly? = coroutineScope {
        val om = async { fetchOpenMeteoWithRetry(settings) }
        val yr = async { fetchYrWithRetry(settings) }
        YrMerger.mergeHourly(om.await()?.hourly, yr.await())
    }

    suspend fun loadWeather(settings: AppSettings): WeatherBundle = coroutineScope {
        val yrDeferred = async { fetchYrWithRetry(settings) }
        val openMeteoDeferred = async { fetchOpenMeteoWithRetry(settings) }
        val openWeatherDeferred = async {
            // Optional extra opinion, only if the user added a key.
            if (settings.openWeatherApiKey.isBlank()) null else withTimeoutOrNull(10_000) {
                runCatching {
                    NetworkModule.openWeatherApi.getCurrentWeather(
                        "${settings.cityName},${settings.countryCode}",
                        apiKey = settings.openWeatherApiKey
                    )
                }.getOrNull()
            }
        }
        val floodDeferred = async { loadFloodContext(settings) }
        val officialDeferred = async { loadOfficialAlerts(settings) }
        val kpDeferred = async {
            withTimeoutOrNull(10_000) {
                runCatching { parseKp(NetworkModule.noaaSwpcApi.getKpForecast()) }.getOrNull()
            } ?: emptyList()
        }

        val yr = yrDeferred.await()
        val openMeteo = openMeteoDeferred.await()
        val openWeather = openWeatherDeferred.await()

        val current = buildCurrent(yr, openMeteo, openWeather)

        // yr.no wins wherever it has data; Open-Meteo fills the rest.
        val hourly = YrMerger.mergeHourly(openMeteo?.hourly, yr)
        val todayDateStr = hourly?.time?.firstOrNull()?.substringBefore('T')
        val tomorrowDateStr = todayDateStr?.let { addIsoDays(it, 1) }

        val todayParts = buildDayParts(hourly, todayDateStr)
        val tomorrowParts = buildDayParts(hourly, tomorrowDateStr)
        val tomorrowDetailed = buildTomorrowDetailed(openMeteo?.daily)

        val next3 = buildNext3Days(openMeteo)
        val monthReal = buildRealMonthDays(openMeteo)
        val monthOutlook = buildOutlookTailDays(settings, monthReal.size)

        WeatherBundle(
            current = current,
            todayParts = todayParts,
            tomorrow = tomorrowDetailed,
            tomorrowParts = tomorrowParts,
            next3Days = next3,
            monthDays = monthReal + monthOutlook,
            hourlyForAlerts = hourly,
            kpForecast = kpDeferred.await(),
            floodContext = floodDeferred.await(),
            officialAlerts = officialDeferred.await()
        )
    }

    /** NOAA serves either [["time_tag","kp",...], [...]] (header row first) or a list of objects; handle both. */
    private fun parseKp(root: JsonElement): List<KpPoint> {
        if (!root.isJsonArray) return emptyList()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val out = mutableListOf<KpPoint>()
        for (row in root.asJsonArray) {
            var time: String? = null
            var kp: String? = null
            if (row.isJsonArray) {
                val a = row.asJsonArray
                if (a.size() >= 2) {
                    time = a[0].takeIf { !it.isJsonNull }?.asString
                    kp = a[1].takeIf { !it.isJsonNull }?.asString
                }
            } else if (row.isJsonObject) {
                val o = row.asJsonObject
                time = o.get("time_tag")?.takeIf { !it.isJsonNull }?.asString
                kp = o.get("kp")?.takeIf { !it.isJsonNull }?.asString
            }
            val t = time?.replace('T', ' ')?.take(19) ?: continue
            val v = kp?.toDoubleOrNull() ?: continue
            val millis = runCatching { fmt.parse(t)?.time }.getOrNull() ?: continue
            out += KpPoint(millis, v)
        }
        return out.sortedBy { it.startMillis }
    }

    /* ---------- current conditions: yr.no is the primary reading; Open-Meteo (and OpenWeather if keyed) are cross-checks ---------- */

    private fun buildCurrent(
        yr: com.weatherfocus.app.data.model.MetNoResponse?,
        openMeteo: com.weatherfocus.app.data.model.OpenMeteoForecastResponse?,
        openWeather: com.weatherfocus.app.data.model.OpenWeatherCurrentResponse?
    ): CurrentWeather {
        val readings = mutableListOf<SourceReading>()
        val yrNow = YrMerger.now(yr)
        val omCurrent = openMeteo?.current

        // Primary first, so it is also the one shown when sources disagree.
        if (yrNow != null) {
            readings += SourceReading("yr.no (MET Norway)", yrNow.temp, WeatherCodeMapper.groupOf(YrMerger.wmoFromSymbol(yrNow.symbol)))
        }
        if (omCurrent?.temperature_2m != null) {
            readings += SourceReading("Open-Meteo", omCurrent.temperature_2m, WeatherCodeMapper.groupOf(omCurrent.weather_code))
        }
        val owTemp = openWeather?.main?.temp
        if (owTemp != null) {
            readings += SourceReading("OpenWeather", owTemp, TextConditionMapper.groupOf(openWeather.weather?.firstOrNull()?.main))
        }
        if (readings.isEmpty()) return CurrentWeather(available = false)

        val primary = readings.first()
        val agreeing = readings.count { abs((it.tempC ?: primary.tempC ?: 0.0) - (primary.tempC ?: 0.0)) <= 2.0 }
        val temp = primary.tempC
        val group = primary.conditionGroup
        val wmo = if (yrNow != null) YrMerger.wmoFromSymbol(yrNow.symbol) else omCurrent?.weather_code

        val daily = openMeteo?.daily
        val windKmh = yrNow?.windKmh ?: omCurrent?.wind_speed_10m
        return CurrentWeather(
            temp = temp,
            feelsLike = if (temp != null && windKmh != null) YrMerger.feelsLike(temp, windKmh) else omCurrent?.apparent_temperature ?: temp,
            humidity = yrNow?.humidity ?: omCurrent?.relative_humidity_2m,
            windSpeedKmh = windKmh,
            pressure = yrNow?.pressure ?: omCurrent?.surface_pressure?.roundToInt(),
            sunrise = daily?.sunrise?.firstOrNull()?.let { formatClockTime(it) },
            sunset = daily?.sunset?.firstOrNull()?.let { formatClockTime(it) },
            description = describeGroup(group, wmo),
            conditionGroup = group,
            tempMin = daily?.temperature_2m_min?.firstOrNull(),
            tempMax = daily?.temperature_2m_max?.firstOrNull(),
            available = true,
            sourcesUsed = readings.map { it.sourceName },
            sourcesAgreeing = agreeing,
            sourcesTotal = readings.size
        )
    }

    /** Turns each of the 6 free global weather models Open-Meteo can serve in one request into its own [SourceReading]. */
    private fun multiModelReadings(response: com.weatherfocus.app.data.model.OpenMeteoMultiModelResponse?): List<SourceReading> {
        val hourly = response?.hourly ?: return emptyList()
        val idx = nearestHourIndex(hourly.time) ?: return emptyList()
        val result = mutableListOf<SourceReading>()
        fun add(name: String, temps: List<Double?>?, codes: List<Int?>?) {
            val t = temps?.getOrNull(idx) ?: return
            result += SourceReading(name, t, WeatherCodeMapper.groupOf(codes?.getOrNull(idx)))
        }
        add("GFS (NOAA)", hourly.tempGfs, hourly.codeGfs)
        add("ECMWF", hourly.tempEcmwf, hourly.codeEcmwf)
        add("ICON (DWD)", hourly.tempIcon, hourly.codeIcon)
        add("UK Met Office", hourly.tempUkmo, hourly.codeUkmo)
        add("JMA (Japan)", hourly.tempJma, hourly.codeJma)
        add("GEM (Canada)", hourly.tempGem, hourly.codeGem)
        return result
    }

    /** Finds the group of readings whose temperatures are mutually within [toleranceC] of each other - the biggest such group wins. */
    private fun mostAgreeingCluster(readings: List<SourceReading>, toleranceC: Double = 2.0): List<SourceReading> {
        val withTemp = readings.filter { it.tempC != null }
        if (withTemp.isEmpty()) return readings
        var best = listOf(withTemp.first())
        for (anchor in withTemp) {
            val cluster = withTemp.filter { abs(it.tempC!! - anchor.tempC!!) <= toleranceC }
            if (cluster.size > best.size) best = cluster
        }
        return best
    }

    private fun describeGroup(group: ConditionGroup, omCode: Int?): String =
        if (omCode != null) WeatherCodeMapper.describe(omCode) else TextConditionMapper.label(group)

    /* ---------- morning / afternoon / evening breakdown for a given date ---------- */

    private fun buildDayParts(hourly: OpenMeteoHourly?, dateStr: String?): List<DayPart> {
        if (hourly?.time == null || dateStr == null) return emptyList()
        val slots = listOf("Morning" to 9, "Afternoon" to 15, "Evening" to 20)
        return slots.mapNotNull { (label, hour) ->
            val wanted = String.format(Locale.US, "%sT%02d:00", dateStr, hour)
            val idx = hourly.time.indexOf(wanted)
            if (idx == -1) return@mapNotNull null
            DayPart(
                label = label,
                temp = hourly.temperature_2m?.getOrNull(idx),
                conditionGroup = WeatherCodeMapper.groupOf(hourly.weather_code?.getOrNull(idx)),
                rainChancePercent = hourly.precipitation_probability?.getOrNull(idx)
            )
        }
    }

    private fun buildTomorrowDetailed(daily: com.weatherfocus.app.data.model.OpenMeteoDaily?): DayForecast? {
        if (daily?.time == null || daily.time.size < 2) return null
        return dayForecastFromDaily(daily, 1, isOutlook = false)
    }

    private fun addIsoDays(dateStr: String, days: Int): String? {
        val date = runCatching { isoDate.parse(dateStr) }.getOrNull() ?: return null
        val cal = Calendar.getInstance().apply { time = date; add(Calendar.DAY_OF_YEAR, days) }
        return isoDate.format(cal.time)
    }

    /** Finds the hourly index closest to "now" - used to read a "current" value out of multi-model hourly arrays. */
    private fun nearestHourIndex(times: List<String>?, nowMillis: Long = System.currentTimeMillis()): Int? {
        if (times.isNullOrEmpty()) return null
        var bestIdx: Int? = null
        var bestDiff = Long.MAX_VALUE
        times.forEachIndexed { i, t ->
            val millis = runCatching { hourlyTimeFormat.parse(t)?.time }.getOrNull() ?: return@forEachIndexed
            val diff = abs(millis - nowMillis)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIdx = i
            }
        }
        return bestIdx
    }

    /* ---------- next 3 days strip, from Open-Meteo daily block (index 1..3, since index 0 is today) ---------- */

    private fun buildNext3Days(openMeteo: com.weatherfocus.app.data.model.OpenMeteoForecastResponse?): List<DayForecast> {
        val daily = openMeteo?.daily ?: return emptyList()
        val times = daily.time.orEmpty()
        val result = mutableListOf<DayForecast>()
        for (i in 1..3) {
            if (i >= times.size) break
            result += dayForecastFromDaily(daily, i, isOutlook = false)
        }
        return result
    }

    /** All available real daily entries (typically 16 including today) as month-view rows. */
    private fun buildRealMonthDays(openMeteo: com.weatherfocus.app.data.model.OpenMeteoForecastResponse?): List<DayForecast> {
        val daily = openMeteo?.daily ?: return emptyList()
        val times = daily.time.orEmpty()
        return times.indices.map { i -> dayForecastFromDaily(daily, i, isOutlook = false) }
    }

    private fun dayForecastFromDaily(daily: com.weatherfocus.app.data.model.OpenMeteoDaily, i: Int, isOutlook: Boolean): DayForecast {
        val dateStr = daily.time?.getOrNull(i)
        val date = dateStr?.let { runCatching { isoDate.parse(it) }.getOrNull() }
        val code = daily.weather_code?.getOrNull(i)
        return DayForecast(
            dayLabel = date?.let { dayFormat.format(it) } ?: "",
            dateLabel = date?.let { dateFormat.format(it) } ?: (dateStr ?: ""),
            minTemp = daily.temperature_2m_min?.getOrNull(i),
            maxTemp = daily.temperature_2m_max?.getOrNull(i),
            description = WeatherCodeMapper.describe(code),
            conditionGroup = WeatherCodeMapper.groupOf(code),
            rainChancePercent = daily.precipitation_probability_max?.getOrNull(i),
            windSpeedKmh = daily.wind_speed_10m_max?.getOrNull(i),
            uvIndexMax = daily.uv_index_max?.getOrNull(i),
            sunrise = daily.sunrise?.getOrNull(i)?.let { formatClockTime(it) },
            sunset = daily.sunset?.getOrNull(i)?.let { formatClockTime(it) },
            feelsMin = daily.apparent_temperature_min?.getOrNull(i),
            feelsMax = daily.apparent_temperature_max?.getOrNull(i),
            precipMm = daily.precipitation_sum?.getOrNull(i),
            snowCm = daily.snowfall_sum?.getOrNull(i),
            gustKmh = daily.wind_gusts_10m_max?.getOrNull(i),
            daylightHours = daily.daylight_duration?.getOrNull(i)?.let { it / 3600.0 },
            isOutlookEstimate = isOutlook
        )
    }

    /**
     * Days beyond Open-Meteo's real 16-day forecast, out to a full month, built from the
     * average of the same calendar dates over the last 3 years (Open-Meteo's historical
     * archive). Clearly flagged via [DayForecast.isOutlookEstimate] - this is a "typical
     * conditions" outlook, not a real forecast, since no provider forecasts that far out.
     */
    private suspend fun buildOutlookTailDays(settings: AppSettings, alreadyHaveDays: Int): List<DayForecast> {
        val remaining = 30 - alreadyHaveDays
        if (remaining <= 0) return emptyList()

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, alreadyHaveDays)
        val windowStart = cal.time
        cal.add(Calendar.DAY_OF_YEAR, remaining - 1)
        val windowEnd = cal.time

        // Fetch the same calendar-date window from each of the last 3 years and average per offset.
        val yearsBack = listOf(1, 2, 3)
        val perYearResults = yearsBack.map { years ->
            val startCal = Calendar.getInstance().apply { time = windowStart; add(Calendar.YEAR, -years) }
            val endCal = Calendar.getInstance().apply { time = windowEnd; add(Calendar.YEAR, -years) }
            runCatching {
                NetworkModule.openMeteoApi.getArchive(
                    settings.latitude, settings.longitude,
                    isoDate.format(startCal.time), isoDate.format(endCal.time)
                ).daily
            }.getOrNull()
        }

        val result = mutableListOf<DayForecast>()
        val labelCal = Calendar.getInstance().apply { time = windowStart }
        for (offset in 0 until remaining) {
            val maxTemps = mutableListOf<Double>()
            val minTemps = mutableListOf<Double>()
            val codes = mutableListOf<Int>()
            perYearResults.forEach { daily ->
                daily?.temperature_2m_max?.getOrNull(offset)?.let { maxTemps += it }
                daily?.temperature_2m_min?.getOrNull(offset)?.let { minTemps += it }
                daily?.weather_code?.getOrNull(offset)?.let { codes += it }
            }
            val avgMax = maxTemps.takeIf { it.isNotEmpty() }?.average()
            val avgMin = minTemps.takeIf { it.isNotEmpty() }?.average()
            val commonCode = codes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

            val theDate = labelCal.time
            result += DayForecast(
                dayLabel = dayFormat.format(theDate),
                dateLabel = dateFormat.format(theDate),
                minTemp = avgMin,
                maxTemp = avgMax,
                description = if (commonCode != null) WeatherCodeMapper.describe(commonCode) else "No data",
                conditionGroup = WeatherCodeMapper.groupOf(commonCode),
                isOutlookEstimate = true
            )
            labelCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun formatClockTime(isoDateTime: String): String {
        // Open-Meteo returns e.g. "2026-08-16T06:12"
        val timePart = isoDateTime.substringAfter('T', "")
        return timePart.ifBlank { isoDateTime }
    }
}
