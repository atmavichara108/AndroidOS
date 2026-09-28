package ru.rudra.androidos.pa.domain.sync

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilePairStoreTest {

    private fun tmpDir(): File =
        java.nio.file.Files.createTempDirectory("pairstore-test").toFile()

    @Test
    fun `identity and partners survive reload`() {
        val dir = tmpDir()
        val a = FilePairStore(dir)
        a.saveSelf("phone-1", "c2FsdA==")
        a.addPartner(PairedDevice("laptop-1", "cGFydG5lcg==", "T1"))

        val b = FilePairStore(dir) // reload from disk
        assertEquals("phone-1", b.selfId())
        assertEquals("c2FsdA==", b.selfSaltB64())
        val partner = b.partner("laptop-1")
        assertEquals("cGFydG5lcg==", partner?.saltB64)
        assertEquals(1, b.partners().size)
    }

    @Test
    fun `revoke survives reload`() {
        val dir = tmpDir()
        val a = FilePairStore(dir)
        a.saveSelf("phone-1", "c2FsdA==")
        a.addPartner(PairedDevice("laptop-1", "cGFydG5lcg==", "T1"))
        a.removePartner("laptop-1")

        val b = FilePairStore(dir)
        assertNull(b.partner("laptop-1"))
        assertTrue(b.partners().isEmpty())
        assertEquals("phone-1", b.selfId(), "revoke must not touch self identity")
    }

    @Test
    fun `multiple partners keep order and attributes`() {
        val store = FilePairStore(tmpDir())
        store.addPartner(PairedDevice("zz-1", "c20=", "T2"))
        store.addPartner(PairedDevice("aa-1", "c2E=", "T1"))
        val ids = store.partners().map { it.deviceId }
        assertEquals(listOf("aa-1", "zz-1"), ids)
        assertEquals("T1", store.partner("aa-1")?.addedAt)
        assertEquals("T2", store.partner("zz-1")?.addedAt)
    }

    @Test
    fun `unknown partner and empty store return null or empty`() {
        val store = FilePairStore(tmpDir())
        assertNull(store.selfId())
        assertNull(store.selfSaltB64())
        assertNull(store.partner("nope"))
        assertTrue(store.partners().isEmpty())
    }
}