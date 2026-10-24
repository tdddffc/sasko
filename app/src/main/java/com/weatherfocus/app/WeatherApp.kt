package com.weatherfocus.app

import android.app.Application
import com.weatherfocus.app.notification.NotificationHelper
import com.weatherfocus.app.worker.WorkScheduler

class WeatherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        WorkScheduler.ensureScheduled(this)
    }
}
