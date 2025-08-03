package ua.com.pimenov.hotreload.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.util.messages.MessageBusConnection
import ua.com.pimenov.hotreload.settings.HotReloadSettings
import ua.com.pimenov.hotreload.websocket.WebSocketServer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service
class HotReloadService {

    private val logger = thisLogger()
    private val isRunning = AtomicBoolean(false)
    private var webSocketServer: WebSocketServer? = null
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var messageBusConnection: MessageBusConnection? = null

    fun start() {
        if (isRunning.get()) {
            logger.warn("Hot Reload already started")
            return
        }

        val settings = HotReloadSettings.getInstance()

        try {
            // Запускаємо WebSocket сервер
            webSocketServer = WebSocketServer(settings.webSocketPort)
            webSocketServer?.start()

            // Підключаємося до Message Bus для отримання подій файлової системи
            messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
            messageBusConnection?.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    for (event in events) {
                        when (event) {
                            is VFileContentChangeEvent -> {
                                event.file?.let { virtualFile ->
                                    logger.info("Hot Reload - The file has been changed: ${virtualFile.name}")
                                    handleFileChange(virtualFile)
                                }
                            }
                            is VFileCreateEvent -> {
                                event.file?.let { virtualFile ->
                                    logger.info("Hot Reload - The file is created: ${virtualFile.name}")
                                    handleFileChange(virtualFile)
                                }
                            }
                        }
                    }
                }
            })

            isRunning.set(true)
            logger.info("Hot Reload started on WebSocket Port ${settings.webSocketPort}")
        } catch (e: Exception) {
            logger.error("Hot Reload Server Failed", e)
        }
    }

    fun stop() {
        if (!isRunning.get()) {
            logger.warn("Hot Reload not started")
            return
        }

        try {
            // Відключаємося від Message Bus
            messageBusConnection?.disconnect()
            messageBusConnection = null

            webSocketServer?.stop()
            webSocketServer = null
            isRunning.set(false)
            logger.info("Hot Reload stopped")
        } catch (e: Exception) {
            logger.error("Error Hot Reload stop action", e)
        }
    }

    fun isRunning(): Boolean = isRunning.get()

    fun notifyFileChanged(fileName: String) {
        logger.info("Hot Reload - Sending a file change message: $fileName")
        if (isRunning.get()) {
            webSocketServer?.broadcastReload(fileName)
        } else {
            logger.warn("Hot Reload not started, can't send a message")
        }
    }

    private fun handleFileChange(file: VirtualFile) {
        val settings = HotReloadSettings.getInstance()

        logger.info("Hot Reload - Processing file changes: ${file.name}")
        logger.info("Hot Reload - Settings enabled: ${settings.isEnabled}")
        logger.info("Hot Reload running: ${isRunning.get()}")

        if (!settings.isEnabled) {
            logger.info("Hot Reload Disabled in settings")
            return
        }

        if (!isRunning.get()) {
            logger.info("Hot Reload The server is not started")
            return
        }

        val extension = file.extension?.lowercase() ?: return
        val watchedExtensions = settings.getWatchedExtensionsSet()

        logger.info("Hot Reload - File extension: $extension")
        logger.info("Hot Reload - Tracking extensions: $watchedExtensions")

        if (extension in watchedExtensions) {
            logger.info("Hot Reload - File extension is tracked, send a change message")
            // Додаємо затримку для уникнення занадто частих оновлень
            executor.schedule({
                notifyFileChanged(file.name)
            }, settings.browserRefreshDelay.toLong(), TimeUnit.MILLISECONDS)
        } else {
            logger.info("Hot Reload - File extension is not tracked")
        }
    }

    companion object {
        fun getInstance(): HotReloadService {
            return com.intellij.openapi.components.service<HotReloadService>()
        }
    }
}