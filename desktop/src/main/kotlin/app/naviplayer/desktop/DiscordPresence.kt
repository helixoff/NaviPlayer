package app.naviplayer.desktop

import org.json.JSONObject
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Lightweight Discord Rich Presence client using Discord's local IPC protocol.
 *
 * Keeping the transport here avoids shipping the abandoned native discord-rpc
 * library. Discord remains optional: a missing client or socket never affects
 * playback.
 */
class DiscordPresence(
    private val applicationId: String = System.getenv("NAVIPLAYER_DISCORD_APP_ID").orEmpty(),
) : AutoCloseable {
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "naviplayer-discord-rpc").apply { isDaemon = true }
    }
    private var connection: DiscordIpcConnection? = null
    private var accumulatedListeningSeconds = 0L
    private var listeningSince: Long? = null
    private val updateLock = Any()
    private var pendingUpdate: PresenceUpdate? = null
    private var updateWorkerRunning = false
    @Volatile private var desiredActivity: JSONObject? = null
    @Volatile private var hasDesiredActivity = false

    @Volatile
    private var closed = false

    init {
        worker.scheduleWithFixedDelay(
            {
                if (!closed && hasDesiredActivity) submitLatest(desiredActivity)
            },
            PRESENCE_REFRESH_SECONDS,
            PRESENCE_REFRESH_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    fun update(song: Song, playing: Boolean) {
        if (applicationId.isBlank() || closed) return

        val now = System.currentTimeMillis() / 1_000L
        if (playing && listeningSince == null) {
            listeningSince = now
        } else if (!playing && listeningSince != null) {
            accumulatedListeningSeconds += now - listeningSince!!
            listeningSince = null
        }

        val activity = JSONObject().apply {
            put("name", "NaviPlayer")
            put("type", 2)
            put("details", song.title.take(128))
            put("state", (song.artist.ifBlank { "Unknown artist" }).take(128))
            put("assets", JSONObject().apply {
                put("large_image", APP_ICON_URL)
                put("large_text", "NaviPlayer")
            })
            if (playing) {
                put("timestamps", JSONObject().apply {
                    put("start", now - accumulatedListeningSeconds)
                })
            }
            put("instance", false)
        }
        submitLatest(activity)
    }

    fun clear() {
        if (applicationId.isBlank() || closed) return
        accumulatedListeningSeconds = 0L
        listeningSince = null
        submitLatest(null)
    }

    override fun close() {
        if (closed) return
        closed = true
        synchronized(updateLock) { pendingUpdate = null }
        val cleared = CountDownLatch(1)
        runCatching {
            worker.execute {
                try {
                    runCatching { connection?.setActivity(null) }
                    connection?.close()
                    connection = null
                } finally {
                    cleared.countDown()
                }
            }
        }.onFailure {
            cleared.countDown()
        }
        worker.shutdown()
        runCatching { cleared.await(2, TimeUnit.SECONDS) }
        if (!worker.isTerminated) worker.shutdownNow()
    }

    private fun submitLatest(activity: JSONObject?) {
        desiredActivity = activity
        hasDesiredActivity = true
        var startWorker = false
        synchronized(updateLock) {
            pendingUpdate = PresenceUpdate(activity)
            if (!updateWorkerRunning) {
                updateWorkerRunning = true
                startWorker = true
            }
        }
        if (startWorker) runCatching { worker.execute(::drainUpdates) }
    }

    private fun drainUpdates() {
        while (!closed) {
            val update = synchronized(updateLock) {
                pendingUpdate.also { pendingUpdate = null }.also {
                    if (it == null) updateWorkerRunning = false
                }
            } ?: return
            publish(update.activity)
        }
    }

    private companion object {
        const val APP_ICON_URL =
            "https://raw.githubusercontent.com/helixoff/NaviPlayer/main/discord-assets/naviplayer.png"
        const val PRESENCE_REFRESH_SECONDS = 5L
    }

    private data class PresenceUpdate(val activity: JSONObject?)

    private fun publish(activity: JSONObject?) {
        repeat(2) {
            val active = connection ?: runCatching {
                DiscordIpcConnection.connect(applicationId)
            }.getOrNull() ?: return

            try {
                active.setActivity(activity)
                connection = active
                return
            } catch (_: Exception) {
                active.close()
                connection = null
            }
        }
    }
}

private class DiscordIpcConnection(
    private val input: InputStream,
    private val output: OutputStream,
    private val transport: Closeable,
    applicationId: String,
) : Closeable {
    private val reader = Thread(::readPackets, "naviplayer-discord-ipc-reader").apply {
        isDaemon = true
    }

    init {
        send(
            HANDSHAKE,
            JSONObject()
                .put("v", 1)
                .put("client_id", applicationId)
                .toString(),
        )
        reader.start()
    }

    fun setActivity(activity: JSONObject?) {
        val args = JSONObject()
            .put("pid", ProcessHandle.current().pid())
            .put("activity", activity ?: JSONObject.NULL)
        val command = JSONObject()
            .put("cmd", "SET_ACTIVITY")
            .put("args", args)
            .put("nonce", UUID.randomUUID().toString())
        send(FRAME, command.toString())
    }

    override fun close() {
        runCatching { transport.close() }
    }

    private fun send(opcode: Int, payload: String) {
        send(opcode, payload.toByteArray(Charsets.UTF_8))
    }

    private fun send(opcode: Int, payload: ByteArray) {
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode)
            .putInt(payload.size)
            .array()
        synchronized(output) {
            output.write(header)
            output.write(payload)
            output.flush()
        }
    }

    private fun readPackets() {
        runCatching {
            while (!Thread.currentThread().isInterrupted) {
                val header = input.readExactly(HEADER_SIZE) ?: break
                val decoded = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val opcode = decoded.int
                val length = decoded.int
                if (length !in 0..MAX_PACKET_SIZE) break
                val payload = input.readExactly(length) ?: break
                if (opcode == PING) send(PONG, payload)
                if (opcode == CLOSE) break
            }
        }
        close()
    }

    companion object {
        private const val HANDSHAKE = 0
        private const val FRAME = 1
        private const val CLOSE = 2
        private const val PING = 3
        private const val PONG = 4
        private const val HEADER_SIZE = 8
        private const val MAX_PACKET_SIZE = 1_048_576

        fun connect(applicationId: String): DiscordIpcConnection {
            var lastFailure: Exception? = null
            for (index in 0..9) {
                try {
                    return if (isWindows()) {
                        connectWindows(index, applicationId)
                    } else {
                        connectUnix(index, applicationId)
                    }
                } catch (error: Exception) {
                    lastFailure = error
                }
            }
            throw lastFailure ?: IllegalStateException("Discord IPC socket was not found")
        }

        private fun connectUnix(index: Int, applicationId: String): DiscordIpcConnection {
            val socket = unixSocketCandidates(index).firstOrNull { Files.exists(it) }
                ?: throw IllegalStateException("Discord IPC socket was not found")
            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            try {
                channel.connect(UnixDomainSocketAddress.of(socket))
                return DiscordIpcConnection(
                    input = Channels.newInputStream(channel),
                    output = Channels.newOutputStream(channel),
                    transport = channel,
                    applicationId = applicationId,
                )
            } catch (error: Exception) {
                channel.close()
                throw error
            }
        }

        private fun connectWindows(index: Int, applicationId: String): DiscordIpcConnection {
            var lastFailure: Exception? = null
            val pipes = listOf("\\\\?\\pipe\\discord-ipc-$index", "\\\\.\\pipe\\discord-ipc-$index")
            for (pipe in pipes) {
                try {
                    val file = RandomAccessFile(pipe, "rw")
                    return DiscordIpcConnection(
                        input = FileInputStream(file.fd),
                        output = FileOutputStream(file.fd),
                        transport = file,
                        applicationId = applicationId,
                    )
                } catch (error: Exception) {
                    lastFailure = error
                }
            }
            throw lastFailure ?: IllegalStateException("Discord IPC pipe was not found")
        }

        private fun unixSocketCandidates(index: Int): List<Path> {
            val roots = linkedSetOf<String>()
            System.getenv("XDG_RUNTIME_DIR")?.let(roots::add)
            System.getenv("TMPDIR")?.let(roots::add)
            System.getenv("TMP")?.let(roots::add)
            System.getenv("TEMP")?.let(roots::add)
            roots += "/run/user/${currentUserId()}"
            roots += "/tmp"
            return roots.map { Path.of(it, "discord-ipc-$index") }
        }

        private fun currentUserId(): String =
            System.getenv("UID")
                ?: runCatching {
                    Files.getAttribute(Path.of("/proc/self"), "unix:uid").toString()
                }.getOrDefault("")

        private fun isWindows() =
            System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    }
}

private fun InputStream.readExactly(length: Int): ByteArray? {
    val bytes = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(bytes, offset, length - offset)
        if (read < 0) return null
        offset += read
    }
    return bytes
}
