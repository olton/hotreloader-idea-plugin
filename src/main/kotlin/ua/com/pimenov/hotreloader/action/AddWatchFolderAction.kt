package ua.com.pimenov.hotreloader.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import ua.com.pimenov.hotreloader.service.HotReloadService
import ua.com.pimenov.hotreloader.utils.Notification
import java.nio.file.Paths

class AddWatchFolderAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.isDirectory) {
            Notification.error(project, "Виберіть папку для відстеження")
            return
        }

        val hotReloadService = HotReloadService.getInstance()

        try {
            val path = Paths.get(virtualFile.path)
            hotReloadService.addWatchPath(path)
            Notification.info(project, "Папку ${virtualFile.name} додано до відстеження Hot Reload")
        } catch (ex: Exception) {
            Notification.error(project, "Помилка додавання папки: ${ex.message}")
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val hotReloadService = HotReloadService.getInstance()

        e.presentation.isEnabledAndVisible =
            virtualFile?.isDirectory == true && hotReloadService.isRunning()
        e.presentation.text = "Add to Hot Reload Watching"
        e.presentation.description = "Додати цю папку до відстеження Hot Reload"
    }
}