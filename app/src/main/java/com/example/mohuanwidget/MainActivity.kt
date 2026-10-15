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

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "MohuanLED & Haier AC Hub"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }

        val subtitle = TextView(this).apply {
            text = "Tip: Drag a 'Haier AC (2x2)' widget onto your home screen to assign a specific room name and IP to each widget."
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }

        val ipInput = EditText(this).apply {
            hint = "Default Haier AC IP (e.g. 192.168.100.45)"
            setText(prefs.getString("ac_ip", ""))
            setSingleLine(true)
        }

        val discoveredList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }

        val scanBtn = Button(this).apply {
            text = "Auto-Scan Wi-Fi for All Haier ACs"
            setOnClickListener {
                text = "Scanning subnet..."
                discoveredList.removeAllViews()
                CoroutineScope(Dispatchers.Main).launch {
                    val foundIps = HaierAcManager.scanAllHaierIps(applicationContext)
                    if (foundIps.isNotEmpty()) {
                        val firstIp: String = foundIps.first()
                        ipInput.setText(firstIp)
                        prefs.edit().putString("ac_ip", firstIp).apply()
                        text = "Found ${foundIps.size} AC(s) on Wi-Fi:"
                        foundIps.forEach { ip ->
                            val row = LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(0, 6, 0, 6)
                            }
                            val selectBtn = Button(this@MainActivity).apply {
                                text = "Use $ip"
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                setOnClickListener {
                                    ipInput.setText(ip)
                                    prefs.edit().putString("ac_ip", ip).apply()
                                }
                            }
                            val beepBtn = Button(this@MainActivity).apply {
                                text = "Beep Test"
                                setOnClickListener {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        HaierAcManager.sendDirect(ip = ip, isOn = true, temp = 24, mode = "Cool", fan = "Auto")
                                    }
                                }
                            }
                            row.addView(selectBtn)
                            row.addView(beepBtn)
                            discoveredList.addView(row)
                        }
                    } else {
                        text = "No open 56800 port found (Enter IP or Webhook)"
                    }
                }
            }
        }

        val webhookInput = EditText(this).apply {
            hint = "Optional: Default Scene / Webhook URL"
            setText(prefs.getString("ac_webhook", ""))
            setSingleLine(true)
        }

        val saveBtn = Button(this).apply {
            text = "Save Default AC Settings"
            setOnClickListener {
                prefs.edit()
                    .putString("ac_ip", ipInput.text.toString().trim())
                    .putString("ac_webhook", webhookInput.text.toString().trim())
                    .apply()
                Toast.makeText(this@MainActivity, "Saved! Add 2x2 Widgets to Home Screen", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(ipInput)
        root.addView(scanBtn)
        root.addView(discoveredList)
        root.addView(webhookInput)
        root.addView(saveBtn)
        setContentView(scroll)
    }
}