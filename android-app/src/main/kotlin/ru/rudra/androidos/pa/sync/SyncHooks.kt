package ru.rudra.androidos.pa.sync

import android.content.Context
import android.util.Log
import ru.rudra.androidos.pa.BuildConfig
import ru.rudra.androidos.pa.data.PaDatabase
import ru.rudra.androidos.pa.data.RoomLocalStore
import ru.rudra.androidos.pa.domain.sync.CryptoBox
import ru.rudra.androidos.pa.domain.sync.ExchangeFile
import ru.rudra.androidos.pa.domain.sync.SyncEngine
import java.io.File

/**
 * Scriptable sync hook for P1-02 device evidence. Triggered from the outside:
 *
 *   adb shell am start -n ru.rudra.androidos.pa/.MainActivity \
 *       --es pa_sync export      # writes files/sync/out.pa-sync from Room
 *   adb shell am start -n ru.rudra.androidos.pa/.MainActivity \
 *       --es pa_sync import      # applies files/sync/in.pa-sync into Room
 *
 * The passphrase is never passed on a command line: it is read from the app's
 * private file `files/sync_pass.txt` (provisioned by the operator for the
 * test). Every run appends a line to files/sync/last_result.txt so the outcome
 * can be read back with adb.
 */
object SyncHooks {

    const val EXTRA_COMMAND = "pa_sync"
    private const val KEY_ID = "pa-local"

    fun run(command: String, context: Context, db: PaDatabase, store: RoomLocalStore): String {
        // Scriptable sync hooks are a test/evidence facility. Keep them out of
        // release builds: MainActivity is an exported launcher activity, so any
        // installed app could otherwise drive export/import.
        if (!BuildConfig.DEBUG) return "sync hooks disabled in release builds"
        val syncDir = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("sync").apply { mkdirs() }
        val passphraseFile = File(context.filesDir, "sync_pass.txt")
        val passphrase = passphraseFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val result = try {
            when (command) {
                "export" -> export(db, store, syncDir, passphrase)
                "import" -> import(db, store, syncDir, passphrase)
                else -> "unknown command: $command"
            }
        } catch (e: Exception) {
            "sync hook failed: ${e.message ?: e::class.java.simpleName}"
        }
        Log.d("PA_SYNC", result)
        File(syncDir, "last_result.txt").appendText(result + "\n")
        return result
    }

    private fun export(
        db: PaDatabase,
        store: RoomLocalStore,
        syncDir: File,
        passphrase: String?,
    ): String {
        if (passphrase == null) return "export failed: files/sync_pass.txt missing"
        val keys = CryptoBox.deriveKeys(passphrase.toCharArray(), ExchangeFile.saltFor(KEY_ID))
        val changes = store.allChanges()
        val engine = SyncEngine(deviceId = "phone-3c3da9f8")
        val envelope = engine.buildEnvelope(
            sequence = System.currentTimeMillis(),
            changes = changes,
            createdAt = java.time.Instant.now().toString(),
            keyId = KEY_ID,
            encryptionAlgorithms = "AES-256-GCM+HMAC-SHA256",
        )
        val out = File(syncDir, "out.pa-sync")
        ExchangeFile.write(out, envelope, keys)
        return "exported ${changes.size} change(s) envelope=${envelope.id} " +
            "hash=${envelope.bundleHash.take(16)} -> ${out.name}"
    }

    private fun import(
        db: PaDatabase,
        store: RoomLocalStore,
        syncDir: File,
        passphrase: String?,
    ): String {
        if (passphrase == null) return "import failed: files/sync_pass.txt missing"
        val keys = CryptoBox.deriveKeys(passphrase.toCharArray(), ExchangeFile.saltFor(KEY_ID))
        val inFile = File(syncDir, "in.pa-sync")
        if (!inFile.exists()) return "import failed: ${inFile.name} missing"
        val envelope = ExchangeFile.read(inFile, keys)
            ?: return "import failed: MAC/tag verification failed (tampered or wrong passphrase)"
        val report = SyncEngine(deviceId = "phone-3c3da9f8").apply(envelope, store)
        return "imported envelope=${report.envelopeId} applied=${report.applied} " +
            "duplicates=${report.duplicates} rejected=${report.rejected}" +
            (report.reason?.let { " reason=$it" } ?: "")
    }
}
