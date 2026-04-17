# Secure Messenger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Kotlin Android messaging app with Signal Protocol E2EE, Tor onion routing, ephemeral in-memory messages, and in-person QR code key exchange — no server, no accounts, no data on disk.

**Architecture:** Four-layer stack (UI → Messaging → Crypto → Transport). Each user's identity is an Ed25519 key pair; their Tor v3 `.onion` address is derived from that key and serves as their address. Messages are encrypted by libsignal-android before entering the Tor circuit; nothing is written to disk except the identity key (in EncryptedSharedPreferences) and the Tor hidden service key (in app private storage).

**Tech Stack:** Kotlin, Jetpack Compose, libsignal-android (Signal Foundation), tor-android (Guardian Project), Jetpack Security (EncryptedSharedPreferences), ZXing (QR), JUnit 4, Espresso.

---

## File Map

```
app/
  src/
    main/
      java/com/securemessenger/
        identity/
          KeyBundle.kt           — data: Ed25519 pubkey + onion address + signed pre-key
          IdentityManager.kt     — generates/loads Ed25519 keypair, derives .onion, encodes QR
        contact/
          Contact.kt             — data: publicKey + onionAddress + Signal address
          ContactStore.kt        — in-memory contact list, cleared on process death
        protocol/
          Packet.kt              — sealed class: MSG | ACK | READ_RECEIPT
          PacketSerializer.kt    — Packet ↔ ByteArray
        crypto/
          InMemorySignalStore.kt — implements SignalProtocolStore (in-memory)
          SignalSessionManager.kt— X3DH session init + Double Ratchet encrypt/decrypt
        transport/
          TorTransport.kt        — embeds tor-android, hosts hidden service, send/receive
        messaging/
          Message.kt             — data class + MessageStatus enum (PENDING→DELIVERED→READ)
          MessageQueue.kt        — ephemeral retry buffer with exponential backoff
          ReceiptManager.kt      — sends delivery ACK + read receipt, updates MessageQueue
          MessagingService.kt    — coordinator: wires transport, crypto, queue, receipts
        ui/
          MainActivity.kt        — single-activity host, sets FLAG_SECURE
          AppNavigation.kt       — Compose NavHost
          theme/Theme.kt         — Material3 dark theme
          contactlist/
            ContactListScreen.kt
            ContactListViewModel.kt
          qr/
            QRScreen.kt          — shows own QR + scans contact QR
            QRViewModel.kt
          chat/
            ChatScreen.kt        — message list with tick indicators
            ChatViewModel.kt
    test/
      java/com/securemessenger/
        identity/IdentityManagerTest.kt
        contact/ContactStoreTest.kt
        protocol/PacketSerializerTest.kt
        crypto/SignalSessionManagerTest.kt
        messaging/MessageQueueTest.kt
        messaging/ReceiptManagerTest.kt
        security/SecurityRegressionTest.kt
    androidTest/
      java/com/securemessenger/
        IntegrationTest.kt       — full round-trip over real Tor (manual, two devices)
  build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml
```

---

## Task 1: Android Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/securemessenger/ui/MainActivity.kt`

- [ ] **Step 1: Initialise project**

  In Android Studio (or via CLI): create a new project with package `com.securemessenger`, Kotlin DSL, Empty Compose Activity, minSdk 26, targetSdk 35.

- [ ] **Step 2: Replace `gradle/libs.versions.toml` with pinned versions**

```toml
[versions]
kotlin = "2.0.21"
agp = "8.7.3"
compose-bom = "2024.12.01"
activity-compose = "1.9.3"
security-crypto = "1.1.0-alpha06"
libsignal = "0.54.0"
tor-android = "0.4.9.4"
jtorctl = "0.4.5.7"
zxing = "3.5.3"
commons-codec = "1.17.0"

[libraries]
compose-bom          = { group = "androidx.compose", name = "compose-bom",           version.ref = "compose-bom" }
compose-ui           = { group = "androidx.compose.ui", name = "ui" }
compose-material3    = { group = "androidx.compose.material3", name = "material3" }
compose-tooling      = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
activity-compose     = { group = "androidx.activity", name = "activity-compose",     version.ref = "activity-compose" }
security-crypto      = { group = "androidx.security", name = "security-crypto",      version.ref = "security-crypto" }
libsignal-android    = { group = "org.signal", name = "libsignal-android",           version.ref = "libsignal" }
tor-android          = { group = "info.guardianproject", name = "tor-android",       version.ref = "tor-android" }
jtorctl              = { group = "info.guardianproject", name = "jtorctl",           version.ref = "jtorctl" }
zxing-core           = { group = "com.google.zxing", name = "core",                 version.ref = "zxing" }
commons-codec        = { group = "commons-codec", name = "commons-codec",           version.ref = "commons-codec" }
junit                = { group = "junit", name = "junit",                            version = "4.13.2" }
```

- [ ] **Step 3: Replace `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.securemessenger"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.securemessenger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.security.crypto)
    implementation(libs.libsignal.android)
    implementation(libs.tor.android)
    implementation(libs.jtorctl)
    implementation(libs.zxing.core)
    implementation(libs.commons.codec)
    testImplementation(libs.junit)
}
```

- [ ] **Step 4: Write `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.CAMERA"/>
    <application
        android:name=".SecureMessengerApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.SecureMessenger">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 5: Write `MainActivity.kt` with FLAG_SECURE**

```kotlin
package com.securemessenger.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.securemessenger.ui.theme.SecureMessengerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            SecureMessengerTheme {
                AppNavigation()
            }
        }
    }
}
```

- [ ] **Step 6: Build to confirm dependencies resolve**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. If a dependency is not found, check the version in `libs.versions.toml` against Maven Central for the correct coordinates.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "feat: android project scaffold with all dependencies"
```

---

## Task 2: Wire Protocol (Packet)

**Files:**
- Create: `app/src/main/java/com/securemessenger/protocol/Packet.kt`
- Create: `app/src/main/java/com/securemessenger/protocol/PacketSerializer.kt`
- Create: `app/src/test/java/com/securemessenger/protocol/PacketSerializerTest.kt`

Every byte sent over Tor is a `Packet`. The first byte is the type tag; the rest is the payload.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.securemessenger.protocol

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PacketSerializerTest {

    @Test fun `MSG round-trips`() {
        val id = UUID.randomUUID()
        val payload = "hello".toByteArray()
        val packet = Packet.Msg(id, payload)
        val bytes = PacketSerializer.serialize(packet)
        val decoded = PacketSerializer.deserialize(bytes)
        assertEquals(packet, decoded)
    }

    @Test fun `ACK round-trips`() {
        val id = UUID.randomUUID()
        val packet = Packet.Ack(id)
        assertEquals(packet, PacketSerializer.deserialize(PacketSerializer.serialize(packet)))
    }

    @Test fun `READ_RECEIPT round-trips`() {
        val id = UUID.randomUUID()
        val packet = Packet.ReadReceipt(id)
        assertEquals(packet, PacketSerializer.deserialize(PacketSerializer.serialize(packet)))
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
./gradlew test --tests "com.securemessenger.protocol.PacketSerializerTest"
```
Expected: compilation error (classes don't exist yet).

- [ ] **Step 3: Write `Packet.kt`**

```kotlin
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
```

- [ ] **Step 4: Write `PacketSerializer.kt`**

```kotlin
package com.securemessenger.protocol

import java.nio.ByteBuffer
import java.util.UUID

object PacketSerializer {
    private const val TAG_MSG: Byte = 0x01
    private const val TAG_ACK: Byte = 0x02
    private const val TAG_READ: Byte = 0x03

    fun serialize(packet: Packet): ByteArray {
        val uuidBytes = ByteBuffer.allocate(16).apply {
            putLong(packet.id.mostSignificantBits)
            putLong(packet.id.leastSignificantBits)
        }.array()
        return when (packet) {
            is Packet.Msg  -> byteArrayOf(TAG_MSG) + uuidBytes + packet.ciphertext
            is Packet.Ack  -> byteArrayOf(TAG_ACK) + uuidBytes
            is Packet.ReadReceipt -> byteArrayOf(TAG_READ) + uuidBytes
        }
    }

    fun deserialize(bytes: ByteArray): Packet {
        require(bytes.size >= 17) { "Packet too short: ${bytes.size}" }
        val buf = ByteBuffer.wrap(bytes, 1, 16)
        val id = UUID(buf.long, buf.long)
        return when (bytes[0]) {
            TAG_MSG  -> Packet.Msg(id, bytes.copyOfRange(17, bytes.size))
            TAG_ACK  -> Packet.Ack(id)
            TAG_READ -> Packet.ReadReceipt(id)
            else     -> error("Unknown packet tag: ${bytes[0]}")
        }
    }
}
```

- [ ] **Step 5: Run tests — confirm they pass**

```bash
./gradlew test --tests "com.securemessenger.protocol.PacketSerializerTest"
```
Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/securemessenger/protocol/ \
        app/src/test/java/com/securemessenger/protocol/
git commit -m "feat: wire protocol packet types and serializer"
```

---

## Task 3: Identity — KeyBundle + IdentityManager

**Files:**
- Create: `app/src/main/java/com/securemessenger/identity/KeyBundle.kt`
- Create: `app/src/main/java/com/securemessenger/identity/IdentityManager.kt`
- Create: `app/src/test/java/com/securemessenger/identity/IdentityManagerTest.kt`

> **Design note:** The identity key IS stored to disk (EncryptedSharedPreferences) so the user's `.onion` address survives app restarts. Messages are still never stored. The Tor hidden service key (derived from the same Ed25519 key) is written to app private storage for the Tor daemon.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.securemessenger.identity

import org.junit.Assert.*
import org.junit.Test

class IdentityManagerTest {

    @Test fun `generateIdentity produces non-null key bundle`() {
        val bundle = IdentityManager.generateIdentity()
        assertNotNull(bundle.ed25519PublicKey)
        assertNotNull(bundle.onionAddress)
        assertNotNull(bundle.signedPreKey)
        assertTrue(bundle.onionAddress.endsWith(".onion"))
        assertEquals(62, bundle.onionAddress.length)  // Tor v3 .onion is 56 base32 chars + ".onion"
    }

    @Test fun `onion address is deterministic for same public key`() {
        val bundle = IdentityManager.generateIdentity()
        val derived = IdentityManager.deriveOnionAddress(bundle.ed25519PublicKey)
        assertEquals(bundle.onionAddress, derived)
    }

    @Test fun `KeyBundle serialises to and from JSON`() {
        val bundle = IdentityManager.generateIdentity()
        val json = bundle.toJson()
        val restored = KeyBundle.fromJson(json)
        assertArrayEquals(bundle.ed25519PublicKey, restored.ed25519PublicKey)
        assertEquals(bundle.onionAddress, restored.onionAddress)
        assertArrayEquals(bundle.signedPreKey, restored.signedPreKey)
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
./gradlew test --tests "com.securemessenger.identity.IdentityManagerTest"
```
Expected: compilation error.

- [ ] **Step 3: Write `KeyBundle.kt`**

```kotlin
package com.securemessenger.identity

import android.util.Base64
import org.json.JSONObject

data class KeyBundle(
    val ed25519PublicKey: ByteArray,   // 32 bytes — Tor hidden service public key
    val onionAddress: String,          // derived Tor v3 .onion address
    val signedPreKey: ByteArray,       // libsignal signed pre-key record bytes
) {
    fun toJson(): String = JSONObject().apply {
        put("pub", Base64.encodeToString(ed25519PublicKey, Base64.NO_WRAP))
        put("onion", onionAddress)
        put("spk", Base64.encodeToString(signedPreKey, Base64.NO_WRAP))
    }.toString()

    override fun equals(other: Any?) = other is KeyBundle
            && ed25519PublicKey.contentEquals(other.ed25519PublicKey)
            && onionAddress == other.onionAddress
            && signedPreKey.contentEquals(other.signedPreKey)
    override fun hashCode() = onionAddress.hashCode()

    companion object {
        fun fromJson(json: String): KeyBundle {
            val obj = JSONObject(json)
            return KeyBundle(
                ed25519PublicKey = Base64.decode(obj.getString("pub"),  Base64.NO_WRAP),
                onionAddress     = obj.getString("onion"),
                signedPreKey     = Base64.decode(obj.getString("spk"),  Base64.NO_WRAP),
            )
        }
    }
}
```

- [ ] **Step 4: Write `IdentityManager.kt`**

```kotlin
package com.securemessenger.identity

import org.apache.commons.codec.binary.Base32
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.security.MessageDigest

object IdentityManager {

    fun generateIdentity(): KeyBundle = generateIdentityWithPrivateKey().first

    // Returns the KeyBundle AND the raw 32-byte Ed25519 private key.
    // The private key is needed by TorTransport to configure the hidden service.
    fun generateIdentityWithPrivateKey(): Pair<KeyBundle, ByteArray> {
        val torKeyPair   = org.signal.libsignal.protocol.ecc.Curve.generateKeyPair()
        val ed25519Pub   = torKeyPair.publicKey.serialize()   // 33 bytes (type byte + 32)
        val pub32        = ed25519Pub.copyOfRange(1, 33)      // strip type byte → 32 raw bytes
        val priv32       = torKeyPair.privateKey.serialize()  // 32 raw bytes

        val signalIdPair = IdentityKeyPair.generate()
        val spkRecord    = KeyHelper.generateSignedPreKey(signalIdPair, 1)

        val bundle = KeyBundle(
            ed25519PublicKey = pub32,
            onionAddress     = deriveOnionAddress(pub32),
            signedPreKey     = spkRecord.serialize(),
        )
        return Pair(bundle, priv32)
    }

    fun deriveOnionAddress(ed25519PublicKey: ByteArray): String {
        require(ed25519PublicKey.size == 32) { "Expected 32-byte public key" }
        val version  = byteArrayOf(0x03)
        val input    = ".onion checksum".toByteArray(Charsets.US_ASCII) + ed25519PublicKey + version
        val checksum = MessageDigest.getInstance("SHA3-256").digest(input).copyOfRange(0, 2)
        val payload  = ed25519PublicKey + checksum + version
        val encoded  = Base32().encodeToString(payload).lowercase().trimEnd('=')
        return "$encoded.onion"  // 56 chars + ".onion" = 62 chars total
    }
}
```

- [ ] **Step 5: Run tests — confirm they pass**

```bash
./gradlew test --tests "com.securemessenger.identity.IdentityManagerTest"
```
Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/securemessenger/identity/ \
        app/src/test/java/com/securemessenger/identity/
git commit -m "feat: identity key generation, onion address derivation, KeyBundle"
```

---

## Task 4: ContactStore

**Files:**
- Create: `app/src/main/java/com/securemessenger/contact/Contact.kt`
- Create: `app/src/main/java/com/securemessenger/contact/ContactStore.kt`
- Create: `app/src/test/java/com/securemessenger/contact/ContactStoreTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.securemessenger.contact

import com.securemessenger.identity.KeyBundle
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContactStoreTest {
    private lateinit var store: ContactStore

    @Before fun setUp() { store = ContactStore() }

    @Test fun `add and retrieve contact by onion address`() {
        val bundle = KeyBundle(ByteArray(32), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.onion", ByteArray(10))
        val contact = Contact.fromKeyBundle(bundle)
        store.add(contact)
        assertEquals(contact, store.getByOnion(bundle.onionAddress))
    }

    @Test fun `getAll returns all contacts`() {
        val b1 = KeyBundle(ByteArray(32) { 1 }, "a".repeat(56) + ".onion", ByteArray(10))
        val b2 = KeyBundle(ByteArray(32) { 2 }, "b".repeat(56) + ".onion", ByteArray(10))
        store.add(Contact.fromKeyBundle(b1))
        store.add(Contact.fromKeyBundle(b2))
        assertEquals(2, store.getAll().size)
    }

    @Test fun `getByOnion returns null for unknown address`() {
        assertNull(store.getByOnion("unknown.onion"))
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
./gradlew test --tests "com.securemessenger.contact.ContactStoreTest"
```
Expected: compilation error.

- [ ] **Step 3: Write `Contact.kt`**

```kotlin
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
```

- [ ] **Step 4: Write `ContactStore.kt`**

```kotlin
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
```

- [ ] **Step 5: Run tests — confirm they pass**

```bash
./gradlew test --tests "com.securemessenger.contact.ContactStoreTest"
```
Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/securemessenger/contact/ \
        app/src/test/java/com/securemessenger/contact/
git commit -m "feat: in-memory contact store"
```

---

## Task 5: Signal Protocol — InMemorySignalStore + SignalSessionManager

**Files:**
- Create: `app/src/main/java/com/securemessenger/crypto/InMemorySignalStore.kt`
- Create: `app/src/main/java/com/securemessenger/crypto/SignalSessionManager.kt`
- Create: `app/src/test/java/com/securemessenger/crypto/SignalSessionManagerTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.securemessenger.crypto

import com.securemessenger.contact.Contact
import com.securemessenger.identity.IdentityManager
import com.securemessenger.identity.KeyBundle
import org.junit.Assert.*
import org.junit.Test
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

class SignalSessionManagerTest {

    @Test fun `encrypt and decrypt round-trips between two managers`() {
        val aliceBundle = IdentityManager.generateIdentity()
        val bobBundle   = IdentityManager.generateIdentity()

        val aliceManager = SignalSessionManager(aliceBundle)
        val bobManager   = SignalSessionManager(bobBundle)

        val bobContact = Contact.fromKeyBundle(bobBundle)
        aliceManager.initSessionWith(bobContact)

        val plaintext  = "hello bob".toByteArray()
        val ciphertext = aliceManager.encrypt(bobContact, plaintext)

        val aliceContact = Contact.fromKeyBundle(aliceBundle)
        val decrypted    = bobManager.decrypt(aliceContact, ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test fun `ratchet advances — same plaintext produces different ciphertext each time`() {
        val aliceBundle = IdentityManager.generateIdentity()
        val bobBundle   = IdentityManager.generateIdentity()
        val aliceManager = SignalSessionManager(aliceBundle)
        val bobContact   = Contact.fromKeyBundle(bobBundle)
        aliceManager.initSessionWith(bobContact)

        val ct1 = aliceManager.encrypt(bobContact, "msg".toByteArray())
        val ct2 = aliceManager.encrypt(bobContact, "msg".toByteArray())
        assertFalse(ct1.contentEquals(ct2))
    }

    @Test fun `decrypt failure on tampered ciphertext throws and does not corrupt session`() {
        val aliceBundle  = IdentityManager.generateIdentity()
        val bobBundle    = IdentityManager.generateIdentity()
        val aliceManager = SignalSessionManager(aliceBundle)
        val bobManager   = SignalSessionManager(bobBundle)
        val bobContact   = Contact.fromKeyBundle(bobBundle)
        aliceManager.initSessionWith(bobContact)
        val aliceContact = Contact.fromKeyBundle(aliceBundle)

        val ciphertext = aliceManager.encrypt(bobContact, "data".toByteArray())
        val tampered   = ciphertext.copyOf().also { it[it.size - 1] = it[it.size - 1].inc() }

        assertThrows(Exception::class.java) {
            bobManager.decrypt(aliceContact, tampered)
        }
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
./gradlew test --tests "com.securemessenger.crypto.SignalSessionManagerTest"
```
Expected: compilation error.

- [ ] **Step 3: Write `InMemorySignalStore.kt`**

```kotlin
package com.securemessenger.crypto

import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.state.*
import java.util.concurrent.ConcurrentHashMap

class InMemorySignalStore(private val identityKeyPair: IdentityKeyPair) : SignalProtocolStore {
    private val sessions    = ConcurrentHashMap<SignalProtocolAddress, SessionRecord>()
    private val identities  = ConcurrentHashMap<SignalProtocolAddress, IdentityKey>()
    private val preKeys     = ConcurrentHashMap<Int, PreKeyRecord>()
    private val signedPreKeys = ConcurrentHashMap<Int, SignedPreKeyRecord>()

    override fun getIdentityKeyPair()          = identityKeyPair
    override fun getLocalRegistrationId()      = 1
    override fun saveIdentity(addr: SignalProtocolAddress, key: IdentityKey): Boolean {
        val prev = identities.put(addr, key)
        return prev != null && prev != key
    }
    override fun isTrustedIdentity(addr: SignalProtocolAddress, key: IdentityKey, dir: IdentityKeyStore.Direction) = true
    override fun getIdentity(addr: SignalProtocolAddress) = identities[addr]
    override fun loadSession(addr: SignalProtocolAddress)      = sessions.getOrDefault(addr, SessionRecord())
    override fun getSubDeviceSessions(name: String)            = emptyList<Int>()
    override fun storeSession(addr: SignalProtocolAddress, rec: SessionRecord) { sessions[addr] = rec }
    override fun containsSession(addr: SignalProtocolAddress)  = sessions.containsKey(addr)
    override fun deleteSession(addr: SignalProtocolAddress)    { sessions.remove(addr) }
    override fun deleteAllSessions(name: String)               { sessions.keys.filter { it.name == name }.forEach { sessions.remove(it) } }
    override fun loadPreKey(id: Int)                           = preKeys[id] ?: throw InvalidKeyIdException("No pre-key $id")
    override fun storePreKey(id: Int, rec: PreKeyRecord)       { preKeys[id] = rec }
    override fun containsPreKey(id: Int)                       = preKeys.containsKey(id)
    override fun removePreKey(id: Int)                         { preKeys.remove(id) }
    override fun loadSignedPreKey(id: Int)                     = signedPreKeys[id] ?: throw InvalidKeyIdException("No signed pre-key $id")
    override fun loadSignedPreKeys()                           = signedPreKeys.values.toList()
    override fun storeSignedPreKey(id: Int, rec: SignedPreKeyRecord) { signedPreKeys[id] = rec }
    override fun containsSignedPreKey(id: Int)                 = signedPreKeys.containsKey(id)
    override fun removeSignedPreKey(id: Int)                   { signedPreKeys.remove(id) }
}
```

- [ ] **Step 4: Write `SignalSessionManager.kt`**

```kotlin
package com.securemessenger.crypto

import com.securemessenger.contact.Contact
import com.securemessenger.identity.KeyBundle
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

class SignalSessionManager(private val ownBundle: KeyBundle) {
    private val identityKeyPair = IdentityKeyPair.generate()
    private val store           = InMemorySignalStore(identityKeyPair)

    init {
        val spk = SignedPreKeyRecord(ownBundle.signedPreKey)
        store.storeSignedPreKey(spk.id, spk)
    }

    fun initSessionWith(contact: Contact) {
        val spk        = SignedPreKeyRecord(contact.keyBundle.signedPreKey)
        val preKeyBundle = PreKeyBundle(
            /* registrationId */ 1,
            /* deviceId       */ 1,
            /* preKeyId       */ -1,
            /* preKey         */ null,
            /* signedPreKeyId */ spk.id,
            /* signedPreKey   */ spk.keyPair.publicKey,
            /* signature      */ spk.signature,
            /* identityKey    */ IdentityKey(contact.keyBundle.ed25519PublicKey, 0),
        )
        SessionBuilder(store, contact.signalAddress).processPreKeyBundle(preKeyBundle)
    }

    fun encrypt(contact: Contact, plaintext: ByteArray): ByteArray {
        if (!store.containsSession(contact.signalAddress)) initSessionWith(contact)
        return SessionCipher(store, contact.signalAddress)
            .encrypt(plaintext)
            .serialize()
    }

    fun decrypt(contact: Contact, ciphertext: ByteArray): ByteArray {
        val cipher = SessionCipher(store, contact.signalAddress)
        return try {
            cipher.decrypt(SignalMessage(ciphertext))
        } catch (_: InvalidMessageException) {
            cipher.decrypt(PreKeySignalMessage(ciphertext))
        }
    }
}
```

- [ ] **Step 5: Run tests — confirm they pass**

```bash
./gradlew test --tests "com.securemessenger.crypto.SignalSessionManagerTest"
```
Expected: 3 tests pass. If libsignal API has changed, adjust method names to match the version in `libs.versions.toml` (check Signal's GitHub for the version's changelog).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/securemessenger/crypto/ \
        app/src/test/java/com/securemessenger/crypto/
git commit -m "feat: in-memory Signal protocol store and session manager"
```

---

## Task 6: TorTransport

**Files:**
- Create: `app/src/main/java/com/securemessenger/transport/TorTransport.kt`

> This task integrates `tor-android`. The exact API depends on the library version — verify against the Guardian Project's docs/source for the pinned version. The interface below is stable; the internal implementation may need adjustment.

- [ ] **Step 1: Write `TorTransport.kt`**

No unit tests for this component — Tor requires a real network. Integration testing happens in Task 16.

```kotlin
package com.securemessenger.transport

import android.content.Context
import info.guardianproject.netcipher.proxy.OrbotHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import net.freehaven.tor.control.TorControlConnection
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class TorTransport(
    private val context: Context,
    private val hiddenServicePrivateKeyBytes: ByteArray,  // 64-byte Ed25519 private key
    private val listenPort: Int = 7979,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _inbound = MutableSharedFlow<Pair<String, ByteArray>>() // (senderOnion, bytes)
    val inbound: SharedFlow<Pair<String, ByteArray>> = _inbound

    private var torControl: TorControlConnection? = null
    var onionAddress: String = ""
        private set

    suspend fun start() = withContext(Dispatchers.IO) {
        val torDataDir = File(context.filesDir, "tor").also { it.mkdirs() }
        val hsDir      = File(torDataDir, "hs").also     { it.mkdirs() }

        // Write Ed25519 private key for the hidden service.
        // Format required by Tor: 64-byte blob prefixed with "== ed25519v1-secret: type0 ==\n"
        val keyHeader = "== ed25519v1-secret: type0 ==\n".toByteArray()
        File(hsDir, "hs_ed25519_secret_key").writeBytes(keyHeader + hiddenServicePrivateKeyBytes)

        // Start Tor using tor-android embedded binary.
        // Verify this call against the tor-android version in use.
        val torProcess = ProcessBuilder(
            File(context.applicationInfo.nativeLibraryDir, "libtor.so").absolutePath,
            "--DataDirectory", torDataDir.absolutePath,
            "--HiddenServiceDir", hsDir.absolutePath,
            "--HiddenServicePort", "7979 127.0.0.1:$listenPort",
            "--ControlPort", "9151",
            "--CookieAuthentication", "1",
            "--SocksPort", "9050",
        ).start()

        // Wait for Tor to bootstrap and publish the hidden service.
        delay(15_000)

        onionAddress = File(hsDir, "hostname").readText().trim()
        startListener()
    }

    private fun startListener() {
        scope.launch {
            ServerSocket(listenPort).use { server ->
                while (isActive) {
                    val socket = server.accept()
                    launch { handleInbound(socket) }
                }
            }
        }
    }

    private suspend fun handleInbound(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use {
            val dis     = DataInputStream(it.inputStream)
            val onion   = dis.readUTF()
            val length  = dis.readInt()
            val payload = ByteArray(length).also { b -> dis.readFully(b) }
            _inbound.emit(Pair(onion, payload))
        }
    }

    fun send(recipientOnion: String, ownOnion: String, payload: ByteArray) {
        scope.launch {
            // Route outbound through Tor SOCKS5 proxy on 127.0.0.1:9050
            val host = recipientOnion.removeSuffix(".onion")
            val proxySocket = Socket()
            // SOCKS5 handshake to Tor
            proxySocket.connect(java.net.InetSocketAddress("127.0.0.1", 9050))
            val socks = DataOutputStream(proxySocket.outputStream)
            val socksIn = DataInputStream(proxySocket.inputStream)

            // SOCKS5 no-auth greeting
            socks.write(byteArrayOf(0x05, 0x01, 0x00))
            socks.flush()
            socksIn.readFully(ByteArray(2)) // server choice

            // SOCKS5 connect to .onion:7979
            val onionBytes = host.toByteArray(Charsets.US_ASCII)
            socks.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, onionBytes.size.toByte()))
            socks.write(onionBytes)
            socks.writeShort(listenPort)
            socks.flush()
            socksIn.readFully(ByteArray(10)) // server response

            // Send framed payload
            val dos = DataOutputStream(proxySocket.outputStream)
            dos.writeUTF(ownOnion)
            dos.writeInt(payload.size)
            dos.write(payload)
            dos.flush()
            proxySocket.close()
        }
    }

    fun stop() { scope.cancel() }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/securemessenger/transport/
git commit -m "feat: tor transport — embedded tor daemon with v3 hidden service"
```

---

## Task 7: Message + MessageQueue

**Files:**
- Create: `app/src/main/java/com/securemessenger/messaging/Message.kt`
- Create: `app/src/main/java/com/securemessenger/messaging/MessageQueue.kt`
- Create: `app/src/test/java/com/securemessenger/messaging/MessageQueueTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.securemessenger.messaging

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MessageQueueTest {

    @Test fun `enqueue sets status to PENDING`() {
        val queue = MessageQueue()
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello".toByteArray(), MessageStatus.PENDING))
        assertEquals(MessageStatus.PENDING, queue.getStatus(id))
    }

    @Test fun `markDelivered transitions PENDING to DELIVERED`() {
        val queue = MessageQueue()
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello".toByteArray(), MessageStatus.PENDING))
        queue.markDelivered(id)
        assertEquals(MessageStatus.DELIVERED, queue.getStatus(id))
    }

    @Test fun `markRead transitions DELIVERED to READ`() {
        val queue = MessageQueue()
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello".toByteArray(), MessageStatus.PENDING))
        queue.markDelivered(id)
        queue.markRead(id)
        assertEquals(MessageStatus.READ, queue.getStatus(id))
    }

    @Test fun `getStatus returns null for unknown id`() {
        assertNull(MessageQueue().getStatus(UUID.randomUUID()))
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
./gradlew test --tests "com.securemessenger.messaging.MessageQueueTest"
```
Expected: compilation error.

- [ ] **Step 3: Write `Message.kt`**

```kotlin
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
```

- [ ] **Step 4: Write `MessageQueue.kt`**

```kotlin
package com.securemessenger.messaging

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MessageQueue {
    private val queue = ConcurrentHashMap<UUID, Message>()

    fun enqueue(message: Message) { queue[message.id] = message }

    fun markDelivered(id: UUID) { queue.computeIfPresent(id) { _, m -> m.copy(status = MessageStatus.DELIVERED) } }

    fun markRead(id: UUID) { queue.computeIfPresent(id) { _, m -> m.copy(status = MessageStatus.READ) } }

    fun getStatus(id: UUID): MessageStatus? = queue[id]?.status

    fun getAll(): List<Message> = queue.values.toList()

    fun clear() { queue.clear() }
}
```

- [ ] **Step 5: Run tests — confirm they pass**

```bash
./gradlew test --tests "com.securemessenger.messaging.MessageQueueTest"
```
Expected: 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/securemessenger/messaging/Message.kt \
        app/src/main/java/com/securemessenger/messaging/MessageQueue.kt \
        app/src/test/java/com/securemessenger/messaging/
git commit -m "feat: message model and ephemeral message queue with status tracking"
```

---

## Task 8: ReceiptManager

**Files:**
- Create: `app/src/main/java/com/securemessenger/messaging/ReceiptManager.kt`
- Create: `app/src/test/java/com/securemessenger/messaging/ReceiptManagerTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.securemessenger.messaging

import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ReceiptManagerTest {
    private val sentPackets = mutableListOf<Pair<String, ByteArray>>()
    private val queue = MessageQueue()
    private val manager = ReceiptManager(
        queue = queue,
        sendFn = { onion, bytes -> sentPackets.add(Pair(onion, bytes)) },
    )

    @Test fun `sendDeliveryAck emits ACK packet and marks message DELIVERED`() {
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "bob.onion", ByteArray(0), MessageStatus.PENDING))
        manager.sendDeliveryAck(id, senderOnion = "alice.onion", ownOnion = "bob.onion")
        assertEquals(1, sentPackets.size)
        val packet = PacketSerializer.deserialize(sentPackets[0].second)
        assertTrue(packet is Packet.Ack)
        assertEquals(id, packet.id)
    }

    @Test fun `sendReadReceipt emits READ_RECEIPT packet and marks message READ`() {
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "bob.onion", ByteArray(0), MessageStatus.PENDING))
        queue.markDelivered(id)
        manager.sendReadReceipt(id, senderOnion = "alice.onion", ownOnion = "bob.onion")
        val packet = PacketSerializer.deserialize(sentPackets.last().second)
        assertTrue(packet is Packet.ReadReceipt)
        assertEquals(MessageStatus.READ, queue.getStatus(id))
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
./gradlew test --tests "com.securemessenger.messaging.ReceiptManagerTest"
```
Expected: compilation error.

- [ ] **Step 3: Write `ReceiptManager.kt`**

```kotlin
package com.securemessenger.messaging

import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import java.util.UUID

class ReceiptManager(
    private val queue: MessageQueue,
    private val sendFn: (recipientOnion: String, bytes: ByteArray) -> Unit,
) {
    fun sendDeliveryAck(messageId: UUID, senderOnion: String, ownOnion: String) {
        queue.markDelivered(messageId)
        sendFn(senderOnion, PacketSerializer.serialize(Packet.Ack(messageId)))
    }

    fun sendReadReceipt(messageId: UUID, senderOnion: String, ownOnion: String) {
        queue.markRead(messageId)
        sendFn(senderOnion, PacketSerializer.serialize(Packet.ReadReceipt(messageId)))
    }

    fun handleInboundReceipt(packet: Packet) {
        when (packet) {
            is Packet.Ack         -> queue.markDelivered(packet.id)
            is Packet.ReadReceipt -> queue.markRead(packet.id)
            else                  -> Unit
        }
    }
}
```

- [ ] **Step 4: Run tests — confirm they pass**

```bash
./gradlew test --tests "com.securemessenger.messaging.ReceiptManagerTest"
```
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/securemessenger/messaging/ReceiptManager.kt \
        app/src/test/java/com/securemessenger/messaging/ReceiptManagerTest.kt
git commit -m "feat: receipt manager — delivery ACK and read receipts"
```

---

## Task 9: MessagingService (Coordinator)

**Files:**
- Create: `app/src/main/java/com/securemessenger/messaging/MessagingService.kt`

`MessagingService` is the single entry point for the UI. It wires `TorTransport`, `SignalSessionManager`, `ContactStore`, `MessageQueue`, and `ReceiptManager`.

- [ ] **Step 1: Write `MessagingService.kt`**

```kotlin
package com.securemessenger.messaging

import com.securemessenger.contact.Contact
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.identity.KeyBundle
import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import com.securemessenger.transport.TorTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class InboundMessage(val senderOnion: String, val plaintext: String, val id: UUID)

class MessagingService(
    val ownBundle: KeyBundle,
    private val transport: TorTransport,
    private val signalManager: SignalSessionManager,
    val contactStore: ContactStore,
    val messageQueue: MessageQueue,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _received = MutableStateFlow<List<InboundMessage>>(emptyList())
    val received: StateFlow<List<InboundMessage>> = _received

    private val receiptManager = ReceiptManager(
        queue  = messageQueue,
        sendFn = { onion, bytes -> transport.send(onion, ownBundle.onionAddress, bytes) },
    )

    init {
        scope.launch {
            transport.inbound.collect { (senderOnion, rawBytes) ->
                handleInbound(senderOnion, rawBytes)
            }
        }
    }

    private fun handleInbound(senderOnion: String, rawBytes: ByteArray) {
        val packet = try { PacketSerializer.deserialize(rawBytes) } catch (_: Exception) { return }
        when (packet) {
            is Packet.Msg -> {
                val contact = contactStore.getByOnion(senderOnion) ?: return // unknown sender — drop
                val plaintext = try {
                    signalManager.decrypt(contact, packet.ciphertext)
                } catch (_: Exception) { return } // decryption failure — drop silently
                _received.value = _received.value + InboundMessage(senderOnion, String(plaintext), packet.id)
                receiptManager.sendDeliveryAck(packet.id, senderOnion = senderOnion, ownOnion = ownBundle.onionAddress)
            }
            is Packet.Ack, is Packet.ReadReceipt -> receiptManager.handleInboundReceipt(packet)
        }
    }

    fun send(contact: Contact, text: String) {
        val id         = UUID.randomUUID()
        val plaintext  = text.toByteArray(Charsets.UTF_8)
        val ciphertext = signalManager.encrypt(contact, plaintext)
        val packet     = PacketSerializer.serialize(Packet.Msg(id, ciphertext))
        messageQueue.enqueue(Message(id, contact.keyBundle.onionAddress, ciphertext, MessageStatus.PENDING))
        transport.send(contact.keyBundle.onionAddress, ownBundle.onionAddress, packet)
    }

    fun markRead(messageId: UUID, senderOnion: String) {
        receiptManager.sendReadReceipt(messageId, senderOnion = senderOnion, ownOnion = ownBundle.onionAddress)
    }
}
```

- [ ] **Step 2: Build to confirm no compilation errors**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/securemessenger/messaging/MessagingService.kt
git commit -m "feat: messaging service — wires transport, crypto, queue, and receipts"
```

---

## Task 10: App Theme + Navigation

**Files:**
- Create: `app/src/main/java/com/securemessenger/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/securemessenger/ui/AppNavigation.kt`
- Create: `app/src/main/java/com/securemessenger/SecureMessengerApp.kt`

- [ ] **Step 1: Write `Theme.kt`**

```kotlin
package com.securemessenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary   = Color(0xFF4A9EFF),
    background = Color(0xFF0D0D0D),
    surface   = Color(0xFF1A1A1A),
    onPrimary  = Color.White,
    onBackground = Color(0xFFE0E0E0),
)

@Composable
fun SecureMessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
```

- [ ] **Step 2: Write `AppNavigation.kt`**

```kotlin
package com.securemessenger.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securemessenger.ui.contactlist.ContactListScreen
import com.securemessenger.ui.chat.ChatScreen
import com.securemessenger.ui.qr.QRScreen

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "contacts") {
        composable("contacts") {
            ContactListScreen(
                onContactClick = { onion -> nav.navigate("chat/$onion") },
                onAddContact   = { nav.navigate("qr") },
            )
        }
        composable("qr") {
            QRScreen(onDone = { nav.popBackStack() })
        }
        composable(
            route = "chat/{onion}",
            arguments = listOf(navArgument("onion") { type = NavType.StringType }),
        ) { backStack ->
            ChatScreen(
                onionAddress = backStack.arguments!!.getString("onion")!!,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 3: Write `SecureMessengerApp.kt`**

```kotlin
package com.securemessenger

import android.app.Application
import android.content.Context
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
        val bundle = loadOrCreateIdentity(this)
        val transport = TorTransport(this, bundle.ed25519PublicKey + ByteArray(32)) // 64-byte key: pub+priv stub
        val session   = SignalSessionManager(bundle)
        val contacts  = ContactStore()
        val queue     = MessageQueue()
        messagingService = MessagingService(bundle, transport, session, contacts, queue)
    }

    // Returns Pair(KeyBundle, ed25519PrivateKeyBytes: ByteArray[32])
    private fun loadOrCreateIdentity(context: Context): Pair<KeyBundle, ByteArray> {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context, "identity", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val existingJson = prefs.getString("bundle_json", null)
        val existingPriv = prefs.getString("priv_b64", null)
        if (existingJson != null && existingPriv != null) {
            return Pair(KeyBundle.fromJson(existingJson), android.util.Base64.decode(existingPriv, android.util.Base64.NO_WRAP))
        }
        val (bundle, privateKeyBytes) = IdentityManager.generateIdentityWithPrivateKey()
        prefs.edit()
            .putString("bundle_json", bundle.toJson())
            .putString("priv_b64", android.util.Base64.encodeToString(privateKeyBytes, android.util.Base64.NO_WRAP))
            .apply()
        return Pair(bundle, privateKeyBytes)
    }
}

// NOTE: IdentityManager must expose `generateIdentityWithPrivateKey(): Pair<KeyBundle, ByteArray>`
// that returns the bundle AND the 32-byte Ed25519 raw private key bytes.
// TorTransport receives the full 64-byte key as: publicKey(32) + privateKey(32).
```

- [ ] **Step 4: Build to confirm no compilation errors**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/securemessenger/ui/theme/ \
        app/src/main/java/com/securemessenger/ui/AppNavigation.kt \
        app/src/main/java/com/securemessenger/SecureMessengerApp.kt
git commit -m "feat: app theme, navigation, and application class with identity persistence"
```

---

## Task 11: UI — ContactListScreen

**Files:**
- Create: `app/src/main/java/com/securemessenger/ui/contactlist/ContactListViewModel.kt`
- Create: `app/src/main/java/com/securemessenger/ui/contactlist/ContactListScreen.kt`

- [ ] **Step 1: Write `ContactListViewModel.kt`**

```kotlin
package com.securemessenger.ui.contactlist

import androidx.lifecycle.ViewModel
import com.securemessenger.SecureMessengerApp
import com.securemessenger.contact.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ContactListViewModel(app: SecureMessengerApp) : ViewModel() {
    private val store = app.messagingService.contactStore
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    fun refresh() { _contacts.value = store.getAll() }
}
```

- [ ] **Step 2: Write `ContactListScreen.kt`**

```kotlin
package com.securemessenger.ui.contactlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessenger.contact.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    contacts: List<Contact>,
    onContactClick: (String) -> Unit,
    onAddContact: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Contacts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(Icons.Default.Add, contentDescription = "Add contact")
            }
        }
    ) { padding ->
        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "No contacts yet.\nTap + to scan a QR code.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(contacts) { contact ->
                    ListItem(
                        headlineContent = {
                            Text(
                                contact.keyBundle.onionAddress.take(20) + "…",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                            )
                        },
                        modifier = Modifier.clickable { onContactClick(contact.keyBundle.onionAddress) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build and confirm**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/securemessenger/ui/contactlist/
git commit -m "feat: contact list screen"
```

---

## Task 12: UI — QRScreen

**Files:**
- Create: `app/src/main/java/com/securemessenger/ui/qr/QRViewModel.kt`
- Create: `app/src/main/java/com/securemessenger/ui/qr/QRScreen.kt`

- [ ] **Step 1: Write `QRViewModel.kt`**

```kotlin
package com.securemessenger.ui.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.securemessenger.SecureMessengerApp
import com.securemessenger.contact.Contact
import com.securemessenger.identity.KeyBundle

class QRViewModel(app: SecureMessengerApp) : ViewModel() {
    private val service = app.messagingService
    val ownQrJson: String = service.contactStore
        .let { app.messagingService }
        .let { (app as SecureMessengerApp).messagingService }
        .let { it::class.java.getDeclaredField("ownBundle").also { f -> f.isAccessible = true }.get(it) as KeyBundle }
        .toJson()

    fun generateQrBitmap(json: String, sizePx: Int = 512): Bitmap {
        val bits = QRCodeWriter().encode(json, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) for (y in 0 until sizePx)
            bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    fun addContactFromJson(json: String) {
        val bundle  = KeyBundle.fromJson(json)
        val contact = Contact.fromKeyBundle(bundle)
        service.contactStore.add(contact)
    }
}

// NOTE: QRViewModel accesses ownBundle via service.ownBundle (a public val on MessagingService).
// Add `val ownBundle: KeyBundle` as a public constructor parameter exposed on MessagingService.
```

- [ ] **Step 2: Write `QRScreen.kt`**

```kotlin
package com.securemessenger.ui.qr

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QRScreen(
    ownQrBitmap: android.graphics.Bitmap,
    onScanResult: (String) -> Unit,
    onDone: () -> Unit,
) {
    var scanning by remember { mutableStateOf(false) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(topBar = { TopAppBar(title = { Text("Exchange Keys") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Your QR code", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            Image(
                bitmap = ownQrBitmap.asImageBitmap(),
                contentDescription = "Your identity QR code",
                modifier = Modifier.size(280.dp).padding(8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (cameraPermission.status.isGranted) scanning = true
                else cameraPermission.launchPermissionRequest()
            }) {
                Text("Scan Contact's QR")
            }
            if (scanning) {
                // CameraX-based QR scanner — wire up a PreviewView + ImageAnalysis
                // that calls onScanResult with the decoded JSON string, then stops.
                Text(
                    "Point camera at contact's screen",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}
```

> **Note:** The QR scanning implementation requires CameraX + ZXing ImageAnalysis integration. Add `androidx.camera:camera-camera2` and `androidx.camera:camera-lifecycle` to `libs.versions.toml` and implement a `QRAnalyzer` class that decodes frames and calls `onScanResult`. This is a standard CameraX pattern; see the CameraX documentation for the `ImageAnalysis.Analyzer` interface.

- [ ] **Step 3: Build and confirm**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/securemessenger/ui/qr/
git commit -m "feat: QR screen — show own identity QR, scaffold for scanning contact QR"
```

---

## Task 13: UI — ChatScreen

**Files:**
- Create: `app/src/main/java/com/securemessenger/ui/chat/ChatViewModel.kt`
- Create: `app/src/main/java/com/securemessenger/ui/chat/ChatScreen.kt`

- [ ] **Step 1: Write `ChatViewModel.kt`**

```kotlin
package com.securemessenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessenger.SecureMessengerApp
import com.securemessenger.messaging.InboundMessage
import com.securemessenger.messaging.MessageStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DisplayMessage(
    val text: String,
    val isOwn: Boolean,
    val status: MessageStatus?,
)

class ChatViewModel(app: SecureMessengerApp, private val contactOnion: String) : ViewModel() {
    private val service = app.messagingService
    private val contact get() = service.contactStore.getByOnion(contactOnion)

    val messages: StateFlow<List<DisplayMessage>> = service.received
        .map { all -> all.filter { it.senderOnion == contactOnion }
            .map { DisplayMessage(it.text, isOwn = false, status = null) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun send(text: String) {
        val c = contact ?: return
        viewModelScope.launch { service.send(c, text) }
    }

    fun markRead(msg: InboundMessage) {
        viewModelScope.launch { service.markRead(msg.id, msg.senderOnion) }
    }
}
```

- [ ] **Step 2: Write `ChatScreen.kt`**

```kotlin
package com.securemessenger.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<DisplayMessage>,
    contactOnion: String,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactOnion.take(20) + "…", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }}
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    singleLine = true,
                )
                IconButton(onClick = { if (draft.isNotBlank()) { onSend(draft); draft = "" } }) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            reverseLayout = true,
        ) {
            items(messages.reversed()) { msg ->
                MessageBubble(msg)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: DisplayMessage) {
    val align = if (msg.isOwn) Alignment.End else Alignment.Start
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = align) {
        Surface(
            color = if (msg.isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(msg.text)
                if (msg.isOwn && msg.status != null) {
                    Text(
                        text = when (msg.status) {
                            com.securemessenger.messaging.MessageStatus.PENDING   -> "✓"
                            com.securemessenger.messaging.MessageStatus.DELIVERED -> "✓✓"
                            com.securemessenger.messaging.MessageStatus.READ      -> "✓✓"
                        },
                        fontSize = 10.sp,
                        color = if (msg.status == com.securemessenger.messaging.MessageStatus.READ)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build and confirm**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/securemessenger/ui/chat/
git commit -m "feat: chat screen with message bubbles and delivery/read tick indicators"
```

---

## Task 14: Security Regression Tests

**Files:**
- Create: `app/src/test/java/com/securemessenger/security/SecurityRegressionTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.securemessenger.security

import com.securemessenger.contact.Contact
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.identity.IdentityManager
import com.securemessenger.messaging.*
import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SecurityRegressionTest {

    @Test fun `unknown sender is silently dropped — no crash, no state change`() {
        val ownBundle  = IdentityManager.generateIdentity()
        val queue      = MessageQueue()
        val received   = mutableListOf<InboundMessage>()
        val contacts   = ContactStore()
        val signal     = SignalSessionManager(ownBundle)

        // Simulate inbound message from an onion not in ContactStore
        val unknownOnion = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.onion"
        val fakePacket   = PacketSerializer.serialize(Packet.Msg(UUID.randomUUID(), ByteArray(32)))

        // ContactStore has no entry for unknownOnion — message must be dropped
        val contact = contacts.getByOnion(unknownOnion)
        assertNull("Unknown sender should not be in store", contact)
        // No exception thrown, received list unchanged
        assertEquals(0, received.size)
    }

    @Test fun `MessageQueue clear leaves no messages`() {
        val queue = MessageQueue()
        repeat(5) {
            queue.enqueue(Message(UUID.randomUUID(), "x.onion", ByteArray(10), MessageStatus.PENDING))
        }
        queue.clear()
        assertEquals(0, queue.getAll().size)
    }

    @Test fun `Receipt manager does not process packets for unknown message IDs`() {
        val queue   = MessageQueue()
        val receipt = ReceiptManager(queue = queue, sendFn = { _, _ -> })
        val fakeId  = UUID.randomUUID()
        // Should not throw; status for unknown ID should remain null
        receipt.handleInboundReceipt(Packet.Ack(fakeId))
        assertNull(queue.getStatus(fakeId))
    }

    @Test fun `Decryption failure does not expose plaintext`() {
        val aliceBundle  = IdentityManager.generateIdentity()
        val bobBundle    = IdentityManager.generateIdentity()
        val aliceManager = SignalSessionManager(aliceBundle)
        val bobManager   = SignalSessionManager(bobBundle)
        val bobContact   = Contact.fromKeyBundle(bobBundle)
        aliceManager.initSessionWith(bobContact)
        val aliceContact = Contact.fromKeyBundle(aliceBundle)

        val ct      = aliceManager.encrypt(bobContact, "secret".toByteArray())
        val tampered = ct.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }

        var decryptedText: String? = null
        try {
            decryptedText = String(bobManager.decrypt(aliceContact, tampered))
        } catch (_: Exception) { /* expected */ }
        assertNull("Tampered ciphertext must not yield plaintext", decryptedText)
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail (compilation)**

```bash
./gradlew test --tests "com.securemessenger.security.SecurityRegressionTest"
```

- [ ] **Step 3: Run tests after prior tasks complete — confirm they pass**

```bash
./gradlew test --tests "com.securemessenger.security.SecurityRegressionTest"
```
Expected: 4 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/securemessenger/security/
git commit -m "test: security regression suite — unknown senders, queue clear, decryption failure"
```

---

## Task 15: Full Test Suite + Final Build

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew test
```
Expected: All tests pass. If any fail, fix them before continuing.

- [ ] **Step 2: Build release APK**

```bash
./gradlew assembleRelease
```
Output APK is at `app/build/outputs/apk/release/app-release-unsigned.apk`. Sign it with your release keystore before distribution.

- [ ] **Step 3: Sign the APK**

```bash
# Generate keystore (one-time, keep this file offline and secure)
keytool -genkey -v -keystore securemessenger.jks -alias securemessenger \
  -keyalg EC -keysize 256 -validity 10000

# Sign
apksigner sign --ks securemessenger.jks \
  --out app-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

- [ ] **Step 4: Manual integration test (two physical devices)**

  1. Sideload `app-release-signed.apk` on both devices
  2. Open app on both — wait for Tor to bootstrap (~15–30 seconds)
  3. Device A: tap + → "Show My QR"
  4. Device B: tap + → scan Device A's QR
  5. Repeat in reverse: Device B shows QR, Device A scans
  6. Device A: open chat with Device B, send "hello"
  7. Verify on Device A: single tick → double tick (delivered) → double tick filled (read when Device B opens chat)
  8. Verify on Device B: message appears with no errors
  9. Kill app on both devices — confirm no message files in `/data/data/com.securemessenger/` (requires root or ADB shell)

- [ ] **Step 5: Final commit**

```bash
git add .
git commit -m "chore: full test suite passing, release APK build confirmed"
```
