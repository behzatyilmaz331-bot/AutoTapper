package com.autotap.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

/**
 * Zamanlama:
 *   Start'a basildiginda bir sonraki 5'in kati dakikayi bekler
 *   (18:23 -> 18:25, 18:27 -> 18:30, 18:41 -> 18:45).
 *   O anda 90 saniye boyunca noktalara 90 ms araliklarla dokunur.
 *   Ardindan yine bir sonraki 5'lik dakikayi bekler. Sonsuz dongu.
 */
class AutoTapAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AutoTapper"

        /** Her dongude kac ms dokunulacak (1 dk 30 sn). */
        const val CYCLE_DURATION_MS = 90_000L

        /** Iki dokunus arasi sure. */
        const val TAP_INTERVAL_MS = 90L

        /** Tek bir dokunusun ekranda kalma suresi. */
        const val TAP_DWELL_MS = 30L

        /** Kac dakikada bir baslasin. */
        const val BOUNDARY_MINUTES = 5

        @Volatile
        var isRunning = false

        @Volatile
        var instance: AutoTapAccessibilityService? = null

        /** Overlay'in play/stop ikonunu guncellemesi icin. */
        var onStateChanged: (() -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    private var cycleEndTime = 0L
    private var pointIndex = 0
    private var tapping = false

    // ------------------------------------------------------------------
    // Servis yasam dongusu
    // ------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        TapPointStore.load(this)
        Log.d(TAG, "Erisilebilirlik servisi baglandi")
        onStateChanged?.invoke()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
        instance = null
    }

    // ------------------------------------------------------------------
    // Baslat / durdur
    // ------------------------------------------------------------------

    fun start() {
        if (isRunning) return
        if (TapPointStore.points.isEmpty()) {
            Log.e(TAG, "Hic nokta secilmemis")
            return
        }
        isRunning = true
        scheduleNextCycle()
        onStateChanged?.invoke()
    }

    fun stop() {
        isRunning = false
        tapping = false
        handler.removeCallbacksAndMessages(null)
        onStateChanged?.invoke()
    }

    /** Bir sonraki dongunun kac saniye sonra baslayacagini dondurur (arayuz icin). */
    fun secondsUntilNextCycle(): Long {
        val now = System.currentTimeMillis()
        return (nextBoundary(now) - now) / 1000L
    }

    // ------------------------------------------------------------------
    // Zamanlama
    // ------------------------------------------------------------------

    private fun scheduleNextCycle() {
        if (!isRunning) return
        val now = System.currentTimeMillis()
        val target = nextBoundary(now)
        val wait = target - now
        Log.d(TAG, "Sonraki dongu ${wait / 1000} sn sonra")
        handler.postDelayed({ beginTapping() }, wait)
    }

    /** Simdiki zamandan sonraki ilk "dakika % 5 == 0, saniye 00" anini bulur. */
    private fun nextBoundary(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.MINUTE, (cal.get(Calendar.MINUTE) / BOUNDARY_MINUTES) * BOUNDARY_MINUTES)
        while (cal.timeInMillis <= now) {
            cal.add(Calendar.MINUTE, BOUNDARY_MINUTES)
        }
        return cal.timeInMillis
    }

    // ------------------------------------------------------------------
    // Dokunma dongusu
    // ------------------------------------------------------------------

    private fun beginTapping() {
        if (!isRunning) return
        cycleEndTime = System.currentTimeMillis() + CYCLE_DURATION_MS
        pointIndex = 0
        tapping = true
        Log.d(TAG, "Dokunma basladi (90 sn)")
        tapTick()
    }

    private fun tapTick() {
        if (!isRunning || !tapping) return

        if (System.currentTimeMillis() >= cycleEndTime) {
            tapping = false
            Log.d(TAG, "Dokunma bitti, siradaki 5'lik dakika bekleniyor")
            scheduleNextCycle()
            return
        }

        val pts = TapPointStore.points
        if (pts.isEmpty()) {
            Log.e(TAG, "Nokta listesi bos, duruldu")
            stop()
            return
        }

        val p = pts[pointIndex % pts.size]
        pointIndex++
        performTap(p.x, p.y)

        handler.postDelayed({ tapTick() }, TAP_INTERVAL_MS)
    }

    private fun performTap(x: Float, y: Float) {
        // Sadece moveTo iceren Path "bos" sayilir ve StrokeDescription
        // IllegalArgumentException firlatir; 1 piksellik lineTo sart.
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }

        try {
            val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DWELL_MS)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture hatasi: ${e.message}")
        }
    }
}
