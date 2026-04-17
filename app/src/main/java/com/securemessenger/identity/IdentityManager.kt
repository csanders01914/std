package com.securemessenger.identity

import org.apache.commons.codec.binary.Base32
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.MessageDigest

object IdentityManager {

    fun generateIdentity(): KeyBundle = generateIdentityWithPrivateKey().first

    // Returns the KeyBundle AND the raw 32-byte Ed25519 private key.
    // The private key is needed by TorTransport to configure the hidden service.
    fun generateIdentityWithPrivateKey(): Pair<KeyBundle, ByteArray> {
        // Generate an EC key pair for the Tor hidden service identity (Ed25519 via libsignal Curve25519)
        val torKeyPair = Curve.generateKeyPair()
        // getPublicKeyBytes() returns the raw 32-byte key without the type prefix byte
        val pub32  = torKeyPair.publicKey.publicKeyBytes
        val priv32 = torKeyPair.privateKey.serialize()  // 32 raw bytes

        // Generate the Signal identity key pair and a signed pre-key
        val signalIdPair = IdentityKeyPair.generate()
        val spkKeyPair   = Curve.generateKeyPair()
        val spkSignature = Curve.calculateSignature(
            signalIdPair.privateKey,
            spkKeyPair.publicKey.serialize()
        )
        val spkRecord = SignedPreKeyRecord(
            1,
            System.currentTimeMillis(),
            spkKeyPair,
            spkSignature
        )

        val bundle = KeyBundle(
            ed25519PublicKey = pub32,
            onionAddress     = deriveOnionAddress(pub32),
            signedPreKey     = spkRecord.serialize(),
        )
        return Pair(bundle, priv32)
    }

    fun deriveOnionAddress(ed25519PublicKey: ByteArray): String {
        require(ed25519PublicKey.size == 32) { "Expected 32-byte public key" }
        val version  = byteArrayOf(0x03)
        val input    = ".onion checksum".toByteArray(Charsets.US_ASCII) + ed25519PublicKey + version
        val checksum = MessageDigest.getInstance("SHA3-256").digest(input).copyOfRange(0, 2)
        val payload  = ed25519PublicKey + checksum + version
        val encoded  = Base32().encodeToString(payload).lowercase().trimEnd('=')
        return "$encoded.onion"  // 56 chars + ".onion" = 62 chars total
    }
}
