package com.facultyconnect.app

import android.app.Application
import com.facultyconnect.app.push.NotificationHelper

class FacultyConnectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
