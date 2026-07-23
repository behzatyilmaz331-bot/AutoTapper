package com.autotap.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = dp(22)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "AutoTapper"
            textSize = 28f
            setTextColor(Color.parseColor("#111827"))
        })

        root.addView(TextView(this).apply {
            text = "Her 5 dakikanin basinda (\u2026:00, :05, :10, :15 \u2026) " +
                "1 dakika 30 saniye boyunca sectigin noktalara 90 ms araliklarla dokunur.\n\n" +
                "Ornek: 18:23'te baslattin \u2192 18:25:00'da calisir, 18:26:30'da durur, " +
                "18:30:00'da tekrar calisir."
            textSize = 15f
            setPadding(0, dp(14), 0, dp(20))
        })

        statusView = TextView(this).apply {
            textSize = 15f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.parseColor("#F3F4F6"))
        }
        root.addView(statusView, lp())

        root.addView(button("1) Erisilebilirlik iznini ver") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(button("2) Diger uygulamalarin uzerinde goster") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })

        root.addView(button("3) Toolbox'i baslat") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Once 2. adimdaki izni ver", Toast.LENGTH_LONG).show()
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                Toast.makeText(this, "Toolbox acildi", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            }
        })

        root.addView(button("Toolbox'i kapat") {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Toolbox kapatildi", Toast.LENGTH_SHORT).show()
        })

        root.addView(TextView(this).apply {
            text = "Kullanim:\n" +
                "\u2022 Toolbox'taki + ile nokta ekle, daireleri parmagini basili tutup surukle.\n" +
                "\u2022 \u2212 son noktayi siler, \u21BB hepsini siler.\n" +
                "\u2022 \u25B6 ile baslat, sonra istedigin uygulamaya gec.\n" +
                "\u2022 En alttaki dort yonlu ok toolbox'i tasir.\n\n" +
                "Not: Pil optimizasyonundan AutoTapper'i hariç tut ve ekranin kapanmamasina dikkat et."
            textSize = 14f
            setPadding(0, dp(24), 0, 0)
            setTextColor(Color.parseColor("#374151"))
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val acc = AutoTapAccessibilityService.instance != null
        val overlay = Settings.canDrawOverlays(this)
        val running = AutoTapAccessibilityService.isRunning

        statusView.text = buildString {
            append(if (acc) "\u2713" else "\u2717").append(" Erisilebilirlik servisi\n")
            append(if (overlay) "\u2713" else "\u2717").append(" Ustte gosterme izni\n")
            append(if (running) "\u25B6 Calisiyor" else "\u25A0 Durdu")
            append("  \u2022  ")
            append("${TapPointStore.points.size} nokta")
        }
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            layoutParams = lp().apply { topMargin = dp(10) }
        }
    }

    private fun lp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
