# API MIDA

Base URL locale: `http://localhost:8200`.

Tutti gli endpoint applicativi sono sotto `/api`. Le risposte sono JSON; gli errori usano [Problem Details](https://www.rfc-editor.org/rfc/rfc9457) con `Content-Type: application/problem+json`.

L'app non prevede autenticazione nella versione 1 e deve essere raggiungibile solo da `localhost` o dal proxy HTTPS sulla rete locale.

## Formato degli errori

```json
{
  "type": "about:blank",
  "title": "Richiesta non valida",
  "status": 400,
  "detail": "from: La data iniziale non può essere successiva alla data finale",
  "instance": "/api/homework"
}
```

| Status | Significato |
| --- | --- |
| `400` | Parametro, data o corpo della richiesta non valido. |
| `404` | Compito o risorsa inesistente. |
| `405` | Metodo HTTP non supportato per la risorsa. |
| `409` | Conflitto di unicità (gestito internamente durante l'importazione). |
| `422` | Errore di parsing DidUP, quando applicabile. |
| `500` | Errore interno; consultare i log del backend. |

## Compiti

### `GET /api/homework`

Elenca i compiti con filtri, ordinamento e paginazione. L'ordinamento è sempre: `dueDate`, `subject`, `teacher`, `id`.

Parametri query:

| Parametro | Tipo | Default | Descrizione |
| --- | --- | --- | --- |
| `from` | data ISO `YYYY-MM-DD` | — | Estremo inferiore della data selezionata. |
| `to` | data ISO `YYYY-MM-DD` | — | Estremo superiore della data selezionata. |
| `dateField` | `DUE` \| `ASSIGNED` | `DUE` | Campo data su cui applicare l'intervallo. |
| `subject` | stringa | — | Materia esatta. |
| `teacher` | stringa | — | Professore esatto. |
| `completed` | booleano | — | `true` solo completati, `false` solo da fare. |
| `q` | stringa | — | Ricerca case-insensitive su descrizione, materia e professore. |
| `page` | intero ≥ 0 | `0` | Pagina richiesta. |
| `size` | intero 1–100 | `20` | Dimensione pagina. |

Esempio:

```bash
curl 'http://localhost:8200/api/homework?from=2026-11-01&to=2026-11-30&completed=false&page=0&size=20'
```

Risposta:

```json
{
  "content": [
    {
      "id": "73010c87-d9b5-4e6b-bc11-a981b50be075",
      "importId": "6af70d3a68d59128bcb2a0ef",
      "sourceCode": "AC508C2D4537",
      "originalFilename": "text-AC508C2D4537-1.txt",
      "importedAt": "2026-11-13T13:14:34.372Z",
      "assignedDate": "2026-11-11",
      "assignedDateRaw": "11 NOVEMBRE",
      "dueDate": "2026-11-18",
      "dueDateRaw": "MERCOLEDÌ 18 NOVEMBRE",
      "subject": "ARTE ED IMMAGINE",
      "teacher": "A. FERTITTA",
      "description": "Portare il consueto materiale di Arte...",
      "completed": false,
      "completedAt": null,
      "revision": 1,
      "updatedAt": "2026-11-13T13:14:34.372Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### `GET /api/homework/calendar`

Restituisce **tutti** i compiti dell'intervallo richiesto, senza paginazione, per il rendering del calendario.

Parametri: `from` e `to` sono obbligatori. Sono accettati anche `dateField`, `subject`, `teacher`, `completed` e `q`.

```bash
curl 'http://localhost:8200/api/homework/calendar?from=2026-11-01&to=2026-11-30&dateField=DUE'
```

La risposta è un array di oggetti identici a quelli di `/api/homework`, non un oggetto paginato.

### `GET /api/homework/{id}`

Restituisce un compito per ID. Se non esiste risponde `404`.

```bash
curl 'http://localhost:8200/api/homework/82aa8e1f-d222-4600-a28f-829b4f185d38'
```

### `PATCH /api/homework/{id}/completion`

Aggiorna il completamento del solo compito indicato. Ogni compito è un documento autonomo, quindi gli altri compiti non vengono toccati.

Corpo:

```json
{ "completed": true }
```

Risposta: l'oggetto aggiornato. Se `completed` è `true`, `completedAt` viene valorizzato; se è `false`, viene rimosso.

```bash
curl -X PATCH 'http://localhost:8200/api/homework/82aa8e1f-d222-4600-a28f-829b4f185d38/completion' \
  -H 'Content-Type: application/json' \
  -d '{"completed":true}'
```

### `GET /api/homework/subjects`

Array di materie distinte, ordinate senza distinzione tra maiuscole e minuscole.

```json
["ARTE ED IMMAGINE", "ITALIANO", "MATEMATICA"]
```

### `GET /api/homework/teachers`

Array di professori distinti, ordinati senza distinzione tra maiuscole e minuscole.

## Diario

Le attività del diario usano una risorsa separata. I campi sono analoghi ai compiti, ma non hanno stato di completamento.

### `GET /api/diary`

Elenca le attività del diario con filtri, ordinamento e paginazione. Le attività sono ordinate per data decrescente, ora, materia e professore.

Parametri: `from`, `to`, `subject`, `teacher`, `q`, `page`, `size`; `from`/`to` si riferiscono ad `activityDate`.

### `GET /api/diary/day`

Restituisce tutte le attività di un giorno specifico, senza paginazione. Parametri: `date` obbligatorio, `subject`, `teacher`, `q` opzionali.

### `GET /api/diary/dates`

Restituisce i giorni disponibili con numero di attività e materie:

```json
[
  { "date": "2026-11-13", "entryCount": 5, "subjects": ["FRANCESE", "INGLESE", "ITALIANO", "MUSICA", "SCIENZE"] }
]
```

### `GET /api/diary/subjects` e `GET /api/diary/teachers`

Elencano materie e professori presenti nelle attività del diario.

### `GET /api/diary/{id}`

Restituisce una singola attività, con descrizione integrale. Il campo `description` può essere una stringa vuota quando l'ora è registrata senza una descrizione dell'attività.

Esempio di attività:

```json
{
  "id": "0de1c4f4-6e57-4b51-8b50-917a7f1f4f11",
  "importId": "6af7201a68d59128bcb2a101",
  "sourceCode": "D1A2B3C4D5E6",
  "originalFilename": "text-D1A2B3C4D5E6-1.txt",
  "importedAt": "2026-11-13T14:20:00Z",
  "activityDate": "2026-11-13",
  "activityDateRaw": "13 NOVEMBRE",
  "period": 1,
  "periodLabel": "1° ORA",
  "subject": "INGLESE",
  "teacher": "E. NIFOSI",
  "description": "Virtual classmates",
  "revision": 1,
  "updatedAt": "2026-11-13T14:20:00Z"
}
```

`revision` vale `1` alla prima importazione e aumenta quando il docente modifica materia, professore o descrizione della stessa ora.

## Importazioni

### `GET /api/imports`

Storico paginato delle importazioni, dalla più recente.

| Parametro | Default | Descrizione |
| --- | --- | --- |
| `page` | `0` | Pagina. |
| `size` | `20` | Dimensione (1–100). |

Ogni elemento contiene `id`, `sourceCode`, `originalFilename`, `filenameSuffix`, `sha256`, `importedAt`, `assignmentCount`, `createdCount`, `updatedCount`, `unchangedCount`, `schoolYearStart`. I tre contatori indicano rispettivamente compiti nuovi, compiti esistenti aggiornati e compiti già presenti senza modifiche.

### `GET /api/imports/diary`

Storico paginato delle importazioni di diario, dalla più recente. Ogni elemento contiene gli stessi campi, con `entryCount` al posto di `assignmentCount`.

### `GET /api/imports/issues`

Anomalie paginate, ordinate per `lastSeenAt` decrescente.

| Parametro | Default | Descrizione |
| --- | --- | --- |
| `status` | tutte | `OPEN`, `RESOLVED` o `ALL`. |
| `page` | `0` | Pagina. |
| `size` | `20` | Dimensione (1–100). |

Esempio:

```bash
curl 'http://localhost:8200/api/imports/issues?status=OPEN'
```

### `GET /api/imports/status`

Stato corrente del monitor e riepilogo del database.

```json
{
  "monitoringEnabled": true,
  "state": "ACTIVE",
  "lastScanAt": "2026-11-13T13:14:36.399Z",
  "lastSuccessfulImportAt": "2026-11-13T13:14:34.383Z",
  "totalImports": 3,
  "totalAssignments": 6,
  "totalDiaryImports": 1,
  "totalDiaryEntries": 5,
  "openIssues": 0,
  "activePushSubscriptions": 0,
  "directoryReadable": true,
  "lastError": null,
  "generatedAt": "2026-11-13T13:14:38.389Z"
}
```

Stati del monitor: `STARTING`, `ACTIVE`, `ERROR`, `DISABLED` (a seconda dello stato interno del servizio).

`totalAssignments` e `totalDiaryEntries` contano i documenti unici in `homeworkAssignments` e `diaryEntries`, non le righe presenti nei file importati.

## Notifiche

### `GET /api/notifications/public-key`

Restituisce la chiave pubblica VAPID e lo stato delle notifiche.

```json
{
  "publicKey": "BLFucg-kULtmo2GXeaAs5HjRKFdLr9Xju_GxjmLsZsyMQyVwbASAcgLWYB8eB2mykGXo17CcYfGNPaVYEVvl5Fk",
  "enabled": true,
  "subject": "mailto:mida@localhost"
}
```

### `GET /api/notifications/preferences`

```json
{
  "enabled": true,
  "newAssignmentsEnabled": true,
  "remindersEnabled": true,
  "reminderDaysBefore": 1,
  "reminderTime": "18:00",
  "timezone": "Europe/Rome",
  "updatedAt": "2026-11-13T13:14:00.003Z"
}
```

### `PUT /api/notifications/preferences`

Corpo:

```json
{
  "enabled": true,
  "newAssignmentsEnabled": true,
  "remindersEnabled": true,
  "reminderDaysBefore": 1,
  "reminderTime": "18:00",
  "timezone": "Europe/Rome"
}
```

Vincoli: `reminderDaysBefore` tra 0 e 14, `reminderTime` nel formato `HH:mm`, `timezone` un identificatore IANA valido.

### `POST /api/notifications/subscriptions`

Registra una sottoscrizione Web Push del browser o la riattiva se l'endpoint esiste già.

```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "p256dh": "B...",
  "auth": "..."
}
```

Risposta:

```json
{
  "id": "6af70f...",
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "active": true
}
```

L'endpoint deve essere HTTPS e pubblico. Le chiavi `p256dh` e `auth` vengono decodificate e validate.

### `DELETE /api/notifications/subscriptions`

Disattiva una sottoscrizione. Risponde `204 No Content`.

```json
{ "endpoint": "https://fcm.googleapis.com/fcm/send/..." }
```

### `POST /api/notifications/test`

Invia una notifica di prova a tutte le sottoscrizioni attive. Non richiede corpo.

```json
{
  "sent": 1,
  "failed": 0,
  "expired": 0,
  "subscriptions": 1
}
```

Se non ci sono dispositivi sottoscritti, `subscriptions` è `0` e il frontend mostra un messaggio informativo.

## Posta Gmail

### `GET /api/google/gmail/status`

```json
{
  "configured": true,
  "connected": true,
  "gmailEnabled": true,
  "gmailQuery": "from:didup",
  "lastSyncAt": "2026-11-18T10:15:00Z",
  "lastSyncStatus": "OK",
  "lastError": null,
  "syncInProgress": false,
  "lastFetchedCount": 12,
  "lastNewCount": 2,
  "lastAttachmentCount": 15,
  "lastNewAttachmentCount": 3
}
```

`lastSyncStatus`: `NEVER`, `SYNCING`, `OK`, `ERROR`.

### `POST /api/google/gmail/sync`

Avvia una sincronizzazione Gmail in background. Se arrivano nuove email e Classroom è collegata, avvia anche la sincronizzazione Classroom.

### `GET /api/mail/emails`

Email sincronizzate da Gmail, ordinate dalla più recente. Parametri: `from`, `q`, `page`, `size`.

```json
{
  "content": [
    {
      "id": "0c8f...",
      "from": "DidUP Family <didup@example.com>",
      "subject": "Compiti di matematica",
      "snippet": "Svolgere gli esercizi...",
      "receivedAt": "2026-11-18T09:30:00Z",
      "hasAttachments": true,
      "attachmentCount": 1
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### `GET /api/mail/emails/{id}`

Restituisce l'email completa con `from`, `to`, `cc`, `subject`, `bodyText`, `receivedAt`, `labels` e `attachments`. Ogni allegato contiene `downloadUrl` (assente se non archiviato) e `importStatus`:

- `IMPORTED`, `ALREADY_IMPORTED`: allegato DidUP importato nei compiti o nel diario;
- `NOT_DIDUP`: testo non riconosciuto;
- `STORED`: archiviato e scaricabile;
- `FAILED`, `TOO_LARGE`: import o download non riuscito.

### `GET /api/attachments/{id}`

Scarica un allegato archiviato in GridFS. La risposta ha `Content-Disposition: attachment` con il nome originale. L'identificativo è opaco e non corrisponde al nome del file.

## Google Classroom

### `GET /api/google/client`

```json
{
  "configured": true,
  "clientId": "1234567890-abc.apps.googleusercontent.com",
  "projectId": "mida-project",
  "redirectUri": "http://localhost:8200/api/google/oauth/callback",
  "importedAt": "2026-11-18T10:00:00Z"
}
```

`configured` è `false` finché non viene importato un JSON valido.

### `POST /api/google/client`

```json
{ "json": "{ ...contenuto del file scaricato da Google Cloud... }" }
```

Valida `client_id`, `client_secret` e un redirect URI che termini con `/api/google/oauth/callback`, genera una chiave UUID, cifra il JSON e lo salva. Scollega l'eventuale account Google collegato e restituisce lo stesso DTO di `GET /api/google/client`. Risposta `400` se il JSON non è valido.

### `DELETE /api/google/client`

Scollega l'account e rimuove le credenziali cifrate. Risposta `204`.

### `GET /api/google/status`

```json
{
  "configured": true,
  "connected": true,
  "accountEmail": "studente@scuola.example",
  "connectedAt": "2026-11-18T10:00:00Z",
  "scopes": ["https://www.googleapis.com/auth/classroom.courses.readonly"],
  "lastSyncAt": "2026-11-18T10:15:00Z",
  "lastSyncStatus": "OK",
  "lastError": null,
  "syncInProgress": false,
  "classroomEnabled": true,
  "lastClassroomItemCount": 8,
  "lastNewClassroomItemCount": 1,
  "settingsUpdatedAt": "2026-11-18T09:00:00Z"
}
```

### `GET /api/google/connect`

Reindirizza a Google per l'autorizzazione Classroom. Se il client OAuth non è importato reindirizza a `/impostazioni?google=not-configured`.

### `GET /api/google/oauth/callback`

Callback OAuth. Al termine reindirizza a `/impostazioni?google=connected` oppure `/impostazioni?google=error&message=...` e avvia la prima sincronizzazione Classroom.

### `POST /api/google/disconnect`

Revoca il token e rimuove la connessione. I dati Classroom già scaricati restano nel database.

### `POST /api/google/sync`

Avvia una sincronizzazione Classroom in background. Risposta `202`:

```json
{ "started": true, "status": "started" }
```

### `PUT /api/google/settings`

```json
{
  "gmailQuery": "from:didup",
  "gmailEnabled": true,
  "classroomEnabled": true
}
```

Restituisce lo stesso DTO di `GET /api/google/status`. Le modifiche a Gmail si vedono in `GET /api/google/gmail/status`.

### `GET /api/google/classroom`

Elementi Classroom, ordinati per ultima modifica. Parametri: `courseId`, `type` (`COURSEWORK`, `MATERIAL`, `ANNOUNCEMENT`), `q`, `page`, `size`. Ogni elemento include `title`, `description`, `courseName`, `itemType`, `dueDate`, `materialCount` e `alternateLink`.

### `GET /api/google/classroom/{id}`

Elemento completo con `description`, `alternateLink`, `dueDate`, `dueTime` e `materials`. Ogni materiale contiene `type`, `status`, `downloadUrl` o `url`.

### `GET /api/google/classroom/courses`

Elenco dei corsi presenti, utile per il filtro.

## Server-Sent Events


### `GET /api/events`

Apre uno stream `text/event-stream`. Il client deve gestire la riconnessione automatica.

Eventi:

| Evento | Payload |
| --- | --- |
| `connected` | `null`; conferma l'apertura dello stream. |
| `import.completed` | `importId`, `sourceCode`, `filename`, `assignmentCount`, `createdCount`, `updatedCount`, `unchangedCount`, `subjects`, `subjectCounts`, `earliestDueDate`, `fileDeleted`. |
| `diary.imported` | `importId`, `sourceCode`, `filename`, `entryCount`, `createdCount`, `updatedCount`, `unchangedCount`, `subjects`, `activityDates`. |
| `homework.updated` | `assignmentId`, `completed`, `subject`, `dueDate`. |
| `import.issue` | DTO dell'anomalia creata o risolta. |
| `import.status` | Snapshot dello stato del monitor. |
| `mail.sync` | `fetched`, `newEmails`, `attachments`, `newAttachments`, `errors`. |
| `google.sync` | `classroomItems`, `newClassroomItems`, `classroomMaterials`, `newClassroomMaterials`, `errors`. |

Esempio di frame:

```text
id:14
event:import.completed
data:{"type":"import.completed","at":"2026-11-13T13:23:00.589Z","data":{"importId":"...","assignmentCount":1,"createdCount":1,"updatedCount":0,"unchangedCount":0,"subjectCounts":{"STORIA":1}}}
```

Ogni messaggio ha anche un commento periodico `:heartbeat` per mantenere viva la connessione attraverso nginx.

## OpenAPI

- JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

Sono raggiungibili tramite nginx su `http://localhost:8200` e, se configurato, su `https://maximilianclassroom.com:4443`; il backend non è esposto direttamente sull'host.
