package com.example.mohuanwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HaierWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_POWER = "com.example.mohuanwidget.AC_POWER"
        const val ACTION_HEADER_ON = "com.example.mohuanwidget.AC_HEADER_ON"
        const val ACTION_TEMP_UP = "com.example.mohuanwidget.AC_TEMP_UP"
        const val ACTION_TEMP_DOWN = "com.example.mohuanwidget.AC_TEMP_DOWN"
        const val ACTION_MODE = "com.example.mohuanwidget.AC_MODE"
        const val ACTION_FAN = "com.example.mohuanwidget.AC_FAN"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences("haier_prefs", Context.MODE_PRIVATE)
        var isOn = prefs.getBoolean("is_on", true)
        var temp = prefs.getInt("temp", 24)
        var mode = prefs.getString("mode", "Cool") ?: "Cool"
        var fan = prefs.getString("fan", "Auto") ?: "Auto"

        when (intent.action) {
            ACTION_HEADER_ON -> isOn = true
            ACTION_POWER -> isOn = !isOn
            ACTION_TEMP_UP -> {
                isOn = true
                temp = (temp + 1).coerceIn(16, 30)
            }
            ACTION_TEMP_DOWN -> {
                isOn = true
                temp = (temp - 1).coerceIn(16, 30)
            }
            ACTION_MODE -> {
                isOn = true
                mode = when (mode) {
                    "Cool" -> "Dry"
                    "Dry" -> "Fan"
                    else -> "Cool"
                }
            }
            ACTION_FAN -> {
                isOn = true
                fan = when (fan) {
                    "Auto" -> "Low"
                    "Low" -> "Med"
                    "Med" -> "High"
                    else -> "Auto"
                }
            }
            else -> return
        }

        prefs.edit()
            .putBoolean("is_on", isOn)
            .putInt("temp", temp)
            .putString("mode", mode)
            .putString("fan", fan)
            .apply()

        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, HaierWidgetProvider::class.java))
        refreshWidgets(context, mgr, ids)

        val res = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HaierAcManager.sendAcState(context.applicationContext, isOn, temp, mode, fan)
            } finally {
                res.finish()
            }
        }
    }

    private fun refreshWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("haier_prefs", Context.MODE_PRIVATE)
        val isOn = prefs.getBoolean("is_on", true)
        val temp = prefs.getInt("temp", 24)
        val mode = prefs.getString("mode", "Cool") ?: "Cool"
        val fan = prefs.getString("fan", "Auto") ?: "Auto"
        val statusText = if (isOn) "$mode • ${temp}°C • $fan" else "Off"

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_haier).apply {
                setTextViewText(R.id.ac_txt_status, statusText)
                setTextViewText(R.id.ac_txt_temp, "${temp}°C")
                setTextViewText(R.id.ac_txt_mode, mode)
                setTextViewText(R.id.ac_txt_fan, fan)

                setInt(R.id.ac_btn_header, "setBackgroundResource",
                    if (isOn) R.drawable.bg_m3_header_on else R.drawable.bg_m3_header_off)
                setInt(R.id.ac_btn_power, "setBackgroundResource",
                    if (isOn) R.drawable.circle_power_on else R.drawable.circle_power_off)
                setImageViewResource(R.id.ac_img_power,
                    if (isOn) R.drawable.ic_power_on else R.drawable.ic_power_off)

                setOnClickPendingIntent(R.id.ac_btn_header, pi(context, ACTION_HEADER_ON))
                setOnClickPendingIntent(R.id.ac_btn_power, pi(context, ACTION_POWER))
                setOnClickPendingIntent(R.id.ac_btn_minus, pi(context, ACTION_TEMP_DOWN))
                setOnClickPendingIntent(R.id.ac_btn_plus, pi(context, ACTION_TEMP_UP))
                setOnClickPendingIntent(R.id.ac_btn_mode, pi(context, ACTION_MODE))
                setOnClickPendingIntent(R.id.ac_btn_fan, pi(context, ACTION_FAN))
            }
            mgr.updateAppWidget(id, views)
        }
    }

    private fun pi(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, HaierWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}