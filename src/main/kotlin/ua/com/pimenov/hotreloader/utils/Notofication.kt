package ua.com.pimenov.hotreloader.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

class Notification {
    companion object {
        const val GROUP_ID = "HotReload"

        fun notify(project: Project?, title: String = "Hot Reloader", content: String = "", type: NotificationType = NotificationType.INFORMATION) {
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)

            if (notificationGroup != null) {
                val notification = notificationGroup.createNotification(title, content, type)
                notification.notify(project)
            } else {
                val notification = Notification(
                    GROUP_ID,
                    title,
                    content,
                    type
                )
                notification.notify(project)
            }
        }

        fun info(project: Project?, title: String = "Hot Reloader", content: String = "") {
            notify(project, title, content, NotificationType.INFORMATION)
        }

        fun error(project: Project?, title: String = "Hot Reloader", content: String = "") {
            notify(project, title, content, NotificationType.ERROR)
        }

        fun warning(project: Project?, title: String = "Hot Reloader", content: String = "") {
            notify(project, title, content, NotificationType.WARNING)
        }

        fun update(project: Project?, title: String = "Hot Reloader", content: String = "") {
            notify(project, title, content, NotificationType.IDE_UPDATE)
        }
    }
}