package com.weatherfocus.app.data.model

import com.google.gson.annotations.SerializedName

/* ================= Open-Meteo DTOs (free, no API key) ================= */

data class OpenMeteoForecastResponse(
    val current: OpenMeteoCurrent?,
    val hourly: OpenMeteoHourly?,
    val daily: OpenMeteoDaily?
)

data class OpenMeteoCurrent(
    val temperature_2m: Double?,
    val apparent_temperature: Double?,
    val relative_humidity_2m: Int?,
    val wind_speed_10m: Double?,
    val surface_pressure: Double?,
    val weather_code: Int?,
    val is_day: Int?
)

data class OpenMeteoHourly(
    val time: List<String>?,
    val temperature_2m: List<Double?>?,
    val precipitation_probability: List<Int?>?,
    val weather_code: List<Int?>?,
    val wind_speed_10m: List<Double?>?,
    val uv_index: List<Double?>?,
    val cloud_cover: List<Int?>? = null,
    val wind_direction_10m: List<Int?>? = null,
    val dew_point_2m: List<Double?>? = null,
    // Winter / Nordic alert inputs (all optional - missing lists just mean those rules can't match)
    val apparent_temperature: List<Double?>? = null,
    val relative_humidity_2m: List<Int?>? = null,
    val rain: List<Double?>? = null,
    /** Snowfall in cm during the hour. */
    val snowfall: List<Double?>? = null,
    /** Visibility in metres. */
    val visibility: List<Double?>? = null,
    val wind_gusts_10m: List<Double?>? = null,
    /** Snow depth on the ground in metres. */
    val snow_depth: List<Double?>? = null
)

data class OpenMeteoDaily(
    val time: List<String>?,
    val weather_code: List<Int?>?,
    val temperature_2m_max: List<Double?>?,
    val temperature_2m_min: List<Double?>?,
    val precipitation_probability_max: List<Int?>?,
    val wind_speed_10m_max: List<Double?>?,
    val uv_index_max: List<Double?>?,
    val sunrise: List<String>?,
    val sunset: List<String>?,
    val apparent_temperature_max: List<Double?>? = null,
    val apparent_temperature_min: List<Double?>? = null,
    /** Total precipitation (rain + melted snow), mm. */
    val precipitation_sum: List<Double?>? = null,
    /** Snowfall, cm. */
    val snowfall_sum: List<Double?>? = null,
    val wind_gusts_10m_max: List<Double?>? = null,
    /** Daylight length in seconds. */
    val daylight_duration: List<Double?>? = null
)

/** Open-Meteo Flood API (GloFAS river discharge, m3/s, daily). */
data class FloodResponse(val daily: FloodDaily?)
data class FloodDaily(val time: List<String>?, val river_discharge: List<Double?>?)

/** What the flood-risk rule needs beyond the hourly forecast: rain that already fell and how the nearby river is trending. */
data class FloodContext(
    /** Rain + melted snow (mm) over the last 5 days including today so far. */
    val rainPast5dMm: Double = 0.0,
    val riverNowM3s: Double? = null,
    /** Highest forecast discharge over the next 7 days. */
    val riverPeakM3s: Double? = null,
    /** Average discharge over the last 30 days. */
    val riverMeanM3s: Double? = null
)

/** Open-Meteo geocoding (city search-as-you-type), also free / no key. */
data class OpenMeteoGeocodingResponse(val results: List<OpenMeteoGeoResult>?)

data class OpenMeteoGeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val country_code: String?,
    val admin1: String?
)

/**
 * Response shape when Open-Meteo is asked for several models at once (see [com.weatherfocus.app.data.remote.OpenMeteoApi.getMultiModel]).
 * Every field is suffixed with the model name by Open-Meteo itself; each becomes an independent [SourceReading].
 */
data class OpenMeteoMultiModelResponse(val hourly: OpenMeteoMultiModelHourly?)

data class OpenMeteoMultiModelHourly(
    val time: List<String>?,
    @SerializedName("temperature_2m_gfs_seamless") val tempGfs: List<Double?>?,
    @SerializedName("weather_code_gfs_seamless") val codeGfs: List<Int?>?,
    @SerializedName("temperature_2m_ecmwf_ifs025") val tempEcmwf: List<Double?>?,
    @SerializedName("weather_code_ecmwf_ifs025") val codeEcmwf: List<Int?>?,
    @SerializedName("temperature_2m_icon_seamless") val tempIcon: List<Double?>?,
    @SerializedName("weather_code_icon_seamless") val codeIcon: List<Int?>?,
    @SerializedName("temperature_2m_ukmo_seamless") val tempUkmo: List<Double?>?,
    @SerializedName("weather_code_ukmo_seamless") val codeUkmo: List<Int?>?,
    @SerializedName("temperature_2m_jma_seamless") val tempJma: List<Double?>?,
    @SerializedName("weather_code_jma_seamless") val codeJma: List<Int?>?,
    @SerializedName("temperature_2m_gem_seamless") val tempGem: List<Double?>?,
    @SerializedName("weather_code_gem_seamless") val codeGem: List<Int?>?
)

/* ================= wttr.in DTO (free, no API key, used as 2nd source) ================= */

data class WttrResponse(
    val current_condition: List<WttrCurrent>?,
    val weather: List<WttrDay>?
)

data class WttrCurrent(
    val temp_C: String?,
    val FeelsLikeC: String?,
    val humidity: String?,
    val windspeedKmph: String?,
    val pressure: String?,
    val weatherDesc: List<WttrDesc>?
)

data class WttrDesc(val value: String?)

data class WttrDay(
    val date: String?,
    val maxtempC: String?,
    val mintempC: String?,
    val hourly: List<WttrHourly>?
)

data class WttrHourly(val chanceofrain: String?, val weatherDesc: List<WttrDesc>?)

/* ================= OpenWeather DTO (optional 4th source, needs user API key) ================= */

data class OpenWeatherCurrentResponse(
    val main: OwMain?,
    val wind: OwWind?,
    val weather: List<OwDesc>?
)

data class OwMain(val temp: Double?, val feels_like: Double?, val humidity: Int?, val pressure: Int?)
data class OwWind(val speed: Double?)
data class OwDesc(val description: String?, val main: String?)

/* ================= MET Norway / Yr.no DTO (free, no API key, used as an independent source) ================= */

data class MetNoResponse(val properties: MetNoProperties?)
data class MetNoProperties(val timeseries: List<MetNoTimeseries>?)
data class MetNoTimeseries(val time: String?, val data: MetNoData?)
data class MetNoData(val instant: MetNoInstant?, val next_1_hours: MetNoNextHours?, val next_6_hours: MetNoNextHours? = null)
data class MetNoInstant(val details: MetNoDetails?)
data class MetNoDetails(
    val air_temperature: Double?,
    val wind_speed: Double?,
    val relative_humidity: Double?,
    val air_pressure_at_sea_level: Double?,
    val wind_speed_of_gust: Double? = null,
    val wind_from_direction: Double? = null,
    val dew_point_temperature: Double? = null,
    val cloud_area_fraction: Double? = null,
    val fog_area_fraction: Double? = null,
    val ultraviolet_index_clear_sky: Double? = null
)
data class MetNoNextHours(val summary: MetNoSummary?, val details: MetNoPeriodDetails? = null)
data class MetNoPeriodDetails(
    val precipitation_amount: Double? = null,
    val probability_of_precipitation: Double? = null,
    val probability_of_thunder: Double? = null
)
data class MetNoSummary(val symbol_code: String?)

/* ================= 7Timer! DTO (free, no API key, used as an independent source) ================= */

data class SevenTimerResponse(val dataseries: List<SevenTimerPoint>?)
data class SevenTimerPoint(
    val temp2m: Int?,
    val weather: String?,
    val rh2m: String?,
    val wind10m: SevenTimerWind?
)
data class SevenTimerWind(val speed: Int?)

/* ================= Clean, UI-ready domain models (mirrors the original app's shape) ================= */

/** One source's reading of current conditions, used to build [CurrentWeather.consensus]. */
data class SourceReading(
    val sourceName: String,
    val tempC: Double?,
    val conditionGroup: ConditionGroup
)

enum class ConditionGroup { CLEAR, CLOUDY, RAIN, SNOW, THUNDER, FOG, UNKNOWN }

data class CurrentWeather(
    val temp: Double? = null,
    val feelsLike: Double? = null,
    val humidity: Int? = null,
    val windSpeedKmh: Double? = null,
    val pressure: Int? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val description: String? = null,
    val conditionGroup: ConditionGroup = ConditionGroup.UNKNOWN,
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val available: Boolean = false,
    /** How many/which sources were combined, and how well they agreed. */
    val sourcesUsed: List<String> = emptyList(),
    val sourcesAgreeing: Int = 0,
    val sourcesTotal: Int = 0
)

/** One day in the "Next 3 Days" strip. */
data class DayForecast(
    val dayLabel: String,
    val dateLabel: String,
    val minTemp: Double? = null,
    val maxTemp: Double? = null,
    val description: String = "",
    val conditionGroup: ConditionGroup = ConditionGroup.UNKNOWN,
    val rainChancePercent: Int? = null,
    val windSpeedKmh: Double? = null,
    val uvIndexMax: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val feelsMin: Double? = null,
    val feelsMax: Double? = null,
    /** Total precipitation, mm. */
    val precipMm: Double? = null,
    /** Snowfall, cm. */
    val snowCm: Double? = null,
    val gustKmh: Double? = null,
    val daylightHours: Double? = null,
    /** True for the ~14 tail days beyond Open-Meteo's 16-day real forecast, built from historical averages instead. */
    val isOutlookEstimate: Boolean = false
)

/** A morning/afternoon/evening slice of a single day, shown under the Today card and the Tomorrow section. */
data class DayPart(
    val label: String,
    val temp: Double? = null,
    val conditionGroup: ConditionGroup = ConditionGroup.UNKNOWN,
    val rainChancePercent: Int? = null
)

data class GeoPlace(
    val name: String,
    val admin1: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double
) {
    val displayLabel: String
        get() = listOfNotNull(name, admin1, country).joinToString(", ")
}

/* ================= Custom alert rules & matches (same shape/behaviour as the original app) ================= */

/** Yellow = good to know (silent notification), orange = take care, red = dangerous (loud, long vibration). */
enum class AlertSeverity { YELLOW, ORANGE, RED }

enum class AlertRuleType {
    TEMP_ABOVE, TEMP_BELOW, UV_INDEX, WIND_SPEED, RAIN_PROBABILITY, THUNDERSTORM, SNOW,
    BLACK_ICE, FREEZING_RAIN, FROST_ARRIVES, HEAVY_SNOW, SNOWSTORM, WIND_GUST,
    WIND_CHILL, LOW_VISIBILITY, RAPID_TEMP_DROP,
    CAR_ICE, CAR_SNOW, ROAD_ICE, FLOOD, OFFICIAL
}

data class CustomAlertRules(
    val enabled: Boolean = false,
    val horizonHours: Int = 24,
    val leadTimeHours: Int = 3,
    val tempAboveEnabled: Boolean = false,
    val tempAboveValue: Double = 30.0,
    val tempBelowEnabled: Boolean = false,
    val tempBelowValue: Double = 0.0,
    val uvIndexEnabled: Boolean = false,
    val uvIndexValue: Double = 6.0,
    val windSpeedEnabled: Boolean = false,
    val windSpeedValue: Double = 40.0,
    val rainProbEnabled: Boolean = false,
    val rainProbValue: Int = 70,
    val thunderstormEnabled: Boolean = false,
    val snowEnabled: Boolean = false,
    // ---- Winter / Nordic rules - every one has its own user-editable value ----
    /** Black ice: wet weather at or below this temp (and not more than 4 degrees colder), or wet air refreezing through 0. */
    val blackIceEnabled: Boolean = false,
    val blackIceMaxTemp: Double = 1.0,
    /** Freezing rain: rain falling at or below this temp (freezing rain/drizzle codes always count). */
    val freezingRainEnabled: Boolean = false,
    val freezingRainMaxTemp: Double = 0.0,
    /** Frost arriving: temperature drops from above to at/below this value. */
    val frostArrivesEnabled: Boolean = false,
    val frostValue: Double = 0.0,
    /** Heavy snowfall, cm per hour at or above. */
    val heavySnowEnabled: Boolean = false,
    val heavySnowValue: Double = 2.0,
    /** Snowstorm: snow together with wind (km/h) at or above. */
    val snowstormEnabled: Boolean = false,
    val snowstormWindValue: Double = 40.0,
    /** Wind gusts (km/h) at or above. */
    val windGustEnabled: Boolean = false,
    val windGustValue: Double = 70.0,
    /** Wind chill / "feels like" (°C) at or below. */
    val windChillEnabled: Boolean = false,
    val windChillValue: Double = -15.0,
    /** Visibility (metres) at or below. */
    val lowVisibilityEnabled: Boolean = false,
    val lowVisibilityValue: Double = 1000.0,
    /** Temperature falls by at least this many °C within 6 hours. */
    val rapidDropEnabled: Boolean = false,
    val rapidDropValue: Double = 8.0,
    // ---- Car & road ----
    /** Ice/frost on the windscreen the coming morning: warn when the chance is at or above this percentage. */
    val carIceEnabled: Boolean = false,
    val carIceChance: Int = 50,
    /** Snow settling on the car overnight (cm, evening until morning). */
    val carSnowEnabled: Boolean = false,
    val carSnowValue: Double = 0.5,
    /** Ice on the road: it rained / was wet in the last 6 h and the air is now at or below this temperature. */
    val roadIceEnabled: Boolean = false,
    val roadIceTemp: Double = -3.0,
    /** Flood risk: rain over the last 5 days + the next 48 h at or above this many mm (or a clearly rising river). */
    val floodEnabled: Boolean = false,
    val floodMm: Double = 60.0,
    /** Also show warnings issued by MET Norway (farevarsel) for your location. */
    val officialEnabled: Boolean = true
) {
    companion object {
        /** "Apply default settings": every alert on, with sensible numbers, alerts delivered well in advance. */
        fun defaults() = CustomAlertRules(
            enabled = true, horizonHours = 48, leadTimeHours = 12,
            tempAboveEnabled = true, tempAboveValue = 30.0,
            tempBelowEnabled = true, tempBelowValue = -15.0,
            uvIndexEnabled = true, uvIndexValue = 6.0,
            // one "strong wind" alert: sustained 14 m/s (50.4 km/h) or gusts 20 m/s (72 km/h)
            windSpeedEnabled = true, windSpeedValue = 50.4, windGustValue = 72.0,
            rainProbEnabled = true, rainProbValue = 70,
            thunderstormEnabled = true,
            blackIceEnabled = true, blackIceMaxTemp = 1.0,
            freezingRainEnabled = true, freezingRainMaxTemp = 0.0,
            heavySnowEnabled = true, heavySnowValue = 2.0,
            snowstormEnabled = true, snowstormWindValue = 39.6,
            windChillEnabled = true, windChillValue = -15.0,
            lowVisibilityEnabled = true, lowVisibilityValue = 1000.0,
            rapidDropEnabled = true, rapidDropValue = 8.0,
            carIceEnabled = true, carIceChance = 50,
            carSnowEnabled = true, carSnowValue = 0.5,
            floodEnabled = true, floodMm = 60.0, officialEnabled = true
        )
    }
}

data class CustomAlertMatch(
    val type: AlertRuleType = AlertRuleType.TEMP_ABOVE,
    val label: String = "",
    val detail: String = "",
    val dayLabel: String = "",
    val triggerAtMillis: Long = 0L,
    val leadWarning: Boolean = false,
    val severity: AlertSeverity = defaultSeverity(type),
    /** True for warnings issued by MET Norway (as opposed to the user's own rules). */
    val official: Boolean = false
)

fun defaultSeverity(type: AlertRuleType): AlertSeverity = when (type) {
    AlertRuleType.FREEZING_RAIN, AlertRuleType.SNOWSTORM -> AlertSeverity.RED
    AlertRuleType.WIND_SPEED, AlertRuleType.WIND_GUST, AlertRuleType.THUNDERSTORM, AlertRuleType.BLACK_ICE,
    AlertRuleType.HEAVY_SNOW, AlertRuleType.WIND_CHILL, AlertRuleType.LOW_VISIBILITY, AlertRuleType.ROAD_ICE,
    AlertRuleType.FLOOD, AlertRuleType.OFFICIAL -> AlertSeverity.ORANGE
    else -> AlertSeverity.YELLOW
}

/** User-selectable app appearance. SYSTEM follows the phone's system-wide dark/light setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A simple daily quiet-hours window during which weather-alert notifications are suppressed. Supports overnight ranges (e.g. 22 -> 7). */
data class DoNotDisturbSettings(
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val endHour: Int = 7
) {
    fun isActiveAt(hourOfDay: Int): Boolean {
        if (!enabled) return false
        return if (startHour == endHour) {
            true // a zero-width window is treated as "always on" once enabled
        } else if (startHour < endHour) {
            hourOfDay in startHour until endHour
        } else {
            // overnight window, e.g. 22 -> 7
            hourOfDay >= startHour || hourOfDay < endHour
        }
    }
}

/** How the widget is coloured: by the weather in the next few hours, or a fixed dark / light look. */
enum class WidgetStyle { WEATHER, DARK, LIGHT, AUTO }

/** Sections of the tile picker in Settings. */
enum class TileGroup(val title: String) {
    ROAD("Car & road"),
    WIND("Wind & cold"),
    PRECIPITATION("Rain, snow & flood"),
    SKY("Visibility & night sky")
}

/**
 * The info tiles the user can pick for the widget (up to 16 are shown, 4 on smaller sizes).
 * Tiles with [hidesWhenEmpty] disappear from the widget when there is nothing to report
 * (e.g. 0% aurora chance), so they never waste space - unless the user turns that off.
 * The set is built around what matters in a Nordic winter: driving, ice, snow, wind chill, flooding and daylight.
 */
enum class WidgetTile(val label: String, val emoji: String, val group: TileGroup, val hidesWhenEmpty: Boolean = false) {
    // Car & road
    FROST_CAR("Ice on car (morning)", "\uD83D\uDE97", TileGroup.ROAD, hidesWhenEmpty = true),
    ROAD_ICE("Road ice (24h)", "\uD83D\uDEE3\uFE0F", TileGroup.ROAD, hidesWhenEmpty = true),
    VISIBILITY("Visibility (3h avg)", "\uD83C\uDF2B\uFE0F", TileGroup.ROAD),
    MORNING_FOG("Morning fog", "\uD83C\uDF01", TileGroup.ROAD, hidesWhenEmpty = true),

    // Wind & cold
    WIND("Wind (m/s) + gusts", "\uD83D\uDCA8", TileGroup.WIND),
    COLDEST("Coldest feels-like 24h", "\uD83E\uDD76", TileGroup.WIND),
    FROST_HOURS("Hours below 0\u00B0C", "\u2744\uFE0F", TileGroup.WIND, hidesWhenEmpty = true),

    // Rain, snow & flood
    SNOW_DEPTH("Snow on the ground", "\u26C4", TileGroup.PRECIPITATION, hidesWhenEmpty = true),
    SNOW("New snow ahead (cm)", "\uD83C\uDF28\uFE0F", TileGroup.PRECIPITATION, hidesWhenEmpty = true),
    NEXT_PRECIP("Next rain/snow", "\u2614", TileGroup.PRECIPITATION, hidesWhenEmpty = true),
    FLOOD("Flood watch (5d + 2d rain)", "\uD83C\uDF0A", TileGroup.PRECIPITATION, hidesWhenEmpty = true),

    // Night sky & light
    AURORA("Aurora (tonight)", "\uD83C\uDF0C", TileGroup.SKY, hidesWhenEmpty = true),
    DAYLIGHT_CHANGE("Daylight gained/lost", "\uD83C\uDF05", TileGroup.SKY),
    UV("UV index", "\uD83D\uDD76\uFE0F", TileGroup.SKY, hidesWhenEmpty = true)
}

/** One 3-hour planetary Kp-index value from NOAA's forecast (drives the aurora tile). */
data class KpPoint(val startMillis: Long, val kp: Double)

/** What the weather was on this calendar date one year ago at the user's location. */
data class LastYearDay(val maxTemp: Double, val minTemp: Double, val weatherCode: Int?)

/** Everything the user can adjust about the home-screen widgets. */
data class WidgetSettings(
    val style: WidgetStyle = WidgetStyle.WEATHER,
    /** In WEATHER style, the widget colour follows the worst weather in the next N hours (0 = just now). */
    val lookaheadHours: Int = 6,
    val showAlert: Boolean = true,
    val showHours: Boolean = true,
    val showTiles: Boolean = true,
    val showDays: Boolean = true,
    val showSun: Boolean = true,
    /** "What to wear today" icons (jacket, scarf, umbrella...) in the header of the big widget. */
    val showWear: Boolean = true,
    /** Tiles flagged hidesWhenEmpty vanish when there is nothing to show. */
    val hideEmptyTiles: Boolean = true,
    val hoursCount: Int = 6,
    val daysCount: Int = 5,
    /** The snow tile adds up forecast snowfall over the next N hours (1..12). */
    val snowHours: Int = 12,
    /** Tip line "cover the windscreen tonight" when frost on the car is likely tomorrow morning. */
    val showCarTip: Boolean = true,
    val tiles: List<WidgetTile> = listOf(
        WidgetTile.FROST_CAR, WidgetTile.ROAD_ICE, WidgetTile.WIND, WidgetTile.COLDEST,
        WidgetTile.SNOW_DEPTH, WidgetTile.SNOW, WidgetTile.NEXT_PRECIP, WidgetTile.FLOOD,
        WidgetTile.MORNING_FOG, WidgetTile.AURORA, WidgetTile.DAYLIGHT_CHANGE
    )
)

data class AppSettings(
    val cityName: String = "Vikersund",
    val countryCode: String = "NO",
    val countryLabel: String = "Norway",
    val latitude: Double = 60.35,
    val longitude: Double = 10.03,
    val useFahrenheit: Boolean = false,
    val openWeatherApiKey: String = "",
    val customAlertRules: CustomAlertRules = CustomAlertRules(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val doNotDisturb: DoNotDisturbSettings = DoNotDisturbSettings(),
    val widget: WidgetSettings = WidgetSettings()
)
