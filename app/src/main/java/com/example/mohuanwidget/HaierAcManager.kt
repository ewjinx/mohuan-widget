package com.example.mohuanwidget

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

object HaierAcManager {
    private const val HAIER_PORT = 56800

    suspend fun sendAcStateForWidget(
        context: Context,
        widgetId: Int,
        isOn: Boolean,
        temp: Int,
        mode: String,
        fan: String
    ) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("haier_prefs", Context.MODE_PRIVATE)
        val ip = prefs.getString("ip_$widgetId", prefs.getString("ac_ip", ""))?.trim().orEmpty()
        val webhook = prefs.getString("webhook_$widgetId", prefs.getString("ac_webhook", ""))?.trim().orEmpty()
        val roomName = prefs.getString("name_$widgetId", "Haier AC") ?: "Haier AC"

        sendDirect(ip, webhook, roomName, isOn, temp, mode, fan)
    }

    suspend fun sendDirect(
        ip: String,
        webhook: String = "",
        roomName: String = "Haier AC",
        isOn: Boolean = true,
        temp: Int = 24,
        mode: String = "Cool",
        fan: String = "Auto"
    ) = withContext(Dispatchers.IO) {
        if (webhook.isNotEmpty()) {
            runCatching {
                val sep = if (webhook.contains("?")) "&" else "?"
                val encodedName = java.net.URLEncoder.encode(roomName, "UTF-8")
                val fullUrl = "${webhook}${sep}unit=$encodedName&ip=$ip&power=${if (isOn) "on" else "off"}&temp=$temp&mode=$mode&fan=$fan"
                val conn = URL(fullUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                conn.responseCode
                conn.disconnect()
            }
        }

        if (ip.isNotEmpty()) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, HAIER_PORT), 2000)
                    val out = socket.getOutputStream()
                    val frame = buildUhomeFrame(isOn, temp, mode, fan)
                    out.write(frame)
                    out.flush()
                }
            }
        }
    }

    private fun buildUhomeFrame(isOn: Boolean, temp: Int, mode: String, fan: String): ByteArray {
        return if (!isOn) {
            byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(), 0x0A, 0x40, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01, 0x5D, 0x01, 0x00, 0xA9.toByte()
            )
        } else {
            val tempOffset = (temp.coerceIn(16, 30) - 16).toByte()
            val modeByte: Byte = when (mode) {
                "Cool" -> 0x02
                "Dry" -> 0x04
                "Fan" -> 0x06
                else -> 0x00
            }
            val fanByte: Byte = when (fan) {
                "Low" -> 0x03
                "Med" -> 0x02
                "High" -> 0x01
                else -> 0x05
            }
            val body = byteArrayOf(
                0x0A, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x4D, 0x01, tempOffset, modeByte, fanByte
            )
            val checksum = (body.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
            byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + body + checksum
        }
    }

    // Scans entire /24 subnet and returns ALL Haier ACs listening on port 56800
    suspend fun scanAllHaierIps(context: Context): List<String> = withContext(Dispatchers.IO) {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wm.connectionInfo.ipAddress
        if (ipInt == 0) return@withContext emptyList()
        val prefix = String.format(
            "%d.%d.%d.",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff
        )
        coroutineScope {
            (2..254).map { host ->
                async {
                    val candidate = "$prefix$host"
                    try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(candidate, HAIER_PORT), 240)
                            candidate
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}