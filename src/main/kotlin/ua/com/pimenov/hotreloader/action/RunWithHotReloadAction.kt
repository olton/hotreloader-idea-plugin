package ua.com.pimenov.hotreloader.action

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.guessProjectDir
import ua.com.pimenov.hotreloader.service.FileServerService
import ua.com.pimenov.hotreloader.service.HotReloadService
import ua.com.pimenov.hotreloader.settings.HotReloadSettings
import ua.com.pimenov.hotreloader.utils.Network
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.intellij.openapi.diagnostic.thisLogger
import ua.com.pimenov.hotreloader.utils.Notification

class RunWithHotReloadAction : AnAction() {
    val logger = thisLogger()

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

            if (!Network.isPortAvailable(settings.httpPort)) {
                if (settings.searchFreePort) {
                    // Спробуємо знайти вільний порт
                    var foundPort = false
                    for (port in settings.httpPort..65535) {
                        if (Network.isPortAvailable(port)) {
                            foundPort = true
                            settings.httpPort = port
                            logger.info("Hot Reloader - Found free HTTP port: ${settings.httpPort}")
                            break
                        }
                    }
                    if (!foundPort) {
                        Notification.error(project, "Hot Reloader","Failed to find free port for HTTP Server")
                        return
                    }
                } else {
                    Notification.error(project, "Hot Reloader","Port ${settings.httpPort} for HTTP Server is busy")
                    return
                }
            }

            // Запускаємо HTTP сервер
            val baseUrl = fileServerService.start(settings.httpPort, projectRoot, settings.webSocketPort)

            // Обчислюємо відносний шлях від кореня проекту до файлу
            val relativePath = getRelativePath(projectRoot.path, virtualFile.path)
            val fileUrl = "$baseUrl/$relativePath"

            // Відкриваємо в браузері
            BrowserUtil.browse(URI(fileUrl))

            // Показуємо toast notification замість діалогового вікна
            Notification.info(
                project,
                "🔥 Hot Reloader is Running",
                "The file is open in browser with an autorenewal"
            )

        } catch (e: Exception) {
            // Показуємо помилку через notification
            Notification.error(
                project,
                "❌ Hot Reloader Error",
                "${e.message}"
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
}