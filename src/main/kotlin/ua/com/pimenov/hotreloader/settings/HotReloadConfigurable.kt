package ua.com.pimenov.hotreloader.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
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
                }

                row("HTTP port:") {
                    intTextField(1024..65535)
                        .bindIntText(settings::httpPort)
                }

                row("WebSocket port:") {
                    intTextField(1024..65535)
                        .bindIntText(settings::webSocketPort)
                }

                row {
                    checkBox("Search free port")
                        .bindSelected(settings::searchFreePort)
                }

                row("Update delay (MS):") {
                    intTextField(0..5000)
                        .bindIntText(settings::browserRefreshDelay)
                }
            }

            group("Web Page") {
                row {
                    checkBox("Show Hot Reloader indicator on page")
                        .bindSelected(settings::showHotReloadIndicator)
                }

                row("Indicator position:") {
                    comboBox(HotReloadSettings.IndicatorPosition.values().toList())
                        .bindItem(
                            { HotReloadSettings.IndicatorPosition.fromValue(settings.indicatorPosition) },
                            { position -> settings.indicatorPosition = position?.value ?: "top_right" }
                        )
                        .component.apply {
                            renderer = SimpleListCellRenderer.create { label, value, _ ->
                                label.text = value?.displayName ?: "Top Right"
                            }
                        }
                }
            }

            group("Automatic stop") {
                lateinit var autoStopCheckBox: Cell<JCheckBox>

                row {
                    autoStopCheckBox = checkBox("Stop when no clients connected")
                        .bindSelected(settings::autoStopEnabled)
                }
                row("Delay before stopping (seconds):") {
                    intTextField(range = 10..3600)
                        .bindIntText(settings::autoStopDelaySeconds)
                        .enabledIf(autoStopCheckBox.selected)
                }
            }

            group("File tracking") {
                row("Tracked Extensions:") {
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
                    text("3. The file automatically opens in the browser with active Hot Reloader")
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

    override fun getDisplayName(): String = "Hot Reloader"
}