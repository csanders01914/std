package com.securemessenger.messaging

import com.securemessenger.contact.Contact
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import com.securemessenger.transport.TorTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID

class GroupManager(
    private val ownOnion: String,
    private val contactStore: ContactStore,
    private val signalManager: SignalSessionManager,
    private val transport: TorTransport,
    private val scope: CoroutineScope,
) {
    private val _groups = MutableStateFlow<Map<UUID, Group>>(emptyMap())
    val groups: StateFlow<Map<UUID, Group>> = _groups

    private val _groupMessages = MutableStateFlow<Map<UUID, List<GroupMessage>>>(emptyMap())
    val groupMessages: StateFlow<Map<UUID, List<GroupMessage>>> = _groupMessages

    // Deduplication: group message IDs that have already been displayed.
    private val seenMsgIds: MutableSet<UUID> = Collections.synchronizedSet(LinkedHashSet())

    /** Creates a group with [contacts] + self, sends invites, returns the new group ID. */
    fun createGroup(contacts: List<Contact>): UUID {
        val groupId = UUID.randomUUID()
        val memberOnions = (contacts.map { it.keyBundle.onionAddress } + ownOnion).distinct()
        val group = Group(groupId, memberOnions)
        _groups.value = _groups.value + (groupId to group)
        _groupMessages.value = _groupMessages.value + (groupId to emptyList())

        val invite = PacketSerializer.serialize(Packet.GroupInvite(groupId, memberOnions))
        contacts.forEach { contact ->
            scope.launch {
                transport.send(contact.keyBundle.onionAddress, ownOnion, invite)
            }
        }
        return groupId
    }

    /** Called when a remote GroupInvite packet arrives. */
    fun handleInvite(senderOnion: String, groupId: UUID, memberOnions: List<String>) {
        if (_groups.value.containsKey(groupId)) return
        // Reject if any member is unknown — all must have pre-shared QR codes.
        val others = memberOnions.filter { it != ownOnion }
        if (others.any { contactStore.getByOnion(it) == null }) {
            android.util.Log.w("GroupManager", "Rejecting group invite $groupId: unknown members")
            return
        }
        _groups.value = _groups.value + (groupId to Group(groupId, memberOnions))
        _groupMessages.value = _groupMessages.value + (groupId to emptyList())
    }

    /** Sends [text] to every other member of [groupId]. */
    fun sendMessage(groupId: UUID, text: String) {
        val group = _groups.value[groupId] ?: return
        val msgId = UUID.randomUUID()
        val timestamp = System.currentTimeMillis()
        val textBytes = text.toByteArray(Charsets.UTF_8)

        // Own display copy — stored as a separate ByteArray so the original can be wiped.
        seenMsgIds.add(msgId)
        addMessage(groupId, GroupMessage(msgId, groupId, ownOnion, textBytes.copyOf(), isOwn = true, timestamp))

        val recipients = group.memberOnions.filter { it != ownOnion }
        recipients.forEach { recipientOnion ->
            val contact = contactStore.getByOnion(recipientOnion) ?: return@forEach
            // Each coroutine gets its own plaintext copy so we can wipe it immediately after encrypt.
            val plainCopy = textBytes.copyOf()
            scope.launch {
                try {
                    if (!signalManager.hasSessionWith(contact)) signalManager.initSessionWith(contact)
                    val ciphertext = signalManager.encrypt(contact, plainCopy)
                    plainCopy.fill(0x00) // wipe own copy once encrypted
                    val packet = PacketSerializer.serialize(Packet.GroupMsg(msgId, groupId, ciphertext))
                    if (!transport.send(recipientOnion, ownOnion, packet)) {
                        android.util.Log.w("GroupManager", "Failed to deliver group message to $recipientOnion")
                    }
                } catch (e: Exception) {
                    plainCopy.fill(0x00)
                    android.util.Log.e("GroupManager", "Error sending group message to $recipientOnion", e)
                }
            }
        }
        // Wipe the original plaintext; per-recipient copies are wiped above after encryption.
        textBytes.fill(0x00)
    }

    /** Called when a remote GroupMsg packet arrives. */
    fun handleIncomingMessage(senderOnion: String, msgId: UUID, groupId: UUID, ciphertext: ByteArray) {
        if (!_groups.value.containsKey(groupId)) return
        if (!seenMsgIds.add(msgId)) return // duplicate
        val contact = contactStore.getByOnion(senderOnion) ?: return
        val plaintextBytes = try {
            signalManager.decrypt(contact, ciphertext)
        } catch (e: Exception) {
            android.util.Log.e("GroupManager", "Failed to decrypt group message from $senderOnion", e)
            return
        }
        addMessage(groupId, GroupMessage(msgId, groupId, senderOnion, plaintextBytes, isOwn = false))
    }

    /** User-initiated close: wipes messages, removes group, notifies members. */
    fun closeGroup(groupId: UUID) {
        val group = _groups.value[groupId] ?: return
        wipeAndRemove(groupId)
        val closePacket = PacketSerializer.serialize(Packet.GroupClose(groupId))
        val recipients = group.memberOnions.filter { it != ownOnion }
        recipients.forEach { recipientOnion ->
            scope.launch { transport.send(recipientOnion, ownOnion, closePacket) }
        }
    }

    /** Called when a remote GroupClose packet arrives. */
    fun handleClose(groupId: UUID) {
        wipeAndRemove(groupId)
    }

    private fun wipeAndRemove(groupId: UUID) {
        // Remove from state first so Compose stops rendering the messages before they're wiped.
        val messages = _groupMessages.value[groupId] ?: emptyList()
        _groups.value = _groups.value - groupId
        _groupMessages.value = _groupMessages.value - groupId
        messages.forEach { it.wipe() }
    }

    private fun addMessage(groupId: UUID, message: GroupMessage) {
        val current = _groupMessages.value[groupId] ?: return
        _groupMessages.value = _groupMessages.value + (groupId to (current + message))
    }
}
