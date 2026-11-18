# Gmail API e Google Classroom

MIDA usa **Gmail API** per scaricare email e allegati e **Google Classroom API** per compiti, materiali e annunci. Entrambe usano OAuth 2.0 e lo stesso account Google Workspace. La password Google non viene mai richiesta né salvata.

La sincronizzazione Classroom non ha un polling proprio: parte dopo il collegamento OAuth, quando Gmail trova almeno una nuova email, oppure manualmente.

## 1. Quote API gratuite

L'uso di MIDA è molto sotto le soglie gratuite.

### Gmail API

| Limite | Valore |
| --- | --- |
| Unità al minuto per progetto | 1.200.000 |
| Unità al minuto per utente | 6.000 |
| Soglia gratuita giornaliera per progetto | 80.000.000 unità |

Costi: `messages.list` 5 unità, `messages.get` 20 (60 in formato full), `messages.attachments.get` 20.

### Classroom API

| Limite | Valore |
| --- | --- |
| Richieste al giorno per client | 4.000.000 |
| Richieste al minuto per client | 3.000 |
| Richieste al minuto per utente | 1.200 |

### Drive API

| Limite | Valore |
| --- | --- |
| Unità al minuto per progetto | 1.000.000 |
| Unità al minuto per utente | 325.000 |
| Soglia gratuita giornaliera per progetto | 400.000.000 unità |
| Egress giornaliero | 1 TB |

Una sincronizzazione Gmail con 50 messaggi nuovi consuma al massimo circa 3.000 unità (5 + 50×60), molto sotto le 6.000 unità/minuto per utente. Una sincronizzazione Classroom con pochi corsi consuma poche decine di richieste. Non serve billing per questi volumi.

## 2. Configurare Google Cloud

1. Apri <https://console.cloud.google.com/> e crea/seleziona un progetto.
2. Abilita **Gmail API**, **Google Classroom API** e **Google Drive API**.
3. Configura la schermata di consenso OAuth:
   - **Internal** se usi un account Workspace scolastico;
   - **External** solo se necessario; in modalità test i refresh token scadono dopo 7 giorni.
4. Aggiungi gli scope elencati nella sezione 3.
5. Crea un client OAuth **Web application** con redirect URI:
   `http://localhost:8200/api/google/oauth/callback`, poi scarica il JSON: va importato in MIDA (sezione 4).

## 3. Scope richiesti

| Scope | Uso |
| --- | --- |
| `openid`, `email`, `profile` | Identificare l'account collegato. |
| `gmail.readonly` | Leggere messaggi, corpo e allegati. |
| `classroom.courses.readonly` | Elencare i corsi attivi. |
| `classroom.coursework.me.readonly` | Leggere i compiti dello studente. |
| `classroom.courseworkmaterials.readonly` | Leggere i materiali dei corsi. |
| `classroom.announcements.readonly` | Leggere gli annunci. |
| `drive.readonly` | Scaricare i file allegati ai materiali Classroom. |

## 4. Importare il client in MIDA

Non servono credenziali Google nel file `.env`. Apri **Impostazioni → Account Google**, premi **Importa JSON** e seleziona il file scaricato da Google Cloud. La stessa procedura è spiegata nell'app alla pagina **Guida Google** (`/guida-google`).

Al momento dell'import MIDA:

1. valida che il JSON contenga `client_id`, `client_secret` e un redirect URI che termini con `/api/google/oauth/callback`;
2. genera una chiave UUID con `UUID.randomUUID()`;
3. cifra il JSON completo con AES-256-GCM e lo salva nella collection `googleClientCredentials`, documento unico `_id = "default"`;
4. legge da quel documento client ID, client secret e redirect URI per OAuth, refresh token e chiamate API.

Dopo l'import premi **Collega account Google**. Se importi un nuovo JSON, la connessione precedente viene scollegata: dovrai autorizzare di nuovo l'account. **Rimuovi client** elimina le credenziali cifrate e scollega l'account; email e materiali già scaricati restano in MIDA.

## 5. Ricerca Gmail

La ricerca predefinita è:

```text
from:didup
```

Al primo collegamento di un account Workspace, se non esiste già una configurazione, MIDA imposta automaticamente:

```text
from:<dominio-della-scuola> OR from:didup
```

Ad esempio `from:icscondove.it OR from:didup`. Per scaricare tutta la posta in arrivo puoi impostare `in:inbox`, tenendo presente che entreranno anche email personali e avvisi Google.

Può essere modificata in **Impostazioni → Account Google → Ricerca Gmail**. Esempi:

```text
from:didup
from:(didup OR "DidUP Family")
subject:compiti
has:attachment from:didup
```

La ricerca viene passata direttamente a Gmail. MIDA scarica al massimo `GOOGLE_GMAIL_MAX_RESULTS` messaggi per sincronizzazione (50 di default).

La sincronizzazione:

1. elenca i messaggi che corrispondono alla ricerca;
2. salta quelli già completi;
3. scarica il messaggio in formato `full`;
4. estrae corpo testuale e allegati;
5. archivia gli allegati in GridFS;
6. se un allegato è un TXT DidUP valido, lo importa nei compiti o nel diario.

Gli allegati non DidUP restano disponibili nella pagina **Posta** e scaricabili.

Per Classroom, la scheda **Classroom** mostra titolo e anteprima della descrizione; i file Drive allegati ai materiali e agli annunci vengono scaricati in GridFS e sono scaricabili dal dettaglio. Il limite predefinito per singolo file è 100 MB (`GOOGLE_MAX_ATTACHMENT_SIZE_BYTES`).

## 6. Quando viene aggiornata Classroom

MIDA non interroga Classroom a ogni polling Gmail. La sincronizzazione Classroom parte:

- dopo il collegamento OAuth;
- quando la sincronizzazione Gmail trova almeno una nuova email;
- quando premi **Sincronizza Classroom**;
- quando premi **Sincronizza email** e arrivano nuove email.

Se non arrivano email, Classroom non cambia finché non premi il pulsante manuale. È una scelta per ridurre le chiamate API.

## 7. Dati e privacy

MIDA conserva:

- `emailMessages`: mittente, destinatari, oggetto, corpo testuale, data, etichette;
- `emailAttachments`: metadati e riferimento GridFS;
- `classroomItems` / `classroomMaterials`: compiti, materiali, annunci e file Drive;
- `gmailSyncState`: stato e contatori dell'ultima sincronizzazione Gmail;
- `googleClientCredentials`: JSON del client OAuth cifrato, chiave UUID e metadati del progetto;
- `googleConnection`: email account cifrata, scope e refresh token cifrato;
- `fs.files` / `fs.chunks`: contenuto binario degli allegati.

L'HTML originale delle email non viene conservato né renderizzato: viene convertito in testo.

MIDA non usa password: l'accesso avviene con OAuth e la password Google non viene mai vista.

## 8. Sicurezza

- Il JSON del client OAuth, il refresh token Google e l'indirizzo dell'account sono cifrati AES-256-GCM.
- La chiave AES è un UUID generato da MIDA e salvato nello stesso documento `googleClientCredentials`: non va configurata a mano.
- L'access token resta solo in memoria.
- MIDA non ha autenticazione utente: non esporla su Internet.
- MongoDB contiene email e allegati: non pubblicare la sua porta sull'host.

## 9. Risoluzione dei problemi

### `redirect_uri_mismatch`

Il redirect URI contenuto nel JSON importato deve coincidere esattamente con quello registrato in Google Cloud, compresi schema, porta e path. Se cambi porta o dominio, aggiorna il client in Google Cloud, scarica il nuovo JSON e reimportalo da Impostazioni.

### Il JSON viene rifiutato

MIDA accetta solo JSON con `client_id`, `client_secret` e un redirect URI che termini con `/api/google/oauth/callback`. Aggiungi l'URI al client OAuth su Google Cloud e scarica di nuovo il file.

### `invalid_grant`

Il refresh token è scaduto o revocato. Premi **Ricollega** nella pagina Posta o in Impostazioni.

### Nessuna email

- Controlla la ricerca Gmail in Impostazioni.
- Verifica che `GOOGLE_GMAIL_ENABLED=true`.
- Premi **Sincronizza email**.
- Controlla `lastError` e i log: `docker compose logs -f mida-backend`.

### Classroom non si aggiorna

- Verifica che l'account Google sia collegato.
- La sincronizzazione parte solo con nuove email o manualmente: premi **Sincronizza Classroom**.
- Controlla `lastError` nella pagina Posta.

### 403 su Gmail, Classroom o Drive

Manca uno scope, l'amministratore del dominio li ha bloccati, oppure l'account non ha accesso alla risorsa. Controlla gli scope nella schermata di consenso e i log.

## 10. Variabili d'ambiente

| Variabile | Default | Descrizione |
| --- | --- | --- |
| `GOOGLE_ENABLED` | `true` | Abilita l'integrazione Google. |
| `GOOGLE_GMAIL_ENABLED` | `true` | Abilita Gmail. |
| `GOOGLE_GMAIL_QUERY` | `from:didup` | Ricerca Gmail iniziale, modificabile da Impostazioni. |
| `GOOGLE_GMAIL_MAX_RESULTS` | `50` | Messaggi massimi per sync (1–500). |
| `GOOGLE_GMAIL_SYNC_INTERVAL_MS` | `300000` | Intervallo polling Gmail (5 minuti). |
| `GOOGLE_GMAIL_INITIAL_DELAY_MS` | `30000` | Attesa prima del primo sync Gmail. |
| `GOOGLE_CLASSROOM_ENABLED` | `true` | Abilita Classroom. |
| `GOOGLE_CLASSROOM_ANNOUNCEMENTS` | `true` | Include gli annunci. |
| `GOOGLE_MAX_ATTACHMENT_SIZE_BYTES` | `104857600` | Limite per allegato/file Drive (100 MB). |
