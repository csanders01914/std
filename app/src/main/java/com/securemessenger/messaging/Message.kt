package com.securemessenger.messaging

import java.util.UUID

enum class MessageStatus { PENDING, DELIVERED, READ }

data class Message(
    val id: UUID,
    val recipientOnion: String,
    val ciphertext: ByteArray,
    val status: MessageStatus,
) {
    override fun equals(other: Any?) = other is Message && id == other.id
    override fun hashCode() = id.hashCode()
}
