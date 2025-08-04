package ua.com.pimenov.hotreloader.action

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.guessProjectDir
import ua.com.pimenov.hotreloader.service.FileServerService
import ua.com.pimenov.hotreloader.service.HotReloadService
import ua.com.pimenov.hotreloader.settings.HotReloadSettings
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
            // Запускаємо WebSocket сервер для HotReload з прив'язкою до проекту
            if (!hotReloadService.isRunning()) {
                hotReloadService.startForProject(project)
            }

            // Знаходимо корінь проекту
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
                "🔥 Hot Reloader is Running",
                "The file is open in browser with an autorenewal"
            )

        } catch (e: Exception) {
            // Показуємо помилку через notification
            showErrorNotification(
                project,
                "❌ Hot Reloader Error",
                "Failed to open the file: ${e.message}"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isHtmlFile = virtualFile?.extension?.lowercase() == "html"

        e.presentation.isEnabledAndVisible = isHtmlFile
        e.presentation.text = "Run with Hot Reloader"
        e.presentation.description = "Open the HTML file in the browser with active Hot Reloader"
    }

    private fun getRelativePath(basePath: String, filePath: String): String {
        val base = basePath.replace('\\', '/').removeSuffix("/")
        val file = filePath.replace('\\', '/')

        val relativePath = if (file.startsWith(base)) {
            file.substring(base.length).removePrefix("/")
        } else {
            // Якщо файл не в проекті, повертаємо тільки ім'я файла
            file.substringAfterLast('/')
        }

        // Кодуємо шлях для використання в URL
        return relativePath.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, StandardCharsets.UTF_8.toString())
        }
    }

    private fun showSuccessNotification(project: com.intellij.openapi.project.Project, title: String, content: String) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("HotReload")

        if (notificationGroup != null) {
            val notification = notificationGroup.createNotification(title, content, NotificationType.INFORMATION)
            notification.notify(project)
        } else {
            // Fallback якщо група не знайдена
            val notification = Notification(
                "HotReload",
                title,
                content,
                NotificationType.INFORMATION
            )
            notification.notify(project)
        }
    }

    private fun showErrorNotification(project: com.intellij.openapi.project.Project, title: String, content: String) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("HotReload")

        if (notificationGroup != null) {
            val notification = notificationGroup.createNotification(title, content, NotificationType.ERROR)
            notification.notify(project)
        } else {
            // Fallback якщо група не знайдена
            val notification = Notification(
                "HotReload",
                title,
                content,
                NotificationType.ERROR
            )
            notification.notify(project)
        }
    }
}