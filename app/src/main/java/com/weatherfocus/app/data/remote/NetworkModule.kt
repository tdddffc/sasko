package com.weatherfocus.app.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofitFor(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val openMeteoApi: OpenMeteoApi by lazy {
        retrofitFor("https://api.open-meteo.com/").create(OpenMeteoApi::class.java)
    }

    val openMeteoGeocodingApi: OpenMeteoGeocodingApi by lazy {
        retrofitFor("https://geocoding-api.open-meteo.com/").create(OpenMeteoGeocodingApi::class.java)
    }

    val floodApi: FloodApi by lazy {
        retrofitFor("https://flood-api.open-meteo.com/").create(FloodApi::class.java)
    }

    val wttrApi: WttrApi by lazy {
        retrofitFor("https://wttr.in/").create(WttrApi::class.java)
    }

    val openWeatherApi: OpenWeatherApi by lazy {
        retrofitFor("https://api.openweathermap.org/").create(OpenWeatherApi::class.java)
    }

    val metNoApi: MetNoApi by lazy {
        retrofitFor("https://api.met.no/").create(MetNoApi::class.java)
    }

    val noaaSwpcApi: NoaaSwpcApi by lazy {
        retrofitFor("https://services.swpc.noaa.gov/").create(NoaaSwpcApi::class.java)
    }

    val sevenTimerApi: SevenTimerApi by lazy {
        retrofitFor("https://www.7timer.info/").create(SevenTimerApi::class.java)
    }
}
