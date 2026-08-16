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

Start it with `npm start`, not `node src/server.js` — the npm scripts pass `--env-file`, which is
what makes `.env` get read at all. Alternatively export `CURSEFORGE_API_KEY` in your shell.

On startup the bridge prints whether the key was loaded and whether CurseForge accepted it:

```
[bridge] API key loaded: aBcD…wXyZ (60 chars)
[bridge] CurseForge accepted the key — discovery is ready.
```

### Getting a 403 with a key you know is valid

Two causes, both handled now but worth knowing:

* **`User-Agent`.** Node's `fetch` sends `User-Agent: node`, which the CDN in front of
  `api.curseforge.com` rejects with 403 regardless of the key. The bridge sends a descriptive
  agent instead; override it with `BRIDGE_USER_AGENT` if you need to.
* **The key never reached the process.** `.env` is only read via the npm scripts, and a key
  pasted with wrapping quotes or a trailing newline is sent verbatim and refused. The bridge now
  strips those and prints the fingerprint above so you can confirm what it actually loaded.

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
