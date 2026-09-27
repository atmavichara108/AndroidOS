package ru.rudra.androidos.pa.sync

import android.content.Context
import ru.rudra.androidos.pa.domain.sync.PairedDevice
import ru.rudra.androidos.pa.domain.sync.PairStore
import java.io.File
import java.util.Properties

/**
 * File-backed [PairStore] in the app's private files dir. The identity and
 * partner list are small and versioned; storing them as a Properties file keeps
 * the dependency surface at zero. All access is synchronized (UI + background
 * sync may touch it concurrently) and persist() writes via a temp file +
 * atomic rename so a kill mid-write cannot truncate pairing.properties.
 *
 * Security: the salt is stored in plaintext because it is public by design
 * (exchanged in the clear during pairing); only the passphrase is secret and
 * keys are PBKDF2-derived from it with this salt. If this file is lost (app
 * data cleared), a fresh salt is re-rolled and all partner shared keys break
 * fail-closed — re-pairing is required. Nothing here encrypts or signs; that
 * is PairingManager's job.
 */
class AndroidPairStore(context: Context) : PairStore {

    private val dir = File(context.filesDir, "pairing").apply { mkdirs() }
    private val propsFile = File(dir, "pairing.properties")

    private val props: Properties = synchronized(this) {
        Properties().apply { if (propsFile.exists()) propsFile.inputStream().use { load(it) } }
    }

    override fun selfId(): String? = synchronized(this) {
        props.getProperty("self.id")?.takeIf { it.isNotEmpty() }
    }

    override fun selfSaltB64(): String? = synchronized(this) {
        props.getProperty("self.salt")?.takeIf { it.isNotEmpty() }
    }

    override fun saveSelf(deviceId: String, saltB64: String): Unit = synchronized(this) {
        props.setProperty("self.id", deviceId)
        props.setProperty("self.salt", saltB64)
        persist()
    }

    override fun partners(): List<PairedDevice> = synchronized(this) {
        val ids = props.getProperty("partners.ids")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val at = props.getProperty("partners.at")?.split(",") ?: emptyList()
        ids.mapIndexed { i, id ->
            PairedDevice(
                deviceId = id,
                saltB64 = props.getProperty("partner.$id.salt") ?: "",
                addedAt = at.getOrElse(i) { "" },
            )
        }
    }

    override fun partner(deviceId: String): PairedDevice? = synchronized(this) {
        val salt = props.getProperty("partner.$deviceId.salt")?.takeIf { it.isNotEmpty() } ?: return null
        PairedDevice(deviceId, salt, props.getProperty("partner.$deviceId.at") ?: "")
    }

    override fun addPartner(device: PairedDevice): Unit = synchronized(this) {
        props.setProperty("partner.${device.deviceId}.salt", device.saltB64)
        props.setProperty("partner.${device.deviceId}.at", device.addedAt)
        val ids = (props.getProperty("partners.ids")?.split(",")?.filter { it.isNotBlank() }
            ?: emptyList()).toMutableSet()
        ids.add(device.deviceId)
        props.setProperty("partners.ids", ids.sorted().joinToString(","))
        val ats = ids.sorted().map { props.getProperty("partner.$it.at") ?: "" }
        props.setProperty("partners.at", ats.joinToString(","))
        persist()
    }

    override fun removePartner(deviceId: String): Unit = synchronized(this) {
        props.remove("partner.$deviceId.salt")
        props.remove("partner.$deviceId.at")
        val ids = (props.getProperty("partners.ids")?.split(",")?.filter { it.isNotBlank() }
            ?: emptyList()).toMutableSet()
        ids.remove(deviceId)
        props.setProperty("partners.ids", ids.sorted().joinToString(","))
        val ats = ids.sorted().map { props.getProperty("partner.$it.at") ?: "" }
        props.setProperty("partners.at", ats.joinToString(","))
        persist()
    }

    private fun persist() {
        val tmp = File(dir, "pairing.properties.tmp")
        tmp.outputStream().use { props.store(it, "androidos pairing") }
        if (!tmp.renameTo(propsFile)) {
            // rename can fail across some filesystems; fall back to a copy-free
            // overwrite, which is still better than losing the update silently.
            propsFile.outputStream().use { props.store(it, "androidos pairing") }
            tmp.delete()
        }
    }
}