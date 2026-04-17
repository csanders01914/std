package com.securemessenger.identity

import org.apache.commons.codec.binary.Base32
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.KeyPairGenerator
import java.security.MessageDigest

object IdentityManager {

    fun generateIdentity(): KeyBundle = generateIdentityWithPrivateKey().first

    // Returns the KeyBundle AND the raw 32-byte Ed25519 private key.
    // The private key is needed by TorTransport to configure the hidden service.
    fun generateIdentityWithPrivateKey(): Pair<KeyBundle, ByteArray> {
        // Generate a true Ed25519 key pair for the Tor hidden service identity.
        // KeyPairGenerator("Ed25519") is available on JDK 15+ / Android API 33+;
        // with core library desugaring (minSdk 26) this also works on the JVM test runner.
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val torKeyPair = kpg.generateKeyPair()

        // Extract raw 32-byte public key: last 32 bytes of the X.509/SubjectPublicKeyInfo encoding.
        val pubKeyEncoded = torKeyPair.public.encoded
        val pub32 = pubKeyEncoded.takeLast(32).toByteArray()

        // Extract raw 32-byte private key: last 32 bytes of the PKCS#8 encoding.
        val privKeyEncoded = torKeyPair.private.encoded
        val priv32 = privKeyEncoded.takeLast(32).toByteArray()

        check(pub32.size == 32) { "Expected 32-byte Ed25519 public key, got ${pub32.size}" }
        check(priv32.size == 32) { "Expected 32-byte Ed25519 private key, got ${priv32.size}" }

        // Generate the Signal identity key pair and a signed pre-key (uses Curve25519 as designed)
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
