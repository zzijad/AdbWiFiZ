package com.zzijad.adbwifiz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * Drži Wireless debugging uključenim dok je telefon na Wi-Fi mreži:
 *  - kad se telefon poveže na Wi-Fi → uključi ga
 *  - kad ga sistem (ili MIUI) ugasi, a Wi-Fi je i dalje tu → ponovo ga uključi
 */
class AdbWifiService : Service() {

    companion object {
        private const val CHANNEL = "status"
        private const val NOTIF_ID = 1
        private const val MAX_TRIES = 6             // najviše pokušaja u prozoru
        private const val WINDOW_MS = 10 * 60_000L  // ...od 10 minuta

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, AdbWifiService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AdbWifiService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val wifiNetworks = mutableSetOf<Network>()
    private val tryTimes = ArrayDeque<Long>()
    private var gaveUp = false

    private val cm by lazy { getSystemService(ConnectivityManager::class.java) }
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    private val enableRunnable = object : Runnable {
        override fun run() {
            if (!Prefs.auto(this@AdbWifiService) || wifiNetworks.isEmpty()) {
                updateNotification(); return
            }
            if (Adb.isEnabled(this@AdbWifiService)) {
                updateNotification(); return
            }
            val now = SystemClock.elapsedRealtime()
            while (tryTimes.isNotEmpty() && now - tryTimes.first() > WINDOW_MS) tryTimes.removeFirst()
            if (tryTimes.size >= MAX_TRIES) {
                gaveUp = true          // sistem stalno gasi — ne borimo se do sledećeg povezivanja
                updateNotification(); return
            }
            tryTimes.addLast(now)
            Adb.enable(this@AdbWifiService)
            // proveri ponovo za nekoliko sekundi (sistem ume da ga odmah ugasi pri promeni mreže)
            handler.postDelayed(this, 5_000)
            updateNotification()
        }
    }

    private val settingObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (!Adb.isEnabled(this@AdbWifiService) && wifiNetworks.isNotEmpty() && !gaveUp) {
                schedule(3_000)
            }
            // mala pauza da se port stigne postaviti
            handler.postDelayed({ updateNotification() }, 1_500)
        }
    }

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                wifiNetworks.add(network)
                gaveUp = false
                tryTimes.clear()
                schedule(2_000)
            }
        }

        override fun onLost(network: Network) {
            handler.post {
                wifiNetworks.remove(network)
                updateNotification()
            }
        }
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(enableRunnable)
        handler.postDelayed(enableRunnable, delay)
    }

    override fun onCreate() {
        super.onCreate()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Status", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }

        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(req, netCallback)
        contentResolver.registerContentObserver(Adb.wifiUri, false, settingObserver)
        Adb.disableAuthTimeout(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        schedule(500)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { cm.unregisterNetworkCallback(netCallback) } catch (_: Exception) {}
        contentResolver.unregisterContentObserver(settingObserver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun statusText(): String = when {
        !Adb.hasPermission(this) -> "Nema dozvole — otvori aplikaciju"
        wifiNetworks.isEmpty() -> "Čeka Wi-Fi"
        Adb.isEnabled(this) -> "Uključen · " + (Adb.address(this) ?: "")
        gaveUp -> "Sistem ga stalno gasi — potvrdi mrežu ručno"
        else -> "Uključujem…"
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Wireless debugging")
            .setContentText(statusText())
            .setContentIntent(pi)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return b.build()
    }

    private fun updateNotification() {
        nm.notify(NOTIF_ID, buildNotification())
    }
}
