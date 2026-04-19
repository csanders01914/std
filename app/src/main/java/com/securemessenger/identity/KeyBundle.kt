package com.securemessenger.identity

import java.util.Base64

data class KeyBundle(
    val ed25519PublicKey: ByteArray,   // 32 bytes — Tor hidden service public key
    val onionAddress: String,          // derived Tor v3 .onion address
    val signedPreKey: ByteArray,       // libsignal signed pre-key record bytes
    val signalIdentityKeyPair: ByteArray = ByteArray(0),
) {
    fun toJson(): String {
        val encoder = Base64.getEncoder().withoutPadding()
        val pub   = encoder.encodeToString(ed25519PublicKey)
        val spk   = encoder.encodeToString(signedPreKey)
        val sid   = encoder.encodeToString(signalIdentityKeyPair)
        return """{"pub":"$pub","onion":"$onionAddress","spk":"$spk","sid":"$sid"}"""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyBundle) return false

        if (!ed25519PublicKey.contentEquals(other.ed25519PublicKey)) return false
        if (onionAddress != other.onionAddress) return false
        if (!signedPreKey.contentEquals(other.signedPreKey)) return false
        if (!signalIdentityKeyPair.contentEquals(other.signalIdentityKeyPair)) return false

        return true
    }

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
        private val SID_RE   = Regex(""""sid"\s*:\s*"([^"]*)"""")

        fun fromJson(json: String): KeyBundle {
            fun extract(re: Regex) = re.find(json)?.groupValues?.get(1)
                ?: error("Missing field matching $re in JSON")
            val sidValue = SID_RE.find(json)?.groupValues?.get(1)
            val decoder = Base64.getDecoder()
            return KeyBundle(
                ed25519PublicKey      = decoder.decode(extract(PUB_RE)),
                onionAddress          = extract(ONION_RE),
                signedPreKey          = decoder.decode(extract(SPK_RE)),
                signalIdentityKeyPair = if (sidValue.isNullOrEmpty()) ByteArray(0)
                                        else decoder.decode(sidValue),
            )
        }
    }
}
