import { access, appendFile, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const marker = '/* MIDA_PUSH_HANDLERS */';
const candidates = [
  process.argv[2],
  join('dist', 'mida-frontend', 'browser', 'ngsw-worker.js'),
  join('dist', 'mida-frontend', 'ngsw-worker.js'),
].filter(Boolean);

let workerPath = null;
for (const candidate of candidates) {
  try {
    await access(candidate);
    workerPath = candidate;
    break;
  } catch {
    // Try the next known Angular output location.
  }
}

if (!workerPath) {
  console.error('ngsw-worker.js non trovato: esegui prima la build di produzione.');
  process.exit(1);
}

const existing = await readFile(workerPath, 'utf8');
if (existing.includes(marker)) {
  console.log('Handler Web Push già presenti in ngsw-worker.js');
  process.exit(0);
}

const handlers = `

${marker}
self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    payload = { body: event.data ? event.data.text() : '' };
  }

  const title = payload.title || 'MIDA';
  const data = payload.data && typeof payload.data === 'object' ? payload.data : {};
  const options = {
    body: payload.body || 'Nuovo aggiornamento da MIDA.',
    icon: payload.icon || '/icons/icon-192.png',
    badge: payload.badge || '/icons/badge-72.png',
    tag: payload.tag || 'mida',
    renotify: false,
    requireInteraction: true,
    data: { ...data, url: payload.url || '/' },
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const targetUrl = new URL(
    (event.notification.data && event.notification.data.url) || '/',
    self.location.origin,
  ).href;

  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      for (const client of windows) {
        if (!client.url.startsWith(self.location.origin)) {
          continue;
        }
        try {
          if ('navigate' in client) {
            await client.navigate(targetUrl);
          }
          if ('focus' in client) {
            await client.focus();
          }
          return;
        } catch (error) {
          // La finestra potrebbe essere stata chiusa o non essere più
          // navigabile: si prova la successiva e poi si apre una nuova.
        }
      }
      if (self.clients.openWindow) {
        await self.clients.openWindow(targetUrl);
      }
    })(),
  );
});
`;

await appendFile(workerPath, handlers, 'utf8');
console.log(`Handler Web Push aggiunti a ${workerPath}`);
