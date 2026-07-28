package com.iblu01.portallauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SleepScheduler.ACTION_IDLE) {
            SleepScheduler.onIdleElapsed(context)
        }
    }
}
