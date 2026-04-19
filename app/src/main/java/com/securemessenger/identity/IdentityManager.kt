package com.securemessenger.identity

import org.apache.commons.codec.binary.Base32
import org.bouncycastle.crypto.digests.SHA3Digest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.SecureRandom

object IdentityManager {

    fun generateIdentity(): KeyBundle = generateIdentityWithPrivateKey().first

    // Returns the KeyBundle AND the raw 32-byte Ed25519 private key.
    // The private key is needed by TorTransport to configure the hidden service.
    fun generateIdentityWithPrivateKey(): Pair<KeyBundle, ByteArray> {
        // Generate a true Ed25519 key pair for the Tor hidden service identity.
        // We use BouncyCastle's lightweight API to avoid JCA provider issues on Android.
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()

        val pub32 = (keyPair.public as Ed25519PublicKeyParameters).encoded
        val priv32 = (keyPair.private as Ed25519PrivateKeyParameters).encoded

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
            ed25519PublicKey      = pub32,
            onionAddress          = deriveOnionAddress(pub32),
            signedPreKey          = spkRecord.serialize(),
            signalIdentityKeyPair = signalIdPair.serialize(),
        )
        return Pair(bundle, priv32)
    }

    fun deriveOnionAddress(ed25519PublicKey: ByteArray): String {
        require(ed25519PublicKey.size == 32) { "Expected 32-byte public key" }
        val version  = byteArrayOf(0x03)
        val input    = ".onion checksum".toByteArray(Charsets.US_ASCII) + ed25519PublicKey + version
        
        // SHA3-256 using BouncyCastle lightweight API
        val digest = SHA3Digest(256)
        digest.update(input, 0, input.size)
        val hash = ByteArray(32)
        digest.doFinal(hash, 0)
        
        val checksum = hash.copyOfRange(0, 2)
        val payload  = ed25519PublicKey + checksum + version
        val encoded  = Base32().encodeToString(payload).lowercase().trimEnd('=')
        return "$encoded.onion"  // 56 chars + ".onion" = 62 chars total
    }
}
