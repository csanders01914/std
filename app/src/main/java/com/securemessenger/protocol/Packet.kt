package com.securemessenger.protocol

import java.util.UUID

sealed class Packet {
    abstract val id: UUID

    data class Msg(override val id: UUID, val ciphertext: ByteArray) : Packet() {
        override fun equals(other: Any?) = other is Msg && id == other.id && ciphertext.contentEquals(other.ciphertext)
        override fun hashCode() = 31 * id.hashCode() + ciphertext.contentHashCode()
    }
    data class Ack(override val id: UUID) : Packet()
    data class ReadReceipt(override val id: UUID) : Packet()
}
