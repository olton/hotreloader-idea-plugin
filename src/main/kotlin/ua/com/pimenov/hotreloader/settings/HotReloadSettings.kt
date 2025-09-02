package ua.com.pimenov.hotreloader.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.concurrent.CopyOnWriteArrayList
import com.intellij.openapi.diagnostic.thisLogger
import ua.com.pimenov.hotreloader.service.HotReloadService

@State(
    name = "HotReloadSettings",
    storages = [Storage("HotReloadSettings.xml")]
)
@Service
class HotReloadSettings : PersistentStateComponent<HotReloadSettings> {
    private val logger = thisLogger()

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
    var autoStopEnabled: Boolean = false
    var autoStopDelaySeconds: Int = 300
    var corePoolSize: Int = 3
    var searchFreePort: Boolean = true
    var notificationTimeout: Int = 3000
    var reconnectAttempts: Int = 5
    var forceVfsSync: Boolean = true // Нове налаштування
    var watchExternalChanges: Boolean = true // Нове налаштування
    var externalWatchPaths: String = "dist,build,lib" // Нове налаштування

    // Список слухачів змін налаштувань
    private val changeListeners = CopyOnWriteArrayList<SettingsChangeListener>()

    override fun getState(): HotReloadSettings = this

    override fun loadState(state: HotReloadSettings) {
        val oldState = HotReloadSettings().apply {
            XmlSerializerUtil.copyBean(this@HotReloadSettings, this)
        }
        XmlSerializerUtil.copyBean(state, this)

        // Повідомляємо про зміни після завантаження стану
        notifySettingsChanged(oldState, this)
    }

    /**
     * Викликається після застосування змін у конфігураторі
     */
    fun notifySettingsApplied() {
        val currentState = HotReloadSettings().apply {
            XmlSerializerUtil.copyBean(this@HotReloadSettings, this)
        }
        notifySettingsChanged(null, currentState)
    }

    /**
     * Додає слухача змін налаштувань
     */
    fun addChangeListener(listener: SettingsChangeListener) {
        changeListeners.add(listener)
    }

    /**
     * Видаляє слухача змін налаштувань
     */
    fun removeChangeListener(listener: SettingsChangeListener) {
        changeListeners.remove(listener)
    }

    /**
     * Повідомляє всіх слухачів про зміни налаштувань
     */
    private fun notifySettingsChanged(oldState: HotReloadSettings?, newState: HotReloadSettings) {
        changeListeners.forEach { listener ->
            try {
                listener.onSettingsChanged(oldState, newState)
            } catch (e: Exception) {
                // Логуємо помилки, але не зупиняємо обробку інших слухачів
                logger.error("Error in settings change listener", e)
            }
        }
    }

    fun getExternalWatchPathsSet(): Set<String> {
        return externalWatchPaths.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
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

    /**
     * Інтерфейс для слухачів змін налаштувань
     */
    interface SettingsChangeListener {
        fun onSettingsChanged(oldState: HotReloadSettings?, newState: HotReloadSettings)
    }

    companion object {
        fun getInstance(): HotReloadSettings {
            return service<HotReloadSettings>()
        }
    }
}