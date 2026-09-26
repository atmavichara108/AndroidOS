package ru.rudra.androidos.pa.domain.sync

import ru.rudra.androidos.pa.domain.model.SyncEnvelope
import java.io.File
import java.util.Base64

/**
 * On-disk exchange format for a sealed envelope — the artifact actually moved
 * between the phone and the laptop peer (via adb, or any other transport).
 *
 * Layout (text, one header per line, ciphertext last so the file is skimmable):
 *   PA-SYNC-1
 *   id: <envelope id, plaintext routing hint>
 *   sender: <device id>
 *   sequence: <n>
 *   keyId: <id>
 *   nonce: <base64>
 *   mac: <base64>
 *   payload: <base64 of the sealed EnvelopeCodec bytes>
 *
 * The plaintext header carries only routing metadata; every field of the
 * envelope itself lives inside the authenticated ciphertext.
 */
object ExchangeFile {

    private const val MAGIC = "PA-SYNC-1"

    data class Header(val id: String, val sender: String, val sequence: Long, val keyId: String)

    fun write(file: File, envelope: SyncEnvelope, keys: CryptoBox.Keys) {
        val sealed = CryptoBox.seal(EnvelopeCodec.encode(envelope), keys)
        val text = buildString {
            append(MAGIC).append('\n')
            append("id: ").append(envelope.id).append('\n')
            append("sender: ").append(envelope.senderDeviceId).append('\n')
            append("sequence: ").append(envelope.sequence).append('\n')
            append("keyId: ").append(envelope.keyId).append('\n')
            append("nonce: ").append(sealed.nonce).append('\n')
            append("mac: ").append(sealed.mac).append('\n')
            append("payload: ").append(sealed.payload).append('\n')
        }
        file.writeText(text)
    }

    /** Returns null when the file is malformed or the MAC/tag does not verify. */
    fun read(file: File, keys: CryptoBox.Keys): SyncEnvelope? {
        return try {
            readOrThrow(file, keys)
        } catch (e: Exception) {
            null
        }
    }

    private fun readOrThrow(file: File, keys: CryptoBox.Keys): SyncEnvelope? {
        val lines = file.readLines()
        if (lines.firstOrNull() != MAGIC) return null
        val fields = lines.drop(1).mapNotNull { line ->
            val idx = line.indexOf(": ")
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 2)
        }.toMap()
        val nonce = fields["nonce"] ?: return null
        val mac = fields["mac"] ?: return null
        val payload = fields["payload"] ?: return null
        val plaintext = CryptoBox.open(CryptoBox.Sealed(payload, nonce, mac), keys) ?: return null
        return EnvelopeCodec.decode(plaintext)
    }

    /** Reads only the plaintext routing header; does not verify or decrypt. */
    fun readHeader(file: File): Header? {
        val lines = file.readLines()
        if (lines.firstOrNull() != MAGIC) return null
        val fields = lines.drop(1).mapNotNull { line ->
            val idx = line.indexOf(": ")
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 2)
        }.toMap()
        return Header(
            id = fields["id"] ?: return null,
            sender = fields["sender"] ?: return null,
            sequence = fields["sequence"]?.toLongOrNull() ?: return null,
            keyId = fields["keyId"] ?: return null,
        )
    }

    fun saltFor(keyId: String): ByteArray = "androidos-sync:$keyId".toByteArray()
}
