package ua.com.pimenov.hotreloader.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import ua.com.pimenov.hotreloader.service.FileServerService
import ua.com.pimenov.hotreloader.service.HotReloadService
import ua.com.pimenov.hotreloader.settings.HotReloadSettings
import java.awt.Dimension
import java.awt.Font
import javax.swing.Action
import javax.swing.JComponent

class DiagnoseHotReloadAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        // Створюємо діагностичну інформацію
        val diagnosticInfo = buildDiagnosticInfo()

        // Показуємо діалогове вікно
        val dialog = DiagnosticDialog(diagnosticInfo)
        dialog.show()
    }

    private fun buildDiagnosticInfo(): String {
        val settings = HotReloadSettings.getInstance()
        val hotReloadService = HotReloadService.getInstance()
        val fileServerService = FileServerService.getInstance()

        val sb = StringBuilder()

        // Заголовок
        sb.appendLine("🔥 Diagnostic Information")
        sb.appendLine("=" * 50)
        sb.appendLine()

        // Статус сервісів
        sb.appendLine("📊 SERVICE STATUS:")
        sb.appendLine("  • Hot Reloader Service: ${if (hotReloadService.isRunning()) "✅ Running" else "❌ Stopped"}")
        sb.appendLine("  • File Server Service: ${getFileServerStatus()}")
        sb.appendLine()

        // Налаштування мережі
        sb.appendLine("🌐 NETWORK SETTINGS:")
        sb.appendLine("  • WebSocket Port: ${settings.webSocketPort}")
        sb.appendLine("  • HTTP Server Port: ${settings.httpPort}")
        sb.appendLine("  • WebSocket URL: ws://localhost:${settings.webSocketPort}")
        sb.appendLine("  • HTTP Server URL: http://localhost:${settings.httpPort}")
        sb.appendLine()

        // Загальні налаштування
        sb.appendLine("⚙️ GENERAL SETTINGS:")
        sb.appendLine("  • Service Enabled: ${if (settings.isEnabled) "✅ Yes" else "❌ No"}")
        sb.appendLine("  • Auto Start Server: ${if (settings.autoStartServer) "✅ Yes" else "❌ No"}")
        sb.appendLine("  • Show Indicator: ${if (settings.showHotReloadIndicator) "✅ Yes" else "❌ No"}")
        sb.appendLine("  • Browser Refresh Delay: ${settings.browserRefreshDelay} ms")
        sb.appendLine()

        // Відстеження файлів
        sb.appendLine("📁 FILE TRACKING:")
        sb.appendLine("  • Watched Extensions: ${settings.watchedExtensions}")
        sb.appendLine("  • Excluded Folders: ${settings.excludedFolders}")
        sb.appendLine()

        // Інформація про проекти
        sb.appendLine("📂 PROJECT INFORMATION:")
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isNotEmpty()) {
            sb.appendLine("  • Open Projects: ${openProjects.size}")
            openProjects.forEachIndexed { index, project ->
                val projectDir = project.guessProjectDir()
                sb.appendLine("    ${index + 1}. ${project.name}")
                sb.appendLine("       Path: ${projectDir?.path ?: "Unknown"}")
            }
        } else {
            sb.appendLine("  • No open projects")
        }
        sb.appendLine()

        // Системна інформація
        sb.appendLine("💻 SYSTEM INFORMATION:")
        sb.appendLine("  • Java Version: ${System.getProperty("java.version")}")
        sb.appendLine("  • OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        sb.appendLine("  • Available Processors: ${Runtime.getRuntime().availableProcessors()}")
        sb.appendLine("  • Total Memory: ${Runtime.getRuntime().totalMemory() / 1024 / 1024} MB")
        sb.appendLine("  • Free Memory: ${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB")
        sb.appendLine()

        // Поради з усунення неполадок
        sb.appendLine("🔧 TROUBLESHOOTING TIPS:")

        if (!hotReloadService.isRunning()) {
            sb.appendLine("  ⚠️  Hot Reloader service is not running!")
            sb.appendLine("     → Use 'Tools > Hot Reloader > Start Hot Reload' or")
            sb.appendLine("     → Right-click on HTML file and select 'Run with Hot Reloader'")
        }

        if (settings.getWatchedExtensionsSet().isEmpty()) {
            sb.appendLine("  ⚠️  No file extensions are being watched!")
            sb.appendLine("     → Add extensions in Settings > Tools > Hot Reloader")
        }

        if (openProjects.isEmpty()) {
            sb.appendLine("  ⚠️  No projects are open!")
            sb.appendLine("     → Open a project to use Hot Reloader")
        }

        sb.appendLine()
        sb.appendLine("📝 HOW TO USE:")
        sb.appendLine("  1. Right-click on an HTML file")
        sb.appendLine("  2. Select 'Run with Hot Reloader'")
        sb.appendLine("  3. The file will open in browser with auto-refresh")
        sb.appendLine("  4. Edit and save tracked files to see changes")

        return sb.toString()
    }

    private fun getFileServerStatus(): String {
        return try {
            // Тут можна додати логіку перевірки статусу файлового сервера
            // Наразі просто показуємо загальний статус
            "📡 Available"
        } catch (e: Exception) {
            "❌ Error: ${e.message}"
        }
    }

    private operator fun String.times(count: Int): String {
        return this.repeat(count)
    }
}

// Окремий клас для діалогового вікна
class DiagnosticDialog(private val diagnosticInfo: String) : DialogWrapper(true) {

    init {
        title = "Hot Reloader Diagnostic"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val textArea = JBTextArea(diagnosticInfo).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            rows = 30
            columns = 80
            lineWrap = false
        }

        val scrollPane = JBScrollPane(textArea).apply {
            preferredSize = Dimension(800, 600)
        }

        return scrollPane
    }

    override fun createActions() = arrayOf(okAction)

    override fun getOKAction() = super.getOKAction().apply {
        putValue(Action.NAME, "Close")
    }
}