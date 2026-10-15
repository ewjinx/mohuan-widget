package com.example.mohuanwidget

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                101
            )
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }

        val prefs = getSharedPreferences("haier_prefs", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "MohuanLED & Haier AC Hub"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        }

        val ipInput = EditText(this).apply {
            hint = "Haier AC Local IP (e.g. 192.168.100.45)"
            setText(prefs.getString("ac_ip", ""))
            setSingleLine(true)
        }

        val scanBtn = Button(this).apply {
            text = "Auto-Scan Wi-Fi for Haier AC (Port 56800)"
            setOnClickListener {
                text = "Scanning subnet..."
                CoroutineScope(Dispatchers.Main).launch {
                    val found = HaierAcManager.scanForHaierIp(applicationContext)
                    if (found != null) {
                        ipInput.setText(found)
                        prefs.edit().putString("ac_ip", found).apply()
                        text = "Found Haier AC at $found!"
                    } else {
                        text = "No open 56800 port found (Enter IP or Webhook)"
                    }
                }
            }
        }

        val webhookInput = EditText(this).apply {
            hint = "Optional: Scene / MacroDroid Webhook URL"
            setText(prefs.getString("ac_webhook", ""))
            setSingleLine(true)
        }

        val saveBtn = Button(this).apply {
            text = "Save Haier AC Settings"
            setOnClickListener {
                prefs.edit()
                    .putString("ac_ip", ipInput.text.toString().trim())
                    .putString("ac_webhook", webhookInput.text.toString().trim())
                    .apply()
                Toast.makeText(this@MainActivity, "Saved! Add Both 2x2 Widgets to Home Screen", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(title)
        root.addView(ipInput)
        root.addView(scanBtn)
        root.addView(webhookInput)
        root.addView(saveBtn)
        setContentView(root)
    }
}