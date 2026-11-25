package com.gaiding.ditchshortvideo

import android.app.Application
import com.gaiding.ditchshortvideo.data.PlanEngine
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.data.UsageStore
import com.gaiding.ditchshortvideo.monitor.Guardian
import com.gaiding.ditchshortvideo.notify.ReminderNotifier

class DitchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.ensureChannel(this)
        Guardian.ensureChannels(this)
        Guardian.schedule(this)
        PlanEngine.applyToday(Prefs(this), UsageStore(this))
    }
}
