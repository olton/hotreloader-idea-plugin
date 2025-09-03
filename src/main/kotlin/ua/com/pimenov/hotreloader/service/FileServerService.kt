package ua.com.pimenov.hotreloader.service

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

    fun isRunning(): Boolean = server != null

    fun start(port: Int, projectRootFile: VirtualFile, wsPort: Int): String {
        // Не зупиняємо сервер якщо він вже працює на тому ж порті та проекті
        if (server != null &&
            this.webSocketPort == wsPort &&
            this.projectRoot?.path == projectRootFile.path) {
            val baseUrl = "http://localhost:$port"
            logger.info("Hot Reloader - HTTP server is already running on $baseUrl for the same project")
            return baseUrl
        }

        stop() // Зупиняємо тільки якщо параметри змінились

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

                logger.debug("Hot Reloader - Serving file: $requestedPath")

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

                    logger.debug("Hot Reloader - Successfully served: ${file.name} (${content.size} bytes)")
                } else {
                    // Файл не знайдено - перевіряємо чи це HTML файл
                    val isHtmlRequest = requestedPath.endsWith(".html", ignoreCase = true) || requestedPath == "/"

                    if (isHtmlRequest) {
                        // Для HTML файлів створюємо мінімальну сторінку з HotReload скриптом
                        logger.warn("Hot Reloader - HTML file not found: $requestedPath, creating minimal page with HotReload")
                        val minimalHtml = createMinimalHtmlWithHotReload(requestedPath)
                        val htmlBytes = minimalHtml.toByteArray(Charsets.UTF_8)

                        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                        exchange.sendResponseHeaders(404, htmlBytes.size.toLong())
                        responseBody = exchange.responseBody
                        writeContentSafely(responseBody, htmlBytes)
                    } else {
                        // Для інших типів файлів - стандартна 404 помилка
                        logger.warn("Hot Reloader - File not found: $requestedPath")
                        val notFoundMessage = "File not found: $requestedPath"
                        val notFoundBytes = notFoundMessage.toByteArray(Charsets.UTF_8)

                        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                        exchange.sendResponseHeaders(404, notFoundBytes.size.toLong())
                        responseBody = exchange.responseBody
                        writeContentSafely(responseBody, notFoundBytes)
                    }
                }
            } catch (e: IOException) {
                // Ця помилка виникає коли клієнт розірвав з'єднання
                if (e.message?.contains("connection was aborted") == true ||
                    e.message?.contains("connection reset") == true ||
                    e.message?.contains("Broken pipe") == true) {
                    logger.debug("Hot Reloader - Client disconnected during file transfer: ${e.message}")
                } else {
                    logger.warn("Hot Reloader - IO error serving file: ${e.message}")
                }
            } catch (e: Exception) {
                logger.error("Hot Reloader - Unexpected error serving file", e)

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
                    logger.debug("Hot Reloader - Could not send error response: ${sendErrorException.message}")
                }
            } finally {
                // Безпечно закриваємо OutputStream
                try {
                    responseBody?.close()
                } catch (e: Exception) {
                    logger.debug("Hot Reloader - Error closing response stream: ${e.message}")
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
                logger.warn("Hot Reloader - Blocked suspicious path: $path")
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

    private fun createMinimalHtmlWithHotReload(requestedPath: String): String {
        val minimalHtml = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Hot Reloader - File not found</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
            margin: 0;
            padding: 40px;
            background: #f5f5f5;
            color: #333;
        }
        .container {
            max-width: 600px;
            margin: 0 auto;
            background: white;
            padding: 40px;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #e74c3c;
            margin-top: 0;
        }
        .path {
            background: #f8f9fa;
            padding: 10px;
            border-radius: 4px;
            font-family: monospace;
            word-break: break-all;
            margin: 20px 0;
        }
        .hot-reload-status {
            background: #d4edda;
            border: 1px solid #c3e6cb;
            color: #155724;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔥 Hot Reloader Active</h1>
        <p><strong>File not found:</strong></p>
        <div class="path">$requestedPath</div>
        <div class="hot-reload-status">
            ✅ Hot Reloader is active and monitoring file changes.<br>
            Create the file and it will automatically reload this page.
        </div>
        <p>The Hot Reloader will continue to work and automatically refresh this page when the file becomes available.</p>
    </div>
</body>
</html>
        """.trimIndent()

        return injectHotReloadScript(minimalHtml)
    }

    private fun injectHotReloadScript(htmlContent: String): String {
        val settings = ua.com.pimenov.hotreloader.settings.HotReloadSettings.getInstance()
        val indicatorPosition = ua.com.pimenov.hotreloader.settings.HotReloadSettings.IndicatorPosition.fromValue(settings.indicatorPosition)

        // Генеруємо унікальний ідентифікатор для кожного HTML файлу
        val fileId = "hr_${System.currentTimeMillis()}_${(0..9999).random()}"

        // Визначаємо CSS стилі для позиції індикатора
        val positionStyles = when (indicatorPosition) {
            ua.com.pimenov.hotreloader.settings.HotReloadSettings.IndicatorPosition.TOP_LEFT ->
                "top: 10px;' + 'left: 10px;"
            ua.com.pimenov.hotreloader.settings.HotReloadSettings.IndicatorPosition.TOP_RIGHT ->
                "top: 10px;' + 'right: 10px;"
            ua.com.pimenov.hotreloader.settings.HotReloadSettings.IndicatorPosition.BOTTOM_LEFT ->
                "bottom: 10px;' + 'left: 10px;"
            ua.com.pimenov.hotreloader.settings.HotReloadSettings.IndicatorPosition.BOTTOM_RIGHT ->
                "bottom: 10px;' + 'right: 10px;"
        }

        // Завантажуємо скрипт з ресурсів
        val scriptTemplate = loadHotReloadScript()

        // Замінюємо плейсхолдери на реальні значення
        val hotReloadScript = "<script>\n" +
                scriptTemplate
                    .replace("{{maxReconnectAttempts}}", settings.reconnectAttempts.toString())
                    .replace("{{webSocketPort}}", webSocketPort.toString())
                    .replace("{{positionStyles}}", positionStyles)
                    .replace("{{fileId}}", fileId) +
                "\n</script>"

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

    private fun loadHotReloadScript(): String {
        return try {
            val inputStream = this::class.java.classLoader.getResourceAsStream("javascript/hotreload.js")
                ?: throw IllegalStateException("Hot reload script not found in resources")

            inputStream.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            logger.error("Failed to load hot reload script from resources", e)
            // Fallback до простого скрипта
            """
            console.log('🔥 Hot Reloader fallback script');
            console.error('Failed to load main hot reload script');
            """.trimIndent()
        }
    }

    companion object {
        fun getInstance(): FileServerService {
            return com.intellij.openapi.components.service<FileServerService>()
        }
    }
}