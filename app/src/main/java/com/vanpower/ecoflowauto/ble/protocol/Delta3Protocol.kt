package com.vanpower.ecoflowauto.ble.protocol

import com.vanpower.ecoflowauto.data.Delta3Telemetry

object Delta3Fields {
    const val F_POW_IN_SUM_W = 3
    const val F_POW_OUT_SUM_W = 4
    const val F_POW_GET_QCUSB1 = 9
    const val F_FLOW_INFO_QCUSB1 = 13
    const val F_FLOW_INFO_12V = 33
    const val F_POW_GET_AC_IN = 54
    const val F_POW_GET_PV = 361
    const val F_FLOW_INFO_AC_OUT = 367
    const val F_POW_GET_AC_OUT = 368
    const val F_BMS_BATT_SOC = 242
    const val F_CMS_BATT_SOC = 262
    const val F_CMS_DSG_REM_TIME = 268
    const val F_CMS_CHG_REM_TIME = 269

    const val DISPLAY_SRC = 0x02
    const val DISPLAY_CMD_SET = 0xFE
    const val DISPLAY_CMD_ID = 0x15

    const val CFG_DC_12V_OUT_OPEN = 18
    const val CFG_USB_OPEN = 19
    const val CFG_AC_OUT_OPEN = 76

    const val CONFIG_SRC = 0x20
    const val CONFIG_DST = 0x02
    const val CONFIG_CMD_SET = 0xFE
    const val CONFIG_CMD_ID = 0x11
    const val CONFIG_VERSION = 0x13
}

class Delta3DeviceState {
    var socPercent: Float? = null
    var inputWatts: Float? = null
    var outputWatts: Float? = null
    var solarWatts: Float? = null
    var acInputWatts: Float? = null
    var acOutputOn: Boolean? = null
    var usbOutputOn: Boolean? = null
    var dcOutputOn: Boolean? = null
    var chargeRemainingMinutes: Int? = null
    var dischargeRemainingMinutes: Int? = null

    fun isDisplayPacket(packet: Packet): Boolean =
        packet.src == Delta3Fields.DISPLAY_SRC &&
            packet.cmdSet == Delta3Fields.DISPLAY_CMD_SET &&
            packet.cmdId == Delta3Fields.DISPLAY_CMD_ID

    fun mergeDisplayPayload(payload: ByteArray) {
        val fields = ProtobufCodec.decodeMessage(payload)

        (fields[Delta3Fields.F_CMS_BATT_SOC] as? Float)?.let { socPercent = it }
            ?: (fields[Delta3Fields.F_BMS_BATT_SOC] as? Float)?.let { socPercent = it }

        (fields[Delta3Fields.F_POW_IN_SUM_W] as? Float)?.let { inputWatts = it }
        (fields[Delta3Fields.F_POW_OUT_SUM_W] as? Float)?.let { outputWatts = it }
        (fields[Delta3Fields.F_POW_GET_PV] as? Float)?.let { solarWatts = it }
        (fields[Delta3Fields.F_POW_GET_AC_IN] as? Float)?.let { acInputWatts = it }
        (fields[Delta3Fields.F_FLOW_INFO_AC_OUT] as? Number)?.let { acOutputOn = it.toInt() != 0 }
        (fields[Delta3Fields.F_FLOW_INFO_QCUSB1] as? Number)?.let { usbOutputOn = it.toInt() != 0 }
        (fields[Delta3Fields.F_FLOW_INFO_12V] as? Number)?.let { dcOutputOn = it.toInt() != 0 }
        (fields[Delta3Fields.F_CMS_CHG_REM_TIME] as? Number)?.let { chargeRemainingMinutes = it.toInt() }
        (fields[Delta3Fields.F_CMS_DSG_REM_TIME] as? Number)?.let { dischargeRemainingMinutes = it.toInt() }
    }

    fun toTelemetry(connected: Boolean): Delta3Telemetry = Delta3Telemetry(
        batteryPercent = socPercent?.toInt() ?: 0,
        solarWatts = solarWatts?.toInt() ?: 0,
        acInputWatts = acInputWatts?.toInt() ?: 0,
        chargeRemainingMinutes = chargeRemainingMinutes,
        dischargeRemainingMinutes = dischargeRemainingMinutes,
        inputWatts = inputWatts?.toInt() ?: 0,
        outputWatts = outputWatts?.toInt() ?: 0,
        acEnabled = acOutputOn ?: false,
        usbEnabled = usbOutputOn ?: false,
        dcEnabled = dcOutputOn ?: false,
        powerOn = outputsActive(acOutputOn, usbOutputOn, dcOutputOn),
        connected = connected
    )
}

private fun outputsActive(
    ac: Boolean?,
    usb: Boolean?,
    dc: Boolean?
): Boolean = (ac == true) || (usb == true) || (dc == true)

object Delta3Commands {
    fun setAcEnabled(enabled: Boolean) = configPacket(Delta3Fields.CFG_AC_OUT_OPEN, enabled)
    fun setUsbEnabled(enabled: Boolean) = configPacket(Delta3Fields.CFG_USB_OPEN, enabled)
    fun setDcEnabled(enabled: Boolean) = configPacket(Delta3Fields.CFG_DC_12V_OUT_OPEN, enabled)

    private fun configPacket(fieldNumber: Int, enabled: Boolean): Packet {
        val payload = ProtobufCodec.encodeBoolField(fieldNumber, enabled)
        return Packet(
            src = Delta3Fields.CONFIG_SRC,
            dst = Delta3Fields.CONFIG_DST,
            cmdSet = Delta3Fields.CONFIG_CMD_SET,
            cmdId = Delta3Fields.CONFIG_CMD_ID,
            payload = payload,
            dsrc = 0x01,
            ddst = 0x01,
            version = Delta3Fields.CONFIG_VERSION
        )
    }
}
