package com.weatherfocus.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.weatherfocus.app.MainActivity
import com.weatherfocus.app.R
import com.weatherfocus.app.data.alert.CustomWeatherAlertEngine
import com.weatherfocus.app.data.model.AppSettings
import com.weatherfocus.app.data.model.ConditionGroup
import com.weatherfocus.app.data.model.CustomAlertMatch
import com.weatherfocus.app.data.model.DayForecast
import com.weatherfocus.app.data.model.WidgetStyle
import com.weatherfocus.app.data.model.WidgetTile
import com.weatherfocus.app.data.repository.WeatherBundle
import com.weatherfocus.app.ui.WeatherFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builds the RemoteViews for the two home-screen widgets (the full weather widget and the alerts-only
 * widget) from the latest weather bundle and the user's widget settings. The weather widget adapts to
 * its size: tiny shows the header only, bigger adds alerts, the hourly strip, info tiles and the
 * multi-day list, and a full-screen size adds the complete alert list.
 */
object WidgetRenderer {
    const val ACTION_REFRESH = "com.weatherfocus.app.WIDGET_REFRESH"

    /**
     * [fetchedAt] is when [bundle] was downloaded (shown as "Updated HH:mm"); [stale] marks a cached redraw while a fresh
     * download is pending/failed; [keepSpinner] leaves the refresh button spinning until the download finishes.
     */
    fun renderAll(
        context: Context, settings0: AppSettings, bundle: WeatherBundle,
        fetchedAt: Long = System.currentTimeMillis(), stale: Boolean = false, keepSpinner: Boolean = false
    ) {
        this.fetchedAt = fetchedAt; this.stale = stale; this.spinning = keepSpinner
        val mgr = AppWidgetManager.getInstance(context)
        val alerts = CustomWeatherAlertEngine.evaluateAll(bundle.hourlyForAlerts, settings0.customAlertRules, bundle.floodContext, bundle.officialAlerts)
        // "Follow phone" resolves to the phone's current light/dark setting every time the widget is drawn.
        val settings = if (settings0.widget.style == WidgetStyle.AUTO) {
            val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            settings0.copy(widget = settings0.widget.copy(style = if (dark) WidgetStyle.DARK else WidgetStyle.LIGHT))
        } else settings0
        mgr.getAppWidgetIds(ComponentName(context, CompactWidgetProvider::class.java)).forEach { id ->
            mgr.updateAppWidget(id, compactViews(context, settings, bundle, alerts))
        }
        mgr.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java)).forEach { id ->
            val (w, h) = sizeOf(mgr, id)
            mgr.updateAppWidget(id, weatherViews(context, settings, bundle, alerts, w, h))
        }
        mgr.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java)).forEach { id ->
            val (w, h) = sizeOf(mgr, id)
            mgr.updateAppWidget(id, alertsViews(context, settings, alerts, w, h))
        }
    }

    @Volatile private var fetchedAt = 0L
    @Volatile private var stale = false
    @Volatile private var spinning = false

    /** Instantly swaps the refresh arrow for a spinner (or back) on every placed widget, without redrawing anything else. */
    fun setRefreshing(context: Context, refreshing: Boolean) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java)) +
            mgr.getAppWidgetIds(ComponentName(context, CompactWidgetProvider::class.java)) +
            mgr.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val pkg = context.packageName
        val weatherIds = mgr.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
        val compactIds = mgr.getAppWidgetIds(ComponentName(context, CompactWidgetProvider::class.java))
        val layoutOf = { id: Int ->
            when {
                id in weatherIds -> R.layout.widget_weather
                id in compactIds -> R.layout.widget_compact
                else -> R.layout.widget_alerts
            }
        }
        ids.forEach { id ->
            val rv = RemoteViews(pkg, layoutOf(id))
            applyRefreshState(rv, refreshing)
            runCatching { mgr.partiallyUpdateAppWidget(id, rv) }
        }
    }

    private fun applyRefreshState(rv: RemoteViews, refreshing: Boolean) {
        rv.setViewVisibility(R.id.refresh, if (refreshing) android.view.View.INVISIBLE else android.view.View.VISIBLE)
        rv.setViewVisibility(R.id.refresh_progress, if (refreshing) android.view.View.VISIBLE else android.view.View.GONE)
    }

    /** Styles the round refresh button for the current look and wires the tap. */
    private fun styleRefresh(context: Context, rv: RemoteViews, pal: Palette, light: Boolean, provider: Class<*>, code: Int) {
        rv.setInt(R.id.refresh_box, "setBackgroundResource", if (light) R.drawable.widget_refresh_bg_light else R.drawable.widget_refresh_bg)
        rv.setInt(R.id.refresh, "setColorFilter", pal.text)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            rv.setColorStateList(R.id.refresh_progress, "setIndeterminateTintList", android.content.res.ColorStateList.valueOf(pal.text))
        }
        applyRefreshState(rv, spinning)
        rv.setOnClickPendingIntent(R.id.refresh_box, refreshIntent(context, provider, code))
    }

    private fun updatedText(now: Long): String {
        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(if (fetchedAt > 0) fetchedAt else now))
        return when {
            spinning -> "Updating\u2026"
            stale -> "Offline \u00B7 last update $t"
            else -> "Updated $t"
        }
    }

    private fun sizeOf(mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = mgr.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300))
        return w to h
    }

    private data class Palette(val text: Int, val bgPanel: Int, val bgTile: Int, val alertBg: Int, val alertText: Int, val alertSub: Int)

    private fun paletteFor(style: WidgetStyle) = when (style) {
        WidgetStyle.LIGHT, WidgetStyle.AUTO -> Palette(
            Color.parseColor("#1D2A4D"), R.drawable.widget_panel_light, R.drawable.widget_tile_light,
            R.drawable.widget_alert_red, Color.WHITE, Color.parseColor("#FFE5E5")
        )
        WidgetStyle.DARK -> Palette(
            Color.parseColor("#EEF0F4"), R.drawable.widget_panel_dark, R.drawable.widget_tile_dark,
            R.drawable.widget_alert_red, Color.WHITE, Color.parseColor("#FFE5E5")
        )
        WidgetStyle.WEATHER -> Palette(
            Color.WHITE, R.drawable.widget_panel_weather, R.drawable.widget_tile_weather,
            R.drawable.widget_alert_red, Color.WHITE, Color.parseColor("#FFE5E5")
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun refreshIntent(context: Context, provider: Class<*>, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, provider).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Background + decoration for the widget: fixed for dark/light, otherwise follows the worst weather in the look-ahead window, day or night. */
    private data class Look(val bg: Int, val fx: Int?)

    private fun lookFor(settings: AppSettings, bundle: WeatherBundle, slots: List<HourSlot>, now: Long): Look {
        when (settings.widget.style) {
            WidgetStyle.DARK -> return Look(R.drawable.widget_bg_dark, null)
            WidgetStyle.LIGHT, WidgetStyle.AUTO -> return Look(R.drawable.widget_bg_light, null)
            WidgetStyle.WEATHER -> Unit
        }
        val cur = bundle.current
        var worst = cur.conditionGroup
        val hours = settings.widget.lookaheadHours
        if (hours > 0) {
            val end = now + hours * 3_600_000L
            slots.filter { it.millis in now..end }.forEach {
                val g = WidgetData.groupOfCode(it.code)
                if (WidgetData.severity(g) > WidgetData.severity(worst)) worst = g
            }
        }
        val n = WidgetData.isNight(now, cur.sunrise, cur.sunset)
        return when (worst) {
            ConditionGroup.CLEAR -> if (n) Look(R.drawable.widget_bg_night, R.drawable.fx_stars) else Look(R.drawable.widget_bg_clear, R.drawable.fx_sun)
            ConditionGroup.CLOUDY, ConditionGroup.UNKNOWN ->
                if (n) Look(R.drawable.widget_bg_cloudy_night, R.drawable.fx_cloudy_night) else Look(R.drawable.widget_bg_cloudy, R.drawable.fx_cloudy)
            ConditionGroup.RAIN -> if (n) Look(R.drawable.widget_bg_rain_night, R.drawable.fx_rain_night) else Look(R.drawable.widget_bg_rain, R.drawable.fx_rain)
            ConditionGroup.THUNDER -> if (n) Look(R.drawable.widget_bg_thunder_night, R.drawable.fx_thunder) else Look(R.drawable.widget_bg_thunder, R.drawable.fx_thunder)
            ConditionGroup.SNOW -> if (n) Look(R.drawable.widget_bg_snow_night, R.drawable.fx_snow) else Look(R.drawable.widget_bg_snow, R.drawable.fx_snow)
            ConditionGroup.FOG -> if (n) Look(R.drawable.widget_bg_fog_night, R.drawable.fx_fog) else Look(R.drawable.widget_bg_fog, R.drawable.fx_fog)
        }
    }

    private fun severityBg(s: com.weatherfocus.app.data.model.AlertSeverity): Int = when (s) {
        com.weatherfocus.app.data.model.AlertSeverity.RED -> R.drawable.widget_alert_red
        com.weatherfocus.app.data.model.AlertSeverity.ORANGE -> R.drawable.widget_alert_orange
        com.weatherfocus.app.data.model.AlertSeverity.YELLOW -> R.drawable.widget_alert_yellow
    }

    /** The small strip widget: weather look, temperature, conditions and the single most severe alert. */
    private fun compactViews(context: Context, settings: AppSettings, bundle: WeatherBundle, alerts: List<CustomAlertMatch>): RemoteViews {
        val ws = settings.widget
        val pal = paletteFor(ws.style)
        val rv = RemoteViews(context.packageName, R.layout.widget_compact)
        val now = System.currentTimeMillis()
        val cur = bundle.current
        val all = WidgetData.slots(bundle.hourlyForAlerts)
        val upcoming = WidgetData.upcoming(all, now)
        val look = lookFor(settings, bundle, all, now)
        rv.setInt(R.id.root, "setBackgroundResource", look.bg)
        if (look.fx != null) {
            rv.setImageViewResource(R.id.fx, look.fx)
            rv.setViewVisibility(R.id.fx, android.view.View.VISIBLE)
        } else rv.setViewVisibility(R.id.fx, android.view.View.GONE)
        val night = WidgetData.isNight(now, cur.sunrise, cur.sunset)
        val emoji = upcoming.firstOrNull()?.code?.let { WidgetData.emojiForCode(it, night) } ?: WidgetData.emojiForGroup(cur.conditionGroup, night)
        rv.setTextViewText(R.id.emoji, emoji)
        rv.setTextViewText(R.id.temp, WeatherFormat.tempNoUnit(cur.temp, settings.useFahrenheit))
        rv.setTextViewText(R.id.loc, "\uD83D\uDCCD ${settings.cityName}")
        rv.setTextViewText(R.id.cond, (cur.description ?: "") + if (spinning) " \u00B7 updating\u2026" else if (stale) " \u00B7 offline" else "")
        listOf(R.id.temp, R.id.loc, R.id.cond).forEach { rv.setTextColor(it, pal.text) }
        val top = alerts.firstOrNull()
        if (top != null && ws.showAlert) {
            val (e, title) = WidgetData.splitEmoji(top.label)
            rv.setViewVisibility(R.id.c_alert, android.view.View.VISIBLE)
            rv.setTextViewText(R.id.c_alert, "$e $title" + if (alerts.size > 1) "  (+${alerts.size - 1})" else "")
            rv.setInt(R.id.c_alert, "setBackgroundResource", severityBg(top.severity))
        } else rv.setViewVisibility(R.id.c_alert, android.view.View.GONE)
        styleRefresh(context, rv, pal, ws.style == WidgetStyle.LIGHT, CompactWidgetProvider::class.java, 3)
        rv.setOnClickPendingIntent(R.id.root, openAppIntent(context))
        return rv
    }

    private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun tileOut(
        tile: WidgetTile, bundle: WeatherBundle, upcoming: List<HourSlot>, now: Long, useF: Boolean,
        lat: Double, lon: Double, snowHours: Int, floodMm: Double = 60.0
    ): TileOut {
        val cur = bundle.current
        val next12 = upcoming.take(12)
        val next24 = upcoming.take(24)
        val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val todayKey = dayFmt.format(Date(now))
        // "Today" tiles count what is still to come today (from the current hour on).
        val restOfToday = upcoming.filter { dayFmt.format(Date(it.millis)) == todayKey }
        val first = upcoming.firstOrNull()
        return when (tile) {
            WidgetTile.WIND -> {
                val ms = cur.windSpeedKmh?.let { (it / 3.6).roundToInt() }
                val gust = upcoming.take(6).mapNotNull { it.gust }.maxOrNull()?.let { (it / 3.6).roundToInt() }
                val dir = first?.windDirDeg?.let { WidgetData.compass(it) }
                TileOut(ms?.let { "$it m/s" } ?: "--", listOfNotNull(dir, gust?.let { "gust $it" }).joinToString(" ").ifEmpty { "Wind" })
            }
            WidgetTile.COLDEST -> TileOut(
                next24.mapNotNull { it.feelsLike }.minOrNull()?.let { WeatherFormat.tempNoUnit(it, useF) } ?: "--"
            )
            WidgetTile.FROST_HOURS -> {
                val n = next24.mapNotNull { it.temp }.takeIf { it.isNotEmpty() }?.count { it <= 0.0 }
                TileOut(n?.let { "$it h" } ?: "--", empty = n == null || n == 0)
            }
            WidgetTile.FROST_CAR -> WidgetExtras.frostOnCar(upcoming, now)
            WidgetTile.ROAD_ICE -> WidgetExtras.roadIce(upcoming, now)
            WidgetTile.SNOW_DEPTH -> WidgetExtras.snowDepth(upcoming)
            WidgetTile.SNOW -> WidgetExtras.snowAhead(upcoming, snowHours)
            WidgetTile.NEXT_PRECIP -> WidgetExtras.nextPrecip(upcoming, now)
            WidgetTile.FLOOD -> WidgetExtras.floodWatch(bundle.floodContext, upcoming, floodMm)
            WidgetTile.UV -> TileOut(first?.uv?.let { fmt1(it) } ?: "--", empty = (first?.uv ?: 0.0) < 3.0)
            WidgetTile.VISIBILITY -> WidgetExtras.visibility3h(upcoming)
            WidgetTile.MORNING_FOG -> WidgetExtras.morningFog(upcoming, now)
            WidgetTile.AURORA -> WidgetExtras.aurora(bundle.kpForecast, upcoming, lat, lon)
            WidgetTile.DAYLIGHT_CHANGE -> WidgetExtras.daylightChange(bundle.monthDays, lat, now)
        }
    }

    private fun weatherViews(
        context: Context, settings: AppSettings, bundle: WeatherBundle,
        alerts: List<CustomAlertMatch>, widthDp: Int, heightDp: Int
    ): RemoteViews {
        val ws = settings.widget
        val pal = paletteFor(ws.style)
        val rv = RemoteViews(context.packageName, R.layout.widget_weather)
        val now = System.currentTimeMillis()
        val useF = settings.useFahrenheit
        val cur = bundle.current
        val all = WidgetData.slots(bundle.hourlyForAlerts)
        val upcoming = WidgetData.upcoming(all, now)
        val show = android.view.View.VISIBLE
        val hide = android.view.View.GONE

        val look = lookFor(settings, bundle, all, now)
        rv.setInt(R.id.root, "setBackgroundResource", look.bg)
        if (look.fx != null) {
            rv.setImageViewResource(R.id.fx, look.fx)
            rv.setViewVisibility(R.id.fx, show)
        } else rv.setViewVisibility(R.id.fx, hide)
        WidgetIds.weatherTexts.forEach { rv.setTextColor(it, pal.text) }
        styleRefresh(context, rv, pal, ws.style == WidgetStyle.LIGHT, WeatherWidgetProvider::class.java, 1)

        // ----- header + main -----
        val night = WidgetData.isNight(now, cur.sunrise, cur.sunset)
        rv.setTextViewText(R.id.loc, "\uD83D\uDCCD ${settings.cityName}, ${settings.countryLabel}")
        rv.setTextViewText(R.id.updated, updatedText(now))
        val emoji = upcoming.firstOrNull()?.code?.let { WidgetData.emojiForCode(it, night) }
            ?: WidgetData.emojiForGroup(cur.conditionGroup, night)
        rv.setTextViewText(R.id.emoji, emoji)
        rv.setTextViewText(R.id.temp, WeatherFormat.tempNoUnit(cur.temp, useF))
        rv.setTextViewText(R.id.cond, cur.description ?: "")
        rv.setTextViewText(R.id.feels, "Feels ${WeatherFormat.tempNoUnit(cur.feelsLike, useF)}")
        rv.setTextViewText(R.id.hilo, "H ${WeatherFormat.tempNoUnit(cur.tempMax, useF)} \u00B7 L ${WeatherFormat.tempNoUnit(cur.tempMin, useF)}")

        // What to wear today: icons in the free space between the temperature and "Feels like" (wide widgets only).
        val wearMax = when {
            !ws.showWear || heightDp < 200 -> 0
            widthDp >= 420 -> 5
            widthDp >= 340 -> 4
            widthDp >= 300 -> 3
            else -> 0
        }
        rv.setTextViewText(R.id.wear, WidgetExtras.wearIcons(cur.feelsLike, upcoming, wearMax))

        // ----- alerts, directly under the temperature -----
        // Big widget: every active alert gets its own row. Smaller sizes: just the first one, with "+N more".
        val bigAlerts = heightDp >= 420
        val alertRows = when {
            !ws.showAlert || alerts.isEmpty() || heightDp < 130 -> 0
            bigAlerts -> minOf(alerts.size, 6)
            else -> 1
        }
        // Tip: cover the windscreen when frost is likely on the car the coming morning.
        val frost = WidgetExtras.frostInfo(upcoming, now)
        val showTip = ws.showCarTip && heightDp >= 170 && frost.pct >= 50
        rv.setViewVisibility(WidgetIds.tipRow, if (showTip) show else hide)
        if (showTip) {
            val low = frost.minTemp?.let { ", low ${WeatherFormat.tempNoUnit(it, useF)}" } ?: ""
            if (frost.tomorrow) {
                rv.setTextViewText(WidgetIds.tipE, "\uD83E\uDDCA")
                rv.setTextViewText(WidgetIds.tipT, "Cover the windscreen tonight")
                rv.setTextViewText(WidgetIds.tipS, "Frost likely on the car tomorrow morning (${frost.pct}%$low)")
            } else {
                rv.setTextViewText(WidgetIds.tipE, "\uD83E\uDDCA")
                rv.setTextViewText(WidgetIds.tipT, "Frost on the windscreen this morning")
                rv.setTextViewText(WidgetIds.tipS, "${frost.pct}% chance$low \u2013 allow extra time to scrape the glass")
            }
        }
        rv.setViewVisibility(R.id.alert_box, if (alertRows > 0 || showTip) show else hide)
        for (i in 0 until 6) {
            val a = alerts.getOrNull(i)
            val visible = i < alertRows && a != null
            rv.setViewVisibility(WidgetIds.awRow[i], if (visible) show else hide)
            if (visible && a != null) {
                val (e, title) = WidgetData.splitEmoji(a.label)
                val more = if (!bigAlerts && alerts.size > 1) "  (+${alerts.size - 1} more)" else ""
                rv.setInt(WidgetIds.awRow[i], "setBackgroundResource", severityBg(a.severity))
                rv.setTextViewText(WidgetIds.awE[i], e)
                rv.setTextViewText(WidgetIds.awT[i], title + more)
                rv.setTextViewText(WidgetIds.awS[i], a.detail)
                rv.setTextColor(WidgetIds.awT[i], pal.alertText)
                rv.setTextColor(WidgetIds.awS[i], pal.alertSub)
            }
        }

        // ----- hourly strip -----
        val showHours = ws.showHours && heightDp >= 210
        rv.setViewVisibility(R.id.hours_box, if (showHours) show else hide)
        if (showHours) {
            rv.setInt(R.id.hours_box, "setBackgroundResource", pal.bgPanel)
            val n = if (widthDp < 300) minOf(ws.hoursCount, 4) else ws.hoursCount.coerceIn(3, 8)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            for (i in 0 until 8) {
                val slot = upcoming.getOrNull(i)
                val visible = i < n && slot != null
                rv.setViewVisibility(WidgetIds.hourBox[i], if (visible) show else hide)
                if (visible && slot != null) {
                    rv.setTextViewText(WidgetIds.hourT[i], if (i == 0) "Now" else timeFmt.format(Date(slot.millis)))
                    rv.setTextViewText(WidgetIds.hourE[i], WidgetData.emojiForCode(slot.code, WidgetData.isNight(slot.millis, cur.sunrise, cur.sunset)))
                    rv.setTextViewText(WidgetIds.hourV[i], WeatherFormat.tempNoUnit(slot.temp, useF))
                    rv.setTextViewText(WidgetIds.hourP[i], slot.rainChance?.let { "\uD83D\uDCA7 $it%" } ?: "")
                }
            }
        }

        // ----- next days: one row of small tiles (icon + high / low) -----
        val realDays: List<DayForecast> = bundle.monthDays.filter { !it.isOutlookEstimate }.drop(1).ifEmpty { bundle.next3Days }
        val dayCount = minOf(if (widthDp < 300) minOf(ws.daysCount, 5) else ws.daysCount, realDays.size)
        val showDays = ws.showDays && dayCount > 0 && heightDp >= 300
        rv.setViewVisibility(R.id.days_box, if (showDays) show else hide)
        if (showDays) {
            for (i in 0 until 7) {
                val d = realDays.getOrNull(i)
                val visible = i < dayCount && d != null
                rv.setViewVisibility(WidgetIds.dayBox[i], if (visible) show else hide)
                if (visible && d != null) {
                    rv.setInt(WidgetIds.dayBox[i], "setBackgroundResource", pal.bgTile)
                    rv.setTextViewText(WidgetIds.dayD[i], d.dayLabel)
                    rv.setTextViewText(WidgetIds.dayE[i], WidgetData.emojiForGroup(d.conditionGroup, false))
                    rv.setTextViewText(WidgetIds.dayT[i], WeatherFormat.tempNoUnit(d.maxTemp, useF))
                    rv.setTextViewText(WidgetIds.dayL[i], WeatherFormat.tempNoUnit(d.minTemp, useF))
                }
            }
        }

        // ----- info tiles: 4 per row, more rows as the widget gets taller (up to 16 tiles) -----
        val tileRows = when {
            heightDp >= 640 -> 4
            heightDp >= 540 -> 3
            heightDp >= 440 -> 2
            heightDp >= 340 -> 1
            else -> 0
        }
        // Work out every picked tile, drop the "smart" ones that have nothing to show, then fill the grid.
        val computed = ws.tiles.map { it to tileOut(it, bundle, upcoming, now, useF, settings.latitude, settings.longitude, ws.snowHours, settings.customAlertRules.floodMm) }
        val chosen = computed
            .filter { (tile, out) -> !(ws.hideEmptyTiles && tile.hidesWhenEmpty && out.empty) }
            .take(minOf(16, tileRows * 4))
        val showTiles = ws.showTiles && chosen.isNotEmpty()
        rv.setViewVisibility(R.id.tiles_box, if (showTiles) show else hide)
        if (showTiles) {
            for (r in 0 until 4) {
                rv.setViewVisibility(WidgetIds.tileRow[r], if (chosen.size > r * 4) show else hide)
            }
            for (i in 0 until 16) {
                val entry = chosen.getOrNull(i)
                rv.setViewVisibility(WidgetIds.tileBox[i], if (entry != null) show else android.view.View.INVISIBLE)
                if (entry != null) {
                    val (tile, out) = entry
                    rv.setInt(WidgetIds.tileBox[i], "setBackgroundResource", pal.bgTile)
                    rv.setTextViewText(WidgetIds.tileE[i], out.emoji ?: tile.emoji)
                    rv.setTextViewText(WidgetIds.tileV[i], out.value)
                    rv.setTextViewText(WidgetIds.tileL[i], out.label ?: tile.label)
                }
            }
        }

        // ----- sunrise / sunset (sun and moon) -----
        val showSun = ws.showSun && heightDp >= 250
        rv.setViewVisibility(R.id.foot_box, if (showSun) show else hide)
        if (showSun) {
            rv.setTextViewText(R.id.sunrise, "\u2600\uFE0F ${cur.sunrise ?: "--"}")
            rv.setTextViewText(R.id.sunset, "\uD83C\uDF19 ${cur.sunset ?: "--"}")
            rv.setTextViewText(R.id.daylight, WidgetData.daylightText(cur.sunrise, cur.sunset)?.let { "$it daylight" } ?: "")
        }

        rv.setOnClickPendingIntent(R.id.root, openAppIntent(context))
        return rv
    }

    private fun alertsViews(context: Context, settings: AppSettings, alerts: List<CustomAlertMatch>, widthDp: Int, heightDp: Int): RemoteViews {
        val ws = settings.widget
        val pal = paletteFor(if (ws.style == WidgetStyle.WEATHER) WidgetStyle.DARK else ws.style)
        val rv = RemoteViews(context.packageName, R.layout.widget_alerts)
        val now = System.currentTimeMillis()

        rv.setInt(R.id.root, "setBackgroundResource", if (ws.style == WidgetStyle.LIGHT) R.drawable.widget_bg_light else R.drawable.widget_bg_dark)
        WidgetIds.alertTexts.forEach { rv.setTextColor(it, pal.text) }
        styleRefresh(context, rv, pal, ws.style == WidgetStyle.LIGHT, AlertsWidgetProvider::class.java, 2)
        rv.setTextViewText(R.id.a_sub, "${settings.cityName} · ${updatedText(now).replaceFirstChar { it.lowercase() }}")

        val rules = settings.customAlertRules
        val maxRows = when {
            heightDp >= 300 -> 5
            heightDp >= 220 -> 4
            heightDp >= 160 -> 3
            heightDp >= 110 -> 2
            else -> 1
        }
        if (alerts.isEmpty()) {
            rv.setViewVisibility(R.id.a_empty, android.view.View.VISIBLE)
            rv.setTextViewText(
                R.id.a_empty,
                if (rules.enabled) "✅ No alerts in the next ${rules.horizonHours} hours. Enjoy!"
                else "Custom alerts are off. Turn them on in the app: Settings → Weather & Winter Alerts."
            )
        } else {
            rv.setViewVisibility(R.id.a_empty, android.view.View.GONE)
        }
        for (i in 0 until 5) {
            val a = alerts.getOrNull(i)
            val visible = a != null && i < maxRows
            rv.setViewVisibility(WidgetIds.alRow[i], if (visible) android.view.View.VISIBLE else android.view.View.GONE)
            if (visible && a != null) {
                val (e, title) = WidgetData.splitEmoji(a.label)
                rv.setInt(WidgetIds.alRow[i], "setBackgroundResource", severityBg(a.severity))
                rv.setTextColor(WidgetIds.alT[i], pal.alertText)
                rv.setTextColor(WidgetIds.alS[i], pal.alertSub)
                rv.setTextViewText(WidgetIds.alE[i], e)
                rv.setTextViewText(WidgetIds.alT[i], title)
                rv.setTextViewText(WidgetIds.alS[i], a.detail)
            }
        }
        rv.setOnClickPendingIntent(R.id.root, openAppIntent(context))
        return rv
    }
}
