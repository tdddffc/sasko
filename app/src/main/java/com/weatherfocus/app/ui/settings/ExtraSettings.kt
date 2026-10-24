package com.weatherfocus.app.ui.settings

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.weatherfocus.app.data.model.AlertSeverity
import com.weatherfocus.app.data.prefs.SavedPlace
import com.weatherfocus.app.notification.AlertPrefs
import com.weatherfocus.app.notification.NotificationHelper
import com.weatherfocus.app.widget.WidgetCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val stamp = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
private fun ts(ms: Long) = if (ms <= 0L) "never" else stamp.format(Date(ms))

@Composable
private fun outlinedColors() = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

@Composable
private fun outline() = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Text(
        (if (ok) "✅ " else "⚠️ ") + text,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/** What is working and what is not, so "why did I not get an alert?" can be answered from the phone. */
@Composable
fun HealthSection() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val cached = remember(refresh) { WidgetCache.load(context) }
    val health = context.getSharedPreferences("weather_health", Context.MODE_PRIVATE)
    val lastCheck = remember(refresh) { health.getLong("last_alert_check", 0L) }
    val now = System.currentTimeMillis()
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val unrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    val allowed = NotificationHelper.areNotificationsAllowed(context)
    val fetched = cached?.fetchedAt ?: 0L

    StatusLine(allowed, if (allowed) "Notifications are allowed" else "Notifications are blocked – open notification settings")
    StatusLine(unrestricted, if (unrestricted) "Battery: unrestricted (background checks can run)" else "Battery: restricted – alerts may be delayed. Allow background use.")
    StatusLine(fetched > 0 && now - fetched < 3 * 3_600_000L, "Last weather update: ${ts(fetched)}")
    StatusLine(lastCheck > 0 && now - lastCheck < 3 * 3_600_000L, "Last alert check: ${ts(lastCheck)}")
    val snooze = AlertPrefs.snoozeUntil(context)
    StatusLine(snooze <= now, if (snooze > now) "Alerts are snoozed until ${ts(snooze)}" else "Alerts are not snoozed")
    Spacer4()
    OutlinedButton(onClick = { refresh++ }, colors = outlinedColors(), border = outline()) { Text("Refresh status") }
}

/** Snooze, weekend car alerts, and the list of alerts that were actually sent. */
@Composable
fun AlertToolsSection() {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var weekdays by remember { mutableStateOf(AlertPrefs.carWeekdaysOnly(context)) }
    val history = remember(tick) { AlertPrefs.history(context) }
    val snoozed = remember(tick) { AlertPrefs.isSnoozed(context) }

    SectionTitle("Snooze")
    Text(
        if (snoozed) "Snoozed until ${ts(AlertPrefs.snoozeUntil(context))}" else "Mute all alert notifications for a while:",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        listOf(1 to "1 h", 6 to "6 h", 24 to "24 h").forEach { (h, label) ->
            OutlinedButton(onClick = { AlertPrefs.snoozeForHours(context, h); tick++ }, colors = outlinedColors(), border = outline()) { Text(label) }
        }
        if (snoozed) OutlinedButton(onClick = { AlertPrefs.snoozeForHours(context, 0); tick++ }, colors = outlinedColors(), border = outline()) { Text("Off") }
    }
    Spacer4()
    RowSwitch("No car alerts for Saturday/Sunday mornings", weekdays) { weekdays = it; AlertPrefs.setCarWeekdaysOnly(context, it) }

    Spacer4()
    SectionTitle("Sent alerts (last 50)")
    if (history.isEmpty()) {
        Text("Nothing sent yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        history.take(15).forEach { e ->
            val dot = when (e.severity) { AlertSeverity.RED -> "🔴"; AlertSeverity.ORANGE -> "🟠"; AlertSeverity.YELLOW -> "🟡" }
            Column(modifier = Modifier.padding(vertical = 3.dp)) {
                Text("$dot ${e.label}", style = MaterialTheme.typography.bodyMedium)
                Text("${ts(e.time)} – ${e.detail.take(120)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { AlertPrefs.clearHistory(context); tick++ }, colors = outlinedColors(), border = outline(), modifier = Modifier.padding(top = 6.dp)) { Text("Clear history") }
    }
}

/** Saved places: home / work / cabin. Each remembers its own alert numbers; the active one drives widgets and alerts. */
@Composable
fun PlacesSection(
    current: String, places: List<SavedPlace>,
    onSaveCurrent: () -> Unit, onSwitch: (SavedPlace) -> Unit, onDelete: (SavedPlace) -> Unit
) {
    Text(
        "Active place: $current. Save it, change the location above and save the next one. Switching places also switches the alert settings.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = onSaveCurrent,
        modifier = Modifier.padding(top = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
    ) { Text("Save \"$current\" with its alert settings") }
    Spacer4()
    places.forEach { p ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSwitch(p) }, modifier = Modifier.weight(1f),
                colors = outlinedColors(), border = outline()
            ) { Text((if (p.name.equals(current, true)) "✅ " else "") + "${p.name}, ${p.countryLabel}") }
            OutlinedButton(onClick = { onDelete(p) }, colors = outlinedColors(), border = outline()) { Text("✕") }
        }
    }
}
