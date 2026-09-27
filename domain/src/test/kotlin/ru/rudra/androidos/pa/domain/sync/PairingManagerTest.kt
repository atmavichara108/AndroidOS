package ru.rudra.androidos.pa.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingManagerTest {

    private class MemPairStore : PairStore {
        var selfId: String? = null
        var selfSalt: String? = null
        val partners = mutableListOf<PairedDevice>()

        override fun selfId(): String? = selfId
        override fun selfSaltB64(): String? = selfSalt
        override fun saveSelf(deviceId: String, saltB64: String) {
            this.selfId = deviceId
            this.selfSalt = saltB64
        }

        override fun partners(): List<PairedDevice> = partners.toList()
        override fun partner(deviceId: String): PairedDevice? = partners.firstOrNull { it.deviceId == deviceId }
        override fun addPartner(device: PairedDevice) { partners.add(device) }
        override fun removePartner(deviceId: String) { partners.removeIf { it.deviceId == deviceId } }
    }

    private fun samePassphrase() = "correct horse battery staple".toCharArray()

    private fun manager(): PairingManager = PairingManager(MemPairStore())

    @Test
    fun `each device draws a unique random salt`() {
        val a = PairingManager(MemPairStore())
        val b = PairingManager(MemPairStore())
        val pass = samePassphrase()
        a.ensureSelf(pass, "phone-a")
        b.ensureSelf(pass, "phone-b")
        val ka = a.selfKeys(pass)
        val kb = b.selfKeys(pass)
        assertNotEquals(
            ka.encryptionKey.toList(), kb.encryptionKey.toList(),
            "same passphrase but different devices must not share keys"
        )
        assertNotEquals(
            ka.macKey.toList(), kb.macKey.toList(),
            "same passphrase but different devices must not share MAC keys"
        )
    }

    @Test
    fun `both paired devices derive the same shared key`() {
        val storeA = MemPairStore()
        val storeB = MemPairStore()
        val a = PairingManager(storeA)
        val b = PairingManager(storeB)
        val pass = samePassphrase()
        a.ensureSelf(pass, "phone-a")
        b.ensureSelf(pass, "phone-b")

        // Exchange public salts, each pairs with the other.
        a.pair(pass, "phone-b", storeB.selfSaltB64()!!, "T")
        b.pair(pass, "phone-a", storeA.selfSaltB64()!!, "T")

        val sharedA = a.sharedKeys(pass, "phone-b")!!
        val sharedB = b.sharedKeys(pass, "phone-a")!!
        assertTrue(sharedA.encryptionKey.contentEquals(sharedB.encryptionKey))
        assertTrue(sharedA.macKey.contentEquals(sharedB.macKey))
    }

    @Test
    fun `revocation breaks the shared key`() {
        val storeA = MemPairStore()
        val storeB = MemPairStore()
        val a = PairingManager(storeA)
        val b = PairingManager(storeB)
        val pass = samePassphrase()
        a.ensureSelf(pass, "phone-a")
        b.ensureSelf(pass, "phone-b")
        a.pair(pass, "phone-b", storeB.selfSaltB64()!!, "T")
        b.pair(pass, "phone-a", storeA.selfSaltB64()!!, "T")

        assertTrue(a.isPaired("phone-b"))
        a.revoke("phone-b")
        assertTrue(!a.isPaired("phone-b"))
        assertNull(a.sharedKeys(pass, "phone-b"))
        // The other side still pairs us, but we no longer open its bundles.
        assertTrue(b.isPaired("phone-a"))
        assertNull(a.sharedKeys(pass, "phone-b"))
    }

    @Test
    fun `unpaired partner yields no key`() {
        val a = manager()
        a.ensureSelf(samePassphrase(), "phone-a")
        assertNull(a.sharedKeys(samePassphrase(), "phone-unknown"))
    }

    @Test
    fun `identity is immutable once created`() {
        val store = MemPairStore()
        val a = PairingManager(store)
        a.ensureSelf(samePassphrase(), "phone-a")
        val b = PairingManager(store)
        try {
            b.ensureSelf(samePassphrase(), "phone-x")
            kotlin.test.fail("expected identity mismatch to fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("refusing"))
        }
    }
}