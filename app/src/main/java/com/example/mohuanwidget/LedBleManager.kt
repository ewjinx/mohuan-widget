package com.example.mohuanwidget

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.*
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
object LedBleManager {
    private val TARGET_NAMES = setOf("BJ_LED", "BJ_LED_M")

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    val CMD_ON = bytes(0x69, 0x96, 0x06, 0x01, 0x01)
    val CMD_OFF = bytes(0x69, 0x96, 0x02, 0x01, 0x00)

    fun cmdColor(r: Int, g: Int, b: Int): ByteArray =
        bytes(0x69, 0x96, 0x05, 0x02, r, g, b, 0xFF)

    fun cmdMode(mode: Int, speed: Int = 0x03): ByteArray =
        bytes(0x69, 0x96, 0x03, 0x03, mode.coerceIn(0, 0x15), speed.coerceIn(1, 0x0A))

    suspend fun sendPayload(context: Context, payload: ByteArray) = withContext(Dispatchers.IO) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter ?: return@withContext
        val prefs = context.getSharedPreferences("mohuan_prefs", Context.MODE_PRIVATE)
        val savedMac = prefs.getString("last_mac", null)

        val device = if (!savedMac.isNullOrBlank()) {
            adapter.getRemoteDevice(savedMac)
        } else {
            scanForDevice(adapter)?.also {
                prefs.edit().putString("last_mac", it.address).apply()
            } ?: return@withContext
        }
        writeGattCommand(context, device, payload)
    }

    private suspend fun scanForDevice(adapter: BluetoothAdapter): BluetoothDevice? =
        withTimeoutOrNull(4000L) {
            suspendCancellableCoroutine { cont ->
                val scanner = adapter.bluetoothLeScanner ?: run {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val name = result.device.name ?: result.scanRecord?.deviceName
                        if (name != null && TARGET_NAMES.any { name.startsWith(it) }) {
                            scanner.stopScan(this)
                            if (cont.isActive) cont.resume(result.device)
                        }
                    }
                }
                scanner.startScan(callback)
                cont.invokeOnCancellation { scanner.stopScan(callback) }
            }
        }

    private suspend fun writeGattCommand(context: Context, device: BluetoothDevice, payload: ByteArray) =
        withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine<Unit> { cont ->
                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
                        else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            gatt.close()
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        val chars = gatt.services.flatMap { it.characteristics }
                        val target = chars.firstOrNull {
                            it.uuid.toString().startsWith("0000ee01") || it.uuid.toString().startsWith("0000ff01")
                        } ?: chars.firstOrNull {
                            (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                            (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                        }
                        if (target != null) {
                            target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            target.value = payload
                            gatt.writeCharacteristic(target)
                        } else gatt.disconnect()
                    }
                    override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) {
                        g.disconnect()
                    }
                }
                val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                cont.invokeOnCancellation { gatt.disconnect(); gatt.close() }
            }
        }
}