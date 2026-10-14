package com.example.mohuanwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MohuanWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_ON = "com.example.mohuanwidget.ACTION_ON"
        const val ACTION_OFF = "com.example.mohuanwidget.ACTION_OFF"
        const val ACTION_CYCLE_BRI = "com.example.mohuanwidget.ACTION_CYCLE_BRI"
        const val ACTION_RED = "com.example.mohuanwidget.ACTION_RED"
        const val ACTION_GREEN = "com.example.mohuanwidget.ACTION_GREEN"
        const val ACTION_BLUE = "com.example.mohuanwidget.ACTION_BLUE"
        const val ACTION_PURPLE = "com.example.mohuanwidget.ACTION_PURPLE"
        const val ACTION_WARM = "com.example.mohuanwidget.ACTION_WARM"
        const val ACTION_RAINBOW = "com.example.mohuanwidget.ACTION_RAINBOW"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences("mohuan_prefs", Context.MODE_PRIVATE)

        fun setSolidColor(r: Int, g: Int, b: Int, label: String, swatchIdx: Int): ByteArray {
            val factor = prefs.getFloat("bri_factor", 1.0f)
            val pct = prefs.getString("bri_label", "100%") ?: "100%"
            prefs.edit()
                .putBoolean("is_on", true)
                .putBoolean("is_rainbow", false)
                .putInt("swatch", swatchIdx)
                .putInt("r", r).putInt("g", g).putInt("b", b)
                .putString("color_name", label)
                .putString("status", "On • $label • $pct")
                .apply()
            return LedBleManager.cmdColor(
                (r * factor).toInt().coerceIn(0, 255),
                (g * factor).toInt().coerceIn(0, 255),
                (b * factor).toInt().coerceIn(0, 255)
            )
        }

        val payload = when (intent.action) {
            ACTION_ON -> {
                val label = prefs.getString("color_name", "Warm") ?: "Warm"
                val pct = prefs.getString("bri_label", "100%") ?: "100%"
                prefs.edit().putBoolean("is_on", true).putString("status", "On • $label • $pct").apply()
                LedBleManager.CMD_ON
            }
            ACTION_OFF -> {
                val currentlyOn = prefs.getBoolean("is_on", true)
                if (currentlyOn) {
                    prefs.edit().putBoolean("is_on", false).putString("status", "Off").apply()
                    LedBleManager.CMD_OFF
                } else {
                    val label = prefs.getString("color_name", "Warm") ?: "Warm"
                    val pct = prefs.getString("bri_label", "100%") ?: "100%"
                    prefs.edit().putBoolean("is_on", true).putString("status", "On • $label • $pct").apply()
                    LedBleManager.CMD_ON
                }
            }
            ACTION_CYCLE_BRI -> {
                val current = prefs.getString("bri_label", "100%") ?: "100%"
                val (nextLabel, nextFactor) = when (current) {
                    "100%" -> "25%" to 0.25f
                    "25%" -> "60%" to 0.60f
                    else -> "100%" to 1.0f
                }
                val r = prefs.getInt("r", 255)
                val g = prefs.getInt("g", 165)
                val b = prefs.getInt("b", 60)
                val label = prefs.getString("color_name", "Warm") ?: "Warm"
                prefs.edit()
                    .putBoolean("is_on", true)
                    .putBoolean("is_rainbow", false)
                    .putString("bri_label", nextLabel)
                    .putFloat("bri_factor", nextFactor)
                    .putString("status", "On • $label • $nextLabel")
                    .apply()
                LedBleManager.cmdColor(
                    (r * nextFactor).toInt().coerceIn(0, 255),
                    (g * nextFactor).toInt().coerceIn(0, 255),
                    (b * nextFactor).toInt().coerceIn(0, 255)
                )
            }
            ACTION_RED -> setSolidColor(255, 0, 40, "Red", 0)
            ACTION_GREEN -> setSolidColor(0, 255, 110, "Green", 1)
            ACTION_BLUE -> setSolidColor(0, 110, 255, "Blue", 2)
            ACTION_PURPLE -> setSolidColor(180, 0, 255, "Purple", 3)
            ACTION_WARM -> setSolidColor(255, 165, 60, "Warm", 4)
            ACTION_RAINBOW -> {
                prefs.edit()
                    .putBoolean("is_on", true)
                    .putBoolean("is_rainbow", true)
                    .putString("status", "On • Fade")
                    .apply()
                LedBleManager.cmdMode(0x01, 0x03)
            }
            else -> return
        }

        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, MohuanWidgetProvider::class.java))
        refreshAllWidgets(context, mgr, ids)

        val res = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { LedBleManager.sendPayload(context.applicationContext, payload) }
            finally { res.finish() }
        }
    }

    private fun refreshAllWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("mohuan_prefs", Context.MODE_PRIVATE)
        val isOn = prefs.getBoolean("is_on", true)
        val isRainbow = prefs.getBoolean("is_rainbow", false)
        val swatch = prefs.getInt("swatch", 4)
        val status = prefs.getString("status", "On • Warm • 100%") ?: "On • Warm • 100%"
        val briLabel = prefs.getString("bri_label", "100%") ?: "100%"

        val dots = listOf(R.id.dot_red, R.id.dot_green, R.id.dot_blue, R.id.dot_purple, R.id.dot_warm)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_mohuan).apply {
                setTextViewText(R.id.txt_status, status)
                setTextViewText(R.id.txt_bri, briLabel)

                setInt(R.id.btn_on, "setBackgroundResource",
                    if (isOn) R.drawable.bg_m3_header_on else R.drawable.bg_m3_header_off)
                setInt(R.id.btn_off, "setBackgroundResource",
                    if (isOn) R.drawable.circle_power_on else R.drawable.circle_power_off)
                setImageViewResource(R.id.img_power,
                    if (isOn) R.drawable.ic_power_on else R.drawable.ic_power_off)
                setInt(R.id.btn_rainbow, "setBackgroundResource",
                    if (isOn && isRainbow) R.drawable.bg_m3_bento_active else R.drawable.bg_m3_bento)

                dots.forEachIndexed { index, dotId ->
                    setViewVisibility(dotId, if (isOn && !isRainbow && index == swatch) View.VISIBLE else View.GONE)
                }

                setOnClickPendingIntent(R.id.btn_on, pi(context, ACTION_ON))
                setOnClickPendingIntent(R.id.btn_off, pi(context, ACTION_OFF))
                setOnClickPendingIntent(R.id.btn_bright, pi(context, ACTION_CYCLE_BRI))
                setOnClickPendingIntent(R.id.btn_red, pi(context, ACTION_RED))
                setOnClickPendingIntent(R.id.btn_green, pi(context, ACTION_GREEN))
                setOnClickPendingIntent(R.id.btn_blue, pi(context, ACTION_BLUE))
                setOnClickPendingIntent(R.id.btn_purple, pi(context, ACTION_PURPLE))
                setOnClickPendingIntent(R.id.btn_warm, pi(context, ACTION_WARM))
                setOnClickPendingIntent(R.id.btn_rainbow, pi(context, ACTION_RAINBOW))
            }
            mgr.updateAppWidget(id, views)
        }
    }

    private fun pi(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, MohuanWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}