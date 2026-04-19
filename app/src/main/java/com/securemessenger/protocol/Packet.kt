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
    data class Ping(override val id: UUID) : Packet()
    data class Pong(override val id: UUID) : Packet()
    /** id = groupId; sent by creator to each member to establish the room. */
    data class GroupInvite(override val id: UUID, val memberOnions: List<String>) : Packet()
    /** id = msgId; ciphertext encrypted 1-to-1 with the specific recipient. groupId routes to the room. */
    data class GroupMsg(override val id: UUID, val groupId: UUID, val ciphertext: ByteArray) : Packet() {
        override fun equals(other: Any?) = other is GroupMsg && id == other.id
        override fun hashCode() = id.hashCode()
    }
    /** id = groupId; instructs all members to wipe and close. */
    data class GroupClose(override val id: UUID) : Packet()
}
