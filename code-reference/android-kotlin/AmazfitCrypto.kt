package org.openbalance.data.ble

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object AmazfitCrypto {

    /**
     * Encrypts the 16-byte random challenge using AES/ECB/NoPadding and the device Auth Key.
     */
    fun encryptChallenge(challenge: ByteArray, authKey: ByteArray): ByteArray {
        require(challenge.size == 16) { "Challenge must be exactly 16 bytes" }
        require(authKey.size == 16) { "Auth key must be exactly 16 bytes" }

        val keySpec = SecretKeySpec(authKey, "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(challenge)
    }

    /**
     * Builds payload for initial pairing (Opcode 0x01 + 0x00 + 16-byte authKey).
     */
    fun buildSendKeyPayload(authKey: ByteArray): ByteArray {
        require(authKey.size == 16) { "Auth key must be 16 bytes" }
        return byteArrayOf(AmazfitConstants.AUTH_OP_SEND_KEY, 0x00) + authKey
    }

    /**
     * Builds packet requesting a random challenge (Opcode 0x02 + 0x00).
     */
    fun buildRequestRandomAuthPayload(): ByteArray {
        return byteArrayOf(AmazfitConstants.AUTH_OP_REQUEST_RANDOM, 0x00)
    }

    /**
     * Modern firmware variant challenge request (Opcode 0x02 + 0x00 + 0x02 + 0x01 + 0x00).
     */
    fun buildModernRequestRandomPayload(): ByteArray {
        return byteArrayOf(AmazfitConstants.AUTH_OP_REQUEST_RANDOM, 0x00, 0x02, 0x01, 0x00)
    }

    /**
     * Builds response returning the encrypted challenge (Opcode 0x03 + 0x00 + 16 bytes).
     */
    fun buildEncryptedAuthPayload(encrypted: ByteArray): ByteArray {
        require(encrypted.size == 16) { "Encrypted data must be 16 bytes" }
        return byteArrayOf(AmazfitConstants.AUTH_OP_SEND_ENCRYPTED, 0x00) + encrypted
    }

    /**
     * Generates a 16-byte cryptographically secure random key.
     */
    fun generateRandomAuthKey(): ByteArray {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("0x", "")
        require(cleanHex.length % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
