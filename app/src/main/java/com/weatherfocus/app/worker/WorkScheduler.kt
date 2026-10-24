package com.weatherfocus.app.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.weatherfocus.app.widget.AlertsWidgetProvider
import com.weatherfocus.app.widget.WeatherWidgetProvider
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val ALERT_WORK = "weather_alert_check"
    private const val WIDGET_WORK = "weather_widget_update"
    private const val NOW_WORK = "weather_widget_now"
    private const val TICK_MINUTES = 30L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Call on every app start / boot / widget event. Everything here is idempotent. */
    fun ensureScheduled(context: Context) {
        scheduleAlertChecks(context)
        if (hasWidgets(context)) scheduleWidgetUpdates(context)
        armTick(context)
    }

    fun scheduleAlertChecks(context: Context) {
        // 30 min (was 60) and UPDATE so changes in this schedule apply after an app update.
        val request = PeriodicWorkRequestBuilder<WeatherAlertCheckWorker>(30, TimeUnit.MINUTES)
            .setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(ALERT_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleWidgetUpdates(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
            .setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WIDGET_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelWidgetUpdates(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_WORK)
    }

    fun hasWidgets(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        return mgr.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java)).isNotEmpty() ||
            mgr.getAppWidgetIds(ComponentName(context, com.weatherfocus.app.widget.CompactWidgetProvider::class.java)).isNotEmpty() ||
            mgr.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java)).isNotEmpty()
    }

    /**
     * Fetch now (widgets + alerts).
     * - Automatic triggers use KEEP so a burst of update callbacks doesn't keep cancelling the download that's running.
     * - A manual tap on the widget's refresh button uses REPLACE (so it can never be swallowed by an older job that is
     *   waiting for network / sitting in retry back-off) and runs as EXPEDITED work, which Android starts immediately
     *   even when the app is in the background or in a restrictive standby bucket - the reason refresh used to work
     *   only after opening the app.
     */
    fun runNow(context: Context, manual: Boolean = false) {
        val builder = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
        if (manual) {
            builder.setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        } else {
            builder.setConstraints(network)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK, if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, builder.build()
        )
    }

    /** Alerts only (used when no widget is placed). */
    private fun runAlertsNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherAlertCheckWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork("weather_alert_now", ExistingWorkPolicy.KEEP, request)
    }

    /**
     * WorkManager's periodic jobs can be postponed for hours by Doze / app-standby / OEM battery managers.
     * This inexact alarm (no special permission needed, allowed while idle) is a second, independent heartbeat that
     * wakes the app roughly every 30 minutes, kicks the work and re-arms itself.
     */
    fun armTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = tickIntent(context)
        val now = System.currentTimeMillis()
        // Every 30 min, but never skip past 18:05: that is when "ice on the car tomorrow" alerts become due.
        val evening = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 18); set(java.util.Calendar.MINUTE, 5)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now + 60_000L) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val next = minOf(now + TimeUnit.MINUTES.toMillis(TICK_MINUTES), evening)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi) }
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 77, Intent(context, TickReceiver::class.java).setAction("com.weatherfocus.app.TICK"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    class TickReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            armTick(context)
            if (hasWidgets(context)) runNow(context) else runAlertsNow(context)
        }
    }
}
