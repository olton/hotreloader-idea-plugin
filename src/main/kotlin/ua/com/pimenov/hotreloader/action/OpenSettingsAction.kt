package ua.com.pimenov.hotreloader.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class OpenSettingsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Відкриваємо налаштування HotReload
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "Hot Reloader"  // displayName з applicationConfigurable
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }
}