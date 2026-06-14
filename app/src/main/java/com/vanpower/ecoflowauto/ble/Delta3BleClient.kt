package com.vanpower.ecoflowauto.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.vanpower.ecoflowauto.ble.protocol.Delta3Commands
import com.vanpower.ecoflowauto.ble.protocol.Delta3DeviceState
import com.vanpower.ecoflowauto.ble.protocol.EncPacket
import com.vanpower.ecoflowauto.ble.protocol.EncPacketAssembler
import com.vanpower.ecoflowauto.ble.protocol.EcoflowEcdh
import com.vanpower.ecoflowauto.ble.protocol.FrameAssembler
import com.vanpower.ecoflowauto.ble.protocol.KeyData
import com.vanpower.ecoflowauto.ble.protocol.Packet
import com.vanpower.ecoflowauto.ble.protocol.PacketError
import com.vanpower.ecoflowauto.ble.protocol.PassthroughAssembler
import com.vanpower.ecoflowauto.ble.protocol.Type7Encryption
import com.vanpower.ecoflowauto.data.Delta3Telemetry
import com.vanpower.ecoflowauto.data.DeviceConfig
import com.vanpower.ecoflowauto.data.OutputPortSnapshot
import com.vanpower.ecoflowauto.data.PortType
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class Delta3BleClient(
    private val context: Context,
    private val config: DeviceConfig,
    private val onTelemetry: (Delta3Telemetry) -> Unit,
    private val onDisconnected: () -> Unit
) {
    private val worker = HandlerThread("Delta3Ble").apply { start() }
    private val handler = Handler(worker.looper)

    private var gatt: BluetoothGatt? = null
    private var assembler: FrameAssembler = PassthroughAssembler()
    private var encryption: Type7Encryption? = null
    private val deviceState = Delta3DeviceState()
    private var savedOutputsBeforeSuspend: OutputPortSnapshot? = null
    private var authenticated = false
    private var serviceUuid: UUID? = null
    private var notifyUuid: UUID? = null
    private var writeUuid: UUID? = null
    private var handshakeStarted = false
    private var lastError = ""
    private var userDisconnect = false
    private var reconnectAttempt = 0
    private var awaitingSoftReconnect = false
    private var sessionEstablished = false

    private val pendingResponse = AtomicReference<ByteArray?>(null)
    private val responseLatch = AtomicReference<CountDownLatch?>(null)
    private val writeLatch = AtomicReference<CountDownLatch?>(null)

    private val connectionTimeout = Runnable {
        if (!authenticated) {
            scheduleReconnectDebounced(wasAuthenticated = false, reason = "Tiempo agotado en handshake")
        }
    }

    private val reconnectRunnable = Runnable {
        if (userDisconnect) return@Runnable
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            fail("Conexión perdida. Pulsa «Probar conexión» para reintentar.")
            handler.post { onDisconnected() }
            return@Runnable
        }
        reconnectAttempt++
        lastError = ""
        Log.i(TAG, "Full reconnect attempt $reconnectAttempt")
        openGatt()
    }

    private val softReconnectTimeout = Runnable {
        if (authenticated || userDisconnect) return@Runnable
        awaitingSoftReconnect = false
        Log.w(TAG, "Soft reconnect timed out, trying full reconnect")
        scheduleFullReconnect()
    }

    private val reconnectDebounce = Runnable {
        if (authenticated || userDisconnect) return@Runnable
        if (trySoftReconnect("debounced")) return@Runnable
        scheduleFullReconnect()
    }

    fun isConnected(): Boolean = authenticated && gatt != null

    @SuppressLint("MissingPermission")
    fun connect() {
        handler.post {
            userDisconnect = false
            if (authenticated && gatt != null) return@post
            reconnectAttempt = 0
            handler.removeCallbacks(reconnectRunnable)
            handler.removeCallbacks(reconnectDebounce)
            handler.removeCallbacks(softReconnectTimeout)
            if (trySoftReconnect("connect()")) return@post
            openGatt()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openGatt() {
        KeyData.init(context.applicationContext)
        lastError = ""
        handshakeStarted = false
        authenticated = false
        sessionEstablished = false
        encryption = null
        assembler = PassthroughAssembler()
        handler.removeCallbacks(connectionTimeout)
        handler.postDelayed(connectionTimeout, 45_000)
        publishTelemetry()

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            fail("Activa el Bluetooth del móvil")
            return
        }
        val mac = normalizeMac(config.macAddress)
        if (mac == null) {
            fail("MAC inválida. Usa formato AA:BB:CC:DD:EE:FF")
            return
        }
        if (config.userId.isBlank()) {
            fail("Falta el User ID de tu cuenta Ecoflow")
            return
        }

        gatt?.close()
        gatt = null
        gatt = adapter.getRemoteDevice(mac).connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    fun release() {
        handler.post {
            userDisconnect = true
            awaitingSoftReconnect = false
            handler.removeCallbacks(reconnectRunnable)
            handler.removeCallbacks(reconnectDebounce)
            handler.removeCallbacks(softReconnectTimeout)
            handler.removeCallbacks(connectionTimeout)
            gatt?.close()
            gatt = null
            authenticated = false
            handshakeStarted = false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        handler.post {
            userDisconnect = true
            awaitingSoftReconnect = false
            handler.removeCallbacks(reconnectRunnable)
            handler.removeCallbacks(reconnectDebounce)
            handler.removeCallbacks(softReconnectTimeout)
            handler.removeCallbacks(connectionTimeout)
            gatt?.close()
            gatt = null
            authenticated = false
            handshakeStarted = false
            onDisconnected()
        }
    }

    fun togglePort(port: PortType) {
        handler.post {
            if (!authenticated) return@post
            val packet = when (port) {
                PortType.AC -> Delta3Commands.setAcEnabled(deviceState.acOutputOn != true)
                PortType.USB -> Delta3Commands.setUsbEnabled(deviceState.usbOutputOn != true)
                PortType.DC -> Delta3Commands.setDcEnabled(deviceState.dcOutputOn != true)
                PortType.POWER -> return@post
            }
            sendPacket(packet)
        }
    }

    fun setAllOutputsEnabled(enabled: Boolean) {
        handler.post {
            if (!authenticated) return@post
            if (!enabled) {
                savedOutputsBeforeSuspend = OutputPortSnapshot(
                    acEnabled = deviceState.acOutputOn ?: false,
                    usbEnabled = deviceState.usbOutputOn ?: false,
                    dcEnabled = deviceState.dcOutputOn ?: false
                )
                applyOutputStates(ac = false, usb = false, dc = false)
            } else {
                val target = savedOutputsBeforeSuspend ?: OutputPortSnapshot(true, true, true)
                applyOutputStates(target.acEnabled, target.usbEnabled, target.dcEnabled)
            }
        }
    }

    private fun applyOutputStates(ac: Boolean, usb: Boolean, dc: Boolean) {
        sendPacket(Delta3Commands.setAcEnabled(ac))
        handler.postDelayed({
            sendPacket(Delta3Commands.setUsbEnabled(usb))
            handler.postDelayed({ sendPacket(Delta3Commands.setDcEnabled(dc)) }, 300)
        }, 300)
    }

    @SuppressLint("MissingPermission")
    private fun sendPacket(packet: Packet) {
        val characteristic = writeCharacteristic() ?: return
        val data = assembler.encode(packet)
        characteristic.writeType = if (assembler.writeWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        characteristic.value = data
        gatt?.writeCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    private fun writeRaw(data: ByteArray, waitForAck: Boolean = true): Boolean {
        val characteristic = writeCharacteristic() ?: return false
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = data
        val writeLatchLocal = if (waitForAck) CountDownLatch(1) else null
        if (waitForAck) writeLatch.set(writeLatchLocal)
        val queued = gatt?.writeCharacteristic(characteristic) == true
        if (!queued) {
            writeLatch.set(null)
            return false
        }
        if (writeLatchLocal == null) return true
        val acked = writeLatchLocal.await(5, TimeUnit.SECONDS)
        writeLatch.set(null)
        return acked
    }

    private fun writeCharacteristic(): BluetoothGattCharacteristic? {
        val service = serviceUuid ?: return null
        val write = writeUuid ?: return null
        return gatt?.getService(service)?.getCharacteristic(write)
    }

    @SuppressLint("MissingPermission")
    private fun requestPacket(packet: Packet, timeoutMs: Long = 10_000): ByteArray? {
        val data = assembler.encode(packet)
        return requestResponse(data, timeoutMs)
    }

    @SuppressLint("MissingPermission")
    private fun requestResponse(data: ByteArray, timeoutMs: Long = 15_000): ByteArray? {
        val latch = CountDownLatch(1)
        responseLatch.set(latch)
        pendingResponse.set(null)
        if (!writeRaw(data)) {
            responseLatch.set(null)
            return null
        }
        val ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        responseLatch.set(null)
        return if (ok) pendingResponse.get() else null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT connected")
                awaitingSoftReconnect = false
                handler.removeCallbacks(reconnectDebounce)
                handler.removeCallbacks(softReconnectTimeout)
                gatt.requestMtu(512)
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT disconnected status=$status")
                handshakeStarted = false
                val wasAuth = authenticated
                authenticated = false
                publishTelemetry()
                if (userDisconnect) {
                    gatt.close()
                    this@Delta3BleClient.gatt = null
                    return
                }
                scheduleReconnectDebounced(wasAuth, "BLE desconectado (código $status)")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("No se pudieron leer servicios BLE ($status)")
                return
            }
            if (!resolveCharacteristics(gatt)) {
                fail("Servicio BLE Ecoflow no encontrado. ¿Delta 3 encendido y app Ecoflow cerrada?")
                return
            }
            val service = serviceUuid ?: return
            val notifyChar = gatt.getService(service)?.getCharacteristic(notifyUuid) ?: run {
                fail("Característica de notificación no encontrada")
                return
            }
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(CLIENT_CONFIG_UUID)
            if (descriptor == null) {
                fail("Descriptor BLE no encontrado")
                return
            }
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("No se activaron notificaciones BLE ($status)")
                return
            }
            if (!handshakeStarted) {
                handshakeStarted = true
                if (sessionEstablished && encryption != null) {
                    Log.i(TAG, "Resuming session without handshake")
                    handler.removeCallbacks(connectionTimeout)
                    publishTelemetry()
                } else {
                    handler.post { runHandshake() }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Write failed status=$status")
            }
            writeLatch.get()?.countDown()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            responseLatch.get()?.let {
                pendingResponse.set(value)
                it.countDown()
                return
            }
            handleNotification(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            onCharacteristicChanged(gatt, characteristic, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveCharacteristics(gatt: BluetoothGatt): Boolean {
        val discovered = buildList {
            for (service in gatt.services) {
                for (characteristic in service.characteristics) {
                    add("${service.uuid} -> ${characteristic.uuid}")
                }
            }
        }
        Log.i(TAG, "GATT services: ${discovered.joinToString("; ")}")

        for (pair in CHARACTERISTIC_PAIRS) {
            var notifyChar: BluetoothGattCharacteristic? = null
            var writeChar: BluetoothGattCharacteristic? = null
            var notifyServiceUuid: UUID? = null

            for (service in gatt.services) {
                for (characteristic in service.characteristics) {
                    when (characteristic.uuid) {
                        pair.notify -> {
                            notifyChar = characteristic
                            notifyServiceUuid = service.uuid
                        }
                        pair.write -> writeChar = characteristic
                    }
                }
            }

            if (notifyChar != null && writeChar != null && notifyServiceUuid != null) {
                serviceUuid = notifyServiceUuid
                notifyUuid = pair.notify
                writeUuid = pair.write
                Log.i(TAG, "Using notify=${pair.notify} write=${pair.write} service=$serviceUuid")
                return true
            }
        }

        Log.e(TAG, "No Ecoflow characteristics among: $discovered")
        return false
    }

    private fun runHandshake() {
        try {
            performEcdhHandshake()
            handler.removeCallbacks(connectionTimeout)
            lastError = ""
            publishTelemetry()
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed", e)
            fail("Handshake falló: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun performEcdhHandshake() {
        val keyMaterial = EcoflowEcdh.generateKeyPair()
        val step1 = EncPacket.encode(
            EncPacket.FRAME_COMMAND,
            byteArrayOf(0x01, 0x00) + keyMaterial.publicKeyBytes
        )
        Log.d(TAG, "ECDH step1 send ${step1.size} bytes")
        val resp1 = requestResponse(step1) ?: error("sin respuesta (paso 1)")
        Log.d(TAG, "ECDH step1 recv ${resp1.size} bytes")
        val body1 = EncPacket.parseSimple(resp1) ?: error("respuesta inválida (paso 1)")
        if (body1.size < 3) error("clave pública inválida (paso 1)")
        val keySize = EcoflowEcdh.ecdhTypeSize(body1[2].toInt() and 0xFF)
        if (body1.size < 3 + keySize) error("clave pública incompleta (paso 1)")
        val devPubBytes = body1.copyOfRange(3, 3 + keySize)
        val devPub = EcoflowEcdh.decodePublicKey(devPubBytes)
        val shared = EcoflowEcdh.sharedSecret(keyMaterial.privateKey, devPub)
        val iv = MessageDigest.getInstance("MD5").digest(shared)
        encryption = Type7Encryption(shared.copyOf(16), iv)

        val step2 = EncPacket.encode(EncPacket.FRAME_COMMAND, byteArrayOf(0x02))
        Log.d(TAG, "ECDH step2 send ${step2.size} bytes")
        val resp2 = requestResponse(step2) ?: error("sin respuesta (paso 2)")
        Log.d(TAG, "ECDH step2 recv ${resp2.size} bytes")
        val encBody = EncPacket.parseSimple(resp2) ?: error("respuesta inválida (paso 2)")
        if (encBody.isEmpty() || encBody[0] != 0x02.toByte()) error("sesión inválida (paso 2)")
        val enc = encryption ?: error("cifrado no inicializado")
        val decrypted = enc.decrypt(encBody.copyOfRange(1, encBody.size))
        if (decrypted.size < 18) error("datos de sesión incompletos (paso 2)")
        val sessionKey = KeyData.genSessionKey(
            decrypted.copyOfRange(16, 18),
            decrypted.copyOfRange(0, 16)
        )
        encryption = Type7Encryption(sessionKey, iv)
        assembler = EncPacketAssembler(encryption!!)

        Log.d(TAG, "Requesting auth status")
        val authStatusResp = requestPacket(
            Packet(0x21, AUTH_DST, 0x35, 0x89, ByteArray(0), 0x01, 0x01, 0x13)
        ) ?: error("sin respuesta (auth status)")
        Log.d(TAG, "Auth status recv ${authStatusResp.size} bytes")
        sendAuthPacket()
        sessionEstablished = true
        Log.i(TAG, "ECDH handshake complete, auth packets sent")
    }

    private fun sendAuthPacket() {
        val digest = MessageDigest.getInstance("MD5")
            .digest((config.userId.trim() + config.serialNumber.trim()).toByteArray(Charsets.US_ASCII))
        val payload = digest.joinToString("") { "%02X".format(it) }.toByteArray(Charsets.US_ASCII)
        sendPacket(Packet(0x21, AUTH_DST, 0x35, 0x86, payload, 0x01, 0x01, 0x13))
    }

    private fun handleNotification(value: ByteArray) {
        try {
            val payloads = assembler.reassemble(value)
            for (raw in payloads) {
                val packet = Packet.fromBytes(raw, xorPayload = true)
                if (packet.cmdId == 0x86 && packet.cmdSet == 0x35) {
                    val authError = authErrorMessage(packet.payload)
                    if (authError != null) {
                        fail(authError)
                        return
                    }
                }
                if (isKeepalive(packet)) continue
                if (isTimeRequest(packet)) {
                    sendTimeSyncPackets()
                    continue
                }
                if (!authenticated) {
                    authenticated = true
                    reconnectAttempt = 0
                    handler.removeCallbacks(connectionTimeout)
                    lastError = ""
                }
                if (deviceState.isDisplayPacket(packet)) {
                    deviceState.mergeDisplayPayload(packet.payload)
                    publishTelemetry()
                    sendReply(packet)
                }
            }
        } catch (e: PacketError) {
            Log.d(TAG, "Packet skip: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Notify error", e)
        }
    }

    private fun isKeepalive(packet: Packet): Boolean =
        packet.src == 0x35 && packet.cmdSet == 0x35 && packet.cmdId == 0x20

    private fun isTimeRequest(packet: Packet): Boolean =
        packet.src == 0x35 && packet.cmdSet == 0x01 && packet.cmdId == CMD_SET_RET_TIME &&
            packet.payload.isEmpty()

    private fun sendTimeSyncPackets() {
        val rtcPayload = buildRtcPayload()
        sendPacket(
            Packet(0x21, 0x0B, 0x01, 0x55, encodeUtcTimePayload(), 0x01, 0x01, 0x13)
        )
        sendPacket(
            Packet(0x21, 0x35, 0x01, CMD_SET_RET_TIME, rtcPayload, 0x01, 0x01, 0x03)
        )
        sendPacket(
            Packet(0x21, 0x35, 0x01, CMD_CHECK_RET_TIME, rtcPayload, 0x01, 0x01, 0x03)
        )
    }

    private fun buildRtcPayload(): ByteArray {
        val timeSec = (System.currentTimeMillis() / 1000).toInt()
        val offsetHours = java.util.TimeZone.getDefault()
            .getOffset(System.currentTimeMillis()) / 3_600_000.0
        val tzMaj = offsetHours.toInt()
        val tzMin = ((offsetHours - tzMaj) * 100).toInt()
        return java.nio.ByteBuffer.allocate(6)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(timeSec)
            .put(tzMaj.toByte())
            .put(tzMin.toByte())
            .array()
    }

    private fun encodeUtcTimePayload(): ByteArray {
        val timeSec = (System.currentTimeMillis() / 1000).toInt()
        return byteArrayOf(0x08) + encodeProtobufVarint(timeSec)
    }

    private fun encodeProtobufVarint(value: Int): ByteArray {
        val out = mutableListOf<Byte>()
        var v = value
        while (v and -0x80 != 0) {
            out.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        out.add(v.toByte())
        return out.toByteArray()
    }

    private fun sendReply(packet: Packet) {
        sendPacket(
            Packet(
                src = packet.dst,
                dst = packet.src,
                cmdSet = packet.cmdSet,
                cmdId = packet.cmdId,
                payload = packet.payload,
                dsrc = 0x01,
                ddst = 0x01,
                version = packet.version,
                seq = packet.seq
            )
        )
    }

    private fun authErrorMessage(payload: ByteArray): String? = when {
        payload.isEmpty() || payload.contentEquals(byteArrayOf(0x00)) -> null
        payload.contentEquals(byteArrayOf(0x01)) -> "User ID caducado. Obtén uno nuevo en gnox.github.io/user_id"
        payload.contentEquals(byteArrayOf(0x06)) -> "User ID o serial incorrectos"
        payload.contentEquals(byteArrayOf(0x03)) -> "Delta 3 ya vinculada a otra cuenta"
        payload.contentEquals(byteArrayOf(0x04)) -> "Vincula primero el dispositivo en la app Ecoflow"
        else -> "Autenticación rechazada (${payload.joinToString("") { "%02X".format(it) }})"
    }

    private fun scheduleReconnectDebounced(wasAuthenticated: Boolean, reason: String) {
        if (userDisconnect) return
        val delay = if (wasAuthenticated) RECONNECT_DEBOUNCE_AUTH_MS else RECONNECT_DEBOUNCE_MS
        Log.w(TAG, "$reason — retry in ${delay}ms")
        handler.removeCallbacks(reconnectDebounce)
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(connectionTimeout)
        handler.postDelayed(reconnectDebounce, delay)
    }

    @SuppressLint("MissingPermission")
    private fun trySoftReconnect(reason: String): Boolean {
        val existing = gatt ?: return false
        if (!sessionEstablished) return false
        awaitingSoftReconnect = true
        handler.removeCallbacks(softReconnectTimeout)
        handler.postDelayed(softReconnectTimeout, SOFT_RECONNECT_TIMEOUT_MS)
        if (existing.connect()) {
            Log.i(TAG, "Soft reconnect via gatt.connect() ($reason)")
            return true
        }
        awaitingSoftReconnect = false
        handler.removeCallbacks(softReconnectTimeout)
        return false
    }

    private fun scheduleFullReconnect() {
        if (userDisconnect) return
        gatt?.close()
        gatt = null
        val delay = minOf(
            RECONNECT_MAX_DELAY_MS,
            RECONNECT_BASE_DELAY_MS * (1 shl minOf(reconnectAttempt, 4))
        )
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, delay)
    }

    private fun fail(message: String) {
        handler.post {
            handler.removeCallbacks(connectionTimeout)
            handler.removeCallbacks(reconnectRunnable)
            handler.removeCallbacks(reconnectDebounce)
            handler.removeCallbacks(softReconnectTimeout)
            lastError = message
            authenticated = false
            sessionEstablished = false
            Log.e(TAG, message)
            publishTelemetry()
            gatt?.close()
            gatt = null
            handshakeStarted = false
        }
    }

    private fun publishTelemetry() {
        onTelemetry(deviceState.toTelemetry(authenticated).copy(statusMessage = lastError))
    }

    private data class CharPair(val notify: UUID, val write: UUID)

    companion object {
        private const val TAG = "Delta3BleClient"
        private const val AUTH_DST = 0x35
        private const val RECONNECT_DEBOUNCE_MS = 5_000L
        private const val RECONNECT_DEBOUNCE_AUTH_MS = 12_000L
        private const val RECONNECT_BASE_DELAY_MS = 8_000L
        private const val RECONNECT_MAX_DELAY_MS = 60_000L
        private const val SOFT_RECONNECT_TIMEOUT_MS = 20_000L
        private const val CMD_SET_RET_TIME = 0x52
        private const val CMD_CHECK_RET_TIME = 0x53
        private const val MAX_RECONNECT_ATTEMPTS = 12
        val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val CHARACTERISTIC_PAIRS = listOf(
            CharPair(
                UUID.fromString("00000003-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")
            ),
            CharPair(
                UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
                UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
            )
        )

        fun normalizeMac(raw: String): String? {
            val hex = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
            if (hex.length != 12) return null
            return hex.chunked(2).joinToString(":")
        }
    }
}
