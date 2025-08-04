package ua.com.pimenov.hotreloader.websocket

import com.intellij.openapi.diagnostic.thisLogger
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer as JavaWebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArraySet

class WebSocketServer(port: Int) : JavaWebSocketServer(InetSocketAddress(port)) {

    private val logger = thisLogger()
    private val connections = CopyOnWriteArraySet<WebSocket>()

    var onConnectionsChanged: ((connectionCount: Int) -> Unit)? = null

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connections.add(conn)
        logger.info("Hot Reloader - The new client is connected: ${conn.remoteSocketAddress}")
        logger.info("Hot Reloader - Active connections: ${connections.size}")
        onConnectionsChanged?.invoke(connections.size)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        connections.remove(conn)
        logger.info("Hot Reloader - Client disconnected: ${conn.remoteSocketAddress}")
        logger.info("Hot Reloader - Active connections: ${connections.size}")
        onConnectionsChanged?.invoke(connections.size)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        logger.debug("Hot Reloader - Received message: $message")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        logger.error("Hot Reloader - WebSocket Error", ex)
        conn?.let { connections.remove(it) }
        onConnectionsChanged?.invoke(connections.size)
    }

    override fun onStart() {
        logger.info("Hot Reloader - WebSocket Server started")
    }

    fun broadcastReload(fileName: String) {
        val message = """{"type": "reload", "file": "$fileName"}"""
        connections.forEach { conn ->
            if (conn.isOpen) {
                conn.send(message)
            }
        }
        logger.debug("Hot Reloader - The update signal for file has been sent: $fileName")
    }

    /**
     * Повертає кількість активних підключень
     */
    fun getActiveConnectionsCount(): Int = connections.size

    /**
     * Перевіряє чи є активні підключення
     */
    fun hasActiveConnections(): Boolean = connections.isNotEmpty()
}