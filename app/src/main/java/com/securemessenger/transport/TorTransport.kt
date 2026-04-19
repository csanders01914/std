package com.securemessenger.transport

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

sealed class TorStatus {
    object Idle : TorStatus()
    data class Bootstrapping(val progress: Int) : TorStatus()
    data class Publishing(val progress: Int) : TorStatus()
    object Ready : TorStatus()
    data class Error(val message: String) : TorStatus()
}

class TorTransport(
    private val context: Context,
    private val hiddenServicePrivateKeyBytes: ByteArray, // 64-byte Ed25519 key: pub(32) + priv(32)
    private val listenPort: Int = 7979,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _inbound = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 64) // (senderOnion, bytes)
    val inbound: SharedFlow<Pair<String, ByteArray>> = _inbound

    private val _status = MutableStateFlow<TorStatus>(TorStatus.Idle)
    val status: StateFlow<TorStatus> = _status

    private var torProcess: Process? = null

    private val bootstrapRegex = Regex("Bootstrapped (\\d+)%")
    private val introPointRegex = Regex("intro points?|Introduction point.*established", RegexOption.IGNORE_CASE)

    var onionAddress: String = ""
        private set

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            val torDataDir = File(context.filesDir, "tor").also { it.mkdirs() }
        torDataDir.setReadable(true, true)
        torDataDir.setWritable(true, true)
        torDataDir.setExecutable(true, true)

        _status.value = TorStatus.Bootstrapping(0)

        val hsDir = File(torDataDir, "hs").also { it.mkdirs() }
        hsDir.setReadable(true, true)
        hsDir.setWritable(true, true)
        hsDir.setExecutable(true, true)

        // Write Ed25519 keys for the hidden service.
        val pub = hiddenServicePrivateKeyBytes.sliceArray(0 until 32)
        val priv = hiddenServicePrivateKeyBytes.sliceArray(32 until 64)

        // Expanded key = SHA-512(seed).
        val expandedKey = ByteArray(64)
        val digest = org.bouncycastle.crypto.digests.SHA512Digest()
        digest.update(priv, 0, 32)
        digest.doFinal(expandedKey, 0)
        
        // Clamp the scalar part
        expandedKey[0] = (expandedKey[0].toInt() and 248).toByte()
        expandedKey[31] = (expandedKey[31].toInt() and 127).toByte()
        expandedKey[31] = (expandedKey[31].toInt() or 64).toByte()

        val secretKeyHeader = ByteArray(32).apply {
            "== ed25519v1-secret: type0 ==".toByteArray(Charsets.US_ASCII).copyInto(this)
        }
        val secretKeyFile = File(hsDir, "hs_ed25519_secret_key")
        secretKeyFile.writeBytes(secretKeyHeader + expandedKey) // REMOVED + pub
        secretKeyFile.setReadable(true, true)
        secretKeyFile.setWritable(true, true)

        val publicKeyHeader = ByteArray(32).apply {
            "== ed25519v1-public: type0 ==".toByteArray(Charsets.US_ASCII).copyInto(this)
        }
        val publicKeyFile = File(hsDir, "hs_ed25519_public_key")
        publicKeyFile.writeBytes(publicKeyHeader + pub)
        publicKeyFile.setReadable(true, true)
        publicKeyFile.setWritable(true, true)

        val hostnameFile = File(hsDir, "hostname")
        if (hostnameFile.exists()) hostnameFile.delete()

        android.util.Log.d("TorTransport", "HS dir: ${hsDir.absolutePath}")
        android.util.Log.d("TorTransport", "Secret key exists: ${secretKeyFile.exists()}, size: ${secretKeyFile.length()}")

        val torBinary = File(context.applicationInfo.nativeLibraryDir, "libtor.so")
        if (!torBinary.exists()) {
            android.util.Log.e("TorTransport", "Tor binary NOT found at ${torBinary.absolutePath}")
            return@withContext
        }

        val torrcFile = File(torDataDir, "torrc")
        torrcFile.writeText(
            """
            DataDirectory ${torDataDir.absolutePath}
            HiddenServiceDir ${hsDir.absolutePath}
            HiddenServicePort 7979 127.0.0.1:$listenPort
            SocksPort 9050
            Log notice stdout
            Log info stdout
            SafeLogging 0
            RunAsDaemon 0
            AvoidDiskWrites 1
            """.trimIndent()
        )

        torProcess?.destroy() // Kill any previous instance
        val process = ProcessBuilder(
            torBinary.absolutePath,
            "-f", torrcFile.absolutePath
        ).redirectErrorStream(true).start()
        torProcess = process

        // Consume Tor stdout to prevent blocking; log bootstrap progress.
        scope.launch {
            var introPoints = 0
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    android.util.Log.d("TorTransport", "TOR: $line")
                    
                    // Bootstrap progress
                    val bootMatch = bootstrapRegex.find(line)
                    if (bootMatch != null) {
                        val progress = bootMatch.groupValues[1].toInt()
                        if (progress < 100) {
                            _status.value = TorStatus.Bootstrapping(progress)
                        } else if (_status.value !is TorStatus.Publishing) {
                            _status.value = TorStatus.Publishing(5)
                        }
                    }

                    // Hidden Service establishment progress
                    if (introPointRegex.containsMatchIn(line)) {
                        introPoints++
                        val pubProgress = (5 + (introPoints * 30)).coerceAtMost(100)
                        _status.value = TorStatus.Publishing(pubProgress)
                        android.util.Log.d("TorTransport", "HS Publishing Progress: $pubProgress% ($introPoints points)")
                    }
                }
            }
        }

        // Wait for Tor to bootstrap and publish the hidden service.
        val deadline = System.currentTimeMillis() + 180_000L
        var isTrulyReady = false
        while (System.currentTimeMillis() < deadline) {
            val currentStatus = _status.value
            // If the hostname file exists, we have an address.
            // If we've seen intro points, we're becoming reachable.
            if (hostnameFile.exists() && hostnameFile.length() > 0 && currentStatus is TorStatus.Publishing && currentStatus.progress >= 95) {
                isTrulyReady = true
                break
            }
            delay(1000)
            if (System.currentTimeMillis() % 10000 < 1000) {
                android.util.Log.d("TorTransport", "Waiting for Tor readiness... Status: $currentStatus, Hostname: ${hostnameFile.exists()}")
            }
        }
        
        if (!isTrulyReady) {
            val lastStatus = _status.value
            android.util.Log.e("TorTransport", "Tor readiness timeout. Last status: $lastStatus")
        }

        onionAddress = if (hostnameFile.exists()) hostnameFile.readText().trim() else ""
        android.util.Log.i("TorTransport", "Tor HSv3 Onion Address: $onionAddress")
        
        // Final transition to Ready
        _status.value = TorStatus.Ready
        startListener()
    } catch (e: Exception) {
        _status.value = TorStatus.Error(e.message ?: "Unknown error")
        throw e
    }
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
        android.util.Log.d("TorTransport", "Inbound connection from ${socket.remoteSocketAddress}")
        socket.use {
            try {
                val dis     = DataInputStream(it.inputStream)
                val onion   = dis.readUTF()
                val length  = dis.readInt()
                android.util.Log.d("TorTransport", "Receiving packet from $onion, length: $length")
                require(length in 1..1_048_576) { "Inbound packet length out of range: $length" }
                val payload = ByteArray(length).also { b -> dis.readFully(b) }
                
                // Transport-level ACK to ensure sender doesn't close too early
                it.outputStream.write(0x01)
                it.outputStream.flush()
                
                _inbound.emit(Pair(onion, payload))
                android.util.Log.d("TorTransport", "Packet emitted to inbound flow")
            } catch (e: Exception) {
                android.util.Log.e("TorTransport", "Error handling inbound connection: ${e.message}")
            }
        }
    }

    suspend fun send(recipientOnion: String, ownOnion: String, payload: ByteArray): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.d("TorTransport", "Attempting to send packet to $recipientOnion")
        try {
            Socket().use { proxySocket ->
                android.util.Log.d("TorTransport", "Connecting to SOCKS5 proxy at 127.0.0.1:9050")
                try {
                    proxySocket.connect(InetSocketAddress("127.0.0.1", 9050), 10000)
                } catch (e: Exception) {
                    android.util.Log.e("TorTransport", "FATAL: Could not connect to Tor SOCKS proxy! Is Tor running? ${e.message}")
                    return@withContext false
                }

                proxySocket.soTimeout = 60000 // 60 second timeout for reads

                val out  = DataOutputStream(proxySocket.outputStream)
                val inp  = DataInputStream(proxySocket.inputStream)

                // SOCKS5 no-auth greeting
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val greetingResponse = ByteArray(2).also { inp.readFully(it) }
                if (greetingResponse[0] != 0x05.toByte() || greetingResponse[1] != 0x00.toByte()) {
                    throw IOException("SOCKS5 greeting failed (auth rejection)")
                }

                // SOCKS5 CONNECT — send the full .onion address so Tor routes via hidden service
                val onionBytes = recipientOnion.toByteArray(Charsets.US_ASCII)
                check(onionBytes.size <= 255) { "Onion hostname too long" }
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, onionBytes.size.toByte()))
                out.write(onionBytes)
                out.writeShort(7979) // Hidden service port
                out.flush()
                
                val connectResponse = ByteArray(10).also { inp.readFully(it) }
                if (connectResponse[1] != 0x00.toByte()) {
                    val errorMsg = when (connectResponse[1].toInt()) {
                        0x01 -> "General failure"
                        0x02 -> "Connection not allowed"
                        0x03 -> "Network unreachable"
                        0x04 -> "Host unreachable (Onion address may be down)"
                        0x05 -> "Connection refused"
                        0x06 -> "TTL expired"
                        0x08 -> "Address type not supported"
                        else -> "Unknown error code ${connectResponse[1]}"
                    }
                    throw IOException("SOCKS5 connect failed: $errorMsg")
                }
                android.util.Log.d("TorTransport", "SOCKS5 connection established to $recipientOnion")

                // Framed payload: sender onion + length prefix + bytes
                out.writeUTF(ownOnion)
                out.writeInt(payload.size)
                out.write(payload)
                out.flush()
                
                // Wait for transport-level ACK (prevent early closure)
                val transportAck = inp.read()
                if (transportAck == 0x01) {
                    android.util.Log.d("TorTransport", "Payload sent and ACKed by $recipientOnion")
                    return@withContext true
                } else {
                    android.util.Log.w("TorTransport", "No transport ACK from $recipientOnion (got $transportAck)")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TorTransport", "Failed to send packet to $recipientOnion: ${e.message}", e)
            return@withContext false
        }
    }

    fun stop() {
        torProcess?.destroy()
        torProcess = null
        scope.cancel()
    }
}
