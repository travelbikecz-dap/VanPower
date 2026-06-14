package com.vanpower.ecoflowauto.data

data class DeviceConfig(
    val macAddress: String,
    val serialNumber: String,
    val userId: String
) {
    val isValid: Boolean
        get() = macAddress.isNotBlank() && serialNumber.isNotBlank()
}

data class Delta3Telemetry(
    val batteryPercent: Int = 0,
    val solarWatts: Int = 0,
    val acInputWatts: Int = 0,
    val chargeRemainingMinutes: Int? = null,
    val dischargeRemainingMinutes: Int? = null,
    val inputWatts: Int = 0,
    val outputWatts: Int = 0,
    val acEnabled: Boolean = false,
    val usbEnabled: Boolean = false,
    val dcEnabled: Boolean = false,
    val powerOn: Boolean = true,
    val connected: Boolean = false,
    val statusMessage: String = ""
)

enum class PortType {
    AC, USB, DC, POWER
}
