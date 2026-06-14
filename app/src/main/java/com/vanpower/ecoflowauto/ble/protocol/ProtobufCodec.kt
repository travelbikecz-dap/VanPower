package com.vanpower.ecoflowauto.ble.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ProtobufCodec {
    const val WIRE_VARINT = 0
    const val WIRE_I64 = 1
    const val WIRE_LEN = 2
    const val WIRE_I32 = 5

    data class ProtoField(val number: Int, val wireType: Int, val value: Any)

    fun decodeMessage(buf: ByteArray): Map<Int, Any> {
        val out = mutableMapOf<Int, Any>()
        var pos = 0
        while (pos < buf.size) {
            val (tag, newPos) = readVarint(buf, pos)
            pos = newPos
            val fieldNumber = (tag shr 3).toInt()
            val wireType = (tag and 0x07).toInt()
            val value: Any = when (wireType) {
                WIRE_VARINT -> {
                    val (v, p) = readVarint(buf, pos)
                    pos = p
                    v
                }
                WIRE_I32 -> {
                    val v = ByteBuffer.wrap(buf, pos, 4).order(ByteOrder.LITTLE_ENDIAN).float
                    pos += 4
                    v
                }
                WIRE_I64 -> {
                    val v = ByteBuffer.wrap(buf, pos, 8).order(ByteOrder.LITTLE_ENDIAN).double
                    pos += 8
                    v
                }
                WIRE_LEN -> {
                    val (size, p) = readVarint(buf, pos)
                    pos = p
                    val len = size.toInt()
                    buf.copyOfRange(pos, pos + len).also { pos += len }
                }
                else -> throw PacketError("unsupported wire type $wireType")
            }
            out[fieldNumber] = value
        }
        return out
    }

    fun encodeMessage(fields: List<ProtoField>): ByteArray {
        val out = mutableListOf<Byte>()
        for (field in fields) {
            out += encodeVarint(((field.number shl 3) or field.wireType).toLong()).toList()
            when (field.wireType) {
                WIRE_VARINT -> out += encodeVarint((field.value as Number).toLong()).toList()
                WIRE_I32 -> {
                    val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putFloat((field.value as Number).toFloat()).array()
                    out += bytes.toList()
                }
                WIRE_LEN -> {
                    val raw = field.value as ByteArray
                    out += encodeVarint(raw.size.toLong()).toList()
                    out += raw.toList()
                }
                else -> throw PacketError("unsupported wire type ${field.wireType}")
            }
        }
        return out.toByteArray()
    }

    fun encodeBoolField(number: Int, enabled: Boolean): ByteArray =
        encodeMessage(listOf(ProtoField(number, WIRE_VARINT, if (enabled) 1 else 0)))

    private fun readVarint(buf: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = start
        while (true) {
            val byte = buf[pos++].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result to pos
            shift += 7
        }
    }

    private fun encodeVarint(valueIn: Long): ByteArray {
        var value = valueIn
        val out = mutableListOf<Byte>()
        while (true) {
            var byte = (value and 0x7F).toInt()
            value = value ushr 7
            if (value != 0L) byte = byte or 0x80
            out.add(byte.toByte())
            if (value == 0L) break
        }
        return out.toByteArray()
    }
}
