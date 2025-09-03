package ua.com.pimenov.hotreloader.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import ua.com.pimenov.hotreloader.service.FileServerService
import ua.com.pimenov.hotreloader.service.HotReloadService
import ua.com.pimenov.hotreloader.utils.Notification

class StopHotReloadAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val hotReloadService = HotReloadService.getInstance()
        val fileServerService = FileServerService.getInstance()

        if (!hotReloadService.isRunning()) {
            Notification.warning(project,"Hot Reloader not started")
        } else {
            hotReloadService.stop()
            fileServerService.stop() // Зупиняємо також HTTP сервер
            Notification.info(project, "Hot Reloader stopped")
        }
    }

    override fun update(e: AnActionEvent) {
        val hotReloadService = HotReloadService.getInstance()
        // HTTP сервер зупиняється разом з HotReload сервісом
        e.presentation.isEnabled = hotReloadService.isRunning()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT // ?
    }
}