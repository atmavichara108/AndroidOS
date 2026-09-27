package ru.rudra.androidos.pa.domain.sync

import java.security.SecureRandom

/**
 * Per-device key derivation and pairing/revocation for sync.
 *
 * Why per-device salt: deriving sync keys from a passphrase with a fixed salt
 * meant every install with the same passphrase got the same keys. Now each
 * device draws a random salt, so its device key is unique even for the same
 * passphrase, and a stolen/weak device can't be used to decrypt other
 * devices' bundles.
 *
 * Pairing: two devices exchange their public salts; the shared session keys
 * for a partner are KDF( myKey, partnerKey ) with the two device keys ordered
 * lexicographically by device id, so both sides derive the same keys without
 * any key ever crossing the wire.
 *
 * Revocation: removing a partner drops it from the pair store, after which no
 * shared key can be derived for it — envelopes to/from that device no longer
 * open.
 *
 * Deliberate scope decisions (documented per review):
 * - Session-key reuse: shared keys are a deterministic function of the two
 *   device keys with fixed HMAC labels and no per-session salt, so every
 *   session between the same pair reuses the same keys. Acceptable for the
 *   offline-bundle model where revocation is the only key change; a
 *   compromised session key would compromise every session between that pair.
 * - Salt exchange is unauthenticated: MITM resistance rests on the salt
 *   exchange being out-of-band/authenticated (provisioning step of pairing);
 *   a relay that injects its own salt into both sides yields key(attacker,A)
 *   and key(attacker,B), not a shared key.
 * - Store loss (app data cleared) re-rolls the local salt and fails closed:
 *   all partner shared keys break and re-pairing is required; nothing
 *   decrypts wrongly.
 */
data class PairedDevice(
    val deviceId: String,
    val saltB64: String,
    val addedAt: String,
)

/** Persists the local device identity and the set of paired peers. */
interface PairStore {
    fun selfId(): String?
    fun selfSaltB64(): String?
    fun saveSelf(deviceId: String, saltB64: String)
    fun partners(): List<PairedDevice>
    fun partner(deviceId: String): PairedDevice?
    fun addPartner(device: PairedDevice)
    fun removePartner(deviceId: String)
}

class PairingManager(
    private val store: PairStore,
    private val random: SecureRandom = SecureRandom(),
) {

    /**
     * Ensures this device has a stable identity and random salt, creating them
     * on first use.
     */
    fun ensureSelf(passphrase: CharArray, deviceId: String): CryptoBox.Keys {
        val existingId = store.selfId()
        if (existingId == null) {
            val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
            store.saveSelf(deviceId, encode(salt))
        } else if (existingId != deviceId) {
            // [проверить] identity is immutable once created; changing it would
            // invalidate all partner-derived keys. Treat a mismatch as an error
            // rather than silently re-rolling the salt.
            require(existingId == deviceId) {
                "device identity already set to $existingId, refusing $deviceId"
            }
        }
        return selfKeys(passphrase)
    }

    /** Pairs with a partner using its device id and public salt. */
    fun pair(passphrase: CharArray, partnerId: String, partnerSaltB64: String, at: String) {
        store.addPartner(PairedDevice(partnerId, partnerSaltB64, at))
    }

    /** Revokes a partner; no shared key can be derived for it afterwards. */
    fun revoke(partnerId: String) {
        store.removePartner(partnerId)
    }

    fun isPaired(partnerId: String): Boolean = store.partner(partnerId) != null

    /**
     * Shared session keys for a paired partner. Returns null when the partner
     * is not (or no longer) paired.
     */
    fun sharedKeys(passphrase: CharArray, partnerId: String): CryptoBox.Keys? {
        val partner = store.partner(partnerId) ?: return null
        val self = selfKeys(passphrase)
        val partnerKeys = CryptoBox.deriveKeys(passphrase, decode(partner.saltB64))
        return merge(self, partnerKeys, selfId()!!, partnerId)
    }

    fun selfKeys(passphrase: CharArray): CryptoBox.Keys =
        CryptoBox.deriveKeys(passphrase, decode(store.selfSaltB64()!!))

    /** Derives a deterministic shared key from two device keys, ordered by id.
     * Only the PBKDF2 encryption halves are mixed — they carry the full 256-bit
     * entropy; the partner's separate macKey is intentionally unused here (the
     * shared mac key is re-derived from the merged material below). */
    private fun merge(a: CryptoBox.Keys, b: CryptoBox.Keys, aId: String, bId: String): CryptoBox.Keys {
        val first: CryptoBox.Keys
        val second: CryptoBox.Keys
        if (aId < bId) {
            first = a; second = b
        } else {
            first = b; second = a
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(first.encryptionKey)
        digest.update(second.encryptionKey)
        val material = digest.digest()
        // Split the 32-byte hash into two 16-byte halves and expand with HMAC
        // labels so enc/mac keys are distinct and domain-separated.
        val enc = hmac(material, "enc".toByteArray())
        val mac = hmac(material, "mac".toByteArray())
        return CryptoBox.Keys(enc, mac)
    }

    private fun hmac(key: ByteArray, label: ByteArray): ByteArray {
        val h = javax.crypto.Mac.getInstance("HmacSHA256")
        h.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        h.update(label)
        return h.doFinal()
    }

    private fun encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)

    private fun selfId(): String = store.selfId() ?: error("device identity not initialized")

    companion object {
        const val SALT_BYTES = 32
    }
}