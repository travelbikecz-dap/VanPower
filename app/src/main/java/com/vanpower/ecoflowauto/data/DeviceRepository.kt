package com.vanpower.ecoflowauto.data

import android.content.Context
import com.vanpower.ecoflowauto.ble.EcoflowBleService

class DeviceRepository(
    private val context: Context,
    private val stateHolder: DeviceStateHolder,
    private val bleServiceProvider: () -> EcoflowBleService?
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val telemetry = stateHolder.telemetry

    fun loadConfig(): DeviceConfig? {
        val mac = prefs.getString(KEY_MAC, null) ?: return null
        val serial = prefs.getString(KEY_SERIAL, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        return DeviceConfig(mac, serial, userId)
    }

    fun saveConfig(config: DeviceConfig) {
        prefs.edit()
            .putString(KEY_MAC, config.macAddress.trim())
            .putString(KEY_SERIAL, config.serialNumber.trim())
            .putString(KEY_USER_ID, config.userId.trim())
            .apply()
    }

    fun isConfigured(): Boolean = loadConfig()?.isValid == true

    fun connect() {
        val config = loadConfig() ?: return
        EcoflowBleService.start(context, config)
    }

    fun disconnect() {
        bleServiceProvider()?.disconnect()
    }

    fun togglePort(port: PortType) {
        bleServiceProvider()?.togglePort(port)
    }

    fun setAllOutputsEnabled(enabled: Boolean) {
        bleServiceProvider()?.setAllOutputsEnabled(enabled)
    }

    companion object {
        private const val PREFS_NAME = "ecoflow_config"
        private const val KEY_MAC = "mac"
        private const val KEY_SERIAL = "serial"
        private const val KEY_USER_ID = "user_id"
    }
}
