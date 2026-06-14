package com.vanpower.ecoflowauto.ble.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketError(message: String) : Exception(message)

data class Packet(
    val src: Int,
    val dst: Int,
    val cmdSet: Int,
    val cmdId: Int,
    val payload: ByteArray = ByteArray(0),
    val dsrc: Int = 1,
    val ddst: Int = 1,
    val version: Int = 0x13,
    val seq: ByteArray = byteArrayOf(0, 0, 0, 0)
) {
    fun toBytes(): ByteArray {
        val ver = version and 0x0F
        var header = byteArrayOf(
            0xAA.toByte(),
            version.toByte()
        )
        header += shortLe(payload.size)
        header += byteArrayOf(EcoflowCrc.crc8(header).toByte())

        var body = byteArrayOf(0x0D)
        body += seq
        body += byteArrayOf(0x00, 0x00)
        body += byteArrayOf(src.toByte(), dst.toByte())
        if (ver >= 3) {
            body += byteArrayOf(dsrc.toByte(), ddst.toByte())
        }
        body += byteArrayOf(cmdSet.toByte(), cmdId.toByte())
        body += payload

        var frame = header + body
        frame += shortLe(EcoflowCrc.crc16(frame))
        return frame
    }

    companion object {
        private const val PREFIX = 0xAA

        fun fromBytes(data: ByteArray, xorPayload: Boolean = false): Packet {
            if (data.size < 5 || (data[0].toInt() and 0xFF) != PREFIX) {
                throw PacketError("bad prefix")
            }
            if (EcoflowCrc.crc8(data, 0, 4) != (data[4].toInt() and 0xFF)) {
                throw PacketError("header CRC8 mismatch")
            }

            val versionByte = data[1].toInt() and 0xFF
            val version = versionByte and 0x0F
            val sentinel = versionByte and 0x10 != 0
            val payloadLength = readU16Le(data, 2)
            val minLen = if (version == 2) 18 else 20
            if (data.size < minLen) throw PacketError("frame too small")

            if (version in 2..3 && !sentinel) {
                val frameCrc = readU16Le(data, data.size - 2)
                if (EcoflowCrc.crc16(data, 0, data.size - 2) != frameCrc) {
                    throw PacketError("frame CRC16 mismatch")
                }
            }

            val seq = data.copyOfRange(6, 10)
            val src = data[12].toInt() and 0xFF
            val dst = data[13].toInt() and 0xFF

            val payloadStart: Int
            val dsrc: Int
            val ddst: Int
            val cmdSet: Int
            val cmdId: Int

            if (version == 2) {
                payloadStart = 16
                dsrc = 0
                ddst = 0
                cmdSet = data[14].toInt() and 0xFF
                cmdId = data[15].toInt() and 0xFF
            } else {
                payloadStart = 18
                dsrc = data[14].toInt() and 0xFF
                ddst = data[15].toInt() and 0xFF
                cmdSet = data[16].toInt() and 0xFF
                cmdId = data[17].toInt() and 0xFF
            }

            var payload = ByteArray(0)
            if (payloadLength > 0) {
                payload = data.copyOfRange(payloadStart, payloadStart + payloadLength)
                if (xorPayload && seq[0].toInt() != 0) {
                    val key = seq[0]
                    payload = ByteArray(payload.size) { i -> (payload[i].toInt() xor key.toInt()).toByte() }
                }
                if (sentinel && payload.size >= 2 &&
                    payload[payload.size - 2] == 0xBB.toByte() &&
                    payload[payload.size - 1] == 0xBB.toByte()
                ) {
                    payload = payload.copyOfRange(0, payload.size - 2)
                }
            }

            return Packet(
                src = src,
                dst = dst,
                cmdSet = cmdSet,
                cmdId = cmdId,
                payload = payload,
                dsrc = dsrc,
                ddst = ddst,
                version = versionByte,
                seq = seq
            )
        }

        private fun shortLe(value: Int): ByteArray =
            byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

        private fun readU16Le(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }
}
