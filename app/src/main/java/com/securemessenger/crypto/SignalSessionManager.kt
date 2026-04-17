package com.securemessenger.crypto

import com.securemessenger.contact.Contact
import com.securemessenger.identity.KeyBundle
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.concurrent.ConcurrentHashMap

/**
 * Wraps libsignal's [SessionBuilder] / [SessionCipher] to provide Double-Ratchet E2EE over a
 * process-local [InMemorySignalStore].
 *
 * Construction loads the libsignal [IdentityKeyPair] and the signed pre-key record from the
 * supplied [KeyBundle] — these were generated together in [com.securemessenger.identity.IdentityManager]
 * so the signature on the signed pre-key verifies against the identity key. This matters because
 * Alice's [SignalSessionManager] and Bob's [SignalSessionManager] are constructed independently
 * in different processes (or test fixtures); they must agree on what Bob's signed pre-key and
 * identity are, and the [KeyBundle] (shared via QR) is the only channel.
 *
 * X3DH initiation: [initSessionWith] builds a [PreKeyBundle] from the remote [Contact]'s key
 * bundle and hands it to [SessionBuilder]. There are no one-time pre-keys (preKeyId = -1,
 * preKey = null) — we rely on signed pre-keys only, which is sufficient for a P2P TOFU setup.
 *
 * First-message handshake: Alice's first [encrypt] after [initSessionWith] produces a
 * [PreKeySignalMessage] (message type `PREKEY_TYPE`). Bob's [decrypt] detects this and runs
 * the implicit X3DH response internally. Subsequent messages are plain [SignalMessage]s.
 */
class SignalSessionManager(ownBundle: KeyBundle) {

    // Fixed registration id. We are single-device P2P; there is no device-id pool to reason about.
    private val registrationId = 1
    private val signedPreKeyId: Int

    private val store: InMemorySignalStore
    // Cache SessionCipher per remote address so we don't rebuild the wrapper on every message.
    private val ciphers = ConcurrentHashMap<SignalProtocolAddress, SessionCipher>()

    init {
        require(ownBundle.signalIdentityKeyPair.isNotEmpty()) {
            "KeyBundle is missing signalIdentityKeyPair — was it produced by IdentityManager?"
        }
        require(ownBundle.signedPreKey.isNotEmpty()) {
            "KeyBundle is missing signedPreKey"
        }

        val identityKeyPair = IdentityKeyPair(ownBundle.signalIdentityKeyPair)
        val signedPreKey    = SignedPreKeyRecord(ownBundle.signedPreKey)
        signedPreKeyId      = signedPreKey.id

        store = InMemorySignalStore(identityKeyPair, registrationId)
        store.storeSignedPreKey(signedPreKeyId, signedPreKey)
    }

    /**
     * Initiate a session with [contact] by processing their pre-key bundle. Must be called
     * before [encrypt] on the initiator side.
     */
    fun initSessionWith(contact: Contact) {
        val remoteBundle = contact.keyBundle
        require(remoteBundle.signalIdentityKeyPair.isNotEmpty()) {
            "Remote contact's KeyBundle is missing signalIdentityKeyPair"
        }
        require(remoteBundle.signedPreKey.isNotEmpty()) {
            "Remote contact's KeyBundle is missing signedPreKey"
        }

        // We can recover the remote's IdentityKey from the serialized IdentityKeyPair.
        // IdentityKeyPair.serialize() is [publicKey || privateKey]; constructing the pair
        // and taking its public half is the safest cross-version approach.
        val remoteIdentityKeyPair = IdentityKeyPair(remoteBundle.signalIdentityKeyPair)
        val remoteIdentityKey     = remoteIdentityKeyPair.publicKey

        val remoteSpk = SignedPreKeyRecord(remoteBundle.signedPreKey)

        val preKeyBundle = PreKeyBundle(
            /* registrationId     */ registrationId,
            /* deviceId           */ contact.signalAddress.deviceId,
            /* preKeyId           */ PreKeyBundle.NULL_PRE_KEY_ID,   // no one-time pre-keys
            /* preKeyPublic       */ null,
            /* signedPreKeyId     */ remoteSpk.id,
            /* signedPreKeyPublic */ remoteSpk.keyPair.publicKey,
            /* signedPreKeySig    */ remoteSpk.signature,
            /* identityKey        */ remoteIdentityKey,
        )

        SessionBuilder(store, contact.signalAddress).process(preKeyBundle)
    }

    /**
     * Encrypt [plaintext] for [contact]. Returns the serialized CiphertextMessage bytes.
     *
     * The returned bytes are either a serialized [PreKeySignalMessage] (on the first message
     * after [initSessionWith] before the remote has acked) or a plain [SignalMessage].
     * [decrypt] handles both transparently.
     */
    fun encrypt(contact: Contact, plaintext: ByteArray): ByteArray {
        val cipher = ciphers.getOrPut(contact.signalAddress) {
            SessionCipher(store, contact.signalAddress)
        }
        return cipher.encrypt(plaintext).serialize()
    }

    /**
     * Decrypt [ciphertext] received from [contact]. Tries [PreKeySignalMessage] first (the
     * X3DH initial-message shape) and falls back to [SignalMessage] (the steady-state shape).
     *
     * Throws whatever libsignal throws on authentication failure — the caller is expected to
     * treat any exception as a hard decrypt failure. libsignal's state machine is crash-safe
     * on bad MACs/tampered ciphertexts: the session is not advanced unless decrypt succeeds.
     */
    fun decrypt(contact: Contact, ciphertext: ByteArray): ByteArray {
        val cipher = ciphers.getOrPut(contact.signalAddress) {
            SessionCipher(store, contact.signalAddress)
        }
        // Try PreKeySignalMessage first (X3DH initial handshake). If parsing fails, fall back
        // to SignalMessage (steady-state). Both share the same serialization envelope but are
        // structurally distinct; libsignal parsers will reject the wrong one.
        return try {
            cipher.decrypt(PreKeySignalMessage(ciphertext))
        } catch (initial: Exception) {
            try {
                cipher.decrypt(SignalMessage(ciphertext))
            } catch (steady: Exception) {
                // Surface the more informative exception. If the bytes are neither, the
                // steady-state parser's error is usually the better signal since PreKey
                // parse failures happen whenever the byte stream isn't a PreKey envelope.
                throw steady
            }
        }
    }
}
