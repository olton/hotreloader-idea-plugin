# Hot Reloader Plugin for IntelliJ IDEA

<!-- Plugin description -->
Hot Reloader is a IDEA Plugin that automatically refreshes your web browser whenever you make changes to your project files.
<!-- Plugin description end -->

## Features

- Real-time browser refresh on file changes
- Built-in WebSocket server for instant updates
- Support for multiple file types (HTML, CSS, JS, TypeScript, etc...)
- Configurable refresh delay and file monitoring
- Visual indicators for reload status
- Easy integration with IDE toolbar and context menus

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

- Right-click on HTML file → `Run with Hot Reload`
- Using button on main toolbar to the Run view

### Configuration

Go to `Settings/Preferences → Tools → Hot Reload` to configure:

- WebSocket port (default: 4081)
- HTTP server port (default: 4080)
- Watched file extensions
- Browser refresh delay
- Auto-start options
- Page indicator visibility

### Default Settings

- `WebSocket Port`: 3000
- `HTTP Server Port`: 4080
- `Watched Extensions`: html, css, js, less
- `Browser Refresh Delay`: 100ms
- `Auto-start Server`: enabled
- `Auto-start Service`: disabled
- `Hot Reload Indicator`: enabled
- `Indicator Position`: top right
- `Excluded Folders`: .idea, .git, node_modules
- `Auto-stop`: disabled
- `Auto-stop Delay`: 300 seconds
- `Core Pool Size`: 3 threads
- `Search Free Port`: enabled
- `Notification Timeout`: 3000ms
- `Reconnect Attempts`: 5
- `Force VFS Sync`: enabled
- `Watch External Changes`: enabled
- `External Watch Paths`: dist, build, lib

## License

This project is licensed under the MIT License.

## Installation

You can install plugin from:

[![Marketplace](marketplace.svg)](https://plugins.jetbrains.com/plugin/28099-hot-reloader--hot-reloading-for-html-css-js-and-more)

Or directly from your IDE:
1. Install a compatible JetBrains IDE, such as WebStorm, or other IntelliJ-based IDEs
2. Launch the IDE and open plugin settings
3. Search for `Hot Reloader` and click install

## Supported Platforms

+ Android Studio — build 2025.1.1 — 2025.1.3 Canary 3
+ AppCode — build 251.0 — 253.*
+ Aqua — build 251.0 — 253.*
+ CLion — 2025.1 — 2025.2
+ Code With Me Guest — build 251.0 — 253.*
+ DataGrip — 2025.1 — 2025.2
+ DataSpell — 2025.1 — 2025.2
+ GoLand — 2025.1 — 2025.2
+ IntelliJ IDEA Community — 2025.1 — 2025.2
+ IntelliJ IDEA Ultimate — 2025.1 — 2025.2
+ JetBrains Client — build 251.0 — 253.*
+ JetBrains Gateway — 2025.1 — 2025.2
+ MPS — 2025.1 — 2025.2-RC1
+ PhpStorm — 2025.1 — 2025.2
+ PyCharm — 2025.1 — 2025.2
+ PyCharm Community — 2025.1 — 2025.2
+ Rider — 2025.1 — 2025.2-RC1
+ RubyMine — 2025.1 — 2025.2
+ RustRover — 2025.1 — 2025.2
+ WebStorm — 2025.1 — 2025.2
+ Writerside — build 251.0 — 253.*

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