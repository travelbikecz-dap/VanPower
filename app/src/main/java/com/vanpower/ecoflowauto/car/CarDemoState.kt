package com.vanpower.ecoflowauto.car

import com.vanpower.ecoflowauto.data.Delta3Telemetry
import com.vanpower.ecoflowauto.data.PortType

object CarDemoState {
    private var acEnabled = true
    private var usbEnabled = true
    private var dcEnabled = false
    private var powerOn = true

    fun telemetry(): Delta3Telemetry = Delta3Telemetry(
        batteryPercent = 78,
        solarWatts = 145,
        chargeRemainingMinutes = 105,
        dischargeRemainingMinutes = 200,
        inputWatts = 145,
        outputWatts = 320,
        acEnabled = acEnabled,
        usbEnabled = usbEnabled,
        dcEnabled = dcEnabled,
        powerOn = powerOn,
        connected = true
    )

    fun setPort(port: PortType, enabled: Boolean) {
        when (port) {
            PortType.AC -> acEnabled = enabled
            PortType.USB -> usbEnabled = enabled
            PortType.DC -> dcEnabled = enabled
            PortType.POWER -> powerOn = enabled
        }
    }

    fun togglePort(port: PortType) {
        when (port) {
            PortType.AC -> acEnabled = !acEnabled
            PortType.USB -> usbEnabled = !usbEnabled
            PortType.DC -> dcEnabled = !dcEnabled
            PortType.POWER -> powerOn = !powerOn
        }
    }
}
