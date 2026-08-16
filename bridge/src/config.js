/**
 * Bridge configuration, read once at startup from the environment.
 *
 * The CurseForge API key lives here and nowhere else: shipping it inside the desktop client
 * would expose it to every user, which CurseForge's terms forbid.
 */

const DEFAULTS = {
  port: 8787,
  host: '127.0.0.1',
  upstream: 'https://api.curseforge.com',
  cacheTtlSeconds: 300,
  requestTimeoutMs: 20000,
  allowedOrigins: '*',
};

function intFromEnv(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export const config = {
  port: intFromEnv('PORT', DEFAULTS.port),
  host: process.env.HOST || DEFAULTS.host,
  apiKey: (process.env.CURSEFORGE_API_KEY || '').trim(),
  upstream: (process.env.CURSEFORGE_API_URL || DEFAULTS.upstream).replace(/\/$/, ''),
  cacheTtlMs: intFromEnv('CACHE_TTL_SECONDS', DEFAULTS.cacheTtlSeconds) * 1000,
  requestTimeoutMs: intFromEnv('REQUEST_TIMEOUT_MS', DEFAULTS.requestTimeoutMs),
  allowedOrigins: process.env.ALLOWED_ORIGINS || DEFAULTS.allowedOrigins,
};

export const hasApiKey = () => config.apiKey.length > 0;
