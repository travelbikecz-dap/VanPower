package com.vanpower.ecoflowauto.car.screens

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.vanpower.ecoflowauto.R
import com.vanpower.ecoflowauto.car.CarAppAccess
import com.vanpower.ecoflowauto.car.CarDemoState
import com.vanpower.ecoflowauto.car.CarTelemetryProvider
import com.vanpower.ecoflowauto.car.CarTimeFormat
import com.vanpower.ecoflowauto.data.Delta3Telemetry
import com.vanpower.ecoflowauto.data.DeviceRepository
import com.vanpower.ecoflowauto.data.PortType
import kotlinx.coroutines.launch

class DashboardScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycleScope.launch {
            val app = CarAppAccess.app(carContext) ?: return@launch
            app.stateHolder.telemetry.collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            buildTemplate()
        } catch (e: Exception) {
            Log.e(TAG, "onGetTemplate failed", e)
            MessageScreens.loadError(carContext)
        }
    }

    private fun buildTemplate(): Template {
        val app = CarAppAccess.app(carContext)
            ?: return MessageScreens.loadError(carContext)
        val repository = app.repository

        if (!repository.isConfigured()) {
            return MessageScreens.notConfigured(carContext)
        }

        val display = CarTelemetryProvider.displayState(app)
        val telemetry = display.telemetry

        val list = ItemList.Builder()
            .addItem(batteryStatusRow(telemetry, display.isDemo, repository))
            .addItem(powerStatusRow(telemetry))
            .addItem(remainingTimeRow(telemetry))
            .addItem(controlRow(R.string.car_ac, telemetry.acEnabled, R.drawable.ic_ac, PortType.AC, display.isDemo, repository))
            .addItem(controlRow(R.string.car_usb, telemetry.usbEnabled, R.drawable.ic_usb, PortType.USB, display.isDemo, repository))
            .addItem(controlRow(R.string.car_dc, telemetry.dcEnabled, R.drawable.ic_dc, PortType.DC, display.isDemo, repository))
            .build()

        return ListTemplate.Builder()
            .setTitle(buildTitle(display.isDemo))
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list)
            .build()
    }

    private fun buildTitle(isDemo: Boolean): String {
        return if (isDemo) {
            "${carContext.getString(R.string.car_dashboard_title)} · ${carContext.getString(R.string.car_demo_badge)}"
        } else {
            carContext.getString(R.string.car_dashboard_title)
        }
    }

    private fun batteryStatusRow(
        telemetry: Delta3Telemetry,
        isDemo: Boolean,
        repository: DeviceRepository
    ): Row {
        val hintText = if (telemetry.powerOn) {
            carContext.getString(R.string.car_station_tap_off)
        } else {
            carContext.getString(R.string.car_station_tap_on)
        }

        val toggle = Toggle.Builder { isChecked ->
            onPortChanged(PortType.POWER, telemetry.powerOn, isChecked, isDemo, repository)
        }
            .setChecked(telemetry.powerOn)
            .build()

        return Row.Builder()
            .setTitle("${telemetry.batteryPercent}% · ${carContext.getString(R.string.car_battery)}")
            .addText(hintText)
            .setImage(carIcon(R.drawable.ic_battery))
            .setToggle(toggle)
            .build()
    }

    private fun powerStatusRow(telemetry: Delta3Telemetry): Row {
        return Row.Builder()
            .setTitle(
                carContext.getString(
                    R.string.car_energy_flow_title,
                    telemetry.inputWatts,
                    telemetry.outputWatts
                )
            )
            .addText(chargeSourcesLine(telemetry))
            .setImage(carIcon(R.drawable.ic_power))
            .build()
    }

    private fun remainingTimeRow(telemetry: Delta3Telemetry): Row {
        return Row.Builder()
            .setTitle(carContext.getString(R.string.car_time_title))
            .addText(remainingTimeLine(telemetry))
            .setImage(carIcon(R.drawable.ic_time))
            .build()
    }

    private fun chargeSourcesLine(telemetry: Delta3Telemetry): String =
        carContext.getString(
            R.string.car_energy_flow_sources,
            telemetry.solarWatts,
            telemetry.acInputWatts
        )

    private fun remainingTimeLine(telemetry: Delta3Telemetry): String =
        carContext.getString(
            R.string.car_energy_flow_time,
            CarTimeFormat.formatMinutes(carContext, telemetry.chargeRemainingMinutes),
            CarTimeFormat.formatMinutes(carContext, telemetry.dischargeRemainingMinutes)
        )

    private fun controlRow(
        titleRes: Int,
        enabled: Boolean,
        iconRes: Int,
        port: PortType,
        isDemo: Boolean,
        repository: DeviceRepository
    ): Row {
        val stateText = if (enabled) {
            carContext.getString(R.string.car_port_on)
        } else {
            carContext.getString(R.string.car_port_off)
        }

        val toggle = Toggle.Builder { isChecked ->
            onPortChanged(port, enabled, isChecked, isDemo, repository)
        }
            .setChecked(enabled)
            .build()

        return Row.Builder()
            .setTitle(carContext.getString(titleRes))
            .addText(stateText)
            .setImage(carIcon(iconRes))
            .setToggle(toggle)
            .build()
    }

    private fun carIcon(drawableRes: Int): CarIcon {
        return CarIcon.Builder(
            IconCompat.createWithResource(carContext, drawableRes)
        ).build()
    }

    private fun onPortChanged(
        port: PortType,
        previous: Boolean,
        isChecked: Boolean,
        isDemo: Boolean,
        repository: DeviceRepository
    ) {
        if (previous == isChecked) return
        if (isDemo) {
            CarDemoState.setPort(port, isChecked)
        } else if (port == PortType.POWER) {
            repository.setAllOutputsEnabled(isChecked)
        } else {
            repository.togglePort(port)
        }
        invalidate()
    }

    companion object {
        private const val TAG = "DashboardScreen"
    }
}
