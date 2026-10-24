package com.weatherfocus.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.weatherfocus.app.MainActivity
import com.weatherfocus.app.R
import com.weatherfocus.app.data.model.AlertRuleType
import com.weatherfocus.app.data.model.AlertSeverity
import com.weatherfocus.app.data.model.CustomAlertMatch
import com.weatherfocus.app.data.model.DoNotDisturbSettings
import java.util.Calendar

object NotificationHelper {
    private const val CH_LOW = "weather_alerts_yellow"
    private const val CH_NORMAL = "weather_alerts_v2"
    private const val CH_RED = "weather_alerts_red"
    private const val OLD_CHANNEL_ID = "weather_alerts"
    private const val GROUP = "weather_alerts_group"
    private const val SUMMARY_ID = 999
    private const val NOTIFICATION_ID_BASE = 1000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        nm.createNotificationChannel(
            NotificationChannel(CH_LOW, "Weather alerts – yellow (silent)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Good-to-know alerts: shown quietly, no sound"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_NORMAL, "Weather alerts – orange", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Take care: ice, strong wind, heavy snow, flood watch"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RED, "Weather alerts – red (dangerous)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Dangerous weather: freezing rain, snowstorm, extreme wind or flood, official red warnings"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 900)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    /** Returns true if the given Do-Not-Disturb window is currently active and notifications should be suppressed. */
    fun isQuietHoursActive(dnd: DoNotDisturbSettings, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!dnd.enabled) return false
        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
        return dnd.isActiveAt(hour)
    }

    /** Shows one alert unless quiet hours / snooze apply. Returns true if it was actually posted. */
    fun showAlert(context: Context, match: CustomAlertMatch, dnd: DoNotDisturbSettings): Boolean {
        if (isQuietHoursActive(dnd) || AlertPrefs.isSnoozed(context)) return false
        return showNow(context, match, grouped = false)
    }

    /**
     * Single entry point for "notify about anything new", shared by the background worker and the app screen so
     * the same alert is never shown twice, and an alert suppressed by quiet hours/snooze is retried later
     * instead of being silently lost. Several new alerts at once become one group with a summary.
     */
    @Synchronized
    fun notifyNew(context: Context, matches: List<CustomAlertMatch>, dnd: DoNotDisturbSettings) {
        val prefs = context.getSharedPreferences("weather_alert_dedup", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cutoff = now - 12L * 60 * 60 * 1000
        val seen = (prefs.getStringSet("keys", emptySet()) ?: emptySet())
            .filter { it.substringAfterLast("|").toLongOrNull()?.let { t -> t > cutoff } ?: false }
            .toMutableSet()

        val blocked = isQuietHoursActive(dnd) || AlertPrefs.isSnoozed(context)
        val carWeekdaysOnly = AlertPrefs.carWeekdaysOnly(context)
        val fresh = matches.filter { m ->
            val isCar = m.type == AlertRuleType.CAR_ICE || m.type == AlertRuleType.CAR_SNOW
            val key = "${m.type}|${m.label.hashCode()}|${m.triggerAtMillis}"
            m.leadWarning && m.triggerAtMillis > now - 60 * 60 * 1000 && key !in seen &&
                !(isCar && carWeekdaysOnly && AlertPrefs.isCarQuietDay(now, m.triggerAtMillis))
        }
        if (!blocked && fresh.isNotEmpty()) {
            val posted = mutableListOf<CustomAlertMatch>()
            val grouped = fresh.size > 1
            fresh.sortedByDescending { it.severity.ordinal }.forEach { m ->
                if (showNow(context, m, grouped)) {
                    seen += "${m.type}|${m.label.hashCode()}|${m.triggerAtMillis}"
                    posted += m
                    AlertPrefs.addHistory(context, AlertPrefs.HistoryEntry(now, m.label, m.detail, m.severity))
                }
            }
            if (posted.size > 1) postSummary(context, posted)
        }
        // Alerts the user's own rules no longer produce still count as seen for 12 h so they can't flap back as "new".
        prefs.edit().putStringSet("keys", seen).apply()
    }

    fun showTestNotification(context: Context) {
        showNow(
            context,
            CustomAlertMatch(
                label = "Test notification",
                detail = "If you can see this, weather alerts will reach you too.",
                severity = AlertSeverity.ORANGE
            ),
            grouped = false
        )
    }

    private fun channelFor(s: AlertSeverity) = when (s) {
        AlertSeverity.YELLOW -> CH_LOW
        AlertSeverity.ORANGE -> CH_NORMAL
        AlertSeverity.RED -> CH_RED
    }

    private fun canPost(context: Context): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return false
        return true
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun showNow(context: Context, match: CustomAlertMatch, grouped: Boolean): Boolean {
        if (!canPost(context)) return false
        val snooze = PendingIntent.getBroadcast(
            context, 5,
            Intent(context, SnoozeReceiver::class.java).putExtra("hours", 6),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(context, channelFor(match.severity))
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(match.label)
            .setContentText(match.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(match.detail))
            .setPriority(if (match.severity == AlertSeverity.YELLOW) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Snooze 6 h", snooze)
        if (grouped) b.setGroup(GROUP)
        // Distinct id per alert so simultaneous alerts each get a tray entry (official warnings: per warning).
        val id = if (match.type == AlertRuleType.OFFICIAL) NOTIFICATION_ID_BASE + 100 + (match.label.hashCode() and 0xFF)
        else NOTIFICATION_ID_BASE + match.type.ordinal
        return runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }.isSuccess
    }

    private fun postSummary(context: Context, posted: List<CustomAlertMatch>) {
        if (!canPost(context)) return
        val worst = posted.maxOf { it.severity.ordinal }
        val style = NotificationCompat.InboxStyle().also { st -> posted.take(6).forEach { st.addLine(it.label) } }
        val n = NotificationCompat.Builder(context, channelFor(AlertSeverity.values()[worst]))
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle("${posted.size} weather alerts")
            .setContentText(posted.first().label)
            .setStyle(style)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, n) }
    }

    fun areNotificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openSystemNotificationSettings(context: Context) {
        val i = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }
}
