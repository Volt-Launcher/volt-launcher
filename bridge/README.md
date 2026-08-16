# VoltLauncher CurseForge Bridge

CurseForge's API requires a per-developer key. Distributing that key inside the launcher would
hand it to every user, so this small Express service holds it instead and proxies requests.

## Setup

```bash
cd bridge
npm install
cp .env.example .env      # then paste your key into CURSEFORGE_API_KEY
npm start
```

Get a key at <https://console.curseforge.com> (Settings → API Keys).

The launcher points at `http://localhost:8787` by default; change it under
**Settings → Content Providers** if you host the bridge elsewhere.

## Endpoints

The bridge mirrors CurseForge's own routes so it stays thin and stable — the launcher does all
normalisation. It adds authentication, a short-lived response cache and two conveniences:

| Route | Purpose |
| --- | --- |
| `GET /health` | Reports whether the bridge is up and whether its key works |
| `GET /v1/mods/search` | Project discovery |
| `GET /v1/mods/:modId` | Project detail, with the rendered description inlined as `descriptionHtml` |
| `GET /v1/mods/:modId/files` | Versions of a project |
| `GET /v1/files/:fileId` | Single file lookup |
| `POST /v1/mods/files` | Bulk file lookup, used when installing a modpack manifest |
| `POST /v1/mods` | Bulk project lookup |
| `GET /v1/categories` | Category listing |

## Running it as a service

The bridge is stateless apart from its in-memory cache, so any process manager works:

```bash
# systemd --user example
systemd-run --user --unit=volt-bridge --working-directory="$PWD" node src/server.js
```
