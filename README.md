<div align="center">

<img src="logo.png" width="128" alt="SushiTriggers logo">

# SushiTriggers

**Live JavaScript scripting for Minecraft — a Fabric 26.2 port of ChatTriggers**

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-8A2BE2?style=for-the-badge)
![Fabric](https://img.shields.io/badge/Fabric-Loader_0.19-blueviolet?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

[Download](../../releases/latest) · [Commands](#-commands) · [Writing scripts](#-writing-scripts) · [Building](#-building)

</div>

---

## ✨ Features

| | |
|---|---|
| 🧩 **JS scripting** | Write modules in JavaScript (Rhino) and reload them live with `/st reload` |
| 🖥️ **In-game GUI** | Type `/st` to open a manager with Scripts, Modules, Console, Events, Editor, Settings and Info tabs |
| ✏️ **Code editor** | Create and edit script files of your local modules right inside the game |
| 📡 **Event log** | Logs damage, knockback, inventory and container clicks, key presses, blocks, effects and more — every category can be toggled or filtered by words |
| 🎙️ **Log recording** | Press a key (default `F8`) to start recording the log and again to save everything in between to a file |
| ☁️ **GitHub import** | `/st import <name>` downloads `scripts/<name>/` from your GitHub repo into your modules folder |
| 📦 **Packet trigger** | `register("packetReceived", ...)` with `cancel(event)` to block packets, e.g. velocity (anti-knockback) |
| 🚀 **Auto releases** | Every push builds the mod and publishes the jar as a GitHub Release |

## 📥 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for **Minecraft 26.2** and the **Fabric API**.
2. Download the newest jar from the [Releases](../../releases/latest) page.
3. Drop it into your `mods` folder and start the game.
4. Scripts live in `config/SushiTriggers/modules/<module name>/`.

## ⌨️ Commands

| Command | What it does |
|---|---|
| `/st` | Opens the GUI |
| `/st help` | Shows all commands |
| `/st load` · `reload` · `unload` | Load, reload or unload every script |
| `/st console [lines]` | Prints the latest console lines in chat |
| `/st dump` | Saves the current log to `config/SushiTriggers/dumps/` |
| `/st record [key <F8\|R>]` | Starts/stops log recording, or changes the record key |
| `/st events [on\|off\|chat]` | Event log on/off, or show events live in chat |
| `/st scripts` | Lists scripts in your GitHub repo |
| `/st import <name>` | Imports `scripts/<name>/` from GitHub |
| `/st repo [owner/repo[@branch]]` | Shows or sets the script repo |
| `/st token <token>` · `/st upload <name>` | Save a GitHub token and upload a module |

## 📜 Writing scripts

A module is a folder with a `metadata.json` and your code:

```
config/SushiTriggers/modules/hello/
├── metadata.json
└── hello.js
```

```json
{ "name": "hello", "version": "1.0.0", "entry": "hello.js" }
```

```js
register("command", function (name) {
    ChatLib.chat("&bHello " + (name || "world") + "!");
}).setName("hello");

register("tick", function () {
    // runs every tick
});

// block incoming packets
register("packetReceived", function (packet, event) {
    // cancel(event);
});
```

> Scripts use Rhino 1.8, so write ES5-style code: no `for...of`, no rest parameters and `Packages.<class>` instead of `Java.type`.

## 🗂️ GitHub script repo

Keep your scripts in a repo like this and import them in-game:

```
scripts/
└── test/
    ├── metadata.json
    └── test.js
```

```
/st repo yourname/yourrepo
/st import test
```

## 🔨 Building

The project builds automatically with GitHub Actions (Gradle 9.8.2, Java 25). To build locally:

```bash
gradle build
```

The jar is created in `build/libs/`.

## ⚠️ Status

This is an ongoing, staged port. The JS engine, commands, GUI, logging and packet triggers work; chat triggers, world/player wrappers and rendering are still being ported.

## 🙏 Credits

SushiTriggers is based on [ChatTriggers](https://github.com/ChatTriggers/ChatTriggers) by FalseHonesty, Ecolsson, kerbybit and contributors, licensed under MIT.
