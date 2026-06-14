package com.vanpower.ecoflowauto

import android.app.Application
import com.vanpower.ecoflowauto.ble.EcoflowBleService
import com.vanpower.ecoflowauto.ble.protocol.KeyData
import com.vanpower.ecoflowauto.data.DeviceRepository
import com.vanpower.ecoflowauto.data.DeviceStateHolder

class EcoflowApp : Application() {

    val stateHolder = DeviceStateHolder()

    lateinit var repository: DeviceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        KeyData.init(this)
        repository = DeviceRepository(
            context = this,
            stateHolder = stateHolder,
            bleServiceProvider = { EcoflowBleService.getInstance() }
        )
    }
}
