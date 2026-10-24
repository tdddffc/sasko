package com.weatherfocus.app.worker

import android.content.Context
import com.weatherfocus.app.data.alert.CustomWeatherAlertEngine
import com.weatherfocus.app.data.model.AppSettings
import com.weatherfocus.app.data.model.CustomAlertMatch
import com.weatherfocus.app.data.model.FloodContext
import com.weatherfocus.app.data.model.OpenMeteoHourly
import com.weatherfocus.app.notification.NotificationHelper

object AlertChecker {
    /** Evaluates the user's alert rules (plus official warnings) and posts notifications for anything new. */
    fun run(
        context: Context, settings: AppSettings, hourly: OpenMeteoHourly?,
        flood: FloodContext? = null, official: List<CustomAlertMatch> = emptyList()
    ) {
        if (!settings.customAlertRules.enabled || hourly == null) return
        val matches = CustomWeatherAlertEngine.evaluateAll(hourly, settings.customAlertRules, flood, official)
        NotificationHelper.notifyNew(context, matches, settings.doNotDisturb)
        context.getSharedPreferences("weather_health", Context.MODE_PRIVATE).edit()
            .putLong("last_alert_check", System.currentTimeMillis()).apply()
    }
}
