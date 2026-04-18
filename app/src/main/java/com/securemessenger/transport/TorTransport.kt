package com.securemessenger.transport

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class TorTransport(
    private val context: Context,
    private val hiddenServicePrivateKeyBytes: ByteArray, // 64-byte Ed25519 key: pub(32) + priv(32)
    private val listenPort: Int = 7979,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _inbound = MutableSharedFlow<Pair<String, ByteArray>>() // (senderOnion, bytes)
    val inbound: SharedFlow<Pair<String, ByteArray>> = _inbound

    var onionAddress: String = ""
        private set

    suspend fun start() = withContext(Dispatchers.IO) {
        val torDataDir = File(context.filesDir, "tor").also { it.mkdirs() }
        val hsDir      = File(torDataDir, "hs").also { it.mkdirs() }

        // Write Ed25519 private key for the hidden service.
        // Tor expects: 32-byte header prefix + 64-byte key blob.
        val keyHeader = "== ed25519v1-secret: type0 ==\n".toByteArray(Charsets.US_ASCII)
        File(hsDir, "hs_ed25519_secret_key").writeBytes(keyHeader + hiddenServicePrivateKeyBytes)

        val torBinary = File(context.applicationInfo.nativeLibraryDir, "libtor.so")
        val torProcess = ProcessBuilder(
            torBinary.absolutePath,
            "--DataDirectory", torDataDir.absolutePath,
            "--HiddenServiceDir", hsDir.absolutePath,
            "--HiddenServicePort", "7979 127.0.0.1:$listenPort",
            "--ControlPort", "9151",
            "--CookieAuthentication", "1",
            "--SocksPort", "9050",
        ).redirectErrorStream(true).start()

        // Consume Tor stdout to prevent blocking; log bootstrap progress.
        scope.launch {
            torProcess.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { /* discard — prevents buffer deadlock */ }
            }
        }

        // Wait for Tor to bootstrap and publish the hidden service hostname.
        val hostnameFile = File(hsDir, "hostname")
        val deadline = System.currentTimeMillis() + 60_000L
        while (!hostnameFile.exists() && System.currentTimeMillis() < deadline) {
            delay(500)
        }
        check(hostnameFile.exists()) { "Tor failed to publish hidden service within 60 s" }

        onionAddress = hostnameFile.readText().trim()
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
            require(length in 1..1_048_576) { "Inbound packet length out of range: $length" }
            val payload = ByteArray(length).also { b -> dis.readFully(b) }
            _inbound.emit(Pair(onion, payload))
        }
    }

    fun send(recipientOnion: String, ownOnion: String, payload: ByteArray) {
        scope.launch {
            val host = recipientOnion.removeSuffix(".onion")
            Socket().use { proxySocket ->
                proxySocket.connect(InetSocketAddress("127.0.0.1", 9050))
                val out  = DataOutputStream(proxySocket.outputStream)
                val inp  = DataInputStream(proxySocket.inputStream)

                // SOCKS5 no-auth greeting
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                inp.readFully(ByteArray(2))

                // SOCKS5 CONNECT to <host>.onion:listenPort
                val onionBytes = host.toByteArray(Charsets.US_ASCII)
                check(onionBytes.size <= 255) { "Onion hostname too long" }
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, onionBytes.size.toByte()))
                out.write(onionBytes)
                out.writeShort(listenPort)
                out.flush()
                inp.readFully(ByteArray(10)) // SOCKS5 response (fixed 10 bytes for IPv4 reply)

                // Framed payload: sender onion + length prefix + bytes
                out.writeUTF(ownOnion)
                out.writeInt(payload.size)
                out.write(payload)
                out.flush()
            }
        }
    }

    fun stop() { scope.cancel() }
}
