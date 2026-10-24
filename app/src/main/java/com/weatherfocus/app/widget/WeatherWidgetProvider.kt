package com.weatherfocus.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.weatherfocus.app.data.prefs.SettingsRepository
import com.weatherfocus.app.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Shared behaviour of both widgets: draw instantly from cache, then fetch fresh data in the background. */
abstract class BaseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = WidgetUpdates.show(context, fetch = true)
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        WidgetUpdates.show(context, fetch = false)
    override fun onEnabled(context: Context) {
        WorkScheduler.scheduleWidgetUpdates(context)
        WorkScheduler.armTick(context)
    }
    override fun onDisabled(context: Context) = WidgetUpdates.cancelIfNoWidgets(context)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRenderer.ACTION_REFRESH) {
            WidgetRenderer.setRefreshing(context, true) // instant visual feedback on the button
            WidgetUpdates.show(context, fetch = true, manual = true)
        }
    }
}

/** The full weather widget. Resize it bigger for more info, up to a full-screen size. */
class WeatherWidgetProvider : BaseWidgetProvider()

/** Small one-strip widget: temperature, conditions and the top alert. */
class CompactWidgetProvider : BaseWidgetProvider()

/** The alerts-only widget. */
class AlertsWidgetProvider : BaseWidgetProvider()

object WidgetUpdates {
    /** Redraws from the cached weather straight away (no blank widget while waiting), optionally followed by a network refresh. */
    fun show(context: Context, fetch: Boolean, manual: Boolean = false) {
        val app = context.applicationContext
        // Quick local work (settings + cache file), safe to do inside the receiver so the process isn't frozen mid-way.
        runCatching {
            runBlocking(Dispatchers.IO) {
                val cached = WidgetCache.load(app)
                if (cached?.bundle != null) {
                    val settings = SettingsRepository(app).settingsFlow.first()
                    WidgetRenderer.renderAll(app, settings, cached.bundle, fetchedAt = cached.fetchedAt, stale = fetch, keepSpinner = fetch)
                }
            }
        }
        if (fetch) WorkScheduler.runNow(app, manual)
    }

    fun cancelIfNoWidgets(context: Context) {
        if (!WorkScheduler.hasWidgets(context)) WorkScheduler.cancelWidgetUpdates(context)
    }
}
