package com.securemessenger.messaging

import java.util.UUID

enum class MessageStatus { PENDING, DELIVERED, READ, FAILED }

data class Message(
    val id: UUID,
    val recipientOnion: String,
    val plaintext: String,
    val ciphertext: ByteArray,
    val status: MessageStatus,
    val timestamp: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?) = other is Message && id == other.id
    override fun hashCode() = id.hashCode()
}
