package com.weatherfocus.app.data.remote

import com.google.gson.JsonElement
import com.weatherfocus.app.data.model.MetNoResponse
import com.weatherfocus.app.data.model.OpenMeteoForecastResponse
import com.weatherfocus.app.data.model.OpenMeteoGeocodingResponse
import com.weatherfocus.app.data.model.OpenMeteoMultiModelResponse
import com.weatherfocus.app.data.model.OpenWeatherCurrentResponse
import com.weatherfocus.app.data.model.SevenTimerResponse
import com.weatherfocus.app.data.model.WttrResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/** Free, no API key required. Primary source: current + hourly (48h, for custom alerts) + daily (16 days). */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,surface_pressure,weather_code,is_day",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,weather_code,wind_speed_10m,uv_index,apparent_temperature,relative_humidity_2m,rain,snowfall,visibility,wind_gusts_10m,cloud_cover,wind_direction_10m,dew_point_2m,snow_depth",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,uv_index_max,sunrise,sunset,apparent_temperature_max,apparent_temperature_min,precipitation_sum,snowfall_sum,wind_gusts_10m_max,daylight_duration",
        @Query("forecast_days") forecastDays: Int = 16,
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoForecastResponse

    /** Rain that already fell: daily precipitation for the last [pastDays] days plus today. */
    @GET("v1/forecast")
    suspend fun getRainHistory(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "precipitation_sum",
        @Query("past_days") pastDays: Int = 7,
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoForecastResponse

    /** Historical daily conditions for a past date range at this location - averaged to build the "long-range outlook" tail of the month view. */
    @GET("v1/archive")
    suspend fun getArchive(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoForecastResponse

    /**
     * Same endpoint, but asking for several independent global weather models at once (NOAA GFS, ECMWF,
     * DWD ICON, UK Met Office, JMA, Environment Canada GEM). Open-Meteo suffixes every field with the
     * model name when multiple models are requested, so each becomes its own independent reading -
     * this is how the app reaches ~10 sources total without needing separate accounts/keys for each.
     */
    @GET("v1/forecast")
    suspend fun getMultiModel(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String = "temperature_2m,weather_code",
        @Query("models") models: String = "gfs_seamless,ecmwf_ifs025,icon_seamless,ukmo_seamless,jma_seamless,gem_seamless",
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoMultiModelResponse
}

/** Free, no API key required. City search-as-you-type autocomplete. */
interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): OpenMeteoGeocodingResponse
}

/** Free, no API key required. Used as a second independent source for cross-checking current conditions. */
interface WttrApi {
    @GET("{location}")
    suspend fun getWeather(
        @Path("location") location: String,
        @Query("format") format: String = "j1"
    ): WttrResponse
}

/** Optional fourth source - only used if the user supplies their own free OpenWeather API key in Settings. */
interface OpenWeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") cityAndCountry: String,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): OpenWeatherCurrentResponse
}

/** Free, no API key required. MET Norway's Locationforecast, also used to power Yr.no - used as an independent 4th/5th source. */
interface MetNoApi {
    @Headers("User-Agent: WeatherFocusApp/1.0 github.com/weatherfocus-app")
    @GET("weatherapi/locationforecast/2.0/complete")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): MetNoResponse

    /** Official warnings (farevarsel) valid for this point. Norway only; returns an empty list elsewhere. */
    @Headers("User-Agent: WeatherFocusApp/1.0 github.com/weatherfocus-app")
    @GET("weatherapi/metalerts/2.0/current.json")
    suspend fun getAlerts(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("lang") lang: String = "en"
    ): JsonElement
}

/** Free, no API key required. 7Timer! civil forecast product - used as an independent 5th source. */
interface SevenTimerApi {
    @GET("bin/api.pl")
    suspend fun getForecast(
        @Query("lon") lon: Double,
        @Query("lat") lat: Double,
        @Query("product") product: String = "civil",
        @Query("output") output: String = "json"
    ): SevenTimerResponse
}

/** Free, no API key. NOAA Space Weather Prediction Center: 3-day planetary Kp forecast (aurora activity). */
interface NoaaSwpcApi {
    @GET("products/noaa-planetary-k-index-forecast.json")
    suspend fun getKpForecast(): JsonElement
}

/** Free, no key. GloFAS river discharge for the largest river near the location. */
interface FloodApi {
    @GET("v1/flood")
    suspend fun getDischarge(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "river_discharge",
        @Query("past_days") pastDays: Int = 30,
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "GMT"
    ): com.weatherfocus.app.data.model.FloodResponse
}
