package ua.com.pimenov.hotreloader.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import ua.com.pimenov.hotreloader.service.HotReloadService
import ua.com.pimenov.hotreloader.utils.Notification

class StartHotReloadAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = HotReloadService.getInstance()

        if (service.isRunning()) {
            Notification.info(project, "Hot Reloader already started")
        } else {
            service.startForProject(project)
            Notification.info(project, "HotReloader started successfully for project: ${project.name}")
        }
    }

    override fun update(e: AnActionEvent) {
        val service = HotReloadService.getInstance()
        e.presentation.isEnabled = !service.isRunning()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT // ?
    }
}