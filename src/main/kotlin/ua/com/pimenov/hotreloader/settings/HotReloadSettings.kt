package ua.com.pimenov.hotreloader.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "HotReloadSettings",
    storages = [Storage("HotReloadSettings.xml")]
)
@Service
class HotReloadSettings : PersistentStateComponent<HotReloadSettings> {

    var isEnabled: Boolean = true
    var webSocketPort: Int = 3000
    var httpPort: Int = 4080
    var watchedExtensions: String = "html,css,js,less"
    var browserRefreshDelay: Int = 100
    var autoStartServer: Boolean = true
    var autoStartService: Boolean = false
    var showHotReloadIndicator: Boolean = true
    var indicatorPosition: String = "top_right"
    var excludedFolders: String = ".idea,.git,node_modules"
    var autoStopEnabled: Boolean = false // Нова опція
    var autoStopDelaySeconds: Int = 300
    var corePoolSize: Int = 3
    var searchFreePort: Boolean = true
    var notificationTimeout: Int = 3000

    override fun getState(): HotReloadSettings = this

    override fun loadState(state: HotReloadSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getWatchedExtensionsSet(): Set<String> {
        return watchedExtensions.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun getExcludedFoldersSet(): Set<String> {
        return excludedFolders.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    enum class IndicatorPosition(val displayName: String, val value: String) {
        TOP_LEFT("Top Left", "top_left"),
        TOP_RIGHT("Top Right", "top_right"),
        BOTTOM_LEFT("Bottom Left", "bottom_left"),
        BOTTOM_RIGHT("Bottom Right", "bottom_right");

        companion object {
            fun fromValue(value: String): IndicatorPosition {
                return values().find { it.value == value } ?: TOP_RIGHT
            }
        }
    }

    companion object {
        fun getInstance(): HotReloadSettings {
            return service<HotReloadSettings>()
        }
    }
}