package com.vanpower.ecoflowauto.ble.protocol

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.SecureRandom
import java.security.Security

object EcoflowEcdh {
    private val domain: ECDomainParameters by lazy {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        val spec = SECNamedCurves.getByName("secp160r1")
        ECDomainParameters(spec.curve, spec.g, spec.n, spec.h, spec.seed)
    }

    data class KeyMaterial(
        val privateKey: ECPrivateKeyParameters,
        val publicKeyBytes: ByteArray
    )

    fun generateKeyPair(): KeyMaterial {
        val generator = ECKeyPairGenerator()
        generator.init(ECKeyGenerationParameters(domain, SecureRandom()))
        val pair = generator.generateKeyPair()
        val public = pair.public as ECPublicKeyParameters
        return KeyMaterial(
            privateKey = pair.private as ECPrivateKeyParameters,
            publicKeyBytes = encodePoint(public.q)
        )
    }

    fun decodePublicKey(bytes: ByteArray): ECPublicKeyParameters {
        val x = BigInteger(1, bytes.copyOfRange(0, 20))
        val y = BigInteger(1, bytes.copyOfRange(20, 40))
        val point: ECPoint = domain.curve.createPoint(x, y)
        return ECPublicKeyParameters(point, domain)
    }

    fun sharedSecret(privateKey: ECPrivateKeyParameters, devicePublic: ECPublicKeyParameters): ByteArray {
        val agreement = ECDHBasicAgreement()
        agreement.init(privateKey)
        val secret = agreement.calculateAgreement(devicePublic)
        return toFixedBytes(secret, 20)
    }

    fun ecdhTypeSize(curveNum: Int): Int = when (curveNum) {
        1 -> 52
        2 -> 56
        3, 4 -> 64
        else -> 40
    }

    private fun encodePoint(point: ECPoint): ByteArray {
        val normalized = point.normalize()
        return toFixedBytes(normalized.xCoord.toBigInteger(), 20) +
            toFixedBytes(normalized.yCoord.toBigInteger(), 20)
    }

    private fun toFixedBytes(value: BigInteger, size: Int): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(size)
        val copyLen = minOf(raw.size, size)
        val srcPos = maxOf(0, raw.size - size)
        val dstPos = size - copyLen
        System.arraycopy(raw, srcPos, out, dstPos, copyLen)
        return out
    }
}
