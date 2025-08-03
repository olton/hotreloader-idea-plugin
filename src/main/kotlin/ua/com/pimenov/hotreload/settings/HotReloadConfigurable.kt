package ua.com.pimenov.hotreload.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class HotReloadConfigurable : Configurable {

    private val settings = HotReloadSettings.getInstance()
    private lateinit var panel: DialogPanel

    override fun createComponent(): JComponent {
        panel = panel {
            group("General settings") {
                row {
                    checkBox("Enable Hot Reload Service")
                        .bindSelected(settings::isEnabled)
                }

                row {
                    checkBox("Automatically run the service with 'Run with Hot Reload'")
                        .bindSelected(settings::autoStartServer)
                }

                row {
                    checkBox("Show Hot Reload indicator on page")
                        .bindSelected(settings::showHotReloadIndicator)
                }
            }

            group("Network settings") {
                row("WebSocket port:") {
                    intTextField(1024..65535)
                        .bindIntText(settings::webSocketPort)
                        .comment("WebSocket port connection with browser")
                }

                row("HTTP port:") {
                    intTextField(1024..65535)
                        .bindIntText(settings::httpPort)
                        .comment("Port for http file server")
                }

                row("Update delay (MS):") {
                    intTextField(0..5000)
                        .bindIntText(settings::browserRefreshDelay)
                        .comment("Delay before the browser update")
                }
            }

            group("File tracking") {
                row("Files Extensions for tracking:") {
                    textField()
                        .bindText(settings::watchedExtensions)
                        .comment("Expanding files through coma (for example: html,css,js)")
                        .columns(COLUMNS_LARGE)
                }
            }

            group("How to use") {
                row {
                    text("1. Right-click on html file")
                }
                row {
                    text("2. Choice 'Run with HotReload'")
                }
                row {
                    text("3. The file automatically opens in the browser with active Hot Reload")
                }
                row {
                    text("4. When you save any track file the page will be updated")
                }
                row {
                    text("Note: Two ports are used - one for WebSocket, the other for HTTP server")
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