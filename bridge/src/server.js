import express from 'express';
import cors from 'cors';
import compression from 'compression';

import { apiKeyFingerprint, config, hasApiKey } from './config.js';
import { createRouter } from './routes.js';
import { checkApiKey, CurseForgeError } from './curseforge.js';

const app = express();

app.disable('x-powered-by');
app.use(compression());
app.use(express.json({ limit: '1mb' }));
app.use(
  cors({
    origin: config.allowedOrigins === '*' ? true : config.allowedOrigins.split(',').map((value) => value.trim()),
  }),
);

app.use('/', createRouter());

app.use((_req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// eslint-disable-next-line no-unused-vars -- Express identifies error handlers by arity.
app.use((error, _req, res, _next) => {
  const status = error instanceof CurseForgeError ? error.status : 500;
  // Expected upstream/configuration problems get one line; anything else gets a stack trace.
  if (error instanceof CurseForgeError) {
    console.warn(`[bridge] ${status} ${error.message}`);
  } else {
    console.error('[bridge]', error);
  }
  res.status(status).json({ error: error.message ?? 'Unexpected bridge error' });
});

const server = app.listen(config.port, config.host, async () => {
  console.log(`[bridge] CurseForge bridge listening on http://${config.host}:${config.port}`);

  if (!hasApiKey()) {
    console.warn(
      '[bridge] No CURSEFORGE_API_KEY found — CurseForge discovery stays disabled.\n' +
        '[bridge] Put the key in bridge/.env and start with "npm start" (plain "node src/server.js"\n' +
        '[bridge] does not read .env), or export CURSEFORGE_API_KEY in your shell.',
    );
    return;
  }

  // Validate at startup rather than on the user's first search, so a bad key is obvious here
  // instead of surfacing as an opaque error inside the launcher.
  console.log(`[bridge] API key loaded: ${apiKeyFingerprint()}`);
  const check = await checkApiKey();
  console.log(
    check.ok
      ? '[bridge] CurseForge accepted the key — discovery is ready.'
      : `[bridge] CurseForge rejected the key.\n[bridge] ${check.reason}`,
  );
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    console.log(`[bridge] ${signal} received, shutting down`);
    server.close(() => process.exit(0));
  });
}
