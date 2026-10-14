package com.example.mohuanwidget

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val info = TextView(this).apply {
            text = "1. Approve Bluetooth permissions when prompted.\n2. Force-close the official MohuanLED app.\n3. Add 'Mohuan Widget' to your Home Screen!"
            textSize = 16f
            setPadding(0, 0, 0, 40)
        }

        val testBtn = Button(this).apply {
            text = "Test Turn ON"
            setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    LedBleManager.sendPayload(applicationContext, LedBleManager.CMD_ON)
                }
            }
        }

        root.addView(info)
        root.addView(testBtn)
        setContentView(root)
    }
}