package com.securemessenger.contact

import java.util.concurrent.ConcurrentHashMap

class ContactStore {
    private val contacts = ConcurrentHashMap<String, Contact>()

    fun add(contact: Contact) {
        contacts[contact.keyBundle.onionAddress] = contact
    }

    fun getByOnion(onionAddress: String): Contact? = contacts[onionAddress]

    fun getAll(): List<Contact> = contacts.values.toList()
}
