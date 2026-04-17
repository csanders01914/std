# Secure Messenger — Design Spec
**Date:** 2026-04-17
**Status:** Approved

---

## Overview

A private, ephemeral, text-only Android messaging app for a small trusted group. No central server. No accounts. No data on disk. Messages are routed over the Tor network and encrypted end-to-end using the Signal Protocol.

Distributed as a sideloaded APK. Designed to run without Google Play Services; targets GrapheneOS/AOSP but is compatible with stock Android.

---

## Architecture

Four clean layers, each with a single responsibility:

```
┌─────────────────────────────────┐
│   UI Layer (Jetpack Compose)    │
├─────────────────────────────────┤
│   Messaging Layer               │
├─────────────────────────────────┤
│   Crypto Layer (libsignal)      │
├─────────────────────────────────┤
│   Transport Layer (tor-android) │
└─────────────────────────────────┘
```

- **UI** — Jetpack Compose, thin shell with no business logic
- **Messaging** — session management, contact store, message queue, receipt tracking
- **Crypto** — libsignal-android (Signal Foundation); X3DH key agreement + Double Ratchet E2EE
- **Transport** — tor-android (Guardian Project); embedded Tor daemon, v3 hidden service

No layer calls below its neighbour; all communication flows downward through defined interfaces.

---

## Components

### IdentityManager
- Generated once on first app launch; never regenerated
- Generates an Ed25519 key pair + an X3DH pre-key bundle (signed pre-key + one-time pre-keys)
- Derives a Tor v3 `.onion` address from the public key — the `.onion` address is the user's identity
- Holds private key and pre-key material in memory only; never written to disk
- Exports public key + `.onion` address + X3DH pre-key bundle as a QR code for contact exchange
- **Identity is non-recoverable** — if the device is lost or the app is closed, the identity is gone. All contacts must perform a fresh QR exchange with the user's new identity.

### ContactStore
- In-memory only; cleared entirely on process death
- Each contact entry: `{ publicKey: Ed25519PublicKey, onionAddress: String }`
- Contacts added exclusively via QR scan — no remote contact discovery
- No names, no timestamps, no metadata

### SignalSessionManager
- Thin wrapper around libsignal-android
- Runs X3DH key agreement when establishing a session with a new contact
- Maintains Double Ratchet state per contact in memory
- Encrypts outbound messages; decrypts inbound messages
- All ratchet state is memory-only; provides forward secrecy and break-in recovery
- Decryption failure: message dropped silently, session flagged for re-keying

### TorTransport
- Wraps tor-android (Guardian Project)
- Starts an embedded Tor daemon on app launch
- Hosts a v3 hidden service to receive inbound messages
- Routes all outbound connections over a Tor circuit (3 hops)
- Exposes a simple interface: `send(onionAddress, ciphertext)` / `onReceive(callback)`

### MessageQueue
- Ephemeral delivery buffer; cleared entirely on app close
- Buffers outbound messages when recipient is offline
- Retries delivery with exponential backoff until ACK is received or app closes
- Drops all undelivered messages on app close — no server-side store-and-forward
- Tracks per-message delivery state: `pending → delivered → read`

### UI (Jetpack Compose)
- Three screens: contact list, chat view, QR scanner/generator
- Sets `FLAG_SECURE` on all windows to block screenshots
- Displays per-message receipt status (see Receipts below)

---

## Key Exchange

In-person only. No remote key exchange is supported.

1. Both users open the app and tap "Show My QR"
2. Each QR encodes: `Ed25519 public key + .onion address + X3DH pre-key bundle`
3. Each user scans the other's QR code
4. Contact is added to ContactStore (memory)
5. SignalSessionManager generates X3DH pre-keys, ready for first message
6. No network is involved during this step — MITM is physically impossible

---

## Message Flow

1. Alice types a message — plaintext exists only in UI layer memory
2. SignalSessionManager encrypts via Double Ratchet → opaque ciphertext; ratchet advances, that message key is gone
3. TorTransport opens a 3-hop Tor circuit to Bob's `.onion` address
4. Ciphertext is delivered to Bob's hidden service; if Bob is offline, MessageQueue retries
5. Bob's TorTransport receives the blob → SignalSessionManager decrypts → plaintext surfaces in UI
6. **Delivery receipt**: Bob's device sends an ACK immediately on successful decryption
7. **Read receipt**: Bob's device sends a second ping when the message is displayed on screen
8. Alice's MessageQueue updates message state: `pending → delivered → read`
9. Plaintext is never written to disk on either device

**Message status indicators:**
- Single tick — sent, awaiting delivery
- Double tick — delivered to device
- Double tick (filled) — opened and read

If Alice closes the app before receiving the read receipt, message status remains "delivered" — this state is not recoverable due to ephemeral design.

---

## Receipts

Read receipts are always-on. In a small trusted group, certainty of delivery outweighs the minor activity-pattern leak. Receipts are routed back over Tor on the same circuit, encrypted the same way as messages.

---

## Security Model

| Threat | Mitigation |
|---|---|
| Network surveillance | Tor 3-hop routing — no IP linkage between sender and recipient |
| Message interception | Double Ratchet E2EE — ciphertext is meaningless without ratchet state |
| Future key compromise | Forward secrecy — past messages unreadable even if keys are later stolen |
| MITM on contact add | In-person QR only — physically impossible to intercept |
| Seized device forensics | Nothing on disk — RAM cleared on app close or reboot |
| Supply chain (Google) | No Play Store, no Google Play Services; runs on AOSP/GrapheneOS |
| Malicious contact | Keys verified at QR exchange — impersonation requires physical device access |
| Screenshot/screen capture | `FLAG_SECURE` set on all windows |

### Known Limitations

- **Offline delivery** — if Bob never comes online before Alice closes the app, the message is lost permanently. No store-and-forward.
- **Tor traffic analysis** — a nation-state adversary with full network visibility can perform global traffic correlation attacks on Tor. This app cannot defend against that.
- **Device compromise** — if the device is rooted before the app runs, RAM contents can be read. Full-disk encryption (GrapheneOS default) reduces but does not eliminate this risk.
- **Shoulder surfing** — `FLAG_SECURE` blocks digital capture; physical observation is out of scope.

---

## Distribution

- Signed APK, distributed directly to users (sideloaded)
- No Google Play Store
- No automatic update mechanism — updates distributed manually as new signed APKs
- Signing key held by the developer, never uploaded to any service

---

## Testing

- **Unit tests** — SignalSessionManager encrypt/decrypt round-trips, ratchet advancement, key derivation, receipt state machine
- **Integration tests** — two in-process instances exchanging messages end-to-end through the full stack
- **Tor** — not mocked; tests run against a local Tor instance or the real network to avoid false confidence
- **Security regression tests** — verify nothing written to disk, RAM cleared on session end, unknown senders dropped silently, `FLAG_SECURE` set
- **Manual QR exchange test** — two physical devices, in-person scan, confirm session establishes and messages + receipts flow both directions

---

## Dependencies

| Library | Maintainer | Purpose |
|---|---|---|
| libsignal-android | Signal Foundation | Signal Protocol (X3DH + Double Ratchet) |
| tor-android | Guardian Project | Embedded Tor daemon + hidden service |
| Jetpack Compose | Google (AOSP) | UI framework |
| ZXing / ML Kit | Google (AOSP) | QR code scanning |

No dependency on Google Play Services. All dependencies available via Maven without Play Store.
