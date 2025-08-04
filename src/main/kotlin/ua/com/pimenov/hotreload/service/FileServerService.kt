package ua.com.pimenov.hotreload.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException
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
            logger.error("File Server Failed", e)
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
            "webp" -> "image/webp"
            "xml" -> "application/xml"
            "txt" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }
    }

    private inner class FileHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            var responseBody: java.io.OutputStream? = null
            try {
                val path = URLDecoder.decode(exchange.requestURI.path, "UTF-8")
                val requestedPath = if (path == "/") "/index.html" else path

                logger.debug("Hot Reload - Serving file: $requestedPath")

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

                    // Встановлюємо заголовки
                    exchange.responseHeaders.apply {
                        set("Content-Type", mimeType)
                        set("Cache-Control", "no-cache, no-store, must-revalidate")
                        set("Pragma", "no-cache")
                        set("Expires", "0")
                        set("Access-Control-Allow-Origin", "*")
                        set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                        set("Access-Control-Allow-Headers", "Content-Type")
                    }

                    // Відправляємо заголовки відповіді
                    exchange.sendResponseHeaders(200, content.size.toLong())
                    responseBody = exchange.responseBody

                    // Записуємо контент частинами для великих файлів
                    writeContentSafely(responseBody, content)

                    logger.debug("Hot Reload - Successfully served: ${file.name} (${content.size} bytes)")
                } else {
                    // Файл не знайдено
                    logger.warn("Hot Reload - File not found: $requestedPath")
                    val notFoundMessage = "File not found: $requestedPath"
                    val notFoundBytes = notFoundMessage.toByteArray(Charsets.UTF_8)

                    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                    exchange.sendResponseHeaders(404, notFoundBytes.size.toLong())
                    responseBody = exchange.responseBody

                    writeContentSafely(responseBody, notFoundBytes)
                }
            } catch (e: IOException) {
                // Ця помилка виникає коли клієнт розірвав з'єднання
                if (e.message?.contains("connection was aborted") == true ||
                    e.message?.contains("connection reset") == true ||
                    e.message?.contains("Broken pipe") == true) {
                    logger.debug("Hot Reload - Client disconnected during file transfer: ${e.message}")
                } else {
                    logger.warn("Hot Reload - IO error serving file: ${e.message}")
                }
            } catch (e: Exception) {
                logger.error("Hot Reload - Unexpected error serving file", e)

                // Спробуємо відправити помилку клієнту, якщо з'єднання ще активне
                try {
                    if (responseBody == null) {
                        val errorMessage = "Server error: ${e.message}"
                        val errorBytes = errorMessage.toByteArray(Charsets.UTF_8)
                        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                        exchange.sendResponseHeaders(500, errorBytes.size.toLong())
                        responseBody = exchange.responseBody
                        writeContentSafely(responseBody, errorBytes)
                    }
                } catch (sendErrorException: Exception) {
                    logger.debug("Hot Reload - Could not send error response: ${sendErrorException.message}")
                }
            } finally {
                // Безпечно закриваємо OutputStream
                try {
                    responseBody?.close()
                } catch (e: Exception) {
                    logger.debug("Hot Reload - Error closing response stream: ${e.message}")
                }
            }
        }

        private fun writeContentSafely(outputStream: java.io.OutputStream, content: ByteArray) {
            try {
                val chunkSize = 8192 // 8KB chunks
                var offset = 0

                while (offset < content.size) {
                    val length = minOf(chunkSize, content.size - offset)
                    outputStream.write(content, offset, length)
                    outputStream.flush()
                    offset += length
                }
            } catch (e: IOException) {
                // Клієнт розірвав з'єднання під час передачі
                throw e
            }
        }

        private fun findFile(path: String): VirtualFile? {
            val projectRoot = this@FileServerService.projectRoot ?: return null

            // Видаляємо початковий слеш та нормалізуємо шлях
            val relativePath = path.removePrefix("/").replace("\\", "/")

            if (relativePath.isEmpty()) {
                // Шукаємо index.html в корені проекту
                return projectRoot.findChild("index.html")
            }

            // Запобігаємо шляхам типу ../../../
            if (relativePath.contains("../") || relativePath.contains("..\\")) {
                logger.warn("Hot Reload - Blocked suspicious path: $path")
                return null
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
        val settings = ua.com.pimenov.hotreload.settings.HotReloadSettings.getInstance()
        val indicatorPosition = ua.com.pimenov.hotreload.settings.HotReloadSettings.IndicatorPosition.fromValue(settings.indicatorPosition)

        // Визначаємо CSS стилі для позиції індикатора
        val positionStyles = when (indicatorPosition) {
            ua.com.pimenov.hotreload.settings.HotReloadSettings.IndicatorPosition.TOP_LEFT ->
                "'top: 10px;' + 'left: 10px;'"
            ua.com.pimenov.hotreload.settings.HotReloadSettings.IndicatorPosition.TOP_RIGHT ->
                "'top: 10px;' + 'right: 10px;'"
            ua.com.pimenov.hotreload.settings.HotReloadSettings.IndicatorPosition.BOTTOM_LEFT ->
                "'bottom: 10px;' + 'left: 10px;'"
            ua.com.pimenov.hotreload.settings.HotReloadSettings.IndicatorPosition.BOTTOM_RIGHT ->
                "'bottom: 10px;' + 'right: 10px;'"
        }

        val hotReloadScript = """
            <script>
            (function() {
                console.log('🔥 Hot Reload activated on port $webSocketPort');
                
                let ws;
                let reconnectAttempts = 0;
                const maxReconnectAttempts = 5;
                let isReloading = false;
                
                function connectWebSocket() {
                    if (isReloading) return;
                    
                    try {
                        ws = new WebSocket('ws://localhost:$webSocketPort');
                        
                        ws.onopen = function(event) {
                            console.log('🔗 Hot Reload: WebSocket Connected');
                            reconnectAttempts = 0;
                            updateIndicator('connected');
                        };
                        
                        ws.onmessage = function(event) {
                            try {
                                const data = JSON.parse(event.data);
                                if (data.type === 'reload') {
                                    isReloading = true;
                                    console.log('🔄 Hot Reload: File changed:', data.file || 'Unknown');
                                    
                                    // Плавний ефект перед оновленням
                                    if (document.body) {
                                        document.body.style.transition = 'opacity 0.2s';
                                        document.body.style.opacity = '0.8';
                                    }
                                    
                                    setTimeout(() => {
                                        location.reload();
                                    }, 200);
                                }
                            } catch (error) {
                                console.error('❌ Hot Reload: Message parse error:', error);
                            }
                        };
                        
                        ws.onclose = function(event) {
                            if (!isReloading) {
                                console.log('🔌 Hot Reload: WebSocket Disconnected, code:', event.code);
                                updateIndicator('disconnected');
                                
                                if (reconnectAttempts < maxReconnectAttempts) {
                                    reconnectAttempts++;
                                    const delay = Math.min(5000, 1000 * reconnectAttempts);
                                    console.log('🔄 Hot Reload: Reconnecting ' + reconnectAttempts + '/' + maxReconnectAttempts + ' in ' + delay + 'ms');
                                    setTimeout(connectWebSocket, delay);
                                } else {
                                    console.error('❌ Hot Reload: Failed to reconnect after', maxReconnectAttempts, 'attempts');
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
                        $positionStyles +
                        'background: #4CAF50;' +
                        'color: white;' +
                        'width: 24px;' +
                        'height: 24px;' +
                        'display: flex;' +
                        'align-items: center;' +
                        'justify-content: center;' +
                        'border-radius: 50%;' +
                        'font-family: monospace;' +
                        'font-size: 12px;' +
                        'z-index: 2147483647;' +
                        'box-shadow: 0 2px 4px rgba(0,0,0,0.2);' +
                        'transition: all 0.3s ease;' +
                        'cursor: pointer;';
                    
                    indicator.addEventListener('click', function() {
                        console.log('🔥 Hot Reload status:', {
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
                            indicator.title = 'Hot Reload connected (click for status)';
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
                    
                    // Приховуємо індикатор через 5 секунд
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
                
                function initialize() {
                    showIndicator();
                    connectWebSocket();
                }
                
                // Запускаємо після завантаження DOM
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initialize);
                } else {
                    initialize();
                }
                
                // Очищуємо ресурси при закритті
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