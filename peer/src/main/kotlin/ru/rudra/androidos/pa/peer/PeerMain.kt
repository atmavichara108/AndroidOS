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
 * Laptop peer for P1-02: a plain JVM process that speaks the same
 * domain/sync contract as the phone. It exchanges *sealed envelope files*
 * with the phone (adb or any transport); it never touches a live SQLite file.
 *
 * Commands:
 *   export <out.pa-sync> <state-dir>   build an envelope from locally pending
 *                                      changes and seal it
 *   apply  <in.pa-sync> <state-dir>    verify + apply idempotently, print report
 *   show   <file.pa-sync>              print the plaintext routing header
 *   selftest <state-dir>               duplicate/reorder fault injection check
 *
 * The passphrase comes from PA_SYNC_PASSPHRASE; keyId defaults to "pa-local"
 * (P2 replaces this with real pairing/revocation).
 */

/** File-backed peer state: pending changes plus the set of applied keys. */
class PeerStore(private val dir: File) : LocalStore {
    private val appliedFile = File(dir, "applied.txt")
    private val applied: MutableSet<String> =
        if (appliedFile.exists()) appliedFile.readLines().toMutableSet() else mutableSetOf()

    override fun applyChange(change: Change): Boolean {
        val fresh = applied.add(change.idempotencyKey)
        if (fresh) {
            dir.mkdirs()
            appliedFile.appendText(change.idempotencyKey + "\n")
        }
        return fresh
    }

    fun appliedKeys(): Set<String> = applied.toSet()

    fun loadChanges(): MutableList<Change> {
        val file = File(dir, "changes.bin")
        if (!file.exists()) return mutableListOf()
        return EnvelopeCodec.decodeChanges(file.readBytes()).toMutableList()
    }

    fun saveChanges(changes: List<Change>) {
        dir.mkdirs()
        File(dir, "changes.bin").writeBytes(EnvelopeCodec.encodeChanges(changes))
    }
}

private fun keys(keyId: String): CryptoBox.Keys {
    val passphrase = System.getenv("PA_SYNC_PASSPHRASE")
        ?: error("set PA_SYNC_PASSPHRASE to derive sync keys")
    return CryptoBox.deriveKeys(passphrase.toCharArray(), ExchangeFile.saltFor(keyId))
}

private fun export(outFile: File, stateDir: File, keyId: String) {
    val engine = SyncEngine(deviceId = "laptop-peer")
    val store = PeerStore(stateDir)
    val changes = store.loadChanges()
    val envelope = engine.buildEnvelope(
        sequence = System.currentTimeMillis(),
        changes = changes,
        createdAt = java.time.Instant.now().toString(),
        keyId = keyId,
    )
    ExchangeFile.write(outFile, envelope, keys(keyId))
    println("exported ${changes.size} change(s) -> ${outFile.absolutePath}")
    println("  envelope=${envelope.id} hash=${envelope.bundleHash.take(16)}...")
}

private fun apply(inFile: File, stateDir: File, keyId: String) {
    val header = ExchangeFile.readHeader(inFile) ?: error("not a PA-SYNC file: $inFile")
    val envelope = ExchangeFile.read(inFile, keys(keyId))
        ?: error("MAC/tag verification failed for ${inFile.name} (tampered or wrong key)")
    val store = PeerStore(stateDir)
    val report = SyncEngine(deviceId = "laptop-peer").apply(envelope, store)
    // Like a real device: applied remote changes become part of local history
    // and are re-exported on the next exchange (which the phone then dedupes).
    if (report.applied > 0) {
        val known = store.loadChanges().associateBy { it.idempotencyKey }.toMutableMap()
        envelope.changes.forEach { known.putIfAbsent(it.idempotencyKey, it) }
        store.saveChanges(known.values.toList())
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
            patch = mapOf("title" to "задача от ноутбука $idSuffix"),
            actorDeviceId = "laptop-peer",
            baseVersion = null,
            occurredAt = java.time.Instant.now().toString(),
            logicalClock = null,
            idempotencyKey = "peer-key-$idSuffix",
            provenance = emptyList(),
            retentionClass = RetentionClass.PERMANENT,
        )
    )
    store.saveChanges(changes)
    println("seeded peer-seed-$idSuffix (total ${changes.size} change(s))")
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
    if (args.isEmpty()) {
        println("usage: export|apply|show|selftest ...")
        kotlin.system.exitProcess(1)
    }
    val keyId = System.getenv("PA_SYNC_KEY_ID") ?: "pa-local"
    when (args[0]) {
        "export" -> export(File(args[1]), File(args[2]), keyId)
        "apply" -> apply(File(args[1]), File(args[2]), keyId)
        "show" -> show(File(args[1]))
        "selftest" -> selftest(File(args[1]), keyId)
        "seed" -> seed(File(args[1]), args[2])
        else -> {
            println("unknown command ${args[0]}")
            kotlin.system.exitProcess(1)
        }
    }
}
