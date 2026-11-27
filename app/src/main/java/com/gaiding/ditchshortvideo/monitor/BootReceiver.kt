package com.gaiding.ditchshortvideo.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the watchdog after a reboot; it cannot re-enable accessibility. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Guardian.ensureChannels(context)
        Guardian.schedule(context)
    }
}
