package com.zzijad.adbwifiz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import java.net.Inet4Address

object Adb {
    private const val KEY_WIFI = "adb_wifi_enabled"
    private const val KEY_AUTH_TIMEOUT = "adb_allowed_connection_time"
    const val GRANT_CMD =
        "adb shell pm grant com.zzijad.adbwifiz android.permission.WRITE_SECURE_SETTINGS"

    val wifiUri get() = Settings.Global.getUriFor(KEY_WIFI)

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isEnabled(ctx: Context) =
        Settings.Global.getInt(ctx.contentResolver, KEY_WIFI, 0) == 1

    fun enable(ctx: Context): Boolean = try {
        Settings.Global.putInt(ctx.contentResolver, KEY_WIFI, 1)
    } catch (e: SecurityException) {
        false
    }

    /** Isključuje "posle 7 dana zaboravi uparene računare" (isto kao "Disable adb authorization timeout"). */
    fun disableAuthTimeout(ctx: Context): Boolean = try {
        Settings.Global.putLong(ctx.contentResolver, KEY_AUTH_TIMEOUT, 0L)
    } catch (e: SecurityException) {
        false
    }

    fun isWifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.any {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    fun wifiIp(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val addr = cm.getLinkProperties(n)?.linkAddresses
                ?.map { it.address }?.firstOrNull { it is Inet4Address }
            if (addr != null) return addr.hostAddress
        }
        return null
    }

    /** Port koji wireless debugging trenutno koristi (može biti nedostupan zbog SELinux-a). */
    fun port(): String? = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", "service.adb.tls.port"))
        val s = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        s.takeIf { it.isNotEmpty() && it != "0" && it != "-1" }
    } catch (e: Exception) {
        null
    }

    fun address(ctx: Context): String? {
        val ip = wifiIp(ctx) ?: return null
        val port = port()
        return if (port != null) "$ip:$port" else ip
    }
}

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("adbwifiz", Context.MODE_PRIVATE)
    fun auto(ctx: Context) = sp(ctx).getBoolean("auto", true)
    fun setAuto(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("auto", v).apply()
}
