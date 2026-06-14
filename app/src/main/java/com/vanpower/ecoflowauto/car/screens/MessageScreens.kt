package com.vanpower.ecoflowauto.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.vanpower.ecoflowauto.R

object MessageScreens {

    fun notConfigured(carContext: CarContext): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.car_not_configured))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    fun bleError(carContext: CarContext): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.car_ble_error))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    fun loadError(carContext: CarContext): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.car_error))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
