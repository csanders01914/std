package com.securemessenger.identity

import java.util.Base64

data class KeyBundle(
    val ed25519PublicKey: ByteArray,   // 32 bytes — Tor hidden service public key
    val onionAddress: String,          // derived Tor v3 .onion address
    val signedPreKey: ByteArray,       // libsignal signed pre-key record bytes
) {
    fun toJson(): String {
        val pub   = Base64.getEncoder().encodeToString(ed25519PublicKey)
        val spk   = Base64.getEncoder().encodeToString(signedPreKey)
        // onionAddress is always a safe ASCII string (base32 + ".onion"), no escaping needed.
        return """{"pub":"$pub","onion":"$onionAddress","spk":"$spk"}"""
    }

    override fun equals(other: Any?) = other is KeyBundle
            && ed25519PublicKey.contentEquals(other.ed25519PublicKey)
            && onionAddress == other.onionAddress
            && signedPreKey.contentEquals(other.signedPreKey)
    override fun hashCode(): Int {
        var result = ed25519PublicKey.contentHashCode()
        result = 31 * result + onionAddress.hashCode()
        result = 31 * result + signedPreKey.contentHashCode()
        return result
    }
    override fun toString() = "KeyBundle(onion=${onionAddress.take(8)}…)"

    companion object {
        private val PUB_RE   = Regex(""""pub"\s*:\s*"([^"]+)"""")
        private val ONION_RE = Regex(""""onion"\s*:\s*"([^"]+)"""")
        private val SPK_RE   = Regex(""""spk"\s*:\s*"([^"]+)"""")

        fun fromJson(json: String): KeyBundle {
            fun extract(re: Regex) = re.find(json)?.groupValues?.get(1)
                ?: error("Missing field matching $re in JSON")
            return KeyBundle(
                ed25519PublicKey = Base64.getDecoder().decode(extract(PUB_RE)),
                onionAddress     = extract(ONION_RE),
                signedPreKey     = Base64.getDecoder().decode(extract(SPK_RE)),
            )
        }
    }
}
