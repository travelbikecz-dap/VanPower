package com.vanpower.ecoflowauto.ble.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object EncPacket {
    private val PREFIX = byteArrayOf(0x5A, 0x5A)

    const val FRAME_COMMAND = 0x00
    const val FRAME_PROTOCOL = 0x01

    fun encode(frameType: Int, payload: ByteArray, enc: Type7Encryption? = null): ByteArray {
        val body = enc?.encrypt(payload) ?: payload
        var data = PREFIX + byteArrayOf((frameType shl 4).toByte(), 0x01)
        data += shortLe(body.size + 2)
        data += body
        data += shortLe(EcoflowCrc.crc16(data))
        return data
    }

    fun parseSimple(data: ByteArray): ByteArray? {
        val start = data.indexOfPrefix(PREFIX)
        if (start < 0 || data.size - start < 8) return null
        val frame = data.copyOfRange(start, data.size)
        val payloadLen = readU16Le(frame, 4)
        val end = 6 + payloadLen
        if (end > frame.size) return null
        val body = frame.copyOfRange(6, end - 2)
        val crc = readU16Le(frame, end - 2)
        if (EcoflowCrc.crc16(frame, 0, end - 2) != crc) return null
        return body
    }

    private fun shortLe(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    private fun readU16Le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.indexOfPrefix(prefix: ByteArray): Int {
        if (size < prefix.size) return -1
        for (i in 0..size - prefix.size) {
            var match = true
            for (j in prefix.indices) {
                if (this[i + j] != prefix[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }
}
