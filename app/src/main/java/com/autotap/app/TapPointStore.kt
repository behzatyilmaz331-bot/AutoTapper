package com.autotap.app

import android.content.Context

/** Ekranda secilen tek bir dokunma noktasi (mutlak ekran koordinati). */
data class TapPoint(val x: Float, val y: Float)

/**
 * Noktalarin ortak deposu.
 * Hem OverlayService (gorsel isaretciler) hem de AccessibilityService
 * (dokunma islemi) ayni listeyi kullanir.
 */
object TapPointStore {

    private const val PREFS = "autotap_prefs"
    private const val KEY_POINTS = "points"

    val points = ArrayList<TapPoint>()

    fun load(context: Context) {
        points.clear()
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_POINTS, "") ?: ""
        if (raw.isEmpty()) return

        for (part in raw.split(";")) {
            val xy = part.split(",")
            if (xy.size != 2) continue
            val x = xy[0].toFloatOrNull() ?: continue
            val y = xy[1].toFloatOrNull() ?: continue
            points.add(TapPoint(x, y))
        }
    }

    fun save(context: Context) {
        val raw = points.joinToString(";") { p -> "${p.x},${p.y}" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POINTS, raw)
            .apply()
    }
}
