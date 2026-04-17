package com.securemessenger.crypto

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [SignalProtocolStore] that backs all protocol state with
 * [ConcurrentHashMap]s.
 *
 * Trust policy: always trust. The real trust anchor for this app is TOFU at QR-scan time;
 * once a contact's [IdentityKey] is saved here we treat it as legitimate for the life of
 * the process. Since we regenerate storage on every app run (no persistence yet), there is
 * no long-term identity compare step to do here.
 *
 * Pre-keys (one-time): not used — we rely on signed pre-keys only. The corresponding
 * [PreKeyStore][org.signal.libsignal.protocol.state.PreKeyStore] methods are still
 * implemented for interface compliance but will never be exercised in the current flow.
 */
class InMemorySignalStore(
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int,
) : SignalProtocolStore {

    private val identities     = ConcurrentHashMap<SignalProtocolAddress, IdentityKey>()
    private val sessions       = ConcurrentHashMap<SignalProtocolAddress, ByteArray>()  // serialized SessionRecord
    private val preKeys        = ConcurrentHashMap<Int, ByteArray>()                    // serialized PreKeyRecord
    private val signedPreKeys  = ConcurrentHashMap<Int, ByteArray>()                    // serialized SignedPreKeyRecord
    private val kyberPreKeys   = ConcurrentHashMap<Int, ByteArray>()                    // serialized KyberPreKeyRecord
    private val kyberPreKeyUsed = ConcurrentHashMap.newKeySet<Int>()                    // ids that have been marked used
    private val senderKeys     = ConcurrentHashMap<Pair<SignalProtocolAddress, UUID>, ByteArray>()  // serialized SenderKeyRecord

    // --- IdentityKeyStore ---------------------------------------------------

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair
    override fun getLocalRegistrationId(): Int = registrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val previous = identities.put(address, identityKey)
        return previous != null && previous != identityKey
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        // TOFU via QR exchange — trust was established at scan time. Always trust here.
        return true
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = identities[address]

    // --- SessionStore -------------------------------------------------------

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val bytes = sessions[address]
        return if (bytes != null) SessionRecord(bytes) else SessionRecord()
    }

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> {
        return addresses.map {
            val bytes = sessions[it] ?: throw NoSessionException("No session for $it")
            SessionRecord(bytes)
        }
    }

    override fun getSubDeviceSessions(name: String): List<Int> =
        sessions.keys.asSequence()
            .filter { it.name == name && it.deviceId != 1 }
            .map { it.deviceId }
            .toList()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessions[address] = record.serialize()
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        sessions.containsKey(address)

    override fun deleteSession(address: SignalProtocolAddress) {
        sessions.remove(address)
    }

    override fun deleteAllSessions(name: String) {
        val toRemove = sessions.keys.filter { it.name == name }
        toRemove.forEach { sessions.remove(it) }
    }

    // --- PreKeyStore (unused — we only use signed pre-keys) ----------------

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val bytes = preKeys[preKeyId] ?: throw InvalidKeyIdException("No prekey for id $preKeyId")
        return PreKeyRecord(bytes)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeys[preKeyId] = record.serialize()
    }

    override fun containsPreKey(preKeyId: Int): Boolean = preKeys.containsKey(preKeyId)

    override fun removePreKey(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }

    // --- SignedPreKeyStore --------------------------------------------------

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val bytes = signedPreKeys[signedPreKeyId]
            ?: throw InvalidKeyIdException("No signed prekey for id $signedPreKeyId")
        return SignedPreKeyRecord(bytes)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> =
        signedPreKeys.values.map { SignedPreKeyRecord(it) }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeys[signedPreKeyId] = record.serialize()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        signedPreKeys.containsKey(signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
    }

    // --- KyberPreKeyStore (unused — we don't use PQ pre-keys) ---------------

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        val bytes = kyberPreKeys[kyberPreKeyId]
            ?: throw InvalidKeyIdException("No kyber prekey for id $kyberPreKeyId")
        return KyberPreKeyRecord(bytes)
    }

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> =
        kyberPreKeys.values.map { KyberPreKeyRecord(it) }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        kyberPreKeys[kyberPreKeyId] = record.serialize()
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean =
        kyberPreKeys.containsKey(kyberPreKeyId)

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int) {
        kyberPreKeyUsed.add(kyberPreKeyId)
    }

    // --- SenderKeyStore (group messaging — not used here) -------------------

    override fun storeSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
        record: SenderKeyRecord,
    ) {
        senderKeys[sender to distributionId] = record.serialize()
    }

    override fun loadSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
    ): SenderKeyRecord? {
        val bytes = senderKeys[sender to distributionId] ?: return null
        return SenderKeyRecord(bytes)
    }
}
