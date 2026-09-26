package ru.rudra.androidos.pa.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoBoxTest {

    private val keys = CryptoBox.deriveKeys(
        passphrase = "correct horse battery staple".toCharArray(),
        salt = "androidos-test-salt".toByteArray(),
    )

    @Test
    fun `seal then open returns the original bytes`() {
        val plaintext = "задача: позвонить маме".toByteArray()
        val sealed = CryptoBox.seal(plaintext, keys)
        val opened = CryptoBox.open(sealed, keys)
        assertEquals(plaintext.toList(), opened?.toList())
    }

    @Test
    fun `each seal uses a fresh nonce`() {
        val a = CryptoBox.seal("one".toByteArray(), keys)
        val b = CryptoBox.seal("one".toByteArray(), keys)
        assertNotEquals(a.nonce, b.nonce)
        assertNotEquals(a.payload, b.payload)
    }

    @Test
    fun `tampered payload is rejected`() {
        val sealed = CryptoBox.seal("данные".toByteArray(), keys)
        val bytes = java.util.Base64.getDecoder().decode(sealed.payload).copyOf()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        val tampered = sealed.copy(payload = java.util.Base64.getEncoder().encodeToString(bytes))
        assertNull(CryptoBox.open(tampered, keys))
    }

    @Test
    fun `tampered mac is rejected`() {
        val sealed = CryptoBox.seal("данные".toByteArray(), keys)
        assertNull(CryptoBox.open(sealed.copy(mac = "AAAA"), keys))
    }

    @Test
    fun `tampered nonce is rejected`() {
        val sealed = CryptoBox.seal("данные".toByteArray(), keys)
        assertNull(CryptoBox.open(sealed.copy(nonce = "AAAAAAAAAAAAAAAA"), keys))
    }

    @Test
    fun `wrong key cannot open`() {
        val other = CryptoBox.deriveKeys("other".toCharArray(), "salt".toByteArray())
        val sealed = CryptoBox.seal("секрет".toByteArray(), keys)
        assertNull(CryptoBox.open(sealed, other))
    }

    @Test
    fun `derivation is deterministic for same passphrase and salt`() {
        val a = CryptoBox.deriveKeys("pass".toCharArray(), "salt".toByteArray())
        val b = CryptoBox.deriveKeys("pass".toCharArray(), "salt".toByteArray())
        assertTrue(a.encryptionKey.contentEquals(b.encryptionKey))
        assertTrue(a.macKey.contentEquals(b.macKey))
        assertFalse(a.encryptionKey.contentEquals(a.macKey))
    }
}
