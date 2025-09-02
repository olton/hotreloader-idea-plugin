package ua.com.pimenov.hotreloader.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.messages.MessageBusConnection
import ua.com.pimenov.hotreloader.settings.HotReloadSettings
import ua.com.pimenov.hotreloader.websocket.WebSocketServer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.intellij.openapi.wm.WindowManager
import ua.com.pimenov.hotreloader.statusbar.HotReloadStatusBarWidget
import ua.com.pimenov.hotreloader.utils.Network
import ua.com.pimenov.hotreloader.utils.Notification
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap

@Service
class HotReloadService {

    private val logger = thisLogger()
    private val isRunning = AtomicBoolean(false)
    private var webSocketServer: WebSocketServer? = null
    private var executor: ScheduledExecutorService? = null
    private var messageBusConnection: MessageBusConnection? = null
    private var currentProject: Project? = null

    // Додаємо File Watcher для відстеження змін поза VFS
    private var fileWatcher: WatchService? = null
    private var watcherThread: Thread? = null
    private val watchedPaths = ConcurrentHashMap<Path, WatchKey>()

    // Кеш для відстеження змін файлів
    private val recentChanges = mutableSetOf<String>()

    // Змінні для автоматичного зупинення
    private var autoStopTask: ScheduledFuture<*>? = null
    private val lastClientDisconnectTime = AtomicLong(0)

    fun start() {
        if (isRunning.get()) {
            logger.warn("Hot Reloader - Service already started")
            return
        }

        val settings = HotReloadSettings.getInstance()

        try {
            if (!Network.isPortAvailable(settings.webSocketPort)) {
                if (settings.searchFreePort) {
                    // Спробуємо знайти вільний порт
                    logger.info("Hot Reloader - Finding free port for WebSocket Server...")
                    var foundPort = false
                    for (port in settings.webSocketPort..65535) {
                        if (Network.isPortAvailable(port)) {
                            foundPort = true
                            settings.webSocketPort = port
                            logger.info("Hot Reloader - Found free WebSocket port: ${settings.webSocketPort}")
                            break
                        }
                    }
                    if (!foundPort) {
                        Notification.error(currentProject, "Failed to find free port for WebSocket Server")
                        return
                    }
                } else {
                    Notification.error(currentProject, "Port ${settings.webSocketPort} for WebSocket Server is busy.")
                    return
                }
            }

            // Створюємо executor з налаштованим розміром пула потоків
            executor = Executors.newScheduledThreadPool(settings.corePoolSize)
            logger.info("Hot Reloader - Created thread pool with ${settings.corePoolSize} threads")

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
                        logger.debug("Hot Reloader - Before event: ${event.javaClass.simpleName} for ${event.path}")
                    }
                }

                override fun after(events: MutableList<out VFileEvent>) {
                    logger.info("Hot Reloader - Processing ${events.size} file events")
                    for (event in events) {
                        logger.info("Hot Reloader - Event: ${event.javaClass.simpleName}, path: ${event.path}")
                        handleFileEvent(event)
                    }
                }
            })

            // Додаємо file watcher для файлів поза VFS (тільки якщо увімкнено)
            if (settings.watchExternalChanges) {
                startFileWatcher()
            }

            isRunning.set(true)
            updateStatusBar()
        } catch (e: Exception) {
            logger.error("Hot Reloader Server Failed", e)
        }
    }

    fun startForProject(project: Project) {
        currentProject = project
        start()

        // Додаємо відстеження папок з налаштувань
        val settings = HotReloadSettings.getInstance()
        if (settings.watchExternalChanges && fileWatcher != null) {
            project.guessProjectDir()?.let { projectDir ->
                try {
                    val projectPath = Paths.get(projectDir.path)

                    // Додаємо папки з налаштувань
                    settings.getExternalWatchPathsSet().forEach { watchPath ->
                        val path = projectPath.resolve(watchPath)
                        if (Files.exists(path)) {
                            addWatchPath(path)
                            logger.info("Hot Reloader - Added configured watch path: $path")
                        } else {
                            logger.debug("Hot Reloader - Configured watch path does not exist: $path")
                        }
                    }

                    // Також додаємо корінь проекту для загального відстеження
                    addWatchPath(projectPath)
                } catch (e: Exception) {
                    logger.error("Hot Reloader - Error setting up watch paths for project", e)
                }
            }
        }
    }

    fun stop() {
        if (!isRunning.get()) {
            logger.warn("Hot Reloader not started")
            return
        }

        try {
            // Зупиняємо file watcher
            stopFileWatcher()

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
                    logger.warn("Hot Reloader - Executor did not terminate gracefully, forcing shutdown")
                    executor?.shutdownNow()
                }
            } catch (e: InterruptedException) {
                logger.warn("Hot Reloader - Interrupted while shutting down executor")
                executor?.shutdownNow()
                Thread.currentThread().interrupt()
            }
            executor = null

            isRunning.set(false)
            currentProject = null
            recentChanges.clear()
            logger.info("Hot Reloader stopped")
            updateStatusBar()
        } catch (e: Exception) {
            logger.error("Error Hot Reloader stop action", e)
        }
    }

    fun isRunning(): Boolean = isRunning.get()

    fun notifyFileChanged(fileName: String) {
        logger.info("Hot Reloader - Sending a file change message: $fileName")
        if (isRunning.get()) {
            webSocketServer?.broadcastReload(fileName)
        } else {
            logger.warn("Hot Reloader not started, can't send a message")
        }
    }

    private fun startFileWatcher() {
        try {
            fileWatcher = FileSystems.getDefault().newWatchService()
            watcherThread = Thread({
                watchFiles()
            }, "HotReloadFileWatcher")
            watcherThread?.isDaemon = true
            watcherThread?.start()
            logger.info("Hot Reloader - File watcher started")
        } catch (e: Exception) {
            logger.error("Hot Reloader - Failed to start file watcher", e)
        }
    }

    private fun watchFiles() {
        val watcher = fileWatcher ?: return
        try {
            while (!Thread.currentThread().isInterrupted) {
                val key = watcher.take()

                for (event in key.pollEvents()) {
                    val kind = event.kind()

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue
                    }

                    val filename = event.context() as? Path ?: continue

                    // Безпечне створення повного шляху
                    val watchedPath = key.watchable() as? Path ?: continue
                    val fullPath = try {
                        // Використовуємо toString() для уникнення ProviderMismatchException
                        Paths.get(watchedPath.toString(), filename.toString())
                    } catch (e: Exception) {
                        logger.warn("Hot Reloader - Failed to resolve path: $watchedPath + $filename", e)
                        continue
                    }

                    logger.info("Hot Reloader - External file change detected: $fullPath")

                    // Перевіряємо розширення файла
                    val settings = HotReloadSettings.getInstance()
                    val extension = filename.toString().substringAfterLast('.', "").lowercase()

                    if (extension in settings.getWatchedExtensionsSet()) {
                        logger.info("Hot Reloader - Processing external change for: ${filename}")

                        // Додаємо в кеш для уникнення дублікатів
                        val timestamp = System.currentTimeMillis()
                        val fileKey = "${fullPath}:${timestamp / 100}"

                        if (!recentChanges.contains(fileKey)) {
                            recentChanges.add(fileKey)

                            // Примусово синхронізуємо VFS, якщо увімкнено
                            if (settings.forceVfsSync) {
                                ApplicationManager.getApplication().invokeLater {
                                    try {
                                        val vFile = VfsUtil.findFile(fullPath, true)
                                        if (vFile != null) {
                                            VfsUtil.markDirtyAndRefresh(false, false, false, vFile)
                                        }
                                    } catch (e: Exception) {
                                        logger.debug("Hot Reloader - Failed to sync VFS for: $fullPath", e)
                                    }
                                }
                            }

                            // Відправляємо повідомлення про зміну файла
                            executor?.schedule({
                                try {
                                    notifyFileChanged(filename.toString())
                                    logger.info("Hot Reloader - External change notification sent for ${filename}")
                                } catch (e: Exception) {
                                    logger.error("Hot Reloader - Error sending external change notification", e)
                                }
                                cleanOldCacheEntries()
                            }, settings.browserRefreshDelay.toLong(), TimeUnit.MILLISECONDS)
                        }
                    }
                }

                if (!key.reset()) {
                    watchedPaths.remove(key.watchable())
                    if (watchedPaths.isEmpty()) {
                        break
                    }
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.info("Hot Reloader - File watcher thread interrupted")
        } catch (e: Exception) {
            logger.error("Hot Reloader - File watcher error", e)
        }
    }

    fun addWatchPath(path: Path) {
        val watcher = fileWatcher ?: return
        try {
            if (!watchedPaths.containsKey(path) && Files.exists(path) && Files.isDirectory(path)) {
                val key = path.register(
                    watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
                watchedPaths[path] = key
                logger.info("Hot Reloader - Added watch path: $path")
            }
        } catch (e: Exception) {
            logger.error("Hot Reloader - Failed to add watch path: $path", e)

            // Якщо це критична помилка - вимикаємо зовнішнє відстеження
            if (e is java.nio.file.ProviderMismatchException ||
                e is java.nio.file.FileSystemException) {
                logger.warn("Hot Reloader - Disabling external file watching due to path compatibility issues")
                try {
                    stopFileWatcher()
                } catch (stopException: Exception) {
                    logger.error("Hot Reloader - Error stopping file watcher", stopException)
                }
            }
        }
    }

    private fun stopFileWatcher() {
        try {
            watcherThread?.interrupt()
            fileWatcher?.close()
            watchedPaths.clear()
            watcherThread = null
            fileWatcher = null
            logger.info("Hot Reloader - External file watcher stopped")
        } catch (e: Exception) {
            logger.error("Hot Reloader - Error stopping file watcher", e)
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
            logger.info("Hot Reloader - All clients disconnected. Auto-stop scheduled in ${settings.autoStopDelaySeconds} seconds")
        } else {
            // З'явились нові підключення - скасовуємо автозупинення
            autoStopTask?.cancel(false)
            autoStopTask = null
            lastClientDisconnectTime.set(0)
            logger.info("Hot Reloader - Client connected. Auto-stop cancelled")
        }
    }

    /**
     * Планує автоматичне зупинення сервісу через визначений час
     */
    private fun scheduleAutoStop(delaySeconds: Int) {
        val settings = HotReloadSettings.getInstance()
        // Спочатку скасовуємо попередню задачу, якщо вона є
        autoStopTask?.cancel(false)

        autoStopTask = executor?.schedule({
            try {
                // Перевіряємо чи дійсно немає підключень перед зупиненням
                val currentConnectionCount = webSocketServer?.getActiveConnectionsCount() ?: 0
                if (currentConnectionCount == 0 && isRunning.get()) {
                    logger.info("Hot Reloader - Auto-stopping service: no clients for $delaySeconds seconds")

                    // Зупиняємо сервіс в UI потоці
                    ApplicationManager.getApplication().invokeLater {
                        stop()
                        // Показуємо повідомлення користувачу
                        Notification.info(currentProject, "Hot Reloader stopped due to a lack of connections over ${settings.autoStopDelaySeconds} seconds.")
                    }
                } else {
                    logger.info("Hot Reloader - Auto-stop cancelled: clients reconnected")
                    Notification.info(currentProject, "Hot Reloader - Auto-stop cancelled: clients reconnected")
                }
            } catch (e: Exception) {
                logger.error("Hot Reloader - Error during auto-stop", e)
                Notification.error(currentProject, "${e.message}")
            }
        }, delaySeconds.toLong(), TimeUnit.SECONDS)
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
                    logger.info("Hot Reloader - File content changed: ${virtualFile.name}")
                    handleFileChange(virtualFile)
                }
            }
            is VFileCreateEvent -> {
                event.file?.let { virtualFile ->
                    logger.info("Hot Reloader - File created: ${virtualFile.name}")
                    handleFileChange(virtualFile)
                }
            }
            is VFileMoveEvent -> {
                event.file?.let { virtualFile ->
                    logger.info("Hot Reloader - File moved: ${virtualFile.name}")
                    handleFileChange(virtualFile)
                }
            }
            is VFileDeleteEvent -> {
                logger.info("Hot Reloader - File deleted: ${event.path}")
                // Можна додати логіку для видалення файлів, якщо потрібно
            }
            is VFilePropertyChangeEvent -> {
                if (event.propertyName == VirtualFile.PROP_NAME) {
                    event.file?.let { virtualFile ->
                        logger.info("Hot Reloader - File renamed: ${virtualFile.name}")
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
            logger.debug("Hot Reloader - Duplicate event ignored: ${file.name}")
            return
        }

        recentChanges.add(fileKey)

        logger.info("Hot Reloader - Processing file change: ${file.name} at ${file.path}")

        if (!settings.isEnabled) {
            logger.debug("Hot Reloader disabled in settings")
            return
        }

        if (!isRunning.get()) {
            logger.debug("Hot Reloader server not started")
            return
        }

        // Перевіряємо чи файл належить до проекту
        if (!isCurrentProjectFile(file)) {
            logger.debug("Hot Reloader - File not from current project: ${file.path}")
            return
        }

        val extension = file.extension?.lowercase() ?: return
        val watchedExtensions = settings.getWatchedExtensionsSet()

        logger.info("Hot Reloader - File extension: $extension, watched: $watchedExtensions")

        if (extension in watchedExtensions) {
            // Перевіряємо дублікати
            val delay = maxOf(settings.browserRefreshDelay.toLong(), 50L) // Мінімум 50мс

            logger.info("Hot Reloader - Scheduling reload for ${file.name} in ${delay}ms")

            executor?.schedule({
                try {
                    notifyFileChanged(file.name)
                    logger.info("Hot Reloader - Notification sent for ${file.name}")
                } catch (e: Exception) {
                    logger.error("Hot Reloader - Error sending notification", e)
                }
                
                cleanOldCacheEntries()
            }, delay, TimeUnit.MILLISECONDS)
        } else {
            logger.debug("Hot Reloader - File extension not tracked: $extension")
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

        val settings = HotReloadSettings.getInstance()
        val excludedFolders = settings.getExcludedFoldersSet()
            .map { it.replace('\\', '/').trim('/') }
            .filter { it.isNotEmpty() }
            .toSet()

        // Відносний шлях файлу від кореня проєкту (з прямими слешами)
        val relativePath = VfsUtilCore.getRelativePath(file, projectDir, '/') ?: return false

        // Виключаємо, якщо відносний шлях дорівнює виключеному шляху
        // або починається з "виключенийШлях/"
        val isExcluded = excludedFolders.any { excluded ->
            relativePath == excluded || relativePath.startsWith("$excluded/")
        }

        return !isExcluded
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