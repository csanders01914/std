package com.securemessenger.identity

import org.junit.Assert.*
import org.junit.Test

class IdentityManagerTest {

    @Test fun `generateIdentity produces non-null key bundle`() {
        val bundle = IdentityManager.generateIdentity()
        assertNotNull(bundle.ed25519PublicKey)
        assertNotNull(bundle.onionAddress)
        assertNotNull(bundle.signedPreKey)
        assertTrue(bundle.onionAddress.endsWith(".onion"))
        assertEquals(62, bundle.onionAddress.length)  // Tor v3 .onion is 56 base32 chars + ".onion"
    }

    @Test fun `onion address is deterministic for same public key`() {
        val bundle = IdentityManager.generateIdentity()
        val derived = IdentityManager.deriveOnionAddress(bundle.ed25519PublicKey)
        assertEquals(bundle.onionAddress, derived)
    }

    @Test fun `KeyBundle serialises to and from JSON`() {
        val bundle = IdentityManager.generateIdentity()
        val json = bundle.toJson()
        val restored = KeyBundle.fromJson(json)
        assertArrayEquals(bundle.ed25519PublicKey, restored.ed25519PublicKey)
        assertEquals(bundle.onionAddress, restored.onionAddress)
        assertArrayEquals(bundle.signedPreKey, restored.signedPreKey)
    }
}
