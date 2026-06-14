package com.vanpower.ecoflowauto.ble.protocol

abstract class FrameAssembler {
    open val writeWithResponse: Boolean = false
    protected var buffer = ByteArray(0)

    abstract fun encode(packet: Packet): ByteArray
    abstract fun reassemble(data: ByteArray): List<ByteArray>
}

class PassthroughAssembler : FrameAssembler() {
    override fun encode(packet: Packet): ByteArray = packet.toBytes()

    override fun reassemble(data: ByteArray): List<ByteArray> {
        var remaining = buffer + data
        buffer = ByteArray(0)
        val payloads = mutableListOf<ByteArray>()
        while (remaining.isNotEmpty()) {
            val start = remaining.indexOf(0xAA.toByte())
            if (start < 0) break
            remaining = remaining.copyOfRange(start, remaining.size)
            if (remaining.size < 20) break
            val length = (remaining[2].toInt() and 0xFF) or ((remaining[3].toInt() and 0xFF) shl 8)
            val frameLen = 18 + length + 2
            if (remaining.size < frameLen) break
            payloads.add(remaining.copyOfRange(0, frameLen))
            remaining = remaining.copyOfRange(frameLen, remaining.size)
        }
        buffer = remaining
        return payloads
    }
}

class EncPacketAssembler(private val encryption: Type7Encryption) : FrameAssembler() {
    override val writeWithResponse = true

    override fun encode(packet: Packet): ByteArray =
        EncPacket.encode(EncPacket.FRAME_PROTOCOL, packet.toBytes(), encryption)

    override fun reassemble(data: ByteArray): List<ByteArray> {
        var remaining = buffer + data
        buffer = ByteArray(0)
        val payloads = mutableListOf<ByteArray>()
        val prefix = byteArrayOf(0x5A, 0x5A)
        while (remaining.isNotEmpty()) {
            val start = remaining.indexOfPrefix(prefix)
            if (start < 0) break
            remaining = remaining.copyOfRange(start, remaining.size)
            if (remaining.size < 8) break
            val payloadLen = (remaining[4].toInt() and 0xFF) or ((remaining[5].toInt() and 0xFF) shl 8)
            if (payloadLen > 10_000) {
                remaining = remaining.copyOfRange(2, remaining.size)
                continue
            }
            val end = 6 + payloadLen
            if (end > remaining.size) {
                val next = remaining.copyOfRange(2, remaining.size).indexOfPrefix(prefix)
                if (next >= 0) {
                    remaining = remaining.copyOfRange(2 + next, remaining.size)
                    continue
                }
                break
            }
            val header = remaining.copyOfRange(0, 6)
            val body = remaining.copyOfRange(6, end - 2)
            val frameCrc = (remaining[end - 2].toInt() and 0xFF) or
                ((remaining[end - 1].toInt() and 0xFF) shl 8)
            if (EcoflowCrc.crc16(header + body) != frameCrc) {
                remaining = remaining.copyOfRange(2, remaining.size)
                continue
            }
            remaining = remaining.copyOfRange(end, remaining.size)
            payloads.add(encryption.decrypt(body))
        }
        buffer = remaining
        return payloads
    }

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
