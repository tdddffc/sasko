package com.weatherfocus.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weatherfocus.app.data.model.AppSettings
import com.weatherfocus.app.data.model.CustomAlertRules
import com.weatherfocus.app.data.model.parseRules
import com.weatherfocus.app.data.model.rulesToJson
import com.weatherfocus.app.data.model.DoNotDisturbSettings
import com.weatherfocus.app.data.model.ThemeMode
import com.weatherfocus.app.data.model.WidgetSettings
import com.weatherfocus.app.data.model.WidgetStyle
import com.weatherfocus.app.data.model.WidgetTile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "weather_only_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CITY = stringPreferencesKey("city_name")
        val COUNTRY_CODE = stringPreferencesKey("country_code")
        val COUNTRY_LABEL = stringPreferencesKey("country_label")
        val LAT = doublePreferencesKey("latitude")
        val LON = doublePreferencesKey("longitude")
        val FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val OW_KEY = stringPreferencesKey("openweather_api_key")

        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val HORIZON_HOURS = intPreferencesKey("horizon_hours")
        val LEAD_HOURS = intPreferencesKey("lead_hours")
        val TEMP_ABOVE_ON = booleanPreferencesKey("temp_above_on")
        val TEMP_ABOVE_VAL = doublePreferencesKey("temp_above_val")
        val TEMP_BELOW_ON = booleanPreferencesKey("temp_below_on")
        val TEMP_BELOW_VAL = doublePreferencesKey("temp_below_val")
        val UV_ON = booleanPreferencesKey("uv_on")
        val UV_VAL = doublePreferencesKey("uv_val")
        val WIND_ON = booleanPreferencesKey("wind_on")
        val WIND_VAL = doublePreferencesKey("wind_val")
        val RAIN_ON = booleanPreferencesKey("rain_on")
        val RAIN_VAL = intPreferencesKey("rain_val")
        val THUNDER_ON = booleanPreferencesKey("thunder_on")
        val SNOW_ON = booleanPreferencesKey("snow_on")
        val BLACK_ICE_ON = booleanPreferencesKey("black_ice_on")
        val BLACK_ICE_VAL = doublePreferencesKey("black_ice_val")
        val FREEZING_RAIN_ON = booleanPreferencesKey("freezing_rain_on")
        val FREEZING_RAIN_VAL = doublePreferencesKey("freezing_rain_val")
        val FROST_ON = booleanPreferencesKey("frost_on")
        val FROST_VAL = doublePreferencesKey("frost_val")
        val HEAVY_SNOW_ON = booleanPreferencesKey("heavy_snow_on")
        val HEAVY_SNOW_VAL = doublePreferencesKey("heavy_snow_val")
        val SNOWSTORM_ON = booleanPreferencesKey("snowstorm_on")
        val SNOWSTORM_VAL = doublePreferencesKey("snowstorm_val")
        val GUST_ON = booleanPreferencesKey("gust_on")
        val GUST_VAL = doublePreferencesKey("gust_val")
        val CHILL_ON = booleanPreferencesKey("chill_on")
        val CHILL_VAL = doublePreferencesKey("chill_val")
        val VIS_ON = booleanPreferencesKey("vis_on")
        val VIS_VAL = doublePreferencesKey("vis_val")
        val DROP_ON = booleanPreferencesKey("drop_on")
        val DROP_VAL = doublePreferencesKey("drop_val")
        /** All alert rules as one JSON document: new rules can never be "forgotten" by a missing key again. */
        val RULES_JSON = stringPreferencesKey("alert_rules_json")
        val FLOOD_ON = booleanPreferencesKey("flood_on")
        val FLOOD_VAL = doublePreferencesKey("flood_val")
        val CAR_ICE_ON = booleanPreferencesKey("car_ice_on")
        val CAR_ICE_VAL = intPreferencesKey("car_ice_val")
        val CAR_SNOW_ON = booleanPreferencesKey("car_snow_on")
        val CAR_SNOW_VAL = doublePreferencesKey("car_snow_val")
        val ROAD_ICE_ON = booleanPreferencesKey("road_ice_on")
        val ROAD_ICE_VAL = doublePreferencesKey("road_ice_val")

        val W_STYLE = stringPreferencesKey("widget_style")
        val W_LOOKAHEAD = intPreferencesKey("widget_lookahead")
        val W_ALERT = booleanPreferencesKey("widget_show_alert")
        val W_HOURS = booleanPreferencesKey("widget_show_hours")
        val W_TILES = booleanPreferencesKey("widget_show_tiles")
        val W_DAYS = booleanPreferencesKey("widget_show_days")
        val W_SUN = booleanPreferencesKey("widget_show_sun")
        val W_HOURS_N = intPreferencesKey("widget_hours_count")
        val W_DAYS_N = intPreferencesKey("widget_days_count")
        val W_TILE_LIST = stringPreferencesKey("widget_tile_list_v2")
        val W_WEAR = booleanPreferencesKey("widget_show_wear")
        val W_HIDE_EMPTY = booleanPreferencesKey("widget_hide_empty_tiles")
        val W_SNOW_H = intPreferencesKey("widget_snow_hours")
        val W_CAR_TIP = booleanPreferencesKey("widget_car_tip")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DND_ENABLED = booleanPreferencesKey("dnd_enabled")
        val DND_START_HOUR = intPreferencesKey("dnd_start_hour")
        val DND_END_HOUR = intPreferencesKey("dnd_end_hour")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            cityName = p[Keys.CITY] ?: "Vikersund",
            countryCode = p[Keys.COUNTRY_CODE] ?: "NO",
            countryLabel = p[Keys.COUNTRY_LABEL] ?: "Norway",
            latitude = p[Keys.LAT] ?: 60.35,
            longitude = p[Keys.LON] ?: 10.03,
            useFahrenheit = p[Keys.FAHRENHEIT] ?: false,
            openWeatherApiKey = p[Keys.OW_KEY] ?: "",
            customAlertRules = p[Keys.RULES_JSON]?.let { parseRules(it) } ?: CustomAlertRules(
                enabled = p[Keys.ALERTS_ENABLED] ?: false,
                horizonHours = p[Keys.HORIZON_HOURS] ?: 24,
                leadTimeHours = p[Keys.LEAD_HOURS] ?: 3,
                tempAboveEnabled = p[Keys.TEMP_ABOVE_ON] ?: false,
                tempAboveValue = p[Keys.TEMP_ABOVE_VAL] ?: 30.0,
                tempBelowEnabled = p[Keys.TEMP_BELOW_ON] ?: false,
                tempBelowValue = p[Keys.TEMP_BELOW_VAL] ?: 0.0,
                uvIndexEnabled = p[Keys.UV_ON] ?: false,
                uvIndexValue = p[Keys.UV_VAL] ?: 6.0,
                windSpeedEnabled = p[Keys.WIND_ON] ?: false,
                windSpeedValue = p[Keys.WIND_VAL] ?: 40.0,
                rainProbEnabled = p[Keys.RAIN_ON] ?: false,
                rainProbValue = p[Keys.RAIN_VAL] ?: 70,
                thunderstormEnabled = p[Keys.THUNDER_ON] ?: false,
                snowEnabled = p[Keys.SNOW_ON] ?: false,
                blackIceEnabled = p[Keys.BLACK_ICE_ON] ?: false,
                blackIceMaxTemp = p[Keys.BLACK_ICE_VAL] ?: 1.0,
                freezingRainEnabled = p[Keys.FREEZING_RAIN_ON] ?: false,
                freezingRainMaxTemp = p[Keys.FREEZING_RAIN_VAL] ?: 0.0,
                frostArrivesEnabled = p[Keys.FROST_ON] ?: false,
                frostValue = p[Keys.FROST_VAL] ?: 0.0,
                heavySnowEnabled = p[Keys.HEAVY_SNOW_ON] ?: false,
                heavySnowValue = p[Keys.HEAVY_SNOW_VAL] ?: 2.0,
                snowstormEnabled = p[Keys.SNOWSTORM_ON] ?: false,
                snowstormWindValue = p[Keys.SNOWSTORM_VAL] ?: 40.0,
                windGustEnabled = p[Keys.GUST_ON] ?: false,
                windGustValue = p[Keys.GUST_VAL] ?: 70.0,
                windChillEnabled = p[Keys.CHILL_ON] ?: false,
                windChillValue = p[Keys.CHILL_VAL] ?: -15.0,
                lowVisibilityEnabled = p[Keys.VIS_ON] ?: false,
                lowVisibilityValue = p[Keys.VIS_VAL] ?: 1000.0,
                rapidDropEnabled = p[Keys.DROP_ON] ?: false,
                rapidDropValue = p[Keys.DROP_VAL] ?: 8.0,
                carIceEnabled = p[Keys.CAR_ICE_ON] ?: false,
                carIceChance = p[Keys.CAR_ICE_VAL] ?: 50,
                carSnowEnabled = p[Keys.CAR_SNOW_ON] ?: false,
                carSnowValue = p[Keys.CAR_SNOW_VAL] ?: 0.5,
                roadIceEnabled = p[Keys.ROAD_ICE_ON] ?: false,
                roadIceTemp = p[Keys.ROAD_ICE_VAL] ?: -3.0,
                floodEnabled = p[Keys.FLOOD_ON] ?: false,
                floodMm = p[Keys.FLOOD_VAL] ?: 60.0
            ),
            widget = WidgetSettings(
                style = runCatching { WidgetStyle.valueOf(p[Keys.W_STYLE] ?: "WEATHER") }.getOrDefault(WidgetStyle.WEATHER),
                lookaheadHours = p[Keys.W_LOOKAHEAD] ?: 6,
                showAlert = p[Keys.W_ALERT] ?: true,
                showHours = p[Keys.W_HOURS] ?: true,
                showTiles = p[Keys.W_TILES] ?: true,
                showDays = p[Keys.W_DAYS] ?: true,
                showSun = p[Keys.W_SUN] ?: true,
                showWear = p[Keys.W_WEAR] ?: true,
                hideEmptyTiles = p[Keys.W_HIDE_EMPTY] ?: true,
                hoursCount = p[Keys.W_HOURS_N] ?: 6,
                daysCount = p[Keys.W_DAYS_N] ?: 5,
                snowHours = (p[Keys.W_SNOW_H] ?: 12).coerceIn(1, 12),
                showCarTip = p[Keys.W_CAR_TIP] ?: true,
                tiles = p[Keys.W_TILE_LIST]?.split(",")?.mapNotNull { n -> runCatching { WidgetTile.valueOf(n) }.getOrNull() }
                    ?: WidgetSettings().tiles
            ),
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
            doNotDisturb = DoNotDisturbSettings(
                enabled = p[Keys.DND_ENABLED] ?: false,
                startHour = p[Keys.DND_START_HOUR] ?: 22,
                endHour = p[Keys.DND_END_HOUR] ?: 7
            )
        )
    }

    suspend fun setLocation(city: String, countryCode: String, countryLabel: String, lat: Double, lon: Double) {
        context.dataStore.edit {
            it[Keys.CITY] = city
            it[Keys.COUNTRY_CODE] = countryCode
            it[Keys.COUNTRY_LABEL] = countryLabel
            it[Keys.LAT] = lat
            it[Keys.LON] = lon
        }
    }

    suspend fun setFahrenheit(value: Boolean) = context.dataStore.edit { it[Keys.FAHRENHEIT] = value }
    suspend fun setOpenWeatherApiKey(value: String) = context.dataStore.edit { it[Keys.OW_KEY] = value }

    suspend fun updateWidgetSettings(ws: WidgetSettings) {
        context.dataStore.edit {
            it[Keys.W_STYLE] = ws.style.name
            it[Keys.W_LOOKAHEAD] = ws.lookaheadHours
            it[Keys.W_ALERT] = ws.showAlert
            it[Keys.W_HOURS] = ws.showHours
            it[Keys.W_TILES] = ws.showTiles
            it[Keys.W_DAYS] = ws.showDays
            it[Keys.W_SUN] = ws.showSun
            it[Keys.W_WEAR] = ws.showWear
            it[Keys.W_HIDE_EMPTY] = ws.hideEmptyTiles
            it[Keys.W_HOURS_N] = ws.hoursCount
            it[Keys.W_DAYS_N] = ws.daysCount
            it[Keys.W_SNOW_H] = ws.snowHours.coerceIn(1, 12)
            it[Keys.W_CAR_TIP] = ws.showCarTip
            it[Keys.W_TILE_LIST] = ws.tiles.joinToString(",") { t -> t.name }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDoNotDisturb(settings: DoNotDisturbSettings) {
        context.dataStore.edit {
            it[Keys.DND_ENABLED] = settings.enabled
            it[Keys.DND_START_HOUR] = settings.startHour
            it[Keys.DND_END_HOUR] = settings.endHour
        }
    }

    suspend fun updateAlertRules(rules: CustomAlertRules) {
        context.dataStore.edit {
            it[Keys.RULES_JSON] = rulesToJson(rules)
            it[Keys.ALERTS_ENABLED] = rules.enabled
            it[Keys.HORIZON_HOURS] = rules.horizonHours
            it[Keys.LEAD_HOURS] = rules.leadTimeHours
            it[Keys.TEMP_ABOVE_ON] = rules.tempAboveEnabled
            it[Keys.TEMP_ABOVE_VAL] = rules.tempAboveValue
            it[Keys.TEMP_BELOW_ON] = rules.tempBelowEnabled
            it[Keys.TEMP_BELOW_VAL] = rules.tempBelowValue
            it[Keys.UV_ON] = rules.uvIndexEnabled
            it[Keys.UV_VAL] = rules.uvIndexValue
            it[Keys.WIND_ON] = rules.windSpeedEnabled
            it[Keys.WIND_VAL] = rules.windSpeedValue
            it[Keys.RAIN_ON] = rules.rainProbEnabled
            it[Keys.RAIN_VAL] = rules.rainProbValue
            it[Keys.THUNDER_ON] = rules.thunderstormEnabled
            it[Keys.SNOW_ON] = rules.snowEnabled
            it[Keys.BLACK_ICE_ON] = rules.blackIceEnabled
            it[Keys.BLACK_ICE_VAL] = rules.blackIceMaxTemp
            it[Keys.FREEZING_RAIN_ON] = rules.freezingRainEnabled
            it[Keys.FREEZING_RAIN_VAL] = rules.freezingRainMaxTemp
            it[Keys.FROST_ON] = rules.frostArrivesEnabled
            it[Keys.FROST_VAL] = rules.frostValue
            it[Keys.HEAVY_SNOW_ON] = rules.heavySnowEnabled
            it[Keys.HEAVY_SNOW_VAL] = rules.heavySnowValue
            it[Keys.SNOWSTORM_ON] = rules.snowstormEnabled
            it[Keys.SNOWSTORM_VAL] = rules.snowstormWindValue
            it[Keys.GUST_ON] = rules.windGustEnabled
            it[Keys.GUST_VAL] = rules.windGustValue
            it[Keys.CHILL_ON] = rules.windChillEnabled
            it[Keys.CHILL_VAL] = rules.windChillValue
            it[Keys.VIS_ON] = rules.lowVisibilityEnabled
            it[Keys.VIS_VAL] = rules.lowVisibilityValue
            it[Keys.DROP_ON] = rules.rapidDropEnabled
            it[Keys.DROP_VAL] = rules.rapidDropValue
            it[Keys.CAR_ICE_ON] = rules.carIceEnabled
            it[Keys.CAR_ICE_VAL] = rules.carIceChance
            it[Keys.CAR_SNOW_ON] = rules.carSnowEnabled
            it[Keys.CAR_SNOW_VAL] = rules.carSnowValue
            it[Keys.ROAD_ICE_ON] = rules.roadIceEnabled
            it[Keys.ROAD_ICE_VAL] = rules.roadIceTemp
            it[Keys.FLOOD_ON] = rules.floodEnabled
            it[Keys.FLOOD_VAL] = rules.floodMm
        }
    }
}

/** Countries offered in the Settings location picker. */
object CountryCatalog {
    data class Entry(val code: String, val label: String)

    val ALL: List<Entry> = listOf(
        Entry("NO", "Norway"), Entry("SE", "Sweden"), Entry("DK", "Denmark"),
        Entry("GB", "United Kingdom"), Entry("US", "United States"), Entry("DE", "Germany"),
        Entry("FR", "France"), Entry("ES", "Spain"), Entry("IT", "Italy"),
        Entry("NL", "Netherlands"), Entry("PL", "Poland"), Entry("FI", "Finland"),
        Entry("PT", "Portugal"), Entry("IE", "Ireland"), Entry("BE", "Belgium"),
        Entry("AT", "Austria"), Entry("CH", "Switzerland"), Entry("CA", "Canada"),
        Entry("AU", "Australia"), Entry("IN", "India"), Entry("BR", "Brazil"),
        Entry("JP", "Japan"), Entry("GR", "Greece"), Entry("TR", "Turkey"),
        Entry("RS", "Serbia")
    )
}
