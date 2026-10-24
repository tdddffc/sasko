package com.weatherfocus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weatherfocus.app.data.prefs.SettingsRepository
import com.weatherfocus.app.data.repository.WeatherRepository
import com.weatherfocus.app.widget.WidgetCache
import kotlinx.coroutines.flow.first

/** Re-checks the user's alert rules in the background and notifies about anything inside the lead-time window. */
class WeatherAlertCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = SettingsRepository(applicationContext).settingsFlow.first()
            if (!settings.customAlertRules.enabled) return Result.success()

            // Shared store first: if the app or widget downloaded fresh data in the last 25 minutes, don't download again.
            val cached = WidgetCache.load(applicationContext)
            val cachedBundle = cached?.bundle
            if (cached != null && cachedBundle?.hourlyForAlerts != null && System.currentTimeMillis() - cached.fetchedAt < 25 * 60_000L) {
                AlertChecker.run(applicationContext, settings, cachedBundle.hourlyForAlerts, cachedBundle.floodContext, cachedBundle.officialAlerts)
                return Result.success()
            }

            val repo = WeatherRepository()
            val hourly = repo.loadAlertForecast(settings)
                ?: return if (runAttemptCount < 3) Result.retry() else Result.success()
            val flood = if (settings.customAlertRules.floodEnabled) repo.loadFloodContext(settings) else null
            val official = repo.loadOfficialAlerts(settings)
            AlertChecker.run(applicationContext, settings, hourly, flood, official)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
