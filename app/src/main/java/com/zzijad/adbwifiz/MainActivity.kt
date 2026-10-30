package com.zzijad.adbwifiz

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvPerm: TextView
    private lateinit var tvWifi: TextView
    private lateinit var tvDebug: TextView
    private lateinit var tvAddr: TextView
    private lateinit var permBox: LinearLayout
    private lateinit var swAuto: Switch

    private val ticker = object : Runnable {
        override fun run() {
            refresh(); handler.postDelayed(this, 2_000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        fun title(t: String) = TextView(this).apply {
            text = t; textSize = 18f; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(6))
        }.also { root.addView(it) }

        fun line() = TextView(this).apply { textSize = 16f; setPadding(0, dp(2), 0, dp(2)) }
            .also { root.addView(it) }

        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }

        TextView(this).apply {
            text = "AdbWiFiZ"; textSize = 26f; setTypeface(typeface, Typeface.BOLD)
        }.also { root.addView(it) }
        TextView(this).apply {
            text = "Automatski uključuje Wireless debugging kad je telefon na Wi-Fi mreži."
            textSize = 14f
        }.also { root.addView(it) }

        title("Status")
        tvPerm = line(); tvWifi = line(); tvDebug = line(); tvAddr = line()

        // --- Uputstvo za dozvolu (prikazuje se samo dok dozvola ne postoji) ---
        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(permBox)
        permBox.addView(TextView(this).apply {
            text = "\nPotrebna je jednokratna dozvola. Poveži telefon kablom sa računarom " +
                "i pokreni (na Xiaomiju prvo uključi „USB debugging (Security settings)“ u Developer options):"
            textSize = 14f
        })
        permBox.addView(TextView(this).apply {
            text = Adb.GRANT_CMD; textSize = 13f; typeface = Typeface.MONOSPACE
            setTextIsSelectable(true); setPadding(0, dp(8), 0, dp(8))
        })
        permBox.addView(button("Kopiraj komandu") {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("adb", Adb.GRANT_CMD))
            Toast.makeText(this, "Kopirano", Toast.LENGTH_SHORT).show()
        })

        title("Podešavanja")
        swAuto = Switch(this).apply {
            text = "Automatski uključuj"; textSize = 16f
            isChecked = Prefs.auto(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setAuto(this@MainActivity, on)
                if (on) startServiceIfPossible() else AdbWifiService.stop(this@MainActivity)
                refresh()
            }
        }
        root.addView(swAuto)

        root.addView(button("Uključi sada") {
            if (!Adb.hasPermission(this)) toast("Nema dozvole (vidi uputstvo gore)")
            else if (!Adb.isWifiConnected(this)) toast("Telefon nije na Wi-Fi mreži")
            else { Adb.enable(this); handler.postDelayed({ refresh() }, 1_500) }
        })

        title("Xiaomi — da ga sistem ne ugasi")
        root.addView(button("1. Baterija: bez ograničenja") { requestBatteryExemption() })
        root.addView(button("2. Autostart (MIUI)") { openAutostart() })
        root.addView(button("Developer options") {
            tryStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        })
        root.addView(TextView(this).apply {
            text = "\nSavet: u Wireless debugging prvi put ručno potvrdi „Uvek dozvoli na ovoj mreži“ " +
                "za svaku Wi-Fi mrežu (kuća, škola). Bez toga Android ne dozvoljava automatsko uključivanje.\n\n" +
                "Takođe u listi skorašnjih aplikacija zaključaj AdbWiFiZ (katanac)."
            textSize = 13f
        })

        setContentView(ScrollView(this).apply { addView(root) })

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.auto(this)) startServiceIfPossible()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun startServiceIfPossible() {
        if (Adb.hasPermission(this)) {
            try { AdbWifiService.start(this) } catch (e: Exception) { toast("Servis nije pokrenut: ${e.message}") }
        }
    }

    private fun refresh() {
        val perm = Adb.hasPermission(this)
        val wifi = Adb.isWifiConnected(this)
        val on = Adb.isEnabled(this)
        tvPerm.text = (if (perm) "✅" else "❌") + "  Dozvola WRITE_SECURE_SETTINGS"
        tvWifi.text = (if (wifi) "✅" else "⚪") + "  Wi-Fi " + (if (wifi) "povezan" else "nije povezan")
        tvDebug.text = (if (on) "✅" else "⚪") + "  Wireless debugging " + (if (on) "uključen" else "isključen")
        val addr = if (on) Adb.address(this) else null
        tvAddr.text = if (addr != null) "     $addr" else ""
        tvAddr.visibility = if (addr != null) View.VISIBLE else View.GONE
        permBox.visibility = if (perm) View.GONE else View.VISIBLE
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("Već je bez ograničenja")
            // MIUI ima i svoju štednju — otvori detalje aplikacije
            tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun openAutostart() {
        val i = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        )
        if (!tryStart(i)) {
            tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun tryStart(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (e: Exception) {
        false
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
