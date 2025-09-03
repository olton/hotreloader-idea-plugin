(function() {
    const fileId = '{{fileId}}';
    const currentFileName = window.location.pathname.split('/').pop() || 'index.html';
    console.log('🔥 Hot Reloader activated for file:', fileId, 'fileName:', currentFileName, 'on port {{webSocketPort}}');

    let ws;
    let reconnectAttempts = 0;
    const maxReconnectAttempts = {{maxReconnectAttempts}};
    let isReloading = false;

    // Зберігаємо інформацію про WebSocket в глобальній області для кожного файлу окремо
    if (!window.hotReloaderInstances) {
        window.hotReloaderInstances = {};
    }

    // Перевіряємо чи вже існує активне з'єднання для цього файлу
    if (window.hotReloaderInstances[fileId]) {
        console.log('🔄 Hot Reloader: Closing previous connection for file:', fileId);
        try {
            window.hotReloaderInstances[fileId].close();
        } catch (e) {
            console.debug('Hot Reloader: Error closing previous connection:', e);
        }
    }

    // Функція для hot reload CSS файлів
    function reloadCSS(cssFileName) {
        console.log('🎨 Hot Reloader: Reloading CSS:', cssFileName || 'all stylesheets');

        const links = document.querySelectorAll('link[rel="stylesheet"]');
        let reloadedCount = 0;

        links.forEach(link => {
            const href = link.getAttribute('href');
            if (!href) return;

            // Якщо вказано конкретний файл, перевіряємо відповідність
            if (cssFileName && !href.includes(cssFileName)) {
                return;
            }

            // Створюємо новий link елемент
            const newLink = document.createElement('link');
            newLink.rel = 'stylesheet';
            newLink.type = 'text/css';

            // Додаємо timestamp для обходу кешу
            const separator = href.includes('?') ? '&' : '?';
            newLink.href = href + separator + '_hotreload=' + Date.now();

            // Вставляємо новий link після старого
            link.parentNode.insertBefore(newLink, link.nextSibling);

            // Видаляємо старий link після завантаження нового
            newLink.onload = function() {
                if (link.parentNode) {
                    link.parentNode.removeChild(link);
                }
            };

            // На всякий випадок видаляємо через таймаут
            setTimeout(() => {
                if (link.parentNode) {
                    link.parentNode.removeChild(link);
                }
            }, 200);

            reloadedCount++;
        });

        // Якщо не знайшли CSS файлів, можливо це inline стилі або @import
        if (reloadedCount === 0 && !cssFileName) {
            // Пробуємо оновити всю сторінку як fallback
            console.log('🔄 Hot Reloader: No CSS links found, falling back to full reload');
            location.reload();
        } else {
            console.log(`✅ Hot Reloader: Reloaded ${reloadedCount} CSS file(s)`);
            showReloadIndicator('css');
        }
    }

    // Функція для hot reload JS файлів (складніше, тому поки fallback до reload)
    function reloadJS(jsFileName) {
        console.log('📜 Hot Reloader: JS file changed:', jsFileName);
        console.log('🔄 Hot Reloader: JS hot reload not implemented, doing full reload');
        showReloadIndicator('js');

        // Плавний ефект перед оновленням
        if (document.body) {
            document.body.style.transition = 'opacity 0.2s';
            document.body.style.opacity = '0.8';
        }

        setTimeout(() => {
            location.reload();
        }, 200);
    }

    // Функція для показу індикатора оновлення
    function showReloadIndicator(type) {
        // Створюємо тимчасовий індикатор
        const reloadIndicator = document.createElement('div');
        const emoji = type === 'css' ? '🎨' : type === 'js' ? '📜' : '🔄';
        const text = type === 'css' ? 'CSS Updated' : type === 'js' ? 'JS Updated' : 'Reloaded';

        reloadIndicator.innerHTML = `${emoji} ${text}`;
        reloadIndicator.style.cssText =
            'position: fixed;' +
            'top: 20px;' +
            'right: 20px;' +
            'background: #4CAF50;' +
            'color: white;' +
            'padding: 8px 16px;' +
            'border-radius: 20px;' +
            'font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;' +
            'font-size: 14px;' +
            'font-weight: 500;' +
            'z-index: 2147483647;' +
            'box-shadow: 0 4px 12px rgba(0,0,0,0.15);' +
            'animation: hotReloadSlideIn 0.3s ease-out;' +
            'pointer-events: none;';

        // Додаємо CSS анімацію якщо її ще немає
        if (!document.getElementById('hotReloadStyles')) {
            const style = document.createElement('style');
            style.id = 'hotReloadStyles';
            style.textContent = `
                @keyframes hotReloadSlideIn {
                    from { transform: translateX(100%); opacity: 0; }
                    to { transform: translateX(0); opacity: 1; }
                }
                @keyframes hotReloadSlideOut {
                    from { transform: translateX(0); opacity: 1; }
                    to { transform: translateX(100%); opacity: 0; }
                }
            `;
            document.head.appendChild(style);
        }

        document.body.appendChild(reloadIndicator);

        // Видаляємо індикатор через 2 секунди
        setTimeout(() => {
            reloadIndicator.style.animation = 'hotReloadSlideOut 0.3s ease-in';
            setTimeout(() => {
                if (reloadIndicator.parentNode) {
                    reloadIndicator.parentNode.removeChild(reloadIndicator);
                }
            }, 300);
        }, 2000);
    }

    // Функція для перевірки чи повідомлення стосується поточного файлу
    function isRelevantFileChange(changedFileName) {
        if (!changedFileName) return true; // якщо не вказано файл - перезавантажуємо все

        const changedFileNameLower = changedFileName.toLowerCase();
        const currentFileNameLower = currentFileName.toLowerCase();

        // Перезавантажуємо якщо:
        // 1. Змінився поточний HTML файл
        // 2. Змінився CSS файл (може впливати на вигляд)
        // 3. Змінився JS файл (може впливати на логіку)
        // 4. Змінились інші ресурси які зазвичай використовують HTML файли

        return (
            changedFileNameLower === currentFileNameLower || // точна відповідність файлу
            changedFileNameLower.endsWith('.css') ||         // всі CSS файли
            changedFileNameLower.endsWith('.js') ||          // всі JS файли
            changedFileNameLower.endsWith('.html') ||        // всі HTML файли (якщо включають один одного)
            changedFileNameLower.endsWith('.json') ||        // конфігураційні файли
            changedFileNameLower.includes('style') ||        // файли зі стилями
            changedFileNameLower.includes('script')          // файли зі скриптами
        );
    }

    function connectWebSocket() {
        if (isReloading) return;

        try {
            ws = new WebSocket('ws://localhost:{{webSocketPort}}');

            // Зберігаємо посилання на WebSocket для цього файлу
            window.hotReloaderInstances[fileId] = ws;

            ws.onopen = function(event) {
                console.log('🔗 Hot Reloader: WebSocket Connected for file:', fileId);
                reconnectAttempts = 0;
                updateIndicator('connected');
            };

            ws.onmessage = function(event) {
                try {
                    const data = JSON.parse(event.data);
                    const changedFileName = data.file;

                    // Перевіряємо чи це повідомлення стосується нашого файлу
                    if (!isRelevantFileChange(changedFileName)) {
                        console.log('🔄 Hot Reloader: Ignoring irrelevant file change for', fileId + ':', changedFileName);
                        return;
                    }

                    switch (data.type) {
                        case 'css-reload':
                            console.log('🎨 Hot Reloader: CSS file changed for', fileId + ':', changedFileName);
                            reloadCSS(changedFileName);
                            break;

                        case 'js-reload':
                            console.log('📜 Hot Reloader: JS file changed for', fileId + ':', changedFileName);
                            reloadJS(changedFileName);
                            break;

                        case 'reload':
                        default:
                            isReloading = true;
                            console.log('🔄 Hot Reloader: File changed for', fileId + ':', changedFileName || 'Unknown');

                            // Плавний ефект перед оновленням
                            if (document.body) {
                                document.body.style.transition = 'opacity 0.2s';
                                document.body.style.opacity = '0.8';
                            }

                            setTimeout(() => {
                                location.reload();
                            }, 200);
                            break;
                    }
                } catch (error) {
                    console.error('❌ Hot Reloader: Message parse error:', error);
                }
            };

            ws.onclose = function(event) {
                // Видаляємо з реєстру при закритті
                if (window.hotReloaderInstances[fileId] === ws) {
                    delete window.hotReloaderInstances[fileId];
                }

                if (!isReloading) {
                    console.log('🔌 Hot Reloader: WebSocket Disconnected for file:', fileId, 'code:', event.code);
                    updateIndicator('disconnected');

                    if (maxReconnectAttempts > 0) {
                        if (reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++;
                            const delay = Math.min(5000, 1000 * reconnectAttempts);
                            console.log('🔄 Hot Reloader: Reconnecting for', fileId, reconnectAttempts + '/' + maxReconnectAttempts + ' in ' + delay + 'ms');
                            setTimeout(connectWebSocket, delay);
                        } else {
                            console.error('❌ Hot Reloader: Failed to reconnect for', fileId, 'after', maxReconnectAttempts, 'attempts');
                            updateIndicator('failed');
                        }
                    } else {
                        const delay = Math.min(5000, 1000 * reconnectAttempts);
                        setTimeout(connectWebSocket, delay);
                    }
                }
            };

            ws.onerror = function(error) {
                console.error('❌ Hot Reloader: WebSocket Error for file', fileId + ':', error);
                updateIndicator('error');
            };

        } catch (error) {
            console.error('❌ Hot Reloader: Failed to create WebSocket for file', fileId + ':', error);
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
            '{{positionStyles}}' +
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
            console.log('🔥 Hot Reloader status for', currentFileName + ':', {
                fileId: fileId,
                fileName: currentFileName,
                connected: ws && ws.readyState === WebSocket.OPEN,
                readyState: ws ? ws.readyState : 'not created',
                reconnectAttempts: reconnectAttempts,
                port: {{webSocketPort}},
                activeInstances: Object.keys(window.hotReloaderInstances || {}).length
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
                indicator.title = 'Hot Reloader connected for ' + currentFileName + ' (click for status)';
                break;
            case 'disconnected':
                indicator.style.background = '#FF9800';
                indicator.innerHTML = '🔄';
                indicator.title = 'Hot Reloader reconnecting for ' + currentFileName;
                break;
            case 'error':
            case 'failed':
                indicator.style.background = '#F44336';
                indicator.innerHTML = '❌';
                indicator.title = 'Hot Reloader connection error for ' + currentFileName;
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
        if (ws && window.hotReloaderInstances[fileId] === ws) {
            ws.close();
            delete window.hotReloaderInstances[fileId];
        }
    });

    // Очищуємо при переході на іншу сторінку
    window.addEventListener('pagehide', function() {
        if (ws && window.hotReloaderInstances[fileId] === ws) {
            ws.close();
            delete window.hotReloaderInstances[fileId];
        }
    });
})();