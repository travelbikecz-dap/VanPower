package com.vanpower.ecoflowauto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vanpower.ecoflowauto.EcoflowApp
import com.vanpower.ecoflowauto.R
import com.vanpower.ecoflowauto.ble.EcoflowBleScanner
import com.vanpower.ecoflowauto.ble.ScannedEcoflowDevice
import com.vanpower.ecoflowauto.data.DeviceConfig
import com.vanpower.ecoflowauto.databinding.ActivitySetupBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var repository: com.vanpower.ecoflowauto.data.DeviceRepository
    private var bleScanner: EcoflowBleScanner? = null
    private var scanDialog: AlertDialog? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            if (pendingScanAfterPermissions) {
                pendingScanAfterPermissions = false
                startBleScan()
            } else {
                startConnect()
            }
        } else {
            showStatus("Permisos BLE denegados (Bluetooth y ubicación)")
            Toast.makeText(this, "Permisos BLE necesarios", Toast.LENGTH_LONG).show()
        }
    }

    private var pendingScanAfterPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = (application as EcoflowApp).repository
        loadSavedConfig()

        binding.btnScan.setOnClickListener { requestPermissionsAndScan() }
        binding.btnSave.setOnClickListener { saveAndConnect() }
        binding.btnTest.setOnClickListener { testConnection() }

        lifecycleScope.launch {
            repository.telemetry.collectLatest { telemetry ->
                val text = when {
                    telemetry.connected ->
                        "Conectado · ${telemetry.batteryPercent}% · Solar ${telemetry.solarWatts}W · Red ${telemetry.acInputWatts}W · Consumo ${telemetry.outputWatts}W"
                    telemetry.statusMessage.isNotBlank() -> telemetry.statusMessage
                    telemetry.batteryPercent > 0 ->
                        "Reconectando… · ${telemetry.batteryPercent}% · Consumo ${telemetry.outputWatts}W"
                    repository.isConfigured() -> getString(R.string.status_connecting)
                    else -> getString(R.string.status_disconnected)
                }
                binding.statusText.text = text
            }
        }
    }

    override fun onDestroy() {
        bleScanner?.stop()
        scanDialog?.dismiss()
        super.onDestroy()
    }

    private fun loadSavedConfig() {
        repository.loadConfig()?.let { config ->
            binding.inputMac.setText(config.macAddress)
            binding.inputSerial.setText(config.serialNumber)
            binding.inputUserId.setText(config.userId)
        }
    }

    private fun requestPermissionsAndScan() {
        val missing = requiredBlePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startBleScan()
        } else {
            pendingScanAfterPermissions = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startBleScan() {
        bleScanner?.stop()
        binding.statusText.setText(R.string.scan_searching)

        bleScanner = EcoflowBleScanner(
            onResult = { devices -> showScanResults(devices) },
            onError = { message ->
                binding.statusText.setText(R.string.status_disconnected)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        )
        bleScanner?.start()
    }

    private fun showScanResults(devices: List<ScannedEcoflowDevice>) {
        if (bleScanner == null) return
        if (devices.isEmpty()) {
            binding.statusText.text = getString(R.string.scan_none)
            return
        }
        if (devices.size == 1) {
            applyScannedDevice(devices.first())
            return
        }
        scanDialog?.dismiss()
        val labels = devices.map { "${it.name}\n${it.macAddress}" }.toTypedArray()
        scanDialog = AlertDialog.Builder(this)
            .setTitle(R.string.scan_pick_title)
            .setItems(labels) { _, which -> applyScannedDevice(devices[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyScannedDevice(device: ScannedEcoflowDevice) {
        binding.inputMac.setText(device.macAddress)
        binding.statusText.text = "Seleccionado: ${device.name} (${device.macAddress})"
        Toast.makeText(this, "MAC copiada: ${device.macAddress}", Toast.LENGTH_SHORT).show()
    }

    private fun saveAndConnect() {
        val config = readConfigFromInputs() ?: return
        repository.saveConfig(config)
        requestBlePermissionsAndConnect()
    }

    private fun testConnection() {
        val config = readConfigFromInputs() ?: return
        repository.saveConfig(config)
        requestBlePermissionsAndConnect()
    }

    private fun startConnect() {
        showStatus(getString(R.string.status_connecting))
        repository.connect()
    }

    private fun showStatus(text: String) {
        binding.statusText.text = text
    }

    private fun readConfigFromInputs(): DeviceConfig? {
        val mac = binding.inputMac.text?.toString().orEmpty().trim()
        val serial = binding.inputSerial.text?.toString().orEmpty().trim()
        val userId = binding.inputUserId.text?.toString().orEmpty().trim()
        val config = DeviceConfig(mac, serial, userId)

        if (!config.isValid) {
            Toast.makeText(this, "MAC y serial son obligatorios", Toast.LENGTH_SHORT).show()
            return null
        }
        return config
    }

    private fun requestBlePermissionsAndConnect() {
        val missing = requiredBlePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startConnect()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requiredBlePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
