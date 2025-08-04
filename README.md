# Hot Reloader Plugin for IntelliJ IDEA

<!-- Plugin description -->
Hot Reloader is a powerful development tool that automatically refreshes your web browser whenever you make changes to
your project files. It establishes a WebSocket connection between your IDE and browser to instantly reflect code
modifications without manual page reloads. Perfect for web developers who want to streamline their development workflow and see changes instantly in the browser.
<!-- Plugin description end -->

## Features

- Real-time browser refresh on file changes
- Built-in WebSocket server for instant updates
- Support for multiple file types (HTML, CSS, JS, TypeScript)
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

- Watched extensions: html, css, js
- WebSocket port: 4081
- HTTP port: 4080
- Refresh delay: 100ms

## License

This project is licensed under the MIT License.

## Installation

You can install plugin from:

[![Marketplace](marketplace.svg)](https://plugins.jetbrains.com/plugin/12213-hot-reloader)

Or directly from your IDE:
1. Install a compatible JetBrains IDE, such as WebStorm, or other IntelliJ-based IDEs
2. Launch the IDE and open plugin settings
3. Search for `Hot Reloader` and click install

## Supported Platforms

+ Android Studio — Narwhal | 2025.1.1+
+ AppCode — build 251.0+
+ Aqua — build 251.0+
+ CLion — 2025.1+
+ Code With Me Guest — 1.0+
+ DataGrip — 2025.1+
+ DataSpell — 2025.1+
+ GoLand — 2025.1+
+ IntelliJ IDEA Community — 2025.1+
+ IntelliJ IDEA Ultimate — 2025.1+
+ JetBrains Client — 1.0+
+ JetBrains Gateway — 2025.1+
+ MPS — 2025.1+
+ PhpStorm — 2025.1+
+ PyCharm — 2025.1+
+ PyCharm Community — 2025.1+
+ Rider — 2025.1+
+ RubyMine — 2025.1+
+ RustRover — 2025.1+
+ WebStorm — 2025.1+
+ Writerside — build 251.0+

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