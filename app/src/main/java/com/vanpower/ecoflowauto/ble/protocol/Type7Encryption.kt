package com.vanpower.ecoflowauto.ble.protocol

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Type7Encryption(
    sessionKey: ByteArray,
    private val iv: ByteArray
) {
    private val keySpec = SecretKeySpec(sessionKey, "AES")

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))
        return cipher.doFinal(plaintext)
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {
        val aligned = ciphertext.size - (ciphertext.size % 16)
        if (aligned == 0) return ciphertext
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
        return try {
            cipher.doFinal(ciphertext.copyOfRange(0, aligned))
        } catch (_: Exception) {
            cipher.doFinal(ciphertext.copyOfRange(0, aligned))
        }
    }
}
