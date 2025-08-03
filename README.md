# Hot Reload Plugin for IntelliJ IDEA

Automatically updates the page open in the browser when changing project files, providing a seamless development
experience.

## Features

- Real-time browser refresh on file changes
- Configurable file extensions monitoring
- Built-in WebSocket server for instant updates
- No temporary files creation
- Configurable refresh delay
- Visual reload indicator (optional)
- Toolbar and context menu integration

## Requirements

- IntelliJ IDEA 2025.1 or newer
- Java 21 or newer

## Installation

1. Open IntelliJ IDEA
2. Go to `Settings/Preferences → Plugins → Marketplace`
3. Search for "Hot Reload"
4. Click `Install`

## Usage

### Starting Hot Reload

You can start Hot Reload in several ways:

- From Tools menu: `Tools → Hot Reload → Start Hot Reload`
- Right-click on HTML file → `Run with Hot Reload`
- Using toolbar button next to the Run button

### Configuration

Go to `Settings/Preferences → Tools → Hot Reload` to configure:

- WebSocket port (default: 4081)
- HTTP server port (default: 4080)
- Watched file extensions
- Browser refresh delay
- Auto-start options
- Page indicator visibility

### Default Settings

- Watched extensions: html, css, js, jsx, ts, tsx, json
- WebSocket port: 4081
- HTTP port: 4080
- Refresh delay: 100ms

## License

This project is licensed under the MIT License.

## Installation

You can install plugin from:

[![Marketplace](marketplace.svg)](https://plugins.jetbrains.com/plugin/xxxxx-hot-reload)

Or directly from your IDE:
1. Install a compatible JetBrains IDE, such as WebStorm, or other IntelliJ-based IDEs
2. Launch the IDE and open plugin settings
3. Search for `Hot Reload` and click install

## Supported Platforms

+ **AppCode** — build 251.0+
+ **CLion** — 2025.1+
+ **GoLand** — 2025.1+
+ **IntelliJ** IDEA Ultimate — 2025.1+
+ **PhpStorm** — 2025.1+
+ **PyCharm Pro** — 2025.1+
+ **Rider** — 2025.1+
+ **RubyMine** — 2025.1+
+ **RustRover** — 2025.1+
+ **WebStorm** — 2025.1+

--- 
## Support

If you like this project, please consider supporting it by:

+ Star this repository on GitHub
+ Sponsor this project on GitHub Sponsors
+ **PayPal** to `serhii@pimenov.com.ua`.
+ [**Patreon**](https://www.patreon.com/metroui)
+ [**Buy me a coffee**](https://buymeacoffee.com/pimenov)

---

Copyright (c) 2025 by [Serhii Pimenov](https://pimenov.com.ua)