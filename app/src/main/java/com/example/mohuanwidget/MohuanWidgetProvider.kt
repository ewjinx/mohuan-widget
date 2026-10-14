package com.example.mohuanwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
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

        fun setSolidColor(r: Int, g: Int, b: Int, label: String): ByteArray {
            prefs.edit()
                .putBoolean("is_on", true)
                .putBoolean("is_rainbow", false)
                .putInt("r", r).putInt("g", g).putInt("b", b)
                .putString("status", "● ON • $label")
                .apply()
            return LedBleManager.cmdColor(r, g, b)
        }

        fun scaleColor(factor: Float, pct: String): ByteArray {
            val r = prefs.getInt("r", 255)
            val g = prefs.getInt("g", 160)
            val b = prefs.getInt("b", 80)
            prefs.edit()
                .putBoolean("is_on", true)
                .putBoolean("is_rainbow", false)
                .putString("status", "● ON • $pct BRIGHT")
                .apply()
            return LedBleManager.cmdColor(
                (r * factor).toInt().coerceIn(0, 255),
                (g * factor).toInt().coerceIn(0, 255),
                (b * factor).toInt().coerceIn(0, 255)
            )
        }

        val payload = when (intent.action) {
            ACTION_ON -> {
                prefs.edit().putBoolean("is_on", true).putString("status", "● POWER ON").apply()
                LedBleManager.CMD_ON
            }
            ACTION_OFF -> {
                prefs.edit().putBoolean("is_on", false).putString("status", "○ STANDBY").apply()
                LedBleManager.CMD_OFF
            }
            ACTION_DIM -> scaleColor(0.25f, "25%")
            ACTION_BRIGHT -> scaleColor(1.0f, "100%")
            ACTION_RED -> setSolidColor(255, 0, 40, "CRIMSON")
            ACTION_GREEN -> setSolidColor(0, 255, 110, "EMERALD")
            ACTION_BLUE -> setSolidColor(0, 110, 255, "ELECTRIC")
            ACTION_PURPLE -> setSolidColor(180, 0, 255, "CYBER")
            ACTION_WARM -> setSolidColor(255, 165, 60, "WARM GOLD")
            ACTION_RAINBOW -> {
                prefs.edit()
                    .putBoolean("is_on", true)
                    .putBoolean("is_rainbow", true)
                    .putString("status", "✨ FADE FX")
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
        val r = prefs.getInt("r", 0)
        val g = prefs.getInt("g", 245)
        val b = prefs.getInt("b", 155)
        val status = prefs.getString("status", "● MOHUAN STUDIO") ?: "● MOHUAN STUDIO"

        val bmp = renderNeonCard(isOn, isRainbow, r, g, b)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_mohuan).apply {
                setImageViewBitmap(R.id.img_bg, bmp)
                setTextViewText(R.id.txt_status, status)
                setOnClickPendingIntent(R.id.btn_on, pi(context, ACTION_ON))
                setOnClickPendingIntent(R.id.btn_off, pi(context, ACTION_OFF))
                setOnClickPendingIntent(R.id.btn_dim, pi(context, ACTION_DIM))
                setOnClickPendingIntent(R.id.btn_bright, pi(context, ACTION_BRIGHT))
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

    private fun renderNeonCard(isOn: Boolean, isRainbow: Boolean, r: Int, g: Int, b: Int): Bitmap {
        val size = 420
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(6f, 6f, size - 6f, size - 6f)
        val radius = 64f

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(Color.parseColor("#F50A0C12"), Color.parseColor("#F5121622"), Color.parseColor("#F50D101A")),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, basePaint)

        if (isOn) {
            val accentColor = if (isRainbow) Color.rgb(220, 40, 255) else Color.rgb(r, g, b)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    size * 0.5f, size * 0.18f, size * 0.75f,
                    intArrayOf(
                        Color.argb(115, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                        Color.argb(35, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, radius, radius, glowPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4.5f
                shader = if (isRainbow) {
                    LinearGradient(
                        0f, 0f, size.toFloat(), size.toFloat(),
                        intArrayOf(Color.parseColor("#FF2A85"), Color.parseColor("#8A2BE2"), Color.parseColor("#00F0FF")),
                        null, Shader.TileMode.CLAMP
                    )
                } else {
                    LinearGradient(
                        0f, 0f, size.toFloat(), size.toFloat(),
                        intArrayOf(
                            Color.argb(230, r, g, b),
                            Color.argb(70, 255, 255, 255),
                            Color.argb(180, r, g, b)
                        ),
                        null, Shader.TileMode.CLAMP
                    )
                }
            }
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
        } else {
            val offStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.parseColor("#28FFFFFF")
            }
            canvas.drawRoundRect(rect, radius, radius, offStroke)
        }

        return bmp
    }

    private fun pi(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, MohuanWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}