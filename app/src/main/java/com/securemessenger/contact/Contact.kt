package com.securemessenger.contact

import com.securemessenger.identity.KeyBundle
import org.signal.libsignal.protocol.SignalProtocolAddress

data class Contact(
    val keyBundle: KeyBundle,
    val signalAddress: SignalProtocolAddress,
) {
    companion object {
        fun fromKeyBundle(bundle: KeyBundle): Contact = Contact(
            keyBundle      = bundle,
            signalAddress  = SignalProtocolAddress(bundle.onionAddress, 1),
        )
    }
}
