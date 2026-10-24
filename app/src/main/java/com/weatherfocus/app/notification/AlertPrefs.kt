package com.weatherfocus.app.notification

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weatherfocus.app.data.model.AlertSeverity
import java.util.Calendar

/** Small non-weather preferences for alerts: snooze, "no car alerts at the weekend", and the sent-alerts history. */
object AlertPrefs {
    private const val FILE = "weather_alert_prefs"
    private const val HISTORY = "weather_alert_history"
    private val gson = Gson()

    data class HistoryEntry(
        val time: Long = 0L,
        val label: String = "",
        val detail: String = "",
        val severity: AlertSeverity = AlertSeverity.ORANGE
    )

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun snoozeUntil(c: Context): Long = prefs(c).getLong("snooze_until", 0L)
    fun isSnoozed(c: Context, now: Long = System.currentTimeMillis()): Boolean = snoozeUntil(c) > now
    fun snoozeForHours(c: Context, hours: Int) {
        prefs(c).edit().putLong("snooze_until", if (hours <= 0) 0L else System.currentTimeMillis() + hours * 3_600_000L).apply()
    }

    fun carWeekdaysOnly(c: Context): Boolean = prefs(c).getBoolean("car_weekdays_only", false)
    fun setCarWeekdaysOnly(c: Context, v: Boolean) = prefs(c).edit().putBoolean("car_weekdays_only", v).apply()

    /** True on Saturday/Sunday mornings the car alert is *for* (the alert is sent the evening before, so Friday/Saturday evening). */
    fun isCarQuietDay(now: Long = System.currentTimeMillis(), triggerAt: Long): Boolean {
        val dow = Calendar.getInstance().apply { timeInMillis = triggerAt }.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    @Synchronized
    fun addHistory(c: Context, e: HistoryEntry) {
        val list = history(c).toMutableList()
        list.add(0, e)
        prefs(c).edit().putString(HISTORY, gson.toJson(list.take(50))).apply()
    }

    fun history(c: Context): List<HistoryEntry> = runCatching {
        val type = object : TypeToken<List<HistoryEntry>>() {}.type
        gson.fromJson<List<HistoryEntry>>(prefs(c).getString(HISTORY, null), type) ?: emptyList()
    }.getOrDefault(emptyList())

    fun clearHistory(c: Context) = prefs(c).edit().remove(HISTORY).apply()
}
