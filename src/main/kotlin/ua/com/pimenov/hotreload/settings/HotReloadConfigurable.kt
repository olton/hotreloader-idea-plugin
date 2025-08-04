package ua.com.pimenov.hotreload.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JCheckBox
import javax.swing.JComponent

class HotReloadConfigurable : Configurable {

    private val settings = HotReloadSettings.getInstance()
    private lateinit var panel: DialogPanel

    override fun createComponent(): JComponent {
        panel = panel {
            group("General settings") {
                row("Threads:") {
                    intTextField(1..Runtime.getRuntime().availableProcessors())
                        .bindIntText(settings::corePoolSize)
                        .comment("Number of threads for Hot Reload service (default: ${settings.corePoolSize})")
                }

                row("WebSocket port:") {
                    intTextField(1024..65535)
                        .bindIntText(settings::webSocketPort)
                        .comment("WebSocket port connection with browser 1024-65535")
                }

                row("HTTP port:") {
                    intTextField(1024..65535)
                        .bindIntText(settings::httpPort)
                        .comment("Port for http file server connection 1024-65535")
                }

                row("Update delay (MS):") {
                    intTextField(0..5000)
                        .bindIntText(settings::browserRefreshDelay)
                        .comment("Delay before browser update (0-5000 ms)")
                }
            }

            group("Web Page") {
                row {
                    checkBox("Show Hot Reload indicator on page")
                        .bindSelected(settings::showHotReloadIndicator)
                }

                row("Indicator position:") {
                    comboBox(HotReloadSettings.IndicatorPosition.values().toList())
                        .bindItem(
                            { HotReloadSettings.IndicatorPosition.fromValue(settings.indicatorPosition) },
                            { position -> settings.indicatorPosition = position?.value ?: "top_right" }
                        )
                        .comment("Position of the Hot Reload indicator on the page")
                }
            }

            group("Automatic stop") {
                lateinit var autoStopCheckBox: Cell<JCheckBox>

                row {
                    autoStopCheckBox = checkBox("Stop when no clients connected")
                        .bindSelected(settings::autoStopEnabled)
                        .comment("Automatically stops Hot Reload Service when all clients have disconnected")
                }
                row("Delay before stopping (seconds):") {
                    intTextField(range = 10..3600)
                        .bindIntText(settings::autoStopDelaySeconds)
                        .comment("Waiting time before service automatically stopping (10-3600 seconds)")
                        .enabledIf(autoStopCheckBox.selected)
                }
            }

            group("File tracking") {
                row("Files Extensions for tracking:") {
                    textField()
                        .bindText(settings::watchedExtensions)
                        .comment("File extensions separated by comma (example: html,css,js)")
                        .columns(COLUMNS_LARGE)
                }

                row("Excluded folders:") {
                    textField()
                        .bindText(settings::excludedFolders)
                        .comment("Folders to exclude from tracking (example: .idea,.git,node_modules)")
                        .columns(COLUMNS_LARGE)
                }
            }

            group("How to use") {
                row {
                    text("1. Right-click on html file")
                }
                row {
                    text("2. Choose 'Run with HotReload'")
                }
                row {
                    text("3. The file automatically opens in the browser with active Hot Reload")
                }
                row {
                    text("4. When you save any tracked file the page will be updated")
                }
            }
        }

        return panel
    }

    override fun isModified(): Boolean = panel.isModified()

    override fun apply() {
        panel.apply()
    }

    override fun reset() {
        panel.reset()
    }

    override fun getDisplayName(): String = "Hot Reload"
}