package com.securemessenger.contact

import com.securemessenger.identity.KeyBundle
import org.signal.libsignal.protocol.SignalProtocolAddress

class Contact(
    val keyBundle: KeyBundle,
    val signalAddress: SignalProtocolAddress,
    val nickname: String? = null,
) {
    val displayName: String get() = nickname ?: (keyBundle.onionAddress.take(8) + "...")

    override fun toString(): String = "Contact(name=$displayName, onion=${keyBundle.onionAddress.take(8)}...)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        
        return keyBundle == other.keyBundle && 
               signalAddress == other.signalAddress && 
               nickname == other.nickname
    }

    override fun hashCode(): Int {
        var result = keyBundle.hashCode()
        result = 31 * result + signalAddress.hashCode()
        result = 31 * result + (nickname?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun fromKeyBundle(bundle: KeyBundle, nickname: String? = null): Contact = Contact(
            keyBundle      = bundle,
            signalAddress  = SignalProtocolAddress(bundle.onionAddress, 1),
            nickname       = nickname
        )
    }
}
