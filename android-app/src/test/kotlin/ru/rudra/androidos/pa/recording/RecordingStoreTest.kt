package ru.rudra.androidos.pa.recording

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `lists m4a files newest first`() {
        val files = tmp.newFolder("Recordings")
        val old = File(files, "a.m4a").apply { writeBytes(ByteArray(10)); setLastModified(1_000) }
        val new = File(files, "b.m4a").apply { writeBytes(ByteArray(20)); setLastModified(2_000) }
        File(files, "c.txt").apply { writeBytes(ByteArray(5)) }

        val result = RecordingStore.list(tmp.root, files)

        assertEquals(listOf(new.path, old.path), result.map { it.path })
        assertEquals(20L, result.first().sizeBytes)
        assertTrue(result.none { it.path.endsWith("c.txt") })
    }

    @Test
    fun `falls back to filesDir when external dir missing`() {
        val result = RecordingStore.list(tmp.root, null)
        assertTrue(result.isEmpty())
    }
}
