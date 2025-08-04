package ua.com.pimenov.hotreloader.statusbar

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import ua.com.pimenov.hotreloader.icons.HotReloadIcons
import ua.com.pimenov.hotreloader.service.HotReloadService
import ua.com.pimenov.hotreloader.settings.HotReloadSettings
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

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return this
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        schedulePeriodicUpdate()
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }

    override fun getIcon(): Icon {
        return try {
            if (hotReloadService.isRunning()) {
                HotReloadIcons.HOTRELOAD_ACTIVE
            } else {
                HotReloadIcons.HOTRELOAD_INACTIVE
            }
        } catch (e: Exception) {
            HotReloadIcons.HOTRELOAD_INACTIVE
        }
    }

    override fun getTooltipText(): String {
        return try {
            when {
                !hotReloadService.isRunning() -> "Hot Reloader: Stopped"
                hotReloadService.getActiveConnectionsCount() > 0 -> {
                    "Hot Reloader: ${hotReloadService.getActiveConnectionsCount()} connection(s) active"
                }

                else -> "Hot Reloader: Running (no connections)"
            }
        } catch (e: Exception) {
            "Hot Reloader: Unknown state"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { event ->
            if (event.button == MouseEvent.BUTTON1) {
                try {
                    showPopup(event)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
                appendLine("❗ Error retrieving Hot Reloader status: ${e.message}")
            }
        }

        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(createPopupContent(content), null)
            .setTitle("Hot Reloader Status")
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
        if (!project.isDisposed) {
            alarm.addRequest({
                try {
                    statusBar?.updateWidget(ID)
                    schedulePeriodicUpdate()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 2000)
        }
    }
}