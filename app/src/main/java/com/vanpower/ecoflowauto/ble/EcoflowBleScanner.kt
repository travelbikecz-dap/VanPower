package com.vanpower.ecoflowauto.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper

data class ScannedEcoflowDevice(
    val name: String,
    val macAddress: String
)

class EcoflowBleScanner(
    private val onResult: (List<ScannedEcoflowDevice>) -> Unit,
    private val onError: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val found = linkedMapOf<String, ScannedEcoflowDevice>()
    private var scanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName ?: return
            if (!isEcoflowName(name)) return
            val mac = device.address ?: return
            found[mac] = ScannedEcoflowDevice(name = name, macAddress = mac)
            publish()
        }

        override fun onScanFailed(errorCode: Int) {
            onError("Escaneo BLE falló (código $errorCode)")
            stop()
        }
    }

    @SuppressLint("MissingPermission")
    fun start(durationMs: Long = 12_000) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            onError("Activa el Bluetooth del móvil")
            return
        }
        stop()
        found.clear()
        scanning = true
        adapter.bluetoothLeScanner.startScan(scanCallback)
        handler.postDelayed({ stop() }, durationMs)
        publish()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning) return
        scanning = false
        handler.removeCallbacksAndMessages(null)
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        publish()
    }

    private fun publish() {
        val devices = found.values.sortedBy { it.name }
        onResult(devices)
    }

    companion object {
        fun isEcoflowName(name: String): Boolean {
            val upper = name.uppercase()
            return upper.startsWith("EF-") || upper.startsWith("ECOFLOW")
        }
    }
}
