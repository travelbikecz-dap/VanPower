package com.vanpower.ecoflowauto.ble.protocol

object EcoflowCrc {
    private val crc8Table = IntArray(256) { i ->
        var crc = i
        repeat(8) {
            crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
        }
        crc
    }

    private val crc16Table = IntArray(256) { i ->
        var crc = i
        repeat(8) {
            crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
        }
        crc
    }

    fun crc8(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = crc8Table[crc xor (data[i].toInt() and 0xFF)]
        }
        return crc
    }

    fun crc16(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = crc16Table[(crc xor (data[i].toInt() and 0xFF)) and 0xFF] xor (crc ushr 8)
        }
        return crc and 0xFFFF
    }
}
