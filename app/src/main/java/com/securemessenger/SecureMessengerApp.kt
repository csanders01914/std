package com.securemessenger

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.identity.IdentityManager
import com.securemessenger.identity.KeyBundle
import com.securemessenger.messaging.MessageQueue
import com.securemessenger.messaging.MessagingService
import com.securemessenger.transport.TorTransport

class SecureMessengerApp : Application() {
    lateinit var messagingService: MessagingService
        private set

    override fun onCreate() {
        super.onCreate()
        val (bundle, privateKeyBytes) = loadOrCreateIdentity(this)
        // TorTransport expects 64-byte Ed25519 key: publicKey(32) + privateKey(32)
        val fullKey   = bundle.ed25519PublicKey + privateKeyBytes
        val transport = TorTransport(this, fullKey)
        val session   = SignalSessionManager(bundle)
        val contacts  = ContactStore()
        val queue     = MessageQueue()
        messagingService = MessagingService(bundle, transport, session, contacts, queue)
    }

    private fun loadOrCreateIdentity(context: Context): Pair<KeyBundle, ByteArray> {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context, "identity", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val existingJson = prefs.getString("bundle_json", null)
        val existingPriv = prefs.getString("priv_b64", null)
        if (existingJson != null && existingPriv != null) {
            return Pair(
                KeyBundle.fromJson(existingJson),
                Base64.decode(existingPriv, Base64.NO_WRAP),
            )
        }
        val (bundle, privateKeyBytes) = IdentityManager.generateIdentityWithPrivateKey()
        prefs.edit()
            .putString("bundle_json", bundle.toJson())
            .putString("priv_b64", Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP))
            .apply()
        return Pair(bundle, privateKeyBytes)
    }
}
