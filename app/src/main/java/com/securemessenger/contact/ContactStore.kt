package com.securemessenger.contact

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.securemessenger.identity.KeyBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class ContactStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        EncryptedSharedPreferences.create(
            context,
            "contacts_v1",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    )

    private val contacts = ConcurrentHashMap<String, Contact>()
    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    val allContacts: StateFlow<List<Contact>> = _allContacts.asStateFlow()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        prefs.all.forEach { (onion, value) ->
            if (value is String) {
                try {
                    // Try to parse as JSON containing both bundle and nickname
                    val nickname: String?
                    val bundle: KeyBundle
                    if (value.startsWith("{") && value.contains("\"bundle\"")) {
                        // New format: {"bundle": "...", "nickname": "..."}
                        val bundleJson = Regex(""""bundle"\s*:\s*(\{.*?\})""").find(value)?.groupValues?.get(1) ?: error("No bundle")
                        nickname = Regex(""""nickname"\s*:\s*"([^"]*)"""").find(value)?.groupValues?.get(1)
                        bundle = KeyBundle.fromJson(bundleJson)
                    } else {
                        // Old format: just the KeyBundle JSON
                        bundle = KeyBundle.fromJson(value)
                        nickname = null
                    }
                    contacts[onion] = Contact.fromKeyBundle(bundle, nickname)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        _allContacts.value = contacts.values.toList()
    }

    fun add(contact: Contact) {
        val onion = contact.keyBundle.onionAddress
        contacts[onion] = contact
        
        val jsonValue = if (contact.nickname != null) {
            """{"bundle":${contact.keyBundle.toJson()},"nickname":"${contact.nickname}"}"""
        } else {
            contact.keyBundle.toJson()
        }
        
        prefs.edit().putString(onion, jsonValue).apply()
        _allContacts.value = contacts.values.toList()
    }

    fun remove(onion: String) {
        contacts.remove(onion)
        prefs.edit().remove(onion).apply()
        _allContacts.value = contacts.values.toList()
    }

    fun rename(onion: String, nickname: String) {
        val contact = contacts[onion] ?: return
        val updated = Contact.fromKeyBundle(contact.keyBundle, nickname.ifBlank { null })
        contacts[onion] = updated
        val jsonValue = """{"bundle":${updated.keyBundle.toJson()},"nickname":"${nickname.replace("\"", "\\\"")}"}"""
        prefs.edit().putString(onion, jsonValue).apply()
        _allContacts.value = contacts.values.toList()
    }

    fun getByOnion(onionAddress: String): Contact? = contacts[onionAddress]

    fun getAll(): List<Contact> = contacts.values.toList()
}
