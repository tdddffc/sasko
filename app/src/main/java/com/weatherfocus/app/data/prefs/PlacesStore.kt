package com.weatherfocus.app.data.prefs

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** A place the user saved (home, work, cabin...) together with the alert rules that belong to it. */
data class SavedPlace(
    val name: String = "",
    val countryCode: String = "NO",
    val countryLabel: String = "Norway",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** Alert rules for this place, as JSON (see parseRules). */
    val rulesJson: String = ""
)

object PlacesStore {
    private const val FILE = "weather_saved_places"
    private val gson = Gson()

    fun load(c: Context): List<SavedPlace> = runCatching {
        val type = object : TypeToken<List<SavedPlace>>() {}.type
        gson.fromJson<List<SavedPlace>>(c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("places", null), type) ?: emptyList()
    }.getOrDefault(emptyList())

    fun save(c: Context, places: List<SavedPlace>) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("places", gson.toJson(places)).apply()
    }

    /** Adds or replaces the place with the same name and country. */
    fun upsert(c: Context, p: SavedPlace): List<SavedPlace> {
        val list = load(c).filterNot { it.name.equals(p.name, true) && it.countryCode == p.countryCode } + p
        save(c, list)
        return list
    }

    fun remove(c: Context, p: SavedPlace): List<SavedPlace> {
        val list = load(c).filterNot { it.name.equals(p.name, true) && it.countryCode == p.countryCode }
        save(c, list)
        return list
    }
}
