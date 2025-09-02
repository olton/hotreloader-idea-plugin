(function() {
    console.log('🔥 Hot Reloader activated on port {{webSocketPort}}');

    let ws;
    let reconnectAttempts = 0;
    const maxReconnectAttempts = {{maxReconnectAttempts}};
    let isReloading = false;

    function connectWebSocket() {
        if (isReloading) return;

        try {
            ws = new WebSocket('ws://localhost:{{webSocketPort}}');

            ws.onopen = function(event) {
                console.log('🔗 Hot Reloader: WebSocket Connected');
                reconnectAttempts = 0;
                updateIndicator('connected');
            };

            ws.onmessage = function(event) {
                try {
                    const data = JSON.parse(event.data);
                    if (data.type === 'reload') {
                        isReloading = true;
                        console.log('🔄 Hot Reloader: File changed:', data.file || 'Unknown');

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
                    console.error('❌ Hot Reloader: Message parse error:', error);
                }
            };

            ws.onclose = function(event) {
                if (!isReloading) {
                    console.log('🔌 Hot Reloader: WebSocket Disconnected, code:', event.code);
                    updateIndicator('disconnected');

                    if (maxReconnectAttempts > 0) {
                        if (reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++;
                            const delay = Math.min(5000, 1000 * reconnectAttempts);
                            console.log('🔄 Hot Reloader: Reconnecting ' + reconnectAttempts + '/' + maxReconnectAttempts + ' in ' + delay + 'ms');
                            setTimeout(connectWebSocket, delay);
                        } else {
                            console.error('❌ Hot Reloader: Failed to reconnect after', maxReconnectAttempts, 'attempts');
                            updateIndicator('failed');
                        }
                    } else {
                        const delay = Math.min(5000, 1000 * reconnectAttempts);
                        setTimeout(connectWebSocket, delay);
                    }
                }
            };

            ws.onerror = function(error) {
                console.error('❌ Hot Reloader: WebSocket Error:', error);
                updateIndicator('error');
            };

        } catch (error) {
            console.error('❌ Hot Reloader: Failed to create WebSocket:', error);
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
            console.log('🔥 Hot Reloader status:', {
                connected: ws && ws.readyState === WebSocket.OPEN,
                readyState: ws ? ws.readyState : 'not created',
                reconnectAttempts: reconnectAttempts,
                port: {{webSocketPort}}
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
                indicator.title = 'Hot Reloader connected (click for status)';
                break;
            case 'disconnected':
                indicator.style.background = '#FF9800';
                indicator.innerHTML = '🔄';
                indicator.title = 'Hot Reloader reconnecting...';
                break;
            case 'error':
            case 'failed':
                indicator.style.background = '#F44336';
                indicator.innerHTML = '❌';
                indicator.title = 'Hot Reloader connection error';
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
