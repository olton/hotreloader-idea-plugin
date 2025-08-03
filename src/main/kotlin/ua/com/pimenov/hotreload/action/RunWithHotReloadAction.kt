package ua.com.pimenov.hotreload.action

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.guessProjectDir
import ua.com.pimenov.hotreload.service.FileServerService
import ua.com.pimenov.hotreload.service.HotReloadService
import ua.com.pimenov.hotreload.settings.HotReloadSettings
import java.net.URI

class RunWithHotReloadAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return

        val fileServerService = FileServerService.getInstance()
        val hotReloadService = HotReloadService.getInstance()
        val settings = HotReloadSettings.getInstance()

        try {
            // Запускаємо WebSocket сервер для HotReload
            if (!hotReloadService.isRunning()) {
                hotReloadService.start()
            }

            // Знаходимо корінь проекту (використовуємо новий API)
            val projectRoot = project.guessProjectDir() ?: virtualFile.parent

            // Запускаємо HTTP сервер
            val baseUrl = fileServerService.start(settings.httpPort, projectRoot, settings.webSocketPort)

            // Обчислюємо відносний шлях від кореня проекту до файлу
            val relativePath = getRelativePath(projectRoot.path, virtualFile.path)
            val fileUrl = "$baseUrl/$relativePath"

            // Відкриваємо в браузері
            BrowserUtil.browse(URI(fileUrl))

            // Показуємо toast notification замість діалогового вікна
            showSuccessNotification(
                project,
                "🔥 Hot Reload is Running",
                "The file is open in browser with an autorenewal\n" +
                        "WebSocket: ws://localhost:${settings.webSocketPort}\n" +
                        "HTTP: $fileUrl"
            )

        } catch (e: Exception) {
            // Показуємо помилку через notification
            showErrorNotification(
                project,
                "❌ Hot Reload Error",
                "Failed to open the file: ${e.message}"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isHtmlFile = virtualFile?.extension?.lowercase() == "html"

        e.presentation.isEnabledAndVisible = isHtmlFile
        e.presentation.text = "Run with Hot Reload"
        e.presentation.description = "Open the HTML file in the browser with active Hot Reload"
    }

    private fun getRelativePath(basePath: String, filePath: String): String {
        val base = basePath.replace('\\', '/').removeSuffix("/")
        val file = filePath.replace('\\', '/')

        return if (file.startsWith(base)) {
            file.substring(base.length).removePrefix("/")
        } else {
            // Якщо файл не в проекті, повертаємо тільки ім'я файла
            file.substringAfterLast('/')
        }
    }

    private fun showSuccessNotification(project: com.intellij.openapi.project.Project, title: String, content: String) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("HotReload.Notifications")

        if (notificationGroup != null) {
            val notification = notificationGroup.createNotification(title, content, NotificationType.INFORMATION)
            notification.notify(project)
        } else {
            // Fallback якщо група не знайдена
            val notification = Notification(
                "HotReload.Notifications",
                title,
                content,
                NotificationType.INFORMATION
            )
            notification.notify(project)
        }
    }

    private fun showErrorNotification(project: com.intellij.openapi.project.Project, title: String, content: String) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("HotReload.Notifications")

        if (notificationGroup != null) {
            val notification = notificationGroup.createNotification(title, content, NotificationType.ERROR)
            notification.notify(project)
        } else {
            // Fallback якщо група не знайдена
            val notification = Notification(
                "HotReload.Notifications",
                title,
                content,
                NotificationType.ERROR
            )
            notification.notify(project)
        }
    }
}