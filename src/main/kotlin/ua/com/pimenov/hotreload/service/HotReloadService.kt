package ua.com.pimenov.hotreload.service

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.intellij.openapi.wm.WindowManager
import ua.com.pimenov.hotreload.statusbar.HotReloadStatusBarWidget

@Service
class HotReloadService {

    private val logger = thisLogger()
    private val isRunning = AtomicBoolean(false)
    private var webSocketServer: WebSocketServer? = null
    private var executor: ScheduledExecutorService? = null
    private var messageBusConnection: MessageBusConnection? = null
    private var currentProject: Project? = null

    // Кеш для відстеження змін файлів
    private val recentChanges = mutableSetOf<String>()

    // Змінні для автоматичного зупинення
    private var autoStopTask: ScheduledFuture<*>? = null
    private val lastClientDisconnectTime = AtomicLong(0)

    fun start() {
        if (isRunning.get()) {
            logger.warn("Hot Reload already started")
            return
        }

        val settings = HotReloadSettings.getInstance()

        try {
            // Створюємо executor з налаштованим розміром пула потоків
            executor = Executors.newScheduledThreadPool(settings.corePoolSize)
            logger.info("Hot Reload - Created thread pool with ${settings.corePoolSize} threads")

            // Запускаємо WebSocket сервер
            webSocketServer = WebSocketServer(settings.webSocketPort)
            // Налаштовуємо callback для відстеження підключень
            webSocketServer?.onConnectionsChanged = { connectionCount ->
                handleConnectionsChanged(connectionCount)
            }
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
            updateStatusBar()
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
            // Скасовуємо задачу автозупинення
            autoStopTask?.cancel(false)
            autoStopTask = null

            messageBusConnection?.disconnect()
            messageBusConnection = null

            webSocketServer?.stop()
            webSocketServer = null

            // Зупиняємо executor
            executor?.shutdown()
            try {
                if (executor?.awaitTermination(5, TimeUnit.SECONDS) == false) {
                    logger.warn("Hot Reload - Executor did not terminate gracefully, forcing shutdown")
                    executor?.shutdownNow()
                }
            } catch (e: InterruptedException) {
                logger.warn("Hot Reload - Interrupted while shutting down executor")
                executor?.shutdownNow()
                Thread.currentThread().interrupt()
            }
            executor = null

            isRunning.set(false)
            currentProject = null
            recentChanges.clear()
            logger.info("Hot Reload stopped")
            updateStatusBar()
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

    /**
     * Обробляє зміни в кількості підключень
     */
    private fun handleConnectionsChanged(connectionCount: Int) {
        val settings = HotReloadSettings.getInstance()

        if (!settings.autoStopEnabled) {
            return // Автозупинення вимкнено
        }

        if (connectionCount == 0) {
            // Всі клієнти відключились - запускаємо таймер
            lastClientDisconnectTime.set(System.currentTimeMillis())
            scheduleAutoStop(settings.autoStopDelaySeconds)
            logger.info("Hot Reload - All clients disconnected. Auto-stop scheduled in ${settings.autoStopDelaySeconds} seconds")
        } else {
            // З'явились нові підключення - скасовуємо автозупинення
            autoStopTask?.cancel(false)
            autoStopTask = null
            lastClientDisconnectTime.set(0)
            logger.info("Hot Reload - Client connected. Auto-stop cancelled")
        }
    }

    /**
     * Планує автоматичне зупинення сервісу через визначений час
     */
    private fun scheduleAutoStop(delaySeconds: Int) {
        // Спочатку скасовуємо попередню задачу, якщо вона є
        autoStopTask?.cancel(false)

        autoStopTask = executor?.schedule({
            try {
                // Перевіряємо чи дійсно немає підключень перед зупиненням
                val currentConnectionCount = webSocketServer?.getActiveConnectionsCount() ?: 0
                if (currentConnectionCount == 0 && isRunning.get()) {
                    logger.info("Hot Reload - Auto-stopping service: no clients for $delaySeconds seconds")

                    // Зупиняємо сервіс в UI потоці
                    ApplicationManager.getApplication().invokeLater {
                        stop()

                        // Показуємо повідомлення користувачу
                        showAutoStopNotification()
                    }
                } else {
                    logger.info("Hot Reload - Auto-stop cancelled: clients reconnected")
                }
            } catch (e: Exception) {
                logger.error("Hot Reload - Error during auto-stop", e)
            }
        }, delaySeconds.toLong(), TimeUnit.SECONDS)
    }

    /**
     * Показує повідомлення про автоматичне зупинення
     */
    private fun showAutoStopNotification() {
        val settings = HotReloadSettings.getInstance()
        val content = "Hot Reload stopped due to a lack of connections over ${settings.autoStopDelaySeconds} seconds."

        NotificationGroupManager.getInstance()
            .getNotificationGroup("HotReload")
            .createNotification(content, NotificationType.INFORMATION)
            .notify(currentProject)
    }

    /**
     * Повертає кількість активних підключень
     */
    fun getActiveConnectionsCount(): Int {
        return webSocketServer?.getActiveConnectionsCount() ?: 0
    }

    /**
     * Повертає час останнього відключення клієнта (в мілісекундах)
     */
    fun getLastClientDisconnectTime(): Long {
        return lastClientDisconnectTime.get()
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

            executor?.schedule({
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

    private fun updateStatusBar() {
        // Оновлюємо для всіх відкритих проектів
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                val statusBar = WindowManager.getInstance().getStatusBar(project)
                statusBar?.updateWidget(HotReloadStatusBarWidget.ID)
            }
        }
    }

    companion object {
        fun getInstance(): HotReloadService {
            return com.intellij.openapi.components.service<HotReloadService>()
        }
    }
}