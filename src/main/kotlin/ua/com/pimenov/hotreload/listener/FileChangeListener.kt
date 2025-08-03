package ua.com.pimenov.hotreload.listener

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import ua.com.pimenov.hotreload.service.HotReloadService
import ua.com.pimenov.hotreload.settings.HotReloadSettings
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FileChangeListener : VirtualFileListener {

    private val logger = thisLogger()
    private val hotReloadService = HotReloadService.getInstance()
    private val settings = HotReloadSettings.getInstance()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun contentsChanged(event: VirtualFileEvent) {
        logger.info("File changed: ${event.file.name}")
        handleFileChange(event.file)
    }

    override fun fileCreated(event: VirtualFileEvent) {
        logger.info("File created: ${event.file.name}")
        handleFileChange(event.file)
    }

    private fun handleFileChange(file: VirtualFile) {
        logger.info("Processing file changes: ${file.name}")
        logger.info("Settings enabled: ${settings.isEnabled}")
        logger.info("Hot Reload running: ${hotReloadService.isRunning()}")

        if (!settings.isEnabled) {
            logger.info("Hot Reload Disabled in settings")
            return
        }

        if (!hotReloadService.isRunning()) {
            logger.info("Hot Reload The server is not started")
            return
        }

        val extension = file.extension?.lowercase() ?: return
        val watchedExtensions = settings.getWatchedExtensionsSet()

        logger.info("File extension: $extension")
        logger.info("Tracking extensions: $watchedExtensions")

        if (extension in watchedExtensions) {
            logger.info("File extension is tracked, send a change message")
            // Додаємо затримку для уникнення занадто частих оновлень
            executor.schedule({
                hotReloadService.notifyFileChanged(file.name)
            }, settings.browserRefreshDelay.toLong(), TimeUnit.MILLISECONDS)
        } else {
            logger.info("File extension is not tracked")
        }
    }
}