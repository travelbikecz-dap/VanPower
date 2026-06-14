package com.vanpower.ecoflowauto.car

import com.vanpower.ecoflowauto.EcoflowApp
import com.vanpower.ecoflowauto.data.Delta3Telemetry

internal data class CarDisplayState(
    val telemetry: Delta3Telemetry,
    val isDemo: Boolean
)

internal object CarTelemetryProvider {
    fun displayState(app: EcoflowApp): CarDisplayState {
        val live = app.stateHolder.telemetry.value
        return if (live.connected) {
            CarDisplayState(live, isDemo = false)
        } else {
            CarDisplayState(CarDemoState.telemetry(), isDemo = true)
        }
    }
}
