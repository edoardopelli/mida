# MIDA Frontend

Applicazione Angular 21 standalone per MIDA. Il frontend è una PWA responsive in italiano che consuma le API del backend Spring Boot tramite il proxy nginx.

Per l'architettura generale, la configurazione e l'avvio dell'intero stack consulta il [`README.md`](../README.md) principale.

## Sviluppo locale

Prerequisiti: Node.js 24 e npm. Le API devono essere raggiungibili su `http://localhost:8080` (stack Docker) oppure va configurato un proxy locale.

```bash
npm ci --legacy-peer-deps
npm start
```

L'app di sviluppo è disponibile su `http://localhost:4200`. Il service worker è attivo solo nella build di produzione.

## Build di produzione

```bash
npm run build
```

La build genera `dist/mida-frontend/browser` e, tramite `scripts/append-push-handler.mjs`, aggiunge gli handler `push` e `notificationclick` a `ngsw-worker.js`.

Il risultato viene servito dal container nginx descritto in `../docker-compose.yml`.

## Test

```bash
npx ng test --watch=false
```

I test usano Angular CLI e Vitest con ambiente jsdom.

## Struttura

- `src/app/core`: modelli, client API, store con Signals per compiti e diario, SSE, Web Push.
- `src/app/pages`: calendario, diario, dashboard, importazioni, impostazioni.
- `src/app/app.*`: shell, routing e configurazione PWA.
- `public/`: manifest, icone e asset statici.
- `nginx.conf`: proxy API/SSE/OpenAPI, header di sicurezza e fallback SPA.

## Note

- Le chiamate API usano percorsi relativi `/api`, così funzionano sia dietro nginx sia in sviluppo con proxy.
- La build disabilita l'inlining critico del CSS perché la CSP di nginx non consente l'`onload` inline generato da Angular.
- Non inserire segreti nel codice: la chiave VAPID privata resta nel backend.
