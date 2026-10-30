package com.zzijad.adbwifiz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.auto(context) && Adb.hasPermission(context)) {
            try { AdbWifiService.start(context) } catch (_: Exception) {}
        }
    }
}
