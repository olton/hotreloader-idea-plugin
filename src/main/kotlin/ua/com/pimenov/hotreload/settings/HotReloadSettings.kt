package ua.com.pimenov.hotreload.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "HotReloadSettings",
    storages = [Storage("HotReloadSettings.xml")]
)
@Service
class HotReloadSettings : PersistentStateComponent<HotReloadSettings> {

    var isEnabled: Boolean = true
    var webSocketPort: Int = 4081  // Порт для WebSocket
    var httpPort: Int = 4080       // Порт для HTTP сервера
    var watchedExtensions: String = "html,css,js,jsx,ts,tsx,json"
    var browserRefreshDelay: Int = 100 // мілісекунди
    var autoStartServer: Boolean = true // Автоматично запускати сервер при Run
    var showHotReloadIndicator: Boolean = true // Показувати індикатор на сторінці

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

    companion object {
        fun getInstance(): HotReloadSettings {
            return service<HotReloadSettings>()
        }
    }
}