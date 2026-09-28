package ru.rudra.androidos.pa.peer

import ru.rudra.androidos.pa.domain.model.Change
import ru.rudra.androidos.pa.domain.model.ChangeOperation
import ru.rudra.androidos.pa.domain.model.RetentionClass
import ru.rudra.androidos.pa.domain.port.LocalStore
import ru.rudra.androidos.pa.domain.sync.CryptoBox
import ru.rudra.androidos.pa.domain.sync.EnvelopeCodec
import ru.rudra.androidos.pa.domain.sync.ExchangeFile
import ru.rudra.androidos.pa.domain.sync.SyncEngine
import java.io.File

/**
 * Laptop peer for P1-02/P2: a plain JVM process that speaks the same
 * domain/sync contract as the phone. It exchanges *sealed envelope files*
 * with the phone (adb or any transport); it never touches a live SQLite file.
 *
 * Commands:
 *   pairinfo <state-dir>               print this device id + public salt
 *   pair <state-dir> <partnerId> <salt> pair with the phone (salts exchanged
 *                                       out of band via adb)
 *   revoke <state-dir> <partnerId>     revoke a partner (shared key dies)
 *   export <out.pa-sync> <state-dir>   seal locally pending changes with the
 *                                       shared session key
 *   apply  <in.pa-sync> <state-dir>    verify + apply idempotently, print report
 *   show   <file.pa-sync>              print the plaintext routing header
 *   selftest <state-dir>               duplicate/reorder fault injection check
 *   seed   <state-dir> <suffix>        append a synthetic local change
 *   seedupdate <state-dir> <entityId> <baseVersion> <title>
 *
 * Keys: PA_SYNC_PASSPHRASE + PairingManager shared session keys (per-device
 * salts). export/apply require pairing with the phone first.
 */
const val PEER_ID = "laptop-peer"
const val PHONE_PARTNER_ID = "phone-3c3da9f8"

/** File-backed peer state: pending changes plus the set of applied keys. */
class PeerStore(private val dir: File) : LocalStore {
    private val appliedFile = File(dir, "applied.txt")
    private val applied: MutableSet<String> =
        if (appliedFile.exists()) appliedFile.readLines().toMutableSet() else mutableSetOf()
    private val ackedFile = File(dir, "acked.txt")
    private val acked: MutableSet<String> =
        if (ackedFile.exists()) ackedFile.readLines().toMutableSet() else mutableSetOf()

    override fun applyChange(change: Change): Boolean {
        val fresh = applied.add(change.idempotencyKey)
        if (fresh) {
            dir.mkdirs()
            appliedFile.appendText(change.idempotencyKey + "\n")
        }
        return fresh
    }

    fun appliedKeys(): Set<String> = applied.toSet()

    fun ack(keys: Set<String>): Int {
        var newly = 0
        for (k in keys) {
            if (acked.add(k)) newly++
        }
        if (newly > 0) {
            dir.mkdirs()
            // Atomic temp+rename; fall back to Files.move ATOMIC_MOVE if the
            // plain renameTo path fails (renameTo is non-replacing on some
            // filesystems when the target exists).
            val tmp = File(dir, "acked.txt.tmp")
            tmp.writeText(acked.joinToString("\n") + "\n")
            val renamed = try {
                java.nio.file.Files.move(
                    tmp.toPath(), ackedFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                ); true
            } catch (e: Exception) {
                tmp.renameTo(ackedFile)
            }
            if (!renamed) ackedFile.writeText(acked.joinToString("\n") + "\n")
        }
        return newly
    }

    fun ackedKeys(): Set<String> = acked.toSet()

    fun loadChanges(): MutableList<Change> {
        val file = File(dir, "changes.bin")
        if (!file.exists()) return mutableListOf()
        return EnvelopeCodec.decodeChanges(file.readBytes()).toMutableList()
    }

    fun saveChanges(changes: List<Change>) {
        dir.mkdirs()
        File(dir, "changes.bin").writeBytes(EnvelopeCodec.encodeChanges(changes))
    }

    /**
     * Moves acked changes out of the live pending log into a separate
     * compacted history (their provenance is preserved for audit) and rewrites
     * changes.bin with only the still-pending changes. Idempotent; acked keys
     * are already persisted in acked.txt, so a crash mid-compaction can only
     * re-send already-delivered changes (harmless).
     */
    fun compactInto(ackedKeys: Set<String>) {
        val all = loadChanges()
        val pending = all.filter { it.idempotencyKey !in ackedKeys }
        if (pending.size != all.size) {
            saveChanges(pending)
            val compactedFile = File(dir, "compacted.bin")
            val ackedChanges = all.filter { it.idempotencyKey in ackedKeys }
            if (compactedFile.exists()) {
                val prior = EnvelopeCodec.decodeChanges(compactedFile.readBytes())
                val merged = (prior + ackedChanges).distinctBy { it.idempotencyKey }
                compactedFile.writeBytes(EnvelopeCodec.encodeChanges(merged))
            } else {
                compactedFile.writeBytes(EnvelopeCodec.encodeChanges(ackedChanges))
            }
        }
    }
}

private fun passphrase(): CharArray =
    (System.getenv("PA_SYNC_PASSPHRASE")
        ?: error("set PA_SYNC_PASSPHRASE to derive sync keys")).toCharArray()

private fun pairing(stateDir: File) =
    ru.rudra.androidos.pa.domain.sync.PairingManager(
        ru.rudra.androidos.pa.domain.sync.FilePairStore(stateDir)
    )

/** Shared session keys with the phone; errors when not paired. */
private fun keys(stateDir: File): CryptoBox.Keys {
    val p = pairing(stateDir)
    p.ensureSelf(passphrase(), PEER_ID)
    return p.sharedKeys(passphrase(), PHONE_PARTNER_ID)
        ?: error("not paired with $PHONE_PARTNER_ID (run pairinfo and exchange salts with the phone)")
}

private fun pairInfo(stateDir: File) {
    val p = pairing(stateDir)
    p.ensureSelf(passphrase(), PEER_ID)
    println("id=$PEER_ID")
    println("salt=${p.selfSaltB64()}")
    println("pair the phone with: am start ... --es pa_sync \"pair $PEER_ID ${p.selfSaltB64()}\"")
}

private fun pair(stateDir: File, partnerId: String, partnerSaltB64: String) {
    val p = pairing(stateDir)
    p.ensureSelf(passphrase(), PEER_ID)
    p.pair(passphrase(), partnerId, partnerSaltB64, java.time.Instant.now().toString())
    println("paired with $partnerId")
}

private fun revoke(stateDir: File, partnerId: String) {
    val p = pairing(stateDir)
    p.ensureSelf(passphrase(), PEER_ID)
    p.revoke(partnerId)
    println("revoked $partnerId")
}

private fun export(outFile: File, stateDir: File, keyId: String) {
    val engine = SyncEngine(deviceId = PEER_ID)
    val store = PeerStore(stateDir)
    val all = store.loadChanges()
    val wm = ru.rudra.androidos.pa.domain.sync.ChangeWatermark().apply {
        // Seed the watermark from previously-acked keys so we never re-send
        // something the phone already confirmed.
        ackKeys(store.ackedKeys())
    }
    val pending = wm.pending(all)
    val envelope = engine.buildEnvelope(
        sequence = System.currentTimeMillis(),
        changes = pending,
        createdAt = java.time.Instant.now().toString(),
        keyId = keyId,
        encryptionAlgorithms = "AES-256-GCM+HMAC-SHA256 (shared-key pairing)",
    )
    ExchangeFile.write(outFile, envelope, keys(stateDir))
    println("exported ${pending.size} change(s) of ${all.size} -> ${outFile.absolutePath}")
    println("  envelope=${envelope.id} hash=${envelope.bundleHash.take(16)}...")
}

private fun apply(inFile: File, stateDir: File, keyId: String) {
    val header = ExchangeFile.readHeader(inFile) ?: error("not a PA-SYNC file: $inFile")
    val envelope = ExchangeFile.read(inFile, keys(stateDir))
        ?: error("MAC/tag verification failed for ${inFile.name} (tampered, wrong passphrase, or not a pair partner)")
    val store = PeerStore(stateDir)
    val report = SyncEngine(deviceId = PEER_ID).apply(envelope, store)
    // Like a real device: applied remote changes become part of local history
    // and are re-exported on the next exchange (which the phone then dedupes).
    if (report.applied > 0) {
        val known = store.loadChanges().associateBy { it.idempotencyKey }.toMutableMap()
        envelope.changes.forEach { known.putIfAbsent(it.idempotencyKey, it) }
        store.saveChanges(known.values.toList())
    }
    // Watermark: any change we ourselves originally produced that came back in
    // this bundle (an echo from the phone) is confirmed delivered — ack it so
    // the next export does not re-send it, and compact the pending log.
    val echo = envelope.changes.filter { it.actorDeviceId == PEER_ID }
    if (echo.isNotEmpty()) {
        val newly = store.ack(echo.map { it.idempotencyKey }.toSet())
        if (newly > 0) {
            store.compactInto(store.ackedKeys())
            println("  watermarked $newly previously-sent change(s) as delivered")
        }
    }
    println(
        "applied envelope=${report.envelopeId} from=${header.sender} " +
            "sequence=${header.sequence} applied=${report.applied} " +
            "duplicates=${report.duplicates} rejected=${report.rejected}" +
            (report.reason?.let { " reason=$it" } ?: "")
    )
    if (report.rejected) kotlin.system.exitProcess(2)
}

/** Test helper: append one synthetic local change so the peer has something to send. */
private fun seed(stateDir: File, idSuffix: String) {
    val store = PeerStore(stateDir)
    val changes = store.loadChanges()
    if (changes.any { it.id == "peer-seed-$idSuffix" }) {
        println("seed peer-seed-$idSuffix already present")
        return
    }
    changes.add(
        Change(
            id = "peer-seed-$idSuffix",
            entityId = "peer-entity-$idSuffix",
            operation = ChangeOperation.CREATE,
            patch = mapOf(
                "kind" to "TASK",
                "title" to "задача от ноутбука $idSuffix",
                "status" to "APPROVED",
            ),
            actorDeviceId = "laptop-peer",
            baseVersion = null,
            occurredAt = java.time.Instant.now().toString(),
            logicalClock = "clock-$idSuffix",
            idempotencyKey = "peer-key-$idSuffix",
            provenance = listOf(
                ru.rudra.androidos.pa.domain.model.ProvenanceEntry(
                    source = ru.rudra.androidos.pa.domain.model.ProvenanceSource.EXTRACTION_ENGINE,
                    actor = "laptop-peer",
                    at = java.time.Instant.now().toString(),
                    detail = "seeded",
                )
            ),
            retentionClass = RetentionClass.PERMANENT,
        )
    )
    store.saveChanges(changes)
    println("seeded peer-seed-$idSuffix (total ${changes.size} change(s))")
}

/** Test helper: append a conflict-injecting UPDATE change for an existing
 * entity with an explicit (possibly stale) baseVersion. */
private fun seedUpdate(stateDir: File, entityId: String, baseVersion: Long, title: String) {
    val store = PeerStore(stateDir)
    val changes = store.loadChanges()
    val id = "peer-update-$entityId-$baseVersion"
    if (changes.any { it.id == id }) {
        println("seedUpdate $id already present")
        return
    }
    changes.add(
        Change(
            id = id,
            entityId = entityId,
            operation = ChangeOperation.UPDATE,
            patch = mapOf(
                "kind" to "TASK",
                "title" to title,
                "status" to "APPROVED",
            ),
            actorDeviceId = "laptop-peer",
            baseVersion = baseVersion,
            occurredAt = java.time.Instant.now().toString(),
            logicalClock = null,
            idempotencyKey = "peer-update-key-$entityId-$baseVersion",
            provenance = emptyList(),
            retentionClass = RetentionClass.PERMANENT,
        )
    )
    store.saveChanges(changes)
    println("seeded $id (base=$baseVersion) for entity=$entityId title='$title' (total ${changes.size})")
}

private fun show(file: File) {
    val header = ExchangeFile.readHeader(file) ?: error("not a PA-SYNC file: $file")
    println("id=${header.id}")
    println("sender=${header.sender}")
    println("sequence=${header.sequence}")
    println("keyId=${header.keyId}")
}

private fun selftest(stateDir: File, keyId: String) {
    val engine = SyncEngine(deviceId = "laptop-peer")
    val dirA = File(stateDir, "fault-a").apply { mkdirs() }
    val dirB = File(stateDir, "fault-b").apply { mkdirs() }

    val c1 = sampleChange("a", "2026-09-26T10:00:01Z")
    val c2 = sampleChange("b", "2026-09-26T10:00:02Z")
    val e1 = engine.buildEnvelope(1, listOf(c1), createdAt = "T1", keyId = keyId)
    val e2 = engine.buildEnvelope(2, listOf(c2), createdAt = "T2", keyId = keyId)

    val storeA = PeerStore(dirA)
    val storeB = PeerStore(dirB)

    val a1 = engine.apply(e1, storeA); val a2 = engine.apply(e2, storeA)
    val b1 = engine.apply(e2, storeB); val b2 = engine.apply(e1, storeB)

    val dupA = engine.apply(e1, storeA)
    val dupB = engine.apply(e1, storeB)

    println("order A: applied=${a1.applied + a2.applied} duplicates=${a1.duplicates + a2.duplicates}")
    println("order B: applied=${b1.applied + b2.applied} duplicates=${b1.duplicates + b2.duplicates}")
    println("duplicate delivery A: applied=${dupA.applied} duplicates=${dupA.duplicates}")
    println("duplicate delivery B: applied=${dupB.applied} duplicates=${dupB.duplicates}")

    val converged = storeA.appliedKeys() == storeB.appliedKeys()
    println("converged=$converged expected=true")
    if (!converged || dupA.applied != 0 || dupB.applied != 0) kotlin.system.exitProcess(3)
}

private fun sampleChange(id: String, occurredAt: String) = Change(
    id = "change-$id",
    entityId = "entity-$id",
    operation = ChangeOperation.CREATE,
    patch = mapOf("title" to "t-$id"),
    actorDeviceId = "laptop-peer",
    baseVersion = null,
    occurredAt = occurredAt,
    logicalClock = null,
    idempotencyKey = "key-$id",
    provenance = emptyList(),
    retentionClass = RetentionClass.PERMANENT,
)

fun main(args: Array<String>) {
    val usage = "usage: pairinfo <state-dir> | pair <state-dir> <partnerId> <saltB64> | " +
        "revoke <state-dir> <partnerId> | export <out.pa-sync> <state-dir> | " +
        "apply <in.pa-sync> <state-dir> | show <file> | selftest <state-dir> | " +
        "seed <state-dir> <suffix> | seedupdate <state-dir> <entityId> <baseVersion> <title>"
    if (args.isEmpty()) {
        println(usage)
        kotlin.system.exitProcess(1)
    }
    // Arity check per command before indexing args, then friendly one-line
    // errors (not-paired, bad salt, missing passphrase) instead of stack traces.
    val minArgs = mapOf(
        "pairinfo" to 2, "pair" to 4, "revoke" to 3, "export" to 3,
        "apply" to 3, "show" to 2, "selftest" to 2, "seed" to 3, "seedupdate" to 5,
    )
    val min = minArgs[args[0]]
    if (min == null) {
        println("unknown command ${args[0]}")
        println(usage)
        kotlin.system.exitProcess(1)
    }
    if (args.size < min) {
        println("missing arguments for ${args[0]}")
        println(usage)
        kotlin.system.exitProcess(1)
    }
    val keyId = System.getenv("PA_SYNC_KEY_ID") ?: "pa-local"
    try {
        when (args[0]) {
            "pairinfo" -> pairInfo(File(args[1]))
            "pair" -> pair(File(args[1]), args[2], args[3])
            "revoke" -> revoke(File(args[1]), args[2])
            "export" -> export(File(args[1]), File(args[2]), keyId)
            "apply" -> apply(File(args[1]), File(args[2]), keyId)
            "show" -> show(File(args[1]))
            "selftest" -> selftest(File(args[1]), keyId)
            "seed" -> seed(File(args[1]), args[2])
            "seedupdate" -> seedUpdate(File(args[1]), args[2], args[3].toLong(), args[4])
        }
    } catch (e: IllegalStateException) {
        System.err.println("error: ${e.message}")
        kotlin.system.exitProcess(1)
    } catch (e: IllegalArgumentException) {
        System.err.println("error: ${e.message}")
        kotlin.system.exitProcess(1)
    }
}
