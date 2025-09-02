package ua.com.pimenov.hotreloader.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object HotReloadIcons {
    @JvmField
    val HOTRELOAD: Icon = IconLoader.getIcon("/icons/hotreload.svg", HotReloadIcons::class.java)
    @JvmField
    val HOTRELOAD_ACTIVE: Icon = IconLoader.getIcon("/icons/hotreload.svg", HotReloadIcons::class.java)
    @JvmField
    val HOTRELOAD_INACTIVE: Icon = IconLoader.getIcon("/icons/hotreload-white.svg", HotReloadIcons::class.java)
    @JvmField
    val HOTRELOAD_RUN: Icon = IconLoader.getIcon("/icons/hotreload-white.svg", HotReloadIcons::class.java)
}