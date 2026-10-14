package com.example.mohuanwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MohuanWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_ON = "com.example.mohuanwidget.ACTION_ON"
        const val ACTION_OFF = "com.example.mohuanwidget.ACTION_OFF"
        const val ACTION_DIM = "com.example.mohuanwidget.ACTION_DIM"
        const val ACTION_BRIGHT = "com.example.mohuanwidget.ACTION_BRIGHT"
        const val ACTION_RED = "com.example.mohuanwidget.ACTION_RED"
        const val ACTION_GREEN = "com.example.mohuanwidget.ACTION_GREEN"
        const val ACTION_BLUE = "com.example.mohuanwidget.ACTION_BLUE"
        const val ACTION_WARM = "com.example.mohuanwidget.ACTION_WARM"
        const val ACTION_RAINBOW = "com.example.mohuanwidget.ACTION_RAINBOW"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_mohuan).apply {
                setOnClickPendingIntent(R.id.btn_on, pi(context, ACTION_ON))
                setOnClickPendingIntent(R.id.btn_off, pi(context, ACTION_OFF))
                setOnClickPendingIntent(R.id.btn_dim, pi(context, ACTION_DIM))
                setOnClickPendingIntent(R.id.btn_bright, pi(context, ACTION_BRIGHT))
                setOnClickPendingIntent(R.id.btn_red, pi(context, ACTION_RED))
                setOnClickPendingIntent(R.id.btn_green, pi(context, ACTION_GREEN))
                setOnClickPendingIntent(R.id.btn_blue, pi(context, ACTION_BLUE))
                setOnClickPendingIntent(R.id.btn_warm, pi(context, ACTION_WARM))
                setOnClickPendingIntent(R.id.btn_rainbow, pi(context, ACTION_RAINBOW))
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences("mohuan_prefs", Context.MODE_PRIVATE)

        fun saveAndBuildColor(r: Int, g: Int, b: Int): ByteArray {
            prefs.edit().putInt("r", r).putInt("g", g).putInt("b", b).apply()
            return LedBleManager.cmdColor(r, g, b)
        }

        fun scaleSavedColor(factor: Float): ByteArray {
            val r = (prefs.getInt("r", 255) * factor).toInt().coerceIn(0, 255)
            val g = (prefs.getInt("g", 160) * factor).toInt().coerceIn(0, 255)
            val b = (prefs.getInt("b", 80) * factor).toInt().coerceIn(0, 255)
            return LedBleManager.cmdColor(r, g, b)
        }

        val payload = when (intent.action) {
            ACTION_ON -> LedBleManager.CMD_ON
            ACTION_OFF -> LedBleManager.CMD_OFF
            ACTION_DIM -> scaleSavedColor(0.25f)
            ACTION_BRIGHT -> scaleSavedColor(1.0f)
            ACTION_RED -> saveAndBuildColor(255, 0, 0)
            ACTION_GREEN -> saveAndBuildColor(0, 255, 0)
            ACTION_BLUE -> saveAndBuildColor(0, 80, 255)
            ACTION_WARM -> saveAndBuildColor(255, 160, 80)
            ACTION_RAINBOW -> LedBleManager.cmdMode(0x01, 0x03)
            else -> return
        }
        val res = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { LedBleManager.sendPayload(context.applicationContext, payload) }
            finally { res.finish() }
        }
    }

    private fun pi(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, MohuanWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}