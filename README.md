# VoltLauncher

A modern Minecraft launcher: multiple accounts, every major mod loader, and content discovery
across Modrinth and CurseForge.

The launcher is a Java backend that exposes a local HTTP API on `127.0.0.1:45938`, driven by an
Electron + Vue front end.

## Modules

| Module | Contains |
| --- | --- |
| `volt-core` | Paths, HTTP, JSON, crypto, async utilities, launcher settings |
| `volt-auth` | Microsoft / Xbox Live / Minecraft authentication, encrypted account store |
| `volt-game` | Profiles, version resolution, mod loaders, asset installation, Java runtimes, launching |
| `volt-providers` | Modrinth and CurseForge clients, content and modpack installation |
| `volt-server` | The local HTTP API |
| `volt-app` | Executable entry point; boots the API and the Electron shell |
| `volt-setup` | Cross-platform installer that registers Start Menu / application entries |
| `ui/` | Vue 3 + Tailwind front end, packaged with Electron |
| `bridge/` | Express service holding the CurseForge API key (see below) |

Dependencies flow one way: `core → auth → game → providers → server → app`. `volt-game` knows
nothing about content providers, which is what keeps the graph acyclic.

## Building

```bash
mvn package                    # all modules; volt-app/target/volt-launcher.jar is runnable
./build.sh                     # full platform build (native image + Electron + packages)
```

`build.sh` delegates to `build-linux.sh`, `build-macos.sh` or `build-windows.bat`.

## Running in development

```bash
# 1. Front end
cd ui && pnpm install && pnpm dev

# 2. Backend
mvn -pl volt-app -am exec:java

# 3. CurseForge bridge (optional — only needed for CurseForge discovery)
cd bridge && npm install && cp .env.example .env   # paste your key, then:
npm start
```

## CurseForge

CurseForge's API requires a per-developer key, and their terms do not allow shipping it inside a
client that users can read it out of. The `bridge/` service holds the key and proxies requests, so
the launcher itself never sees it.

Get a key at <https://console.curseforge.com>, put it in `bridge/.env`, and start the bridge. The
launcher points at `http://localhost:8787` by default; change it under **Settings → Content
providers**. Without a running bridge, Modrinth still works and CurseForge is shown as
unavailable with an explanation.

## Installing

```bash
java -jar volt-setup.jar                    # per-user install + application menu entry
java -jar volt-setup.jar --desktop-shortcut # also place a desktop shortcut
java -jar volt-setup.jar --help
```

The installer needs no administrator rights: it installs into `%LOCALAPPDATA%\Programs`,
`~/.local/share/volt-launcher` or `~/Applications` depending on the platform.

## Data layout

Everything lives under `~/.voltlauncher` (override with `VOLTLAUNCHER_HOME`):

```
config/settings.json     launcher preferences
auth/                    encrypted account store
instances/<slug>/game/   per-profile worlds, mods, configs
shared/                  assets, libraries, client jars and natives, shared across profiles
runtimes/temurin/        Java runtimes the launcher downloaded
cache/installers/        cached Forge/NeoForge installer JARs
logs/                    launcher and per-profile game logs
```

Assets and libraries are shared rather than copied per profile — a Minecraft version costs
roughly a gigabyte, and duplicating that for every profile added up quickly.
