# Database MongoDB

MIDA usa MongoDB 8 con database `mida`. Il volume Docker è `mida-mongodb-data`; senza quel volume si perdono compiti, storico, preferenze e chiavi VAPID.

## Collezioni

| Collezione | Scopo |
| --- | --- |
| `homeworkAssignments` | Un documento per compito, con impronte semantiche, revisione e stato di completamento. |
| `diaryEntries` | Un documento per ogni ora del diario, identificato dalla coppia data e ora. |
| `homeworkImports` | Storico dei file DidUP di compiti, con contatori nuovi/aggiornati/invariati. |
| `diaryImports` | Storico dei file DidUP di diario, con contatori nuovi/aggiornati/invariati. |
| `emailMessages` | Email Gmail sincronizzate, con corpo testuale. |
| `emailAttachments` | Metadati degli allegati Gmail e riferimento GridFS. |
| `classroomItems` | Compiti, materiali e annunci Google Classroom. |
| `classroomMaterials` | File Drive, link, video e moduli collegati agli elementi Classroom. |
| `gmailSyncState` | Stato e contatori dell'ultima sincronizzazione Gmail. |
| `googleClientCredentials` | JSON del client OAuth cifrato e chiave UUID generata da MIDA. |
| `googleConnection` | Account Google collegato e refresh token cifrato. |
| `googleSettings` | Ricerca Gmail e sincronizzazioni abilitate. |
| `googleOauthStates` | Stati OAuth monouso con scadenza TTL. |
| `fs.files` / `fs.chunks` | Contenuto binario degli allegati in GridFS. |
| `importIssues` | Anomalie di importazione, deduplicate per fingerprint. |
| `pushSubscriptions` | Sottoscrizioni Web Push dei dispositivi. |
| `notificationPreferences` | Preferenze globali di notifica (documento unico). |
| `notificationDeliveries` | Tentativi di consegna push e chiavi di deduplicazione. |
| `appSettings` | Impostazioni generate, incluse le chiavi VAPID se non fornite da ambiente. |

## `homeworkAssignments`

Ogni compito è un documento autonomo. L'identità è ricostruita dal contenuto, perché DidUP non fornisce un ID stabile per il singolo compito: la stessa assegnazione può comparire in più file con codici diversi.

```json
{
  "_id": "82aa8e1f-d222-4600-a28f-829b4f185d38",
  "contentHash": "b6f8...64 caratteri esadecimali...",
  "slotKey": "2c41...",
  "baseKey": "90de...",
  "descriptionHash": "77a0...",
  "assignedDate": "2026-11-12",
  "assignedDateRaw": "12 NOVEMBRE",
  "dueDate": "2026-11-20",
  "dueDateRaw": "VENERDÌ 20 NOVEMBRE",
  "subject": "ITALIANO",
  "teacher": "G. FAZIO",
  "description": "Antologia: esercizi su fotocopia \"Dal testo al genere\" e \"Dal testo alla realtà\".",
  "completed": false,
  "completedAt": null,
  "revision": 1,
  "importId": "6af70d3a68d59128bcb2a0ee",
  "sourceCode": "125BB5C551CC",
  "originalFilename": "text-125BB5C551CC.txt",
  "importedAt": "2026-11-13T13:14:34.341Z",
  "firstImportId": "6af70d3a68d59128bcb2a0ee",
  "firstSeenAt": "2026-11-13T13:14:34.341Z",
  "lastSeenAt": "2026-11-16T09:00:00.000Z",
  "updatedAt": "2026-11-13T13:14:34.341Z"
}
```

Campi:

| Campo | Tipo | Note |
| --- | --- | --- |
| `_id` | string UUID | Identificatore del compito, stabile per tutta la sua vita. |
| `contentHash` | string | SHA-256 di data assegnazione, consegna, materia, professore e descrizione normalizzati. Univoco. |
| `slotKey` | string | SHA-256 di data assegnazione, consegna, materia e professore. |
| `baseKey` | string | SHA-256 di data assegnazione, materia e professore, senza consegna. |
| `descriptionHash` | string | SHA-256 della sola descrizione normalizzata. |
| `assignedDate` | date | Data di assegnazione interpretata. |
| `assignedDateRaw` | string | Testo originale, es. `12 NOVEMBRE`. |
| `dueDate` | date | Data di consegna interpretata. |
| `dueDateRaw` | string | Testo originale, es. `VENERDÌ 20 NOVEMBRE`. |
| `subject` | string | Materia. |
| `teacher` | string | Professore. |
| `description` | string | Descrizione completa, anche multilinea. |
| `completed` | bool | Stato di completamento impostato dall'utente. |
| `completedAt` | date/null | Istante di completamento. |
| `revision` | int | `1` alla creazione; aumenta a ogni modifica reale del compito. |
| `importId` | string | Ultima importazione che ha creato o modificato il compito. |
| `sourceCode`, `originalFilename`, `importedAt` | — | Origine dell'ultima modifica. |
| `firstImportId` | string | Importazione in cui il compito è comparso per la prima volta; usata per notificare solo i compiti nuovi. |
| `firstSeenAt`, `lastSeenAt` | date | Primo e ultimo avvistamento del compito. |
| `updatedAt` | date | Ultima modifica reale di contenuto o scadenza. |

### Identità e revisione

La deduplicazione è conservativa:

- `contentHash` identico: nessuna scrittura, si aggiorna solo `lastSeenAt`;
- stessa `slotKey` e descrizione identica, estesa o con somiglianza Jaccard sufficiente (≥ 0,75): stesso compito, `revision` aumenta e `completed` resta invariato;
- stessa descrizione con `dueDate` diversa e un solo candidato per `baseKey`: scadenza spostata sullo stesso documento, `revision` aumenta;
- descrizioni diverse con numeri diversi (es. `pagina 10` e `pagina 11`): compiti distinti, anche a parità di materia e date.

Le esportazioni parziali non eliminano i compiti non presenti nel file. Questo evita di perdere dati quando DidUP esporta un sottoinsieme delle assegnazioni.

### Indici

| Nome | Campi | Tipo |
| --- | --- | --- |
| `ux_homework_assignments_content_hash` | `contentHash` | unique |
| `ix_homework_assignments_slot_key` | `slotKey` | normale |
| `ix_homework_assignments_base_key` | `baseKey` | normale |
| `ix_homework_assignments_description_hash` | `descriptionHash` | normale |
| `ix_homework_assignments_first_import` | `firstImportId` | normale |
| `ix_homework_assignments_due_date` | `dueDate` | normale |
| `ix_homework_assignments_assigned_date` | `assignedDate` | normale |
| `ix_homework_assignments_subject` | `subject` | normale |
| `ix_homework_assignments_teacher` | `teacher` | normale |
| `ix_homework_assignments_due_completed` | `dueDate`, `completed` | composto |
| `tx_homework_assignments_search` | `description`, `subject`, `teacher` | text |

Gli indici sono creati all'avvio da `MongoIndexInitializer` e dalle annotazioni delle entità. Se MongoDB non è raggiungibile, l'applicazione registra l'errore ma non perde i dati già presenti.

## `homeworkImports`

Un documento per ogni file di compiti importato. Non contiene più i compiti: conserva lo storico del file e i contatori dell'esito.

```json
{
  "_id": "6af70d3a68d59128bcb2a0ee",
  "sourceCode": "125BB5C551CC",
  "originalFilename": "text-125BB5C551CC.txt",
  "filenameSuffix": null,
  "sha256": "4b35ed1b3a677f8c267de25f392a6ece39212a6a8d6c278100c155b516c6ccff",
  "importedAt": "2026-11-13T13:14:34.341Z",
  "assignmentCount": 1,
  "createdCount": 1,
  "updatedCount": 0,
  "unchangedCount": 0,
  "schoolYearStart": 2026
}
```

| Campo | Tipo | Note |
| --- | --- | --- |
| `_id` | string UUID | ID dell'importazione. |
| `sourceCode` | string | Codice di 12 caratteri esadecimali, univoco nella collezione. |
| `originalFilename` | string | Nome del file originale. |
| `filenameSuffix` | int/null | Suffisso numerico del nome, solo informativo. |
| `sha256` | string | Impronta del contenuto del file. |
| `importedAt` | date | Istante di importazione. |
| `assignmentCount` | int | Compiti presenti nel file. |
| `createdCount` | int | Compiti nuovi creati. |
| `updatedCount` | int | Compiti esistenti aggiornati. |
| `unchangedCount` | int | Compiti già presenti senza modifiche. |
| `schoolYearStart` | int | Primo anno dell'anno scolastico usato dal parser. |

### Indici

| Nome | Campi | Tipo |
| --- | --- | --- |
| `ux_homework_imports_source_code` | `sourceCode` | unique |
| `ix_homework_imports_sha256` | `sha256` | normale |
| `ix_homework_imports_imported_at` | `importedAt` | normale |

## `diaryEntries`

Un documento per ogni ora del diario. L'identità è la coppia `activityDate + period`: una nuova esportazione dello stesso giorno aggiorna la riga esistente invece di duplicarla.

```json
{
  "_id": "0de1c4f4-6e57-4b51-8b50-917a7f1f4f11",
  "activityDate": "2026-11-13",
  "activityDateRaw": "13 NOVEMBRE",
  "period": 1,
  "periodLabel": "1° ORA",
  "subject": "INGLESE",
  "teacher": "E. NIFOSI",
  "description": "Virtual classmates",
  "contentHash": "a4d1...",
  "revision": 1,
  "importId": "6af7201a68d59128bcb2a101",
  "sourceCode": "D1A2B3C4D5E6",
  "originalFilename": "text-D1A2B3C4D5E6-1.txt",
  "importedAt": "2026-11-13T14:20:00.000Z",
  "firstImportId": "6af7201a68d59128bcb2a101",
  "firstSeenAt": "2026-11-13T14:20:00.000Z",
  "lastSeenAt": "2026-11-13T14:20:00.000Z",
  "updatedAt": "2026-11-13T14:20:00.000Z"
}
```

| Campo | Tipo | Note |
| --- | --- | --- |
| `_id` | string UUID | Identificatore stabile dell'attività. |
| `activityDate` | date | Data dell'attività interpretata con l'anno scolastico. |
| `activityDateRaw` | string | Testo originale, es. `13 NOVEMBRE`. |
| `period` | int | Numero dell'ora, es. `1`, `2`, `4`. |
| `periodLabel` | string | Etichetta originale, es. `1° ORA`. |
| `subject` | string | Materia. |
| `teacher` | string | Professore. |
| `description` | string | Attività svolta, anche multilinea. Può essere vuota (ora senza descrizione). |
| `contentHash` | string | SHA-256 di data, ora, materia, professore e descrizione normalizzati. |
| `revision` | int | `1` alla creazione; aumenta se cambia materia, professore o descrizione della stessa ora. |
| `importId` | string | Ultima importazione che ha creato o modificato la riga. |
| `sourceCode`, `originalFilename`, `importedAt` | — | Origine dell'ultima modifica. |
| `firstImportId` | string | Prima importazione in cui è comparsa l'ora. |
| `firstSeenAt`, `lastSeenAt`, `updatedAt` | date | Primo avvistamento, ultimo avvistamento e ultima modifica reale. |

### Indici

| Nome | Campi | Tipo |
| --- | --- | --- |
| `ux_diary_entries_date_period` | `activityDate`, `period` | unique composto |
| `ix_diary_entries_activity_date` | `activityDate` | normale |
| `ix_diary_entries_subject` | `subject` | normale |
| `ix_diary_entries_teacher` | `teacher` | normale |
| `tx_diary_entries_search` | `description`, `subject`, `teacher` | text |

## `diaryImports`

Un documento per ogni file di diario importato, con gli stessi contatori dello storico compiti.

```json
{
  "_id": "6af7201a68d59128bcb2a101",
  "sourceCode": "D1A2B3C4D5E6",
  "originalFilename": "text-D1A2B3C4D5E6-1.txt",
  "filenameSuffix": 1,
  "sha256": "a2f7...",
  "importedAt": "2026-11-13T14:20:00.000Z",
  "entryCount": 5,
  "createdCount": 5,
  "updatedCount": 0,
  "unchangedCount": 0,
  "schoolYearStart": 2026
}
```

### Indici

| Nome | Campi | Tipo |
| --- | --- | --- |
| `ux_diary_imports_source_code` | `sourceCode` | unique |
| `ix_diary_imports_sha256` | `sha256` | normale |
| `ix_diary_imports_imported_at` | `importedAt` | normale |

## `emailMessages`

Una email Gmail conservata per la consultazione. L'HTML non viene memorizzato: `bodyText` contiene la versione testuale.

```json
{
  "_id": "0c8f3a6e-...",
  "providerMessageId": "18f2...",
  "provider": "GMAIL",
  "mailbox": "GMAIL",
  "uid": null,
  "threadId": "18f2...",
  "from": "DidUP Family <didup@example.com>",
  "to": "studente@scuola.example",
  "cc": null,
  "subject": "Compiti di matematica",
  "snippet": "Svolgere gli esercizi...",
  "bodyText": "Svolgere gli esercizi pagina 111.",
  "receivedAt": "2026-11-18T09:30:00Z",
  "labels": ["INBOX"],
  "hasAttachments": true,
  "attachmentsComplete": true,
  "attachmentCount": 1,
  "firstSeenAt": "2026-11-18T10:00:00Z",
  "lastSeenAt": "2026-11-18T10:00:00Z"
}
```

`providerMessageId` è l'ID Gmail del messaggio ed è univoco. `attachmentsComplete=false` indica che una sincronizzazione precedente non è riuscita a completare tutti gli allegati: il messaggio viene ritentato.

## `emailAttachments`

```json
{
  "_id": "9f4c...",
  "emailMessageId": "0c8f3a6e-...",
  "providerAttachmentId": "ANGjdJ...",
  "filename": "text-ABCDEF123456.txt",
  "mimeType": "text/plain",
  "size": 1842,
  "sha256": "5c2e...",
  "gridFsId": "671f...",
  "inline": false,
  "contentId": null,
  "importStatus": "IMPORTED",
  "importMessage": "1 nuovo compito",
  "createdAt": "2026-11-18T10:00:00Z"
}
```

Indici: univoco composto `emailMessageId + providerAttachmentId`, più `emailMessageId` e `sha256`.

## `classroomItems`

```json
{
  "_id": "5b1e...",
  "classroomKey": "course-1:COURSEWORK:work-1",
  "courseId": "course-1",
  "courseName": "Matematica",
  "courseSection": "3A",
  "itemType": "COURSEWORK",
  "title": "Esercizi sulle frazioni",
  "description": "Completare gli esercizi dal libro.",
  "state": "PUBLISHED",
  "alternateLink": "https://classroom.google.com/c/...",
  "dueDate": "2026-11-20",
  "dueTime": "09:00",
  "creationTime": "2026-11-18T08:00:00Z",
  "updateTime": "2026-11-18T08:00:00Z",
  "materialCount": 1,
  "firstSeenAt": "2026-11-18T10:00:00Z",
  "lastSeenAt": "2026-11-18T10:00:00Z",
  "updatedAt": "2026-11-18T10:00:00Z"
}
```

`itemType`: `COURSEWORK`, `MATERIAL`, `ANNOUNCEMENT`. Indice univoco su `classroomKey`.

## `classroomMaterials`

```json
{
  "_id": "c7a2...",
  "itemId": "5b1e...",
  "type": "DRIVE_FILE",
  "externalKey": "1AbC...",
  "title": "Foglio esercizi",
  "url": "https://drive.google.com/file/d/...",
  "filename": "Foglio esercizi.pdf",
  "mimeType": "application/pdf",
  "size": 48213,
  "gridFsId": "671f...",
  "sha256": "a1b2...",
  "status": "DOWNLOADED",
  "error": null,
  "createdAt": "2026-11-18T10:00:00Z"
}
```

`type`: `DRIVE_FILE`, `LINK`, `YOUTUBE_VIDEO`, `FORM`, `OTHER`. `status`: `DOWNLOADED`, `LINK_ONLY`, `ERROR`, `TOO_LARGE`, `SKIPPED`. Indice univoco composto `itemId + externalKey`.

## `gmailSyncState`

Documento unico (`_id = "gmail"`) con lo stato dell'ultima sincronizzazione Gmail: `lastSyncAt`, `lastSyncStatus`, `lastError` e i contatori `lastFetchedCount`, `lastNewCount`, `lastAttachmentCount`, `lastNewAttachmentCount`.

## `googleClientCredentials`

Documento unico (`_id = "default"`) con il JSON del client OAuth scaricato da Google Cloud:

| Campo | Descrizione |
| --- | --- |
| `projectId` | ID del progetto Google Cloud, se presente nel JSON. |
| `clientId` | Client ID estratto dal JSON, usato per lo stato e le API. |
| `redirectUri` | Redirect URI che termina con `/api/google/oauth/callback`. |
| `encryptionKey` | UUID generato da MIDA al momento dell'import. |
| `credentialsJson` | JSON completo (incluso `client_secret`) cifrato AES-256-GCM. |
| `createdAt` / `updatedAt` | Date di creazione e ultimo aggiornamento. |

Il segreto non viene mai restituito dalle API né salvato in chiaro. Reimportare un JSON sostituisce il documento e scollega la connessione precedente.

## `googleConnection`, `googleSettings`, `googleOauthStates`

`googleConnection` è un documento unico (`_id = "google"`) con `accountEmail` e `refreshToken` cifrati AES-256-GCM, `scopes`, date di connessione e ultima sincronizzazione, contatori e ultimo errore. I token e l'indirizzo in chiaro non vengono mai restituiti dalle API.

`googleSettings` è un documento unico (`_id = "default"`) con `gmailQuery`, `gmailEnabled`, `classroomEnabled` e `updatedAt`.

`googleOauthStates` contiene gli stati OAuth monouso con TTL su `expiresAt`. Dopo il callback lo stato viene eliminato subito.

## Migrazione dal modello incorporato

Al primo avvio della nuova versione, `LegacyDataMigrator` legge gli array `assignments` e `entries` ancora presenti nei documenti di importazione, li importa nelle nuove collezioni conservando gli ID originali e lo stato `completed`, quindi rimuove gli array. La migrazione è idempotente: ogni documento di importazione viene marcato (`assignmentsMigrated` o `entriesMigrated`) e ripulito singolarmente. Se un documento contiene dati non interpretabili resta intatto e viene ritentato al riavvio, senza perdere nulla.

## `importIssues`

```json
{
  "_id": "6af70d5c68d59128bcb2a0f1",
  "fingerprint": "DUPLICATE_CONFLICT|text-125BB5C551CC-2.txt|e5488af6...",
  "type": "DUPLICATE_CONFLICT",
  "status": "OPEN",
  "filename": "text-125BB5C551CC-2.txt",
  "sourceCode": "125BB5C551CC",
  "sha256": "e5488af6...",
  "description": "Il codice è già stato importato con un contenuto diverso...",
  "firstSeenAt": "2026-11-13T13:15:08.481Z",
  "lastSeenAt": "2026-11-13T13:15:08.481Z",
  "attempts": 1,
  "resolvedAt": null
}
```

Tipi:

- `DIRECTORY_UNAVAILABLE`
- `FILE_TOO_LARGE`
- `READ_ERROR`
- `PARSE_ERROR`
- `VALIDATION_ERROR`
- `DUPLICATE_CONFLICT`
- `FILE_DELETION_FAILED`
- `FILE_CHANGED_DURING_IMPORT`
- `PERSISTENCE_ERROR`
- `UNEXPECTED_ERROR`

Stati: `OPEN`, `RESOLVED`.

`fingerprint` è univoca, così lo stesso problema sullo stesso contenuto non crea una nuova riga a ogni polling. Il contatore `attempts` aumenta quando il problema viene osservato di nuovo. Un'anomalia risolta può riaprirsi se lo stesso problema ricompare.

## `pushSubscriptions`

```json
{
  "_id": "6af70f...",
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "p256dh": "B...",
  "auth": "...",
  "userAgent": "Mozilla/5.0 ...",
  "active": true,
  "createdAt": "2026-11-13T13:14:00Z",
  "updatedAt": "2026-11-13T13:14:00Z",
  "lastSuccessAt": null,
  "lastFailureAt": null
}
```

`endpoint` è univoco. Una sottoscrizione scaduta non viene cancellata subito: viene marcata `active=false` e conservata per diagnostica.

## `notificationPreferences`

Documento unico con `_id = "default"`.

```json
{
  "_id": "default",
  "enabled": true,
  "newAssignmentsEnabled": true,
  "remindersEnabled": true,
  "reminderDaysBefore": 1,
  "reminderTime": "18:00",
  "timezone": "Europe/Rome",
  "updatedAt": "2026-11-13T13:14:00Z"
}
```

## `notificationDeliveries`

Rappresenta un tentativo logico per dispositivo.

```json
{
  "_id": "6af70f...",
  "dedupKey": "due-reminder:2026-11-18:6af70e...",
  "type": "DUE_REMINDER",
  "subscriptionId": "6af70e...",
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "importId": null,
  "targetDate": "2026-11-18",
  "title": "MIDA — Compiti per domani",
  "body": "Hai 3 compiti da consegnare domani...",
  "status": "SENT",
  "createdAt": "2026-11-17T17:00:01Z",
  "sentAt": "2026-11-17T17:00:02Z",
  "error": null,
  "attempts": 1
}
```

Chiavi di deduplicazione:

- `new-assignments:<importId>:<subscriptionId>`
- `due-reminder:<targetDate>:<subscriptionId>`
- `test:<uuid>:<subscriptionId>`

`dedupKey` ha un indice unique. Prima dell'invio viene inserita una riga `PENDING`; se l'inserimento fallisce per duplicato, la notifica è già stata gestita e non viene inviata di nuovo. Questo rende la pipeline idempotente anche dopo un riavvio.

Status: `PENDING`, `SENT`, `FAILED`, `EXPIRED`.

## `appSettings`

Contiene le chiavi VAPID generate automaticamente.

```json
{
  "_id": "vapid",
  "publicKey": "B...",
  "privateKey": "...",
  "createdAt": "2026-11-13T13:14:00Z"
}
```

La chiave privata non viene mai esposta dalle API. Se si forniscono `VAPID_PUBLIC_KEY` e `VAPID_PRIVATE_KEY` tramite ambiente, il documento non viene usato.

## Query di lettura

Le API leggono direttamente i documenti autonomi; non serve più `$unwind`:

```javascript
db.homeworkAssignments.find({
  dueDate: { $gte: ISODate("2026-11-01"), $lte: ISODate("2026-11-30") }
}).sort({ dueDate: 1, subject: 1, teacher: 1, _id: 1 })
```

Il diario di un giorno è altrettanto semplice:

```javascript
db.diaryEntries.find({ activityDate: ISODate("2026-11-13") }).sort({ period: 1 })
```

L'indice univoco composto su `activityDate + period` garantisce una sola riga per ora anche in caso di importazioni concorrenti.

## Backup e ripristino

Il modo più semplice è fermare lo stack e copiare il volume Docker, oppure esportare il database:

```bash
docker exec mida-mongodb mongodump \
  --db mida \
  --archive=/tmp/mida.archive --gzip
docker cp mida-mongodb:/tmp/mida.archive ./mida-mongo-backup.archive
```

Per il ripristino:

```bash
docker compose stop mida-backend
docker cp ./mida-mongo-backup.archive mida-mongodb:/tmp/mida.archive
docker exec mida-mongodb mongorestore \
  --drop --db mida \
  --gzip --archive=/tmp/mida.archive
docker compose start mida-backend
```

Non eseguire `docker compose down -v` su un'installazione con dati che vuoi conservare.
