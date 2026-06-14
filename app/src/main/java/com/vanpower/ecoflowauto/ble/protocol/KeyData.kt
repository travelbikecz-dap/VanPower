package com.vanpower.ecoflowauto.ble.protocol

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Firmware key table for encrypt_type 7 session derivation.
 * Data from ha-ef-ble / ecoflow2nut-pibridge (Apache-2.0).
 */
object KeyData {
    private var data: ByteArray? = null

    fun init(context: Context) {
        if (data != null) return
        context.assets.open("ecoflow_keydata.bin").use { stream ->
            data = stream.readBytes()
        }
    }

    fun get8Bytes(pos: Int): ByteArray {
        val table = data ?: error("KeyData not initialized")
        return table.copyOfRange(pos, pos + 8)
    }

    fun genSessionKey(seed: ByteArray, srand: ByteArray): ByteArray {
        val pos = ((seed[0].toInt() and 0xFF) * 0x10) +
            (((seed[1].toInt() and 0xFF) - 1) and 0xFF) * 0x100
        val num0 = get8Bytes(pos).toLongLe()
        val num1 = get8Bytes(pos + 8).toLongLe()
        require(srand.size < 0x20) { "srand >= 32 bytes is not supported" }
        val num2 = srand.copyOfRange(0, 8).toLongLe()
        val num3 = srand.copyOfRange(8, 16).toLongLe()
        val packed = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(num0).putLong(num1).putLong(num2).putLong(num3).array()
        return MessageDigest.getInstance("MD5").digest(packed)
    }

    private fun ByteArray.toLongLe(): Long =
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).long
}
