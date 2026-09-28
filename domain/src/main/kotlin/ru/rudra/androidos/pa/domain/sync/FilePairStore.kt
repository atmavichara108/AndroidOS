package ru.rudra.androidos.pa.domain.sync

import java.io.File
import java.util.Properties

/**
 * JVM file-backed [PairStore] shared by the phone app and the laptop peer.
 * The identity and partner list are a small Properties file in [dir]; the salt
 * is stored in plaintext because it is public by design (exchanged in the clear
 * during pairing) — only the passphrase is secret. If this file is lost (app
 * data cleared), a fresh salt is re-rolled and all partner shared keys break
 * fail-closed — re-pairing is required.
 *
 * All access is synchronized (UI + background sync may touch it concurrently)
 * and persist() writes via a temp file + atomic rename so a kill mid-write
 * cannot truncate the properties file.
 */
class FilePairStore(private val dir: File) : PairStore {

    private val propsFile = File(dir, "pairing.properties")

    private val props: Properties = Properties().apply {
        if (propsFile.exists()) propsFile.inputStream().use { load(it) }
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
        val salt = props.getProperty("partner.$deviceId.salt")?.takeIf { it.isNotEmpty() }
            ?: return null
        PairedDevice(deviceId, salt, props.getProperty("partner.$deviceId.at") ?: "")
    }

    override fun addPartner(device: PairedDevice): Unit = synchronized(this) {
        props.setProperty("partner.${device.deviceId}.salt", device.saltB64)
        props.setProperty("partner.${device.deviceId}.at", device.addedAt)
        val ids = partnerIds()
        ids.add(device.deviceId)
        writePartnerIds(ids)
        persist()
    }

    override fun removePartner(deviceId: String): Unit = synchronized(this) {
        props.remove("partner.$deviceId.salt")
        props.remove("partner.$deviceId.at")
        val ids = partnerIds()
        ids.remove(deviceId)
        writePartnerIds(ids)
        persist()
    }

    private fun partnerIds(): MutableSet<String> =
        (props.getProperty("partners.ids")?.split(",")?.filter { it.isNotBlank() } ?: emptyList())
            .toMutableSet()

    private fun writePartnerIds(ids: Set<String>) {
        val sorted = ids.sorted()
        props.setProperty("partners.ids", sorted.joinToString(","))
        props.setProperty("partners.at", sorted.map { props.getProperty("partner.$it.at") ?: "" }.joinToString(","))
    }

    private fun persist() {
        dir.mkdirs()
        val tmp = File(dir, "pairing.properties.tmp")
        tmp.outputStream().use { props.store(it, "androidos pairing") }
        if (!tmp.renameTo(propsFile)) {
            // rename can fail across some filesystems; fall back to an overwrite
            // rather than losing the update silently.
            propsFile.outputStream().use { props.store(it, "androidos pairing") }
            tmp.delete()
        }
    }
}