package com.gaiding.ditchshortvideo.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gaiding.ditchshortvideo.data.Prefs
import com.gaiding.ditchshortvideo.system.SystemStatus

/** Wakes up every 15 minutes to check whether the detector is still alive. */
class GuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = Prefs(context)
        Guardian.schedule(context)

        if (SystemStatus.accessibilityEnabled(context)) {
            if (!MonitorRuntime.state.connected) Guardian.notifyDisconnected(context)
        } else {
            if (prefs.guardArmed) Guardian.notifyDisconnected(context)
        }
    }
}
