package com.securemessenger.identity

import java.util.Base64

data class KeyBundle(
    val ed25519PublicKey: ByteArray,   // 32 bytes — Tor hidden service public key
    val onionAddress: String,          // derived Tor v3 .onion address
    val signedPreKey: ByteArray,       // libsignal signed pre-key record bytes
    // Optional: libsignal IdentityKeyPair serialized bytes. When present, this carries
    // both the public identity (used by a remote party to build a PreKeyBundle for us)
    // AND our private identity (used locally by SignalSessionManager to decrypt). The
    // field is optional (defaults to empty) to preserve backwards compatibility with
    // callers/tests that construct KeyBundle without libsignal identity material.
    val signalIdentityKeyPair: ByteArray = ByteArray(0),
) {
    fun toJson(): String {
        val pub   = Base64.getEncoder().encodeToString(ed25519PublicKey)
        val spk   = Base64.getEncoder().encodeToString(signedPreKey)
        val sid   = Base64.getEncoder().encodeToString(signalIdentityKeyPair)
        // onionAddress is always a safe ASCII string (base32 + ".onion"), no escaping needed.
        return """{"pub":"$pub","onion":"$onionAddress","spk":"$spk","sid":"$sid"}"""
    }

    override fun equals(other: Any?) = other is KeyBundle
            && ed25519PublicKey.contentEquals(other.ed25519PublicKey)
            && onionAddress == other.onionAddress
            && signedPreKey.contentEquals(other.signedPreKey)
            && signalIdentityKeyPair.contentEquals(other.signalIdentityKeyPair)
    override fun hashCode(): Int {
        var result = ed25519PublicKey.contentHashCode()
        result = 31 * result + onionAddress.hashCode()
        result = 31 * result + signedPreKey.contentHashCode()
        result = 31 * result + signalIdentityKeyPair.contentHashCode()
        return result
    }
    override fun toString() = "KeyBundle(onion=${onionAddress.take(8)}…)"

    companion object {
        private val PUB_RE   = Regex(""""pub"\s*:\s*"([^"]+)"""")
        private val ONION_RE = Regex(""""onion"\s*:\s*"([^"]+)"""")
        private val SPK_RE   = Regex(""""spk"\s*:\s*"([^"]+)"""")
        private val SID_RE   = Regex(""""sid"\s*:\s*"([^"]*)"""")  // may be empty

        fun fromJson(json: String): KeyBundle {
            fun extract(re: Regex) = re.find(json)?.groupValues?.get(1)
                ?: error("Missing field matching $re in JSON")
            val sidValue = SID_RE.find(json)?.groupValues?.get(1)  // nullable/optional
            return KeyBundle(
                ed25519PublicKey      = Base64.getDecoder().decode(extract(PUB_RE)),
                onionAddress          = extract(ONION_RE),
                signedPreKey          = Base64.getDecoder().decode(extract(SPK_RE)),
                signalIdentityKeyPair = if (sidValue.isNullOrEmpty()) ByteArray(0)
                                        else Base64.getDecoder().decode(sidValue),
            )
        }
    }
}
