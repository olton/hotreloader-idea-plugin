package ua.com.pimenov.hotreload.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import ua.com.pimenov.hotreload.service.HotReloadService
import ua.com.pimenov.hotreload.settings.HotReloadSettings

class DiagnoseHotReloadAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val service = HotReloadService.getInstance()
        val settings = HotReloadSettings.getInstance()
        var testStatus = ""

        if (service.isRunning()) {
            testStatus = "The test message has been sent successful!"
        } else {
            testStatus = "HotReload service not started!"
        }

        val diagnostics = """
            === HotReload Діагностика ===
            
            Settings:
            - Enabled: ${if (settings.isEnabled) "Так" else "Ні"}
            - WebSocket Port: ${settings.webSocketPort}
            - HTTP Port: ${settings.httpPort}
            - Tracking extensions: ${settings.watchedExtensions}
            - Update delay: ${settings.browserRefreshDelay}мс
            
            Сервіси:
            - HotReload The service is started: ${if (service.isRunning()) "Yes" else "No"}
            
            Test WebSocket:
            - Attempted to send a test message...${testStatus}
        """.trimIndent()

        Messages.showInfoMessage(diagnostics, "Hot Reload Diagnosis")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = "Diagnosis Hot Reload"
        e.presentation.description = "Show Hot Reload Status and send a text message"
    }
}