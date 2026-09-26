package ru.rudra.androidos.pa.domain.sync

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

/**
 * Authenticated encryption for sync envelopes, built only on JCA so the same
 * code runs on the laptop peer (JVM) and on Android (API 29+).
 *
 * Construction: encrypt-then-MAC.
 *   - AES-256-GCM with a random 12-byte IV for confidentiality + integrity of
 *     the ciphertext itself;
 *   - HMAC-SHA256 over (iv || ciphertext) with a domain-separated key, so a
 *     recipient can reject a tampered bundle before attempting decryption.
 * Keys are supplied by the caller (key management is out of scope until P2
 * pairing/revocation); [deriveKeys] provides deterministic test/dev keys from
 * a passphrase via PBKDF2.
 */
object CryptoBox {

    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val HMAC_ALGO = "HmacSHA256"
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 210_000

    data class Keys(val encryptionKey: ByteArray, val macKey: ByteArray)

    data class Sealed(val payload: String, val nonce: String, val mac: String)

    /** Derives an encryption key and a domain-separated MAC key from a passphrase. */
    fun deriveKeys(passphrase: CharArray, salt: ByteArray): Keys {
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS * 2)
        val material = factory.generateSecret(spec).encoded
        val enc = material.copyOfRange(0, KEY_BITS / 8)
        val mac = material.copyOfRange(KEY_BITS / 8, KEY_BITS / 4)
        spec.clearPassword()
        return Keys(enc, mac)
    }

    fun seal(plaintext: ByteArray, keys: Keys): Sealed {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keys.encryptionKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        val ciphertext = cipher.doFinal(plaintext)
        return Sealed(
            payload = b64(ciphertext),
            nonce = b64(iv),
            mac = b64(hmac(iv, ciphertext, keys.macKey)),
        )
    }

    /**
     * Verifies the MAC before decrypting, then decrypts. Returns null when the
     * input is malformed, the MAC does not verify, or the GCM tag is wrong — a
     * tampered bundle never yields plaintext and never throws.
     */
    fun open(sealed: Sealed, keys: Keys): ByteArray? {
        val iv: ByteArray
        val ciphertext: ByteArray
        val mac: ByteArray
        try {
            iv = unb64(sealed.nonce)
            ciphertext = unb64(sealed.payload)
            mac = unb64(sealed.mac)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val expectedMac = hmac(iv, ciphertext, keys.macKey)
        if (!constantTimeEquals(expectedMac, mac)) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keys.encryptionKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun hmac(iv: ByteArray, ciphertext: ByteArray, macKey: ByteArray): ByteArray {
        val hmac = Mac.getInstance(HMAC_ALGO)
        hmac.init(SecretKeySpec(macKey, HMAC_ALGO))
        hmac.update(iv)
        hmac.update(ciphertext)
        return hmac.doFinal()
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        java.security.MessageDigest.isEqual(a, b)

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
