package com.vanpower.ecoflowauto.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceStateHolder {
    private val _telemetry = MutableStateFlow(Delta3Telemetry())
    val telemetry: StateFlow<Delta3Telemetry> = _telemetry.asStateFlow()

    fun update(block: Delta3Telemetry.() -> Delta3Telemetry) {
        _telemetry.value = _telemetry.value.block()
    }

    fun setTelemetry(value: Delta3Telemetry) {
        _telemetry.value = value
    }
}
