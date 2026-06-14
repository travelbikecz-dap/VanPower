package com.vanpower.ecoflowauto.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vanpower.ecoflowauto.EcoflowApp
import com.vanpower.ecoflowauto.R
import com.vanpower.ecoflowauto.data.DeviceConfig
import com.vanpower.ecoflowauto.data.DeviceStateHolder
import com.vanpower.ecoflowauto.data.PortType

class EcoflowBleService : Service() {

    private lateinit var stateHolder: DeviceStateHolder
    private var config: DeviceConfig? = null
    private var client: Delta3BleClient? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        stateHolder = (application as EcoflowApp).stateHolder
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mac = intent?.getStringExtra(EXTRA_MAC)
        val serial = intent?.getStringExtra(EXTRA_SERIAL)
        val userId = intent?.getStringExtra(EXTRA_USER_ID).orEmpty()

        if (mac != null && serial != null) {
            val newConfig = DeviceConfig(mac, serial, userId)
            val configChanged = config != null && config != newConfig
            config = newConfig
            if (client?.isConnected() == true) {
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)))
                return START_STICKY
            }
            when {
                client == null || configChanged -> connectFresh()
                else -> client?.connect()
            }
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)))
        }

        return START_STICKY
    }

    private fun connectFresh() {
        val cfg = config ?: return
        val previous = client
        client = Delta3BleClient(
            context = this,
            config = cfg,
            onTelemetry = { telemetry ->
                stateHolder.setTelemetry(telemetry)
                val text = when {
                    telemetry.connected ->
                        getString(R.string.status_connected) +
                            " · ${telemetry.batteryPercent}% · ${telemetry.solarWatts}W solar"
                    telemetry.statusMessage.isNotBlank() -> telemetry.statusMessage
                    else -> getString(R.string.status_connecting)
                }
                updateNotification(text)
            },
            onDisconnected = {
                stateHolder.update { copy(connected = false) }
                val msg = stateHolder.telemetry.value.statusMessage
                updateNotification(
                    if (msg.isNotBlank()) msg else getString(R.string.status_disconnected)
                )
            }
        )
        previous?.release()
        client?.connect()
    }

    fun disconnect() {
        client?.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun togglePort(port: PortType) {
        client?.togglePort(port)
    }

    fun setAllOutputsEnabled(enabled: Boolean) {
        client?.setAllOutputsEnabled(enabled)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        client?.disconnect()
        instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_battery)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "ecoflow_ble"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_MAC = "mac"
        private const val EXTRA_SERIAL = "serial"
        private const val EXTRA_USER_ID = "user_id"

        @Volatile
        private var instance: EcoflowBleService? = null

        fun getInstance(): EcoflowBleService? = instance

        fun start(context: Context, config: DeviceConfig) {
            val intent = Intent(context, EcoflowBleService::class.java).apply {
                putExtra(EXTRA_MAC, config.macAddress)
                putExtra(EXTRA_SERIAL, config.serialNumber)
                putExtra(EXTRA_USER_ID, config.userId)
            }
            context.startForegroundService(intent)
        }
    }
}
