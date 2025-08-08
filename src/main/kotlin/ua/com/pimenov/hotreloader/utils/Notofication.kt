package ua.com.pimenov.hotreloader.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.com.pimenov.hotreloader.settings.HotReloadSettings

class Notification {
    companion object {
        const val GROUP_ID = "HotReload"

        // Час у мілісекундах, після якого нотифікація автоматично зникне
        private const val DEFAULT_EXPIRATION_TIME = 3000L

        fun notify(
            project: Project?,
            title: String = "Hot Reloader",
            content: String = "",
            type: NotificationType = NotificationType.INFORMATION,
            expireTime: Long = HotReloadSettings.getInstance().notificationTimeout.toLong()
        ) {
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)

            val notification = notificationGroup?.createNotification(title, content, type)
                ?: Notification(
                    GROUP_ID,
                    title,
                    content,
                    type
                )

            notification.notify(project)

            // Використовуємо корутини для автоматичного зникнення нотифікації
            if (expireTime > 0) {
                CoroutineScope(Dispatchers.Default).launch {
                    delay(expireTime)
                    ApplicationManager.getApplication().invokeLater {
                        notification.expire()
                    }
                }
            }
        }

        fun info(project: Project?, content: String = "", title: String = "Hot Reloader") {
            notify(project, title, content, NotificationType.INFORMATION)
        }

        fun error(project: Project?, content: String = "", title: String = "Hot Reloader") {
            notify(project, title, content, NotificationType.ERROR)
        }

        fun warning(project: Project?, content: String = "", title: String = "Hot Reloader") {
            notify(project, title, content, NotificationType.WARNING)
        }

        fun update(project: Project?, content: String = "", title: String = "Hot Reloader") {
            notify(project, title, content, NotificationType.IDE_UPDATE)
        }
    }
}