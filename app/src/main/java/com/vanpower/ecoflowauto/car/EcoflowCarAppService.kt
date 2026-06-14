package com.vanpower.ecoflowauto.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.vanpower.ecoflowauto.BuildConfig
import com.vanpower.ecoflowauto.R
import com.vanpower.ecoflowauto.car.screens.DashboardScreen
import com.vanpower.ecoflowauto.car.screens.MessageScreens

class EcoflowCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        if (BuildConfig.DEBUG) {
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        }
        return HostValidator.Builder(applicationContext)
            .addAllowedHosts(R.array.hosts_allowlist)
            .build()
    }

    override fun onCreateSession(): Session = EcoflowCarSession()
}

class EcoflowCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return try {
            DashboardScreen(carContext)
        } catch (e: Exception) {
            android.util.Log.e("EcoflowCarSession", "onCreateScreen failed", e)
            FallbackScreen(carContext)
        }
    }
}

private class FallbackScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate() = MessageScreens.loadError(carContext)
}
