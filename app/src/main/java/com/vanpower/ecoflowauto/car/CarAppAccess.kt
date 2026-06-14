package com.vanpower.ecoflowauto.car

import androidx.car.app.CarContext
import com.vanpower.ecoflowauto.EcoflowApp
import com.vanpower.ecoflowauto.data.DeviceRepository

internal object CarAppAccess {
    fun app(carContext: CarContext): EcoflowApp? =
        carContext.applicationContext as? EcoflowApp

    fun repository(carContext: CarContext): DeviceRepository? = app(carContext)?.repository
}
