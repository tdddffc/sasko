package com.weatherfocus.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** "Snooze 6 h" button on alert notifications. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertPrefs.snoozeForHours(context, intent.getIntExtra("hours", 6))
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
    }
}
