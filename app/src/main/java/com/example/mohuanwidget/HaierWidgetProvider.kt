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

class HaierWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_POWER = "com.example.mohuanwidget.AC_POWER"
        const val ACTION_HEADER_ON = "com.example.mohuanwidget.AC_HEADER_ON"
        const val ACTION_TEMP_UP = "com.example.mohuanwidget.AC_TEMP_UP"
        const val ACTION_TEMP_DOWN = "com.example.mohuanwidget.AC_TEMP_DOWN"
        const val ACTION_MODE = "com.example.mohuanwidget.AC_MODE"
        const val ACTION_FAN = "com.example.mohuanwidget.AC_FAN"

        fun updateSingleWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("haier_prefs", Context.MODE_PRIVATE)
            val title = prefs.getString("name_$widgetId", "Haier AC") ?: "Haier AC"
            val isOn = prefs.getBoolean("is_on_$widgetId", true)
            val temp = prefs.getInt("temp_$widgetId", 24)
            val mode = prefs.getString("mode_$widgetId", "Cool") ?: "Cool"
            val fan = prefs.getString("fan_$widgetId", "Auto") ?: "Auto"
            val statusText = if (isOn) "$mode • ${temp}°C • $fan" else "Off"

            val views = RemoteViews(context.packageName, R.layout.widget_haier).apply {
                setTextViewText(R.id.ac_txt_title, title)
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

                setOnClickPendingIntent(R.id.ac_btn_header, pi(context, ACTION_HEADER_ON, widgetId))
                setOnClickPendingIntent(R.id.ac_btn_power, pi(context, ACTION_POWER, widgetId))
                setOnClickPendingIntent(R.id.ac_btn_minus, pi(context, ACTION_TEMP_DOWN, widgetId))
                setOnClickPendingIntent(R.id.ac_btn_plus, pi(context, ACTION_TEMP_UP, widgetId))
                setOnClickPendingIntent(R.id.ac_btn_mode, pi(context, ACTION_MODE, widgetId))
                setOnClickPendingIntent(R.id.ac_btn_fan, pi(context, ACTION_FAN, widgetId))
            }
            mgr.updateAppWidget(widgetId, views)
        }

        private fun pi(context: Context, action: String, widgetId: Int): PendingIntent {
            val intent = Intent(context, HaierWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                (action.hashCode() * 31) + widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences("haier_prefs", Context.MODE_PRIVATE).edit()
        for (id in appWidgetIds) {
            editor.remove("name_$id").remove("ip_$id").remove("webhook_$id")
                .remove("is_on_$id").remove("temp_$id").remove("mode_$id").remove("fan_$id")
        }
        editor.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val prefs = context.getSharedPreferences("haier_prefs", Context.MODE_PRIVATE)
        var isOn = prefs.getBoolean("is_on_$widgetId", true)
        var temp = prefs.getInt("temp_$widgetId", 24)
        var mode = prefs.getString("mode_$widgetId", "Cool") ?: "Cool"
        var fan = prefs.getString("fan_$widgetId", "Auto") ?: "Auto"

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
            .putBoolean("is_on_$widgetId", isOn)
            .putInt("temp_$widgetId", temp)
            .putString("mode_$widgetId", mode)
            .putString("fan_$widgetId", fan)
            .apply()

        val mgr = AppWidgetManager.getInstance(context)
        updateSingleWidget(context, mgr, widgetId)

        val res = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HaierAcManager.sendAcStateForWidget(
                    context.applicationContext,
                    widgetId, isOn, temp, mode, fan
                )
            } finally {
                res.finish()
            }
        }
    }
}