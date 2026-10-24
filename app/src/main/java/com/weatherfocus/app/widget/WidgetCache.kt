package com.weatherfocus.app.widget

import android.content.Context
import com.google.gson.Gson
import com.weatherfocus.app.data.repository.WeatherBundle
import java.io.File

/**
 * Remembers the last successfully loaded weather so widgets can always draw something real (marked as stale)
 * when the network is slow or the phone is waking from Doze, instead of showing blanks or "--".
 */
object WidgetCache {
    data class Stored(val fetchedAt: Long = 0L, val bundle: WeatherBundle? = null)

    private val gson = Gson()
    private fun file(context: Context) = File(context.filesDir, "widget_weather_cache.json")

    @Synchronized
    fun save(context: Context, bundle: WeatherBundle, fetchedAt: Long) {
        runCatching {
            val tmp = File(context.filesDir, "widget_weather_cache.tmp")
            tmp.writeText(gson.toJson(Stored(fetchedAt, bundle)))
            tmp.renameTo(file(context))
        }
    }

    @Synchronized
    fun load(context: Context): Stored? = runCatching {
        val f = file(context)
        if (!f.exists()) null else gson.fromJson(f.readText(), Stored::class.java)?.takeIf { it.bundle != null }
    }.getOrNull()
}
