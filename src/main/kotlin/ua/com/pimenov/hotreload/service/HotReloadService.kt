package ua.com.pimenov.hotreload.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
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
//    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val executor = Executors.newScheduledThreadPool(2)
    private var messageBusConnection: MessageBusConnection? = null
    private var currentProject: Project? = null

    // Кеш для відстеження змін файлів
    private val recentChanges = mutableSetOf<String>()

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
                override fun before(events: MutableList<out VFileEvent>) {
                    // Логування подій перед виконанням
                    for (event in events) {
                        logger.debug("Hot Reload - Before event: ${event.javaClass.simpleName} for ${event.path}")
                    }
                }

                override fun after(events: MutableList<out VFileEvent>) {
                    logger.info("Hot Reload - Processing ${events.size} file events")
                    for (event in events) {
                        logger.info("Hot Reload - Event: ${event.javaClass.simpleName}, path: ${event.path}")
                        handleFileEvent(event)
                    }
                }
            })

            isRunning.set(true)
            logger.info("Hot Reload started on WebSocket Port ${settings.webSocketPort}")
        } catch (e: Exception) {
            logger.error("Hot Reload Server Failed", e)
        }
    }

    fun startForProject(project: Project) {
        currentProject = project
        start()
    }

    fun stop() {
        if (!isRunning.get()) {
            logger.warn("Hot Reload not started")
            return
        }

        try {
            messageBusConnection?.disconnect()
            messageBusConnection = null

            webSocketServer?.stop()
            webSocketServer = null
            isRunning.set(false)
            currentProject = null
            recentChanges.clear()
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

    private fun handleFileEvent(event: VFileEvent) {
        when (event) {
            is VFileContentChangeEvent -> {
                event.file?.let { virtualFile ->
                    logger.info("Hot Reload - File content changed: ${virtualFile.name}")
                    handleFileChange(virtualFile)
                }
            }
            is VFileCreateEvent -> {
                event.file?.let { virtualFile ->
                    logger.info("Hot Reload - File created: ${virtualFile.name}")
                    handleFileChange(virtualFile)
                }
            }
            is VFileMoveEvent -> {
                event.file?.let { virtualFile ->
                    logger.info("Hot Reload - File moved: ${virtualFile.name}")
                    handleFileChange(virtualFile)
                }
            }
            is VFileDeleteEvent -> {
                logger.info("Hot Reload - File deleted: ${event.path}")
                // Можна додати логіку для видалення файлів, якщо потрібно
            }
            is VFilePropertyChangeEvent -> {
                if (event.propertyName == VirtualFile.PROP_NAME) {
                    event.file?.let { virtualFile ->
                        logger.info("Hot Reload - File renamed: ${virtualFile.name}")
                        handleFileChange(virtualFile)
                    }
                }
            }
        }
    }

    private fun handleFileChange(file: VirtualFile) {
        val settings = HotReloadSettings.getInstance()
        val timestamp = System.currentTimeMillis()
        val fileKey = "${file.path}:${timestamp / 100}" // Групуємо по секундах

        if (recentChanges.contains(fileKey)) {
            logger.debug("Hot Reload - Duplicate event ignored: ${file.name}")
            return
        }

        recentChanges.add(fileKey)

        logger.info("Hot Reload - Processing file change: ${file.name} at ${file.path}")

        if (!settings.isEnabled) {
            logger.debug("Hot Reload disabled in settings")
            return
        }

        if (!isRunning.get()) {
            logger.debug("Hot Reload server not started")
            return
        }

        // Перевіряємо чи файл належить до проекту
        if (!isCurrentProjectFile(file)) {
            logger.debug("Hot Reload - File not from current project: ${file.path}")
            return
        }

        val extension = file.extension?.lowercase() ?: return
        val watchedExtensions = settings.getWatchedExtensionsSet()

        logger.info("Hot Reload - File extension: $extension, watched: $watchedExtensions")

        if (extension in watchedExtensions) {
            // Перевіряємо дублікати
            val delay = maxOf(settings.browserRefreshDelay.toLong(), 50L) // Мінімум 50мс

            logger.info("Hot Reload - Scheduling reload for ${file.name} in ${delay}ms")

            executor.schedule({
                try {
                    notifyFileChanged(file.name)
                    logger.info("Hot Reload - Notification sent for ${file.name}")
                } catch (e: Exception) {
                    logger.error("Hot Reload - Error sending notification", e)
                }
                
                cleanOldCacheEntries()
            }, delay, TimeUnit.MILLISECONDS)
        } else {
            logger.debug("Hot Reload - File extension not tracked: $extension")
        }
    }

    private fun cleanOldCacheEntries() {
        val now = System.currentTimeMillis()
        recentChanges.removeIf { key ->
            val keyTime = key.substringAfterLast(":").toLongOrNull() ?: 0
            (now / 100) - keyTime > 20 // Видаляємо записи старші 5 секунд
        }
    }

    private fun isCurrentProjectFile(file: VirtualFile): Boolean {
        val projectsToCheck = if (currentProject != null) {
            listOf(currentProject!!)
        } else {
            ProjectManager.getInstance().openProjects.toList()
        }

        return projectsToCheck.any { project ->
            isFileInProject(file, project)
        }
    }

    private fun isFileInProject(file: VirtualFile, project: Project): Boolean {
        val projectDir = project.guessProjectDir() ?: return false
        val projectPath = projectDir.path
        val filePath = file.path

        if (!filePath.startsWith(projectPath)) {
            return false
        }

        // Отримуємо виключені папки з налаштувань
        val settings = HotReloadSettings.getInstance()
        val excludedFolders = settings.getExcludedFoldersSet()

        // Перевіряємо чи шлях містить будь-яку з виключених папок
        return excludedFolders.none { excludedFolder ->
            containsFolderInPath(filePath, excludedFolder)
        }
    }

    private fun containsFolderInPath(filePath: String, folderName: String): Boolean {
        val pathParts = filePath.replace('\\', '/').split('/')
        return pathParts.any { part -> part == folderName }
    }

    companion object {
        fun getInstance(): HotReloadService {
            return com.intellij.openapi.components.service<HotReloadService>()
        }
    }
}