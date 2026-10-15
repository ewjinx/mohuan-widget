package com.example.mohuanwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*

class HaierConfigActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = getSharedPreferences("haier_prefs", Context.MODE_PRIVATE)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        scroll.addView(root)

        val header = TextView(this).apply {
            text = "Configure Haier AC Widget"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }

        val nameInput = EditText(this).apply {
            hint = "Room / AC Name (e.g. Bedroom AC, Lounge AC)"
            setText(prefs.getString("name_$appWidgetId", "Bedroom AC"))
            setSingleLine(true)
        }

        val ipInput = EditText(this).apply {
            hint = "AC Local IP (e.g. 192.168.100.45)"
            setText(prefs.getString("ip_$appWidgetId", prefs.getString("ac_ip", "")))
            setSingleLine(true)
        }

        val webhookInput = EditText(this).apply {
            hint = "Optional: Webhook URL for this AC"
            setText(prefs.getString("webhook_$appWidgetId", prefs.getString("ac_webhook", "")))
            setSingleLine(true)
        }

        val discoveredContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        val scanBtn = Button(this).apply {
            text = "Scan Wi-Fi for All Haier ACs"
            setOnClickListener {
                text = "Scanning Wi-Fi for all ACs..."
                discoveredContainer.removeAllViews()
                CoroutineScope(Dispatchers.Main).launch {
                    val ips = HaierAcManager.scanAllHaierIps(applicationContext)
                    if (ips.isEmpty()) {
                        text = "No open 56800 ports found (Type IP manually below)"
                    } else {
                        text = "Found ${ips.size} Haier AC(s) — Tap 'Beep' to Identify:"
                        ips.forEachIndexed { idx, foundIp ->
                            val row = LinearLayout(this@HaierConfigActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(0, 8, 0, 8)
                            }
                            val pickBtn = Button(this@HaierConfigActivity).apply {
                                text = "Use $foundIp"
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                setOnClickListener {
                                    ipInput.setText(foundIp)
                                    if (nameInput.text.isNullOrBlank()) {
                                        nameInput.setText("AC #${idx + 1}")
                                    }
                                }
                            }
                            val beepBtn = Button(this@HaierConfigActivity).apply {
                                text = "Beep Test"
                                setOnClickListener {
                                    Toast.makeText(this@HaierConfigActivity, "Pinging $foundIp...", Toast.LENGTH_SHORT).show()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        HaierAcManager.sendDirect(ip = foundIp, isOn = true, temp = 24, mode = "Cool", fan = "Auto")
                                    }
                                }
                            }
                            row.addView(pickBtn)
                            row.addView(beepBtn)
                            discoveredContainer.addView(row)
                        }
                    }
                }
            }
        }

        val saveBtn = Button(this).apply {
            text = "Save & Create Widget"
            setOnClickListener {
                val chosenName = nameInput.text.toString().trim().ifEmpty { "Haier AC" }
                val chosenIp = ipInput.text.toString().trim()
                val chosenHook = webhookInput.text.toString().trim()

                prefs.edit()
                    .putString("name_$appWidgetId", chosenName)
                    .putString("ip_$appWidgetId", chosenIp)
                    .putString("webhook_$appWidgetId", chosenHook)
                    .putBoolean("is_on_$appWidgetId", true)
                    .putInt("temp_$appWidgetId", 24)
                    .putString("mode_$appWidgetId", "Cool")
                    .putString("fan_$appWidgetId", "Auto")
                    .apply()

                val mgr = AppWidgetManager.getInstance(this@HaierConfigActivity)
                HaierWidgetProvider.updateSingleWidget(this@HaierConfigActivity, mgr, appWidgetId)

                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
                finish()
            }
        }

        root.addView(header)
        root.addView(nameInput)
        root.addView(scanBtn)
        root.addView(discoveredContainer)
        root.addView(ipInput)
        root.addView(webhookInput)
        root.addView(saveBtn)
        setContentView(scroll)
    }
}