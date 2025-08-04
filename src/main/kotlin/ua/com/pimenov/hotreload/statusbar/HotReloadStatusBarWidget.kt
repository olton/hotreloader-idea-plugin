package ua.com.pimenov.hotreload.statusbar

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import ua.com.pimenov.hotreload.icons.HotReloadIcons
import ua.com.pimenov.hotreload.service.HotReloadService
import ua.com.pimenov.hotreload.settings.HotReloadSettings
import java.awt.event.MouseEvent
import javax.swing.Icon

class HotReloadStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {

    companion object {
        const val ID = "HotReloadStatusWidget"
    }

    private val hotReloadService = service<HotReloadService>()
    private val settings = HotReloadSettings.getInstance()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
    private var statusBar: StatusBar? = null

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        schedulePeriodicUpdate()
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }

    override fun getIcon(): Icon {
        return if (hotReloadService.isRunning()) {
            HotReloadIcons.HOTRELOAD_ACTIVE
        } else {
            HotReloadIcons.HOTRELOAD_INACTIVE
        }
    }

    override fun getTooltipText(): String {
        return try {
            when {
                !hotReloadService.isRunning() -> "Hot Reload: Stopped"
                hotReloadService.getActiveConnectionsCount() > 0 -> {
                    "Hot Reload: ${hotReloadService.getActiveConnectionsCount()} connection(s) active"
                }
                else -> "Hot Reload: Running (no connections)"
            }
        } catch (e: Exception) {
            "Hot Reload: Unknown state"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { event ->
            if (event.button == MouseEvent.BUTTON1) {
                showPopup(event)
            }
        }
    }

    private fun showPopup(event: MouseEvent) {
        val popup = createStatusPopup()
        popup.showUnderneathOf(event.component)
    }

    private fun createStatusPopup(): JBPopup {
        val content = buildString {
            try {
                if (hotReloadService.isRunning()) {
                    appendLine("📡 Status: Running")
                    appendLine("🔌 Connections: ${hotReloadService.getActiveConnectionsCount()}")
                    appendLine("🌐 WebSocket Port: ${settings.webSocketPort}")
                    appendLine("📁 HTTP Port: ${settings.httpPort}")

                    val lastDisconnect = hotReloadService.getLastClientDisconnectTime()
                    if (lastDisconnect > 0) {
                        val timeSinceDisconnect = (System.currentTimeMillis() - lastDisconnect) / 1000
                        appendLine("⏰ Last disconnect: ${timeSinceDisconnect}s ago")
                    }
                } else {
                    appendLine("🔥 HotReload Stopped")
                }
            } catch (e: Exception) {
                appendLine("❗ Error retrieving Hot Reload status: ${e.message}")
            }
        }

        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(createPopupContent(content), null)
            .setTitle("Hot Reload Status")
            .setResizable(false)
            .setMovable(false)
            .createPopup()
    }

    private fun createPopupContent(text: String): javax.swing.JComponent {
        val label = javax.swing.JLabel("<html><pre>$text</pre></html>")
        label.border = javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)
        return label
    }

    private fun schedulePeriodicUpdate() {
        alarm.addRequest({
            statusBar?.updateWidget(ID)
            schedulePeriodicUpdate() // Повторне планування
        }, 2000) // Кожні 2 секунди
    }
}