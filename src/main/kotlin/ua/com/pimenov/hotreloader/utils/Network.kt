package ua.com.pimenov.hotreloader.utils

import java.net.ServerSocket
import com.intellij.openapi.diagnostic.thisLogger

class Network {

    companion object {
        val logger = thisLogger()

        fun isPortAvailable(port: Int): Boolean {
            try {
                ServerSocket(port).use { serverSocket ->
                    return serverSocket.localPort == port
                }
            } catch (e: Exception) {
                logger.warn("Hot Reloader - Port $port is already in use")
                return false
            }
        }
    }
}