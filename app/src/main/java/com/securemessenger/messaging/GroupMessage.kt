package com.securemessenger.messaging

import java.security.SecureRandom
import java.util.UUID

class GroupMessage(
    val id: UUID,
    val groupId: UUID,
    val senderOnion: String,
    val plaintextBytes: ByteArray,
    val isOwn: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val plaintext: String get() = String(plaintextBytes, Charsets.UTF_8)

    /** Overwrite plaintext bytes four times before releasing. */
    fun wipe() {
        plaintextBytes.fill(0x00)               // pass 1: zeros
        plaintextBytes.fill(0xFF.toByte())       // pass 2: ones
        SecureRandom().nextBytes(plaintextBytes) // pass 3: random
        plaintextBytes.fill(0x00)               // pass 4: zeros
    }
}
