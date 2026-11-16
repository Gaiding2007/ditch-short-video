package com.gaiding.ditchshortvideo

import android.app.Application
import com.gaiding.ditchshortvideo.notify.ReminderNotifier

class DitchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.ensureChannel(this)
    }
}
