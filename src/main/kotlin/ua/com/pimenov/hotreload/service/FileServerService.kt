package ua.com.pimenov.hotreload.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors

@Service
class FileServerService {

    private val logger = thisLogger()
    private var server: HttpServer? = null
    private var projectRoot: VirtualFile? = null
    private var webSocketPort: Int = 8080

    fun start(port: Int, projectRootFile: VirtualFile, wsPort: Int): String {
        stop() // Зупиняємо попередній сервер якщо він був

        this.projectRoot = projectRootFile
        this.webSocketPort = wsPort

        try {
            server = HttpServer.create(InetSocketAddress("localhost", port), 0)
            server?.createContext("/", FileHandler())
            server?.executor = Executors.newFixedThreadPool(8)
            server?.start()

            val baseUrl = "http://localhost:$port"
            logger.info("The file server is running on $baseUrl")
            return baseUrl
        } catch (e: Exception) {
            logger.error("Fale Server Failed", e)
            throw e
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        projectRoot = null
        logger.info("The file server is stopped")
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "html" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js" -> "application/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "eot" -> "application/vnd.ms-fontobject"
            else -> "application/octet-stream"
        }
    }

    private inner class FileHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val path = URLDecoder.decode(exchange.requestURI.path, "UTF-8")
                val requestedPath = if (path == "/") "/index.html" else path

                val file = findFile(requestedPath)
                if (file != null && file.exists()) {
                    val content = if (file.extension?.lowercase() == "html") {
                        // Вставляємо HotReload скрипт в HTML файли
                        val originalContent = String(file.contentsToByteArray(), Charsets.UTF_8)
                        injectHotReloadScript(originalContent).toByteArray(Charsets.UTF_8)
                    } else {
                        file.contentsToByteArray()
                    }

                    val mimeType = getMimeType(file.name)
                    exchange.responseHeaders.set("Content-Type", mimeType)
                    exchange.responseHeaders.set("Cache-Control", "no-cache, no-store, must-revalidate")
                    exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")

                    exchange.sendResponseHeaders(200, content.size.toLong())
                    exchange.responseBody.write(content)
                } else {
                    val notFoundMessage = "File not found: $requestedPath"
                    val notFoundBytes = notFoundMessage.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "text/plain")
                    exchange.sendResponseHeaders(404, notFoundBytes.size.toLong())
                    exchange.responseBody.write(notFoundBytes)
                }
            } catch (e: Exception) {
                logger.error("File service error", e)
                val errorMessage = "Server error: ${e.message}"
                val errorBytes = errorMessage.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/plain")
                exchange.sendResponseHeaders(500, errorBytes.size.toLong())
                exchange.responseBody.write(errorBytes)
            } finally {
                exchange.responseBody.close()
            }
        }

        private fun findFile(path: String): VirtualFile? {
            val projectRoot = this@FileServerService.projectRoot ?: return null

            // Видаляємо початковий слеш
            val relativePath = path.removePrefix("/")

            if (relativePath.isEmpty()) {
                // Шукаємо index.html в корені проекту
                return projectRoot.findChild("index.html")
            }

            // Розділяємо шлях на частини
            val pathParts = relativePath.split("/").filter { it.isNotEmpty() }

            var current = projectRoot
            for (part in pathParts) {
                current = current.findChild(part) ?: return null
            }

            return current
        }
    }

    private fun injectHotReloadScript(htmlContent: String): String {
        val hotReloadScript = """
            <script>
            (function() {
                console.log('🔥 Hot Reload activated on the port $webSocketPort');
                
                let ws;
                let reconnectAttempts = 0;
                const maxReconnectAttempts = 5;
                let isReloading = false;
                
                function connectWebSocket() {
                    if (isReloading) return;
                    
                    try {
                        ws = new WebSocket('ws://localhost:$webSocketPort');
                        
                        ws.onopen = function(event) {
                            console.log('🔗 HotReload: WebSocket Connected');
                            reconnectAttempts = 0;
                            updateIndicator('connected');
                        };
                        
                        ws.onmessage = function(event) {
                            try {
                                const data = JSON.parse(event.data);
                                if (data.type === 'reload') {
                                    isReloading = true;
                                    console.log('🔄 HotReload: Changes in file have been detected:', data.file || 'Unknown');
                                    
                                    // Додаємо плавний ефект перед оновленням
                                    if (document.body) {
                                        document.body.style.transition = 'opacity 0.2s';
                                        document.body.style.opacity = '0.7';
                                    }
                                    
                                    setTimeout(() => {
                                        location.reload();
                                    }, 200);
                                }
                            } catch (error) {
                                console.error('❌ Hot Reload: Parsing error message:', error);
                            }
                        };
                        
                        ws.onclose = function(event) {
                            if (!isReloading) {
                                console.log('🔌 Hot Reload: WebSocket Disconnected, code:', event.code);
                                updateIndicator('disconnected');
                                
                                // Спробуємо переподключитися
                                if (reconnectAttempts < maxReconnectAttempts) {
                                    reconnectAttempts++;
                                    const delay = Math.min(5000, 1000 * reconnectAttempts);
                                    console.log('🔄 Hot Reload: Attempting to reconnect ' + reconnectAttempts + '/' + maxReconnectAttempts + ' через ' + delay + 'мс');
                                    setTimeout(connectWebSocket, delay);
                                } else {
                                    console.error('❌ Hot Reload: Failed to reconnect after', maxReconnectAttempts, 'спроб');
                                    updateIndicator('failed');
                                }
                            }
                        };
                        
                        ws.onerror = function(error) {
                            console.error('❌ Hot Reload: WebSocket Error:', error);
                            updateIndicator('error');
                        };
                        
                    } catch (error) {
                        console.error('❌ Hot Reload: Failed to create WebSocket:', error);
                        updateIndicator('error');
                        
                        // Спробуємо ще раз
                        if (reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++;
                            setTimeout(connectWebSocket, 2000);
                        }
                    }
                }
                
                let indicator;
                
                function createIndicator() {
                    indicator = document.createElement('div');
                    indicator.innerHTML = '🔥';
                    indicator.style.cssText = 
                        'position: fixed;' +
                        'top: 10px;' +
                        'right: 10px;' +
                        'background: #4CAF50;' +
                        'color: white;' +
                        'padding: 5px 10px;' +
                        'border-radius: 4px;' +
                        'font-family: monospace;' +
                        'font-size: 12px;' +
                        'z-index: 9999;' +
                        'box-shadow: 0 2px 4px rgba(0,0,0,0.2);' +
                        'transition: all 0.3s ease;' +
                        'cursor: pointer;';
                    
                    // Додаємо клік для показу статусу
                    indicator.addEventListener('click', function() {
                        console.log('🔥 Hot Reload staus:', {
                            connected: ws && ws.readyState === WebSocket.OPEN,
                            readyState: ws ? ws.readyState : 'not created',
                            reconnectAttempts: reconnectAttempts,
                            port: $webSocketPort
                        });
                    });
                    
                    return indicator;
                }
                
                function updateIndicator(status) {
                    if (!indicator) return;
                    
                    switch (status) {
                        case 'connected':
                            indicator.style.background = '#4CAF50';
                            indicator.innerHTML = '🔥';
                            indicator.title = 'Hot Reload connected (click for details)';
                            break;
                        case 'disconnected':
                            indicator.style.background = '#FF9800';
                            indicator.innerHTML = '🔄';
                            indicator.title = 'Hot Reload reconnecting...';
                            break;
                        case 'error':
                        case 'failed':
                            indicator.style.background = '#F44336';
                            indicator.innerHTML = '❌';
                            indicator.title = 'Hot Reload connection error';
                            break;
                    }
                }
                
                function showIndicator() {
                    if (!document.body) return;
                    
                    indicator = createIndicator();
                    document.body.appendChild(indicator);
                    
                    // Ховаємо індикатор через 5 секунд, якщо все добре
                    setTimeout(function() {
                        if (indicator && ws && ws.readyState === WebSocket.OPEN) {
                            indicator.style.opacity = '0.7';
                            setTimeout(function() {
                                if (indicator && indicator.parentNode) {
                                    indicator.style.opacity = '0.3';
                                }
                            }, 2000);
                        }
                    }, 5000);
                }
                
                // Ініціалізуємо після завантаження DOM
                function initialize() {
                    showIndicator();
                    connectWebSocket();
                }
                
                // Запускаємо ініціалізацію
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initialize);
                } else {
                    initialize();
                }
                
                // Очищуємо ресурси при закритті сторінки
                window.addEventListener('beforeunload', function() {
                    if (ws) {
                        ws.close();
                    }
                });
            })();
            </script>
        """.trimIndent()

        // Вставляємо скрипт перед закриваючим тегом </head> або </body>
        return when {
            htmlContent.contains("</head>", ignoreCase = true) -> {
                htmlContent.replace("</head>", "$hotReloadScript\n</head>", ignoreCase = true)
            }
            htmlContent.contains("</body>", ignoreCase = true) -> {
                htmlContent.replace("</body>", "$hotReloadScript\n</body>", ignoreCase = true)
            }
            else -> {
                // Якщо немає тегів head або body, додаємо в кінець
                "$htmlContent\n$hotReloadScript"
            }
        }
    }

    companion object {
        fun getInstance(): FileServerService {
            return com.intellij.openapi.components.service<FileServerService>()
        }
    }
}