package ru.rudra.androidos.pa.sync

import android.content.Context
import android.util.Log
import ru.rudra.androidos.pa.BuildConfig
import ru.rudra.androidos.pa.data.PaDatabase
import ru.rudra.androidos.pa.data.RoomLocalStore
import ru.rudra.androidos.pa.domain.sync.ExchangeFile
import ru.rudra.androidos.pa.domain.sync.PairingManager
import ru.rudra.androidos.pa.domain.sync.SyncEngine
import java.io.File

/**
 * Scriptable sync hook. Triggered from the outside (adb):
 *
 *   adb shell am start -n ru.rudra.androidos.pa/.MainActivity \
 *       --es pa_sync pairinfo            # print this device id + public salt
 *   adb shell am start -n ru.rudra.androidos.pa/.MainActivity \
 *       --es pa_sync "pair laptop-peer <saltB64>"   # pair with the laptop
 *   adb shell am start -n ru.rudra.androidos.pa/.MainActivity \
 *       --es pa_sync "revoke laptop-peer"            # revoke a partner
 *   adb shell am start -n ru.rudra.androidos.pa/.MainActivity \
 *       --es pa_sync export      # seal files/sync/out.pa-sync with the shared key
 *   adb shell am start -n ru.rudra.androidos.pa/.MainActivity \
 *       --es pa_sync import      # apply files/sync/in.pa-sync
 *
 * export/import require pairing: keys are the PairingManager shared session
 * keys (per-device salts), not the legacy fixed salt. The passphrase is never
 * passed on a command line; it is read from the app's private
 * `files/sync_pass.txt`. Every run appends to files/sync/last_result.txt.
 */
object SyncHooks {

    const val EXTRA_COMMAND = "pa_sync"
    const val SELF_ID = "phone-3c3da9f8"
    const val PARTNER_ID = "laptop-peer"

    fun run(command: String, context: Context, db: PaDatabase, store: RoomLocalStore): String {
        // Sync hooks are a test/evidence facility: MainActivity is an exported
        // launcher activity, so keep them out of release builds.
        if (!BuildConfig.DEBUG) return "sync hooks disabled in release builds"
        val syncDir = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("sync").apply { mkdirs() }
        val passphrase = File(context.filesDir, "sync_pass.txt")
            .takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val result = try {
            val pairing = PairingManager(AndroidPairStore(context))
            val parts = command.trim().split(Regex("\\s+"))
            when (parts[0]) {
                "pairinfo" -> pairInfo(pairing, passphrase)
                "pair" -> pair(pairing, passphrase, parts)
                "revoke" -> revoke(pairing, passphrase, parts)
                "export" -> export(db, store, syncDir, pairing, passphrase)
                "import" -> import(db, store, syncDir, pairing, passphrase)
                else -> "unknown command: $command"
            }
        } catch (e: Exception) {
            "sync hook failed: ${e.message ?: e::class.java.simpleName}"
        }
        Log.d("PA_SYNC", result)
        File(syncDir, "last_result.txt").appendText(result + "\n")
        return result
    }

    private fun requirePassphrase(passphrase: String?): CharArray? =
        passphrase?.toCharArray()

    private fun pairInfo(pairing: PairingManager, passphrase: String?): String {
        val pass = requirePassphrase(passphrase)
            ?: return "pairinfo failed: files/sync_pass.txt missing"
        pairing.ensureSelf(pass, SELF_ID)
        val salt = pairing.selfSaltB64()
        return "id=$SELF_ID salt=$salt (pair the laptop with these values)"
    }

    private fun pair(pairing: PairingManager, passphrase: String?, parts: List<String>): String {
        val pass = requirePassphrase(passphrase)
            ?: return "pair failed: files/sync_pass.txt missing"
        if (parts.size != 3) return "usage: pair <partnerId> <saltB64>"
        pairing.ensureSelf(pass, SELF_ID)
        pairing.pair(pass, parts[1], parts[2], java.time.Instant.now().toString())
        return "paired with ${parts[1]}"
    }

    private fun revoke(pairing: PairingManager, passphrase: String?, parts: List<String>): String {
        val pass = requirePassphrase(passphrase)
            ?: return "revoke failed: files/sync_pass.txt missing"
        if (parts.size != 2) return "usage: revoke <partnerId>"
        pairing.ensureSelf(pass, SELF_ID)
        pairing.revoke(parts[1])
        return "revoked ${parts[1]}"
    }

    /** Shared session keys with the laptop; null when not (or no longer) paired. */
    private fun sharedKeys(pairing: PairingManager, passphrase: String?): ru.rudra.androidos.pa.domain.sync.CryptoBox.Keys? {
        val pass = requirePassphrase(passphrase) ?: return null
        pairing.ensureSelf(pass, SELF_ID)
        return pairing.sharedKeys(pass, PARTNER_ID)
    }

    private fun export(
        db: PaDatabase,
        store: RoomLocalStore,
        syncDir: File,
        pairing: PairingManager,
        passphrase: String?,
    ): String {
        val keys = sharedKeys(pairing, passphrase)
            ?: return "export failed: not paired with $PARTNER_ID (run pairinfo + pair first)"
        val changes = store.allChanges()
        val engine = SyncEngine(deviceId = SELF_ID)
        val envelope = engine.buildEnvelope(
            sequence = System.currentTimeMillis(),
            changes = changes,
            createdAt = java.time.Instant.now().toString(),
            keyId = PARTNER_ID,
            encryptionAlgorithms = "AES-256-GCM+HMAC-SHA256 (shared-key pairing)",
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
        pairing: PairingManager,
        passphrase: String?,
    ): String {
        val keys = sharedKeys(pairing, passphrase)
            ?: return "import failed: not paired with $PARTNER_ID (run pairinfo + pair first)"
        val inFile = File(syncDir, "in.pa-sync")
        if (!inFile.exists()) return "import failed: ${inFile.name} missing"
        val envelope = ExchangeFile.read(inFile, keys)
            ?: return "import failed: MAC/tag verification failed (tampered, wrong passphrase, or not a pair partner)"
        val report = SyncEngine(deviceId = SELF_ID).apply(envelope, store)
        return "imported envelope=${report.envelopeId} applied=${report.applied} " +
            "duplicates=${report.duplicates} rejected=${report.rejected}" +
            (report.reason?.let { " reason=$it" } ?: "")
    }
}