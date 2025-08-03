package ua.com.pimenov.hotreload.websocket

import com.intellij.openapi.diagnostic.thisLogger
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer as JavaWebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArraySet

class WebSocketServer(port: Int) : JavaWebSocketServer(InetSocketAddress(port)) {

    private val logger = thisLogger()
    private val connections = CopyOnWriteArraySet<WebSocket>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connections.add(conn)
        logger.info("Hot Reload - The new client is connected: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        connections.remove(conn)
        logger.info("Hot Reload - Client disconnected: ${conn.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        logger.debug("Hot Reload - Received message: $message")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        logger.error("Hot Reload - WebSocket Error", ex)
        conn?.let { connections.remove(it) }
    }

    override fun onStart() {
        logger.info("Hot Reload - WebSocket Server started")
    }

    fun broadcastReload(fileName: String) {
        val message = """{"type": "reload", "file": "$fileName"}"""
        connections.forEach { conn ->
            if (conn.isOpen) {
                conn.send(message)
            }
        }
        logger.debug("Hot Reload - The update signal for file has been sent: $fileName")
    }
}