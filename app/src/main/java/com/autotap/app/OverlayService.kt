package com.autotap.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Ekranin sag tarafinda duran kucuk toolbox ve dokunma noktasi isaretcileri.
 * Butun uygulamalarin uzerinde gorunur, surukleyerek tasinabilir.
 *
 *   >   Baslat / Durdur
 *   +   Yeni dokunma noktasi ekle
 *   -   Son noktayi sil
 *   O   Butun noktalari sil
 *   *   Ana ekrani ac
 *   +   (en alt) Toolbox'i suruklemek icin tutamac
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager

    private var toolbox: LinearLayout? = null
    private var playBtn: TextView? = null
    private val markers = ArrayList<TextView>()

    private var markerSize = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        markerSize = dp(46)

        startAsForeground()
        buildToolbox()
        restoreMarkers()

        AutoTapAccessibilityService.onStateChanged = { updatePlayIcon() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        AutoTapAccessibilityService.onStateChanged = null
        AutoTapAccessibilityService.instance?.stop()

        toolbox?.let { v -> runCatching { wm.removeView(v) } }
        for (m in markers) runCatching { wm.removeView(m) }
        markers.clear()
        toolbox = null
        playBtn = null
    }

    // ------------------------------------------------------------------
    // Bildirim (arka planda hayatta kalmak icin)
    // ------------------------------------------------------------------

    private fun startAsForeground() {
        val channelId = "autotap_overlay"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "AutoTapper", NotificationManager.IMPORTANCE_LOW)
        )

        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val n: Notification = Notification.Builder(this, channelId)
            .setContentTitle("AutoTapper acik")
            .setContentText("Toolbox ekranda. Kapatmak icin dokun.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ------------------------------------------------------------------
    // Toolbox
    // ------------------------------------------------------------------

    private fun buildToolbox() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6101010"))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(3), dp(6), dp(3), dp(6))
        }

        playBtn = addIcon(box, "\u25B6", "#3B82F6") { toggleRun() }
        addIcon(box, "\uFF0B", "#22C55E") { addMarker(null, null) }
        addIcon(box, "\u2212", "#EF4444") { removeLastMarker() }
        addIcon(box, "\u21BB", "#EAB308") { clearMarkers() }
        addIcon(box, "\u2699", "#9CA3AF") { openApp() }
        val handle = addIcon(box, "\u2725", "#6B7280") { }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(58)
            y = resources.displayMetrics.heightPixels / 3
        }

        wm.addView(box, params)
        toolbox = box
        makeDraggable(handle, box)
    }

    private fun addIcon(
        parent: LinearLayout,
        label: String,
        colorHex: String,
        onClick: () -> Unit
    ): TextView {
        val tv = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(colorHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
        parent.addView(tv, LinearLayout.LayoutParams(dp(46), dp(44)))
        return tv
    }

    // ------------------------------------------------------------------
    // Dokunma noktasi isaretcileri
    // ------------------------------------------------------------------

    private fun restoreMarkers() {
        TapPointStore.load(this)
        for (p in TapPointStore.points) {
            addMarker(
                (p.x - markerSize / 2f).toInt(),
                (p.y - markerSize / 2f).toInt()
            )
        }
    }

    private fun addMarker(px: Int?, py: Int?) {
        if (AutoTapAccessibilityService.isRunning) {
            toast("Once durdur, sonra nokta ekle")
            return
        }

        val index = markers.size + 1

        val tv = TextView(this).apply {
            text = index.toString()
            setTextColor(Color.parseColor("#FF7A00"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            isClickable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#403B82F6"))
                setStroke(dp(3), Color.parseColor("#3B82F6"))
            }
        }

        val lp = WindowManager.LayoutParams(
            markerSize,
            markerSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = px ?: (resources.displayMetrics.widthPixels / 2 - markerSize / 2)
            y = py ?: (resources.displayMetrics.heightPixels / 2 - markerSize / 2 + index * dp(14))
        }

        wm.addView(tv, lp)
        markers.add(tv)
        makeDraggable(tv, tv)
    }

    private fun removeLastMarker() {
        if (AutoTapAccessibilityService.isRunning) {
            toast("Once durdur")
            return
        }
        val last = markers.removeLastOrNull() ?: return
        runCatching { wm.removeView(last) }
        syncPoints()
    }

    private fun clearMarkers() {
        if (AutoTapAccessibilityService.isRunning) {
            toast("Once durdur")
            return
        }
        for (m in markers) runCatching { wm.removeView(m) }
        markers.clear()
        syncPoints()
        toast("Butun noktalar silindi")
    }

    private fun showMarkers(visible: Boolean) {
        for (m in markers) {
            m.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    /** Isaretcilerin merkez koordinatlarini depoya yazar. */
    private fun syncPoints() {
        TapPointStore.points.clear()
        for (m in markers) {
            val lp = m.layoutParams as WindowManager.LayoutParams
            TapPointStore.points.add(
                TapPoint(
                    (lp.x + markerSize / 2).toFloat(),
                    (lp.y + markerSize / 2).toFloat()
                )
            )
        }
        TapPointStore.save(this)
    }

    // ------------------------------------------------------------------
    // Baslat / durdur
    // ------------------------------------------------------------------

    private fun toggleRun() {
        val svc = AutoTapAccessibilityService.instance
        if (svc == null) {
            toast("Once Ayarlar > Erisilebilirlik'ten AutoTapper'i ac")
            return
        }

        if (AutoTapAccessibilityService.isRunning) {
            svc.stop()
            showMarkers(true)
            toast("Durduruldu")
        } else {
            if (markers.isEmpty()) {
                toast("Once + ile en az bir nokta ekle")
                return
            }
            syncPoints()
            showMarkers(false)   // isaretciler dokunuslari engellemesin
            svc.start()
            toast("Baslayacak: ${svc.secondsUntilNextCycle()} sn sonra")
        }
        updatePlayIcon()
    }

    private fun updatePlayIcon() {
        playBtn?.text = if (AutoTapAccessibilityService.isRunning) "\u25A0" else "\u25B6"
        playBtn?.setTextColor(
            Color.parseColor(if (AutoTapAccessibilityService.isRunning) "#EF4444" else "#3B82F6")
        )
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    private fun makeDraggable(handle: View, target: View) {
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f

        handle.setOnTouchListener { _, e ->
            val lp = target.layoutParams as WindowManager.LayoutParams
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downX = e.rawX
                    downY = e.rawY
                    false   // tiklamalar da calissin
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt()
                    lp.y = startY + (e.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(target, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    syncPoints()
                    false
                }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
