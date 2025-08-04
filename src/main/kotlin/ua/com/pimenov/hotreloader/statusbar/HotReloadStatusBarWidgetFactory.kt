package ua.com.pimenov.hotreloader.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import ua.com.pimenov.hotreloader.settings.HotReloadSettings

class HotReloadStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = HotReloadStatusBarWidget.ID

    override fun getDisplayName(): String = "HotReload Status"

    override fun isAvailable(project: Project): Boolean {
        return HotReloadSettings.getInstance().showHotReloadIndicator
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return HotReloadStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        // Очищення ресурсів віджета
    }

    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar): Boolean = true
}