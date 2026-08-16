import { config, hasApiKey } from './config.js';

/**
 * Thin CurseForge client with an in-memory TTL cache.
 *
 * CurseForge rate-limits per key, and the launcher's discovery screen re-queries on every filter
 * change, so identical requests within the TTL are served from memory.
 */

const cache = new Map();

function cacheKey(method, path, body) {
  return `${method} ${path} ${body ? JSON.stringify(body) : ''}`;
}

function readCache(key) {
  const entry = cache.get(key);
  if (!entry) return null;
  if (Date.now() > entry.expiresAt) {
    cache.delete(key);
    return null;
  }
  return entry.value;
}

function writeCache(key, value) {
  cache.set(key, { value, expiresAt: Date.now() + config.cacheTtlMs });
  // Cheap bound: discovery browsing is bursty, so evicting the oldest entries is enough.
  if (cache.size > 500) {
    const oldest = cache.keys().next().value;
    cache.delete(oldest);
  }
}

export class CurseForgeError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'CurseForgeError';
    this.status = status;
  }
}

/**
 * Performs a request against the CurseForge API.
 *
 * @param {string} path  path beginning with a slash, e.g. `/v1/mods/search?gameId=432`
 * @param {{method?: string, body?: unknown, cacheable?: boolean}} [options]
 */
export async function callCurseForge(path, options = {}) {
  if (!hasApiKey()) {
    throw new CurseForgeError(
      'The bridge has no CURSEFORGE_API_KEY configured. Request a key at https://console.curseforge.com and set it in bridge/.env',
      503,
    );
  }

  const method = options.method ?? 'GET';
  const cacheable = options.cacheable ?? method === 'GET';
  const key = cacheKey(method, path, options.body);

  if (cacheable) {
    const hit = readCache(key);
    if (hit) return hit;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.requestTimeoutMs);

  try {
    const response = await fetch(`${config.upstream}${path}`, {
      method,
      headers: {
        'x-api-key': config.apiKey,
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: controller.signal,
    });

    if (!response.ok) {
      const detail = await response.text().catch(() => '');
      throw new CurseForgeError(
        `CurseForge responded with ${response.status}${detail ? `: ${detail.slice(0, 300)}` : ''}`,
        response.status,
      );
    }

    const payload = await response.json();
    if (cacheable) writeCache(key, payload);
    return payload;
  } catch (error) {
    if (error instanceof CurseForgeError) throw error;
    if (error.name === 'AbortError') {
      throw new CurseForgeError('CurseForge did not respond in time', 504);
    }
    throw new CurseForgeError(`Could not reach CurseForge: ${error.message}`, 502);
  } finally {
    clearTimeout(timeout);
  }
}

/** Verifies the configured key by making the cheapest authenticated call available. */
export async function checkApiKey() {
  if (!hasApiKey()) return { ok: false, reason: 'no-api-key' };
  try {
    await callCurseForge('/v1/games/432');
    return { ok: true };
  } catch (error) {
    return { ok: false, reason: error.message };
  }
}
