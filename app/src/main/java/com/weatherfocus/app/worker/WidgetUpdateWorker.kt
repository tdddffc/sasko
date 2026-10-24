package com.weatherfocus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weatherfocus.app.data.prefs.SettingsRepository
import com.weatherfocus.app.data.repository.WeatherRepository
import com.weatherfocus.app.widget.WidgetCache
import com.weatherfocus.app.widget.WidgetRenderer
import kotlinx.coroutines.flow.first

/** Fetches fresh weather, redraws all home-screen widgets and checks the alert rules (so alerts also fire while the app is closed). */
class WidgetUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val settings = SettingsRepository(ctx).settingsFlow.first()
            // Hard cap so a hung connection can never leave the refresh spinner turning forever.
            val bundle = kotlinx.coroutines.withTimeoutOrNull(40_000) { WeatherRepository().loadWeather(settings) }
                ?: return fail(ctx)
            // loadWeather swallows individual source errors; "no data at all" has to count as a failure here,
            // otherwise the widget would be redrawn full of "--" (the "I have to refresh it a few times" bug).
            val gotData = bundle.current.available && bundle.hourlyForAlerts != null
            if (gotData) {
                val now = System.currentTimeMillis()
                WidgetCache.save(ctx, bundle, now)
                WidgetRenderer.renderAll(ctx, settings, bundle, fetchedAt = now)
                runCatching { AlertChecker.run(ctx, settings, bundle.hourlyForAlerts, bundle.floodContext, bundle.officialAlerts) }
                Result.success()
            } else fail(ctx)
        } catch (e: Exception) {
            fail(ctx)
        }
    }

    private suspend fun fail(ctx: Context): Result {
        showCachedOrStopSpinner(ctx)
        return if (runAttemptCount < 3) Result.retry() else Result.success()
    }

    /** Needed for expedited work on Android 11 and older (shown only briefly while the refresh runs). */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val ctx = applicationContext
        val chId = "widget_refresh"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            nm?.createNotificationChannel(
                android.app.NotificationChannel(chId, "Widget refresh", android.app.NotificationManager.IMPORTANCE_MIN)
            )
        }
        val n = androidx.core.app.NotificationCompat.Builder(ctx, chId)
            .setSmallIcon(com.weatherfocus.app.R.drawable.ic_notification_alert)
            .setContentTitle("Updating weather\u2026")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            androidx.work.ForegroundInfo(4711, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else androidx.work.ForegroundInfo(4711, n)
    }

    private suspend fun showCachedOrStopSpinner(ctx: Context) {
        val settings = runCatching { SettingsRepository(ctx).settingsFlow.first() }.getOrNull()
        val cached = WidgetCache.load(ctx)
        if (settings != null && cached?.bundle != null) {
            WidgetRenderer.renderAll(ctx, settings, cached.bundle, fetchedAt = cached.fetchedAt, stale = true)
        } else {
            WidgetRenderer.setRefreshing(ctx, false)
        }
    }
}
