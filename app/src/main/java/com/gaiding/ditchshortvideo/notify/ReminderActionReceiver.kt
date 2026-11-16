package com.gaiding.ditchshortvideo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ReminderNotifier.dismissAction()) return
        ReminderNotifier.cancelReminder(context)
        ReminderNotifier.cancelTest(context)
    }
}
