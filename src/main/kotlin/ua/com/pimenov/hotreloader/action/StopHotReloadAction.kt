package ua.com.pimenov.hotreloader.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import ua.com.pimenov.hotreloader.service.HotReloadService

class StopHotReloadAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = HotReloadService.getInstance()

        if (!service.isRunning()) {
            showNotification(project, "ℹ️ Hot Reloader", "Hot Reloader not started", NotificationType.INFORMATION)
        } else {
            service.stop()
            showNotification(project, "🛑 Hot Reloader", "Hot Reloader stopped", NotificationType.INFORMATION)
        }
    }

    override fun update(e: AnActionEvent) {
        val service = HotReloadService.getInstance()
        e.presentation.isEnabled = service.isRunning()
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, title: String, content: String, type: NotificationType) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("HotReload")

        val notification = notificationGroup?.createNotification(title, content, type)
        notification?.notify(project)
    }
}