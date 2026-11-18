# MIDA

**Monitoraggio Integrato dei Doveri Assegnati**

MIDA è un'applicazione web self-hosted per gestire i compiti e il diario di classe senza copiarli a mano. Legge i file TXT esportati da **DidUP Family**, li interpreta, li salva in MongoDB e li mostra in una PWA responsive. Opzionalmente può collegarsi a **Gmail** e **Google Classroom** per archiviare email, allegati e materiali dei corsi.

MIDA è pensata per un singolo nucleo familiare: non richiede un account, non usa servizi cloud propri e, se non abiliti l'integrazione Google, non invia dati a servizi esterni. Tutto gira con Docker Compose sul computer o sul server di casa.

## Indice

1. [Che cos'è MIDA](#1-che-cosè-mida)
2. [Come funziona](#2-come-funziona)
3. [Funzionalità](#3-funzionalità)
4. [Requisiti](#4-requisiti)
5. [Installazione](#5-installazione)
6. [Compiti e diario: come si condividono](#6-compiti-e-diario-come-si-condividono)
7. [Formato dei file DidUP](#7-formato-dei-file-didup)
8. [Uso dell'app](#8-uso-dellapp)
9. [Notifiche push](#9-notifiche-push)
10. [Gmail e Google Classroom](#10-gmail-e-google-classroom)
11. [API REST, OpenAPI e SSE](#11-api-rest-openapi-e-sse)
12. [Database, backup e ripristino](#12-database-backup-e-ripristino)
13. [Proxy HTTPS su maximilianclassroom.com](#13-proxy-https-su-maximilianclassroomcom)
14. [Configurazione](#14-configurazione)
15. [Sicurezza e limiti](#15-sicurezza-e-limiti)
16. [Manutenzione e aggiornamento](#16-manutenzione-e-aggiornamento)
17. [Test](#17-test)
18. [Risoluzione dei problemi](#18-risoluzione-dei-problemi)
19. [Struttura del progetto](#19-struttura-del-progetto)
20. [Documentazione di dettaglio](#20-documentazione-di-dettaglio)

## 1. Che cos'è MIDA

MIDA nasce per eliminare la copia manuale dei compiti da DidUP Family. Il flusso è semplice:

1. La scuola pubblica compiti e attività del diario su **DidUP Family**.
2. Tu esporti o condividi i file TXT da DidUP e li salvi nella cartella `Downloads`.
3. MIDA rileva i file, li interpreta e salva compiti e diario in MongoDB.
4. L'app mostra calendario, agenda, diario, dashboard e notifiche.

MIDA non accede a DidUP con username e password: non fa scraping e non ha credenziali della scuola. Legge solo i file che hai esportato.

Oltre ai compiti, MIDA può:

- importare il diario con le attività svolte ora per ora;
- inviare notifiche per i nuovi compiti e per le scadenze;
- leggere email Gmail e materiali Google Classroom, se configurati;
- archiviare gli allegati in MongoDB GridFS;
- funzionare come PWA su desktop e mobile;
- essere raggiunta via HTTPS sulla rete locale.

## 2. Come funziona

```text
Browser / PWA
   │
   ├── http://localhost:8200 ─────────────┐
   │                                      ▼
   │                            ┌─────────────────────┐
   │                            │ mida-frontend       │  Angular + nginx
   │                            └─────────┬───────────┘
   │                                      │ /api
   │                                      ▼
   │                            ┌─────────────────────┐
   └── https://…:4443 ────────▶ │ mida-backend        │  Spring Boot / Java
        (mida-proxy)            └─────────┬───────────┘
                                          │
                                          ▼
                                ┌─────────────────────┐
                                │ mida-mongodb        │  MongoDB 8
                                └─────────────────────┘

Cartella Downloads ──bind mount──▶ /imports del backend
```

### I quattro container

| Container | Ruolo | Porta sull'host |
| --- | --- | --- |
| `mida-mongodb` | Database MongoDB 8 con volume persistente. | Nessuna: solo rete interna. |
| `mida-backend` | Java 25 + Spring Boot 4.1.2. Monitora Downloads, importa, espone API, SSE e Web Push, gestisce Google. | Nessuna: solo rete interna. |
| `mida-frontend` | Build Angular 21 servita da nginx. | `127.0.0.1:8200` |
| `mida-proxy` | nginx con TLS che espone frontend e backend. | `4443` |

La rete Docker `mida-internal` collega i container. MongoDB e backend non sono raggiungibili direttamente dall'host.

### Flusso di importazione

1. Il monitor del backend controlla la cartella montata in `/imports` ogni pochi secondi.
2. Il file deve chiamarsi `text-<codice>.txt` e deve essere un file regolare, non un link simbolico.
3. MIDA attende che il file sia stabile per due scansioni consecutive.
4. Il contenuto viene riconosciuto come **compiti** (`ASSEGNATI IL ...`) o **diario** (`Attività svolta il ...`).
5. Il parser valida date, materie, professori e struttura.
6. Compiti e ore del diario vengono salvati in MongoDB.
7. Il file viene eliminato solo dopo il salvataggio confermato (comportamento predefinito).

Se qualcosa non è valido, il file resta nella cartella e viene registrata un'anomalia nella pagina **Importazioni**.

## 3. Funzionalità

### Compiti

- Import automatico dei file DidUP.
- Calendario mensile, settimanale, giornaliero e agenda.
- Filtri per materia, professore, stato e ricerca testuale.
- Ordinamento per data di consegna, materia e professore.
- Dettaglio completo del compito.
- Stato **completato** aggiornato in tempo reale.
- Riconoscimento degli aggiornamenti: descrizione estesa, scadenza spostata, revisione del compito.
- Nessun duplicato quando lo stesso compito compare in più esportazioni.

### Diario

- Una riga per ogni ora di lezione.
- Navigazione per giorno con pulsante **Oggi**.
- Elenco dei giorni disponibili.
- Filtri per materia, professore e ricerca testuale.
- Aggiornamento automatico quando arriva un nuovo diario.

### Importazioni e anomalie

- Storico separato per file di compiti e diario.
- Contatori di compiti nuovi, aggiornati e invariati.
- Registro delle anomalie con stato aperto/risolto.
- Stato del monitor, ultima scansione e ultimo file importato.

### Notifiche

- Nuovi compiti importati, con riepilogo per materia.
- Promemoria di consegna con anticipo e orario configurabili.
- Notifica di prova dal pannello Impostazioni.
- Notifiche persistenti finché non vengono chiuse.
- Click sulla notifica che apre l'agenda o il dettaglio del compito.

### Google, se configurato

- Lettura delle email Gmail che corrispondono a una ricerca.
- Archivio di corpo testuale e allegati.
- Import automatico dei TXT DidUP trovati come allegati.
- Lettura di corsi, compiti, materiali e annunci Classroom.
- Download dei file Drive allegati ai materiali Classroom.

### Altro

- Dashboard con scadenze, completati, importazioni e anomalie.
- Aggiornamenti in tempo reale via SSE.
- PWA installabile.
- API REST documentate con OpenAPI e Swagger UI.
- HTTPS opzionale su `maximilianclassroom.com:4443`.

## 4. Requisiti

Per usare MIDA con Docker:

- macOS, Linux o Windows con **Docker Desktop** o Docker Engine.
- **Docker Compose v2**.
- Almeno **2 GB di RAM** liberi per lo stack.
- Porta **8200** libera sull'host (frontend locale).
- Porta **4443** libera sull'host, se avvii il proxy HTTPS (vedi sezione 13).
- Accesso alla cartella dalla quale vuoi importare i file (di norma `Downloads`).

Non serve installare MongoDB, Java o Node: sono dentro i container.

Per lo sviluppo senza Docker servono anche:

- **Java 25**;
- **Node.js 24** e npm.

## 5. Installazione

### 5.1 Copia il progetto

Posizionati nella cartella del progetto:

```bash
cd /percorso/della/cartella/mida
```

### 5.2 Crea il file `.env`

```bash
cp .env.example .env
```

Apri `.env` e imposta almeno:

```dotenv
DOWNLOADS_PATH=/Users/tuo-utente/Downloads
MIDA_UID=501
MIDA_GID=20
```

Su macOS puoi ottenere i valori con:

```bash
echo "$HOME/Downloads"
id -u
id -g
```

`DOWNLOADS_PATH` deve essere un percorso assoluto. MIDA legge solo i file che si trovano direttamente in quella cartella.

`MIDA_UID` e `MIDA_GID` sono l'utente e il gruppo con cui gira il backend nel container: devono poter leggere ed eliminare i file nella cartella. Su macOS corrispondono normalmente a `501` e `20`.

Su macOS, Docker Desktop deve avere accesso alla cartella: **Impostazioni di Sistema → Privacy e sicurezza → File e cartelle → Download** (oppure **Accesso completo al disco** se il bind mount risulta vuoto).

Su Linux usa il percorso assoluto della cartella e lo stesso utente proprietario dei file. Su Windows indica un percorso condiviso con Docker Desktop, per esempio `C:\Users\tuo-utente\Downloads`.

### 5.3 Genera il certificato del proxy HTTPS

Il proxy `mida-proxy` fa parte dello stack. Prima del primo avvio crea un certificato self-signed:

```bash
./scripts/generate-proxy-cert.sh
```

I file finiscono in `proxy/certs/` e non vengono versionati.

Se non vuoi usare il proxy HTTPS, puoi avviare solo i servizi principali:

```bash
docker compose up -d --build mida-mongodb mida-backend mida-frontend
```

In alternativa puoi rimuovere il servizio `mida-proxy` dal `docker-compose.yml`.

### 5.4 Avvia lo stack

```bash
docker compose up -d --build
docker compose ps
```

Tutti i container devono risultare `healthy`. Il frontend parte solo dopo che backend e database sono pronti.

Al primo avvio il backend genera le chiavi **VAPID** per le notifiche push e le salva in MongoDB.

### 5.5 Apri MIDA

Apri:

```text
http://localhost:8200
```

Se hai avviato il proxy HTTPS, puoi usare anche:

```text
https://maximilianclassroom.com:4443
```

Con il certificato self-signed il browser mostra un avviso da accettare. Per installare la PWA e ricevere notifiche su questo hostname serve un certificato considerato valido dal sistema: vedi la sezione 13.

### 5.6 Installa la PWA

- **Chrome desktop**: icona di installazione nella barra degli indirizzi, oppure menu → *Installa MIDA*.
- **iPhone/iPad**: apri MIDA in Safari, tocca **Condividi** → **Aggiungi alla schermata Home**.
- **Android**: menu di Chrome → **Installa app**.

L'installazione della PWA è necessaria, su iOS, anche per ricevere le notifiche push.

### 5.7 Log e stato

```bash
docker compose ps
docker compose logs -f mida-backend
docker compose logs -f mida-frontend
docker compose logs -f mida-proxy
```

Smoke test non distruttivo:

```bash
./scripts/smoke-test.sh
```

## 6. Compiti e diario: come si condividono

Questa è la parte che sostituisce la copia manuale dei compiti.

### 6.1 La condivisione avviene su DidUP Family

La scuola pubblica compiti e diario su **DidUP Family**. MIDA non è un social network e non è un'app multiutente: non serve a “condividere” compiti con altri utenti. La condivisione vera avviene a monte, su DidUP.

MIDA si occupa solo di prendere i file esportati da DidUP e renderli disponibili nella sua interfaccia.

MIDA non ha account separati: chi apre la stessa istanza vede gli stessi compiti e lo stesso diario. Se in famiglia volete usarla in più persone, condividete l'accesso alla stessa istanza (per esempio via HTTPS sulla rete locale); non esiste una condivisione selettiva per singolo utente.

### 6.2 Esporta i compiti

1. Apri **DidUP Family** con l'account dello studente o della famiglia.
2. Vai nella sezione dei **compiti assegnati**.
3. Usa la funzione di **condivisione/esportazione** messa a disposizione da DidUP.
4. Salva il file TXT nella cartella indicata in `DOWNLOADS_PATH` (di norma `Downloads`).

A seconda della versione di DidUP il comando può chiamarsi **Condividi**, **Esporta** o **Salva**: l'importante è che produca un file `.txt` con il contenuto dei compiti.

DidUP genera file con un nome simile a:

```text
text-201C6E324707.txt
text-201C6E324707-1.txt
```

Il nome deve iniziare con `text-`, seguito da un codice di 12 caratteri esadecimali e dall'estensione `.txt`. Se l'esportazione produce uno ZIP, estrai i file TXT nella cartella.

### 6.3 Esporta il diario

La procedura è la stessa:

1. Apri la sezione **diario** o **attività svolte** di DidUP Family.
2. Usa la condivisione/esportazione.
3. Salva il TXT nella stessa cartella `Downloads`.

MIDA riconosce il tipo di file dal contenuto, non dal nome:

- i compiti iniziano con `ASSEGNATI IL ...`;
- il diario inizia con `Attività svolta il ...`.

### 6.4 Cosa succede dopo

1. MIDA rileva il file entro pochi secondi.
2. Attende che il file sia stabile.
3. Interpreta il contenuto.
4. Salva compiti o ore del diario in MongoDB.
5. Elimina il TXT dopo il salvataggio confermato.
6. Aggiorna l'interfaccia in tempo reale.

Non devi premere nessun pulsante di caricamento: l'importazione è automatica.

### 6.5 File già importati e vecchie esportazioni

Puoi copiare nella cartella anche file esportati giorni prima. MIDA li importa se non li ha già visti.

Lo stesso compito può comparire in più file, con codici diversi. MIDA calcola impronte del contenuto e riconosce i duplicati, quindi non crea copie multiple. Se il professore modifica la descrizione o sposta la scadenza, MIDA aggiorna il compito esistente e incrementa la revisione.

Le esportazioni parziali non cancellano mai compiti o ore non presenti: MIDA aggiunge e aggiorna, non elimina automaticamente i dati già importati.

### 6.6 Se vuoi conservare i TXT

Per impostazione predefinita il file viene eliminato dopo l'importazione. Per conservarlo:

```dotenv
IMPORT_DELETE_AFTER_IMPORT=false
```

MIDA continuerà a vederlo, ma la deduplicazione evita di importarlo di nuovo.

### 6.7 Errori e file non validi

Se un file non è valido:

- non viene eliminato;
- viene registrata un'anomalia nella pagina **Importazioni**;
- non viene ritentato finché contenuto e SHA-256 non cambiano.

Un file che contiene sia compiti sia diario viene rifiutato: esporta i due tipi separatamente.

## 7. Formato dei file DidUP

### 7.1 Compiti

Esempio:

```text
ASSEGNATI IL 12 NOVEMBRE

ITALIANO (G. FAZIO):
Antologia: esercizi su fotocopia "Dal testo al genere".
--- CONSEGNA VENERDÌ 20 NOVEMBRE ---
```

Il parser:

- supporta più blocchi `ASSEGNATI IL` nello stesso file;
- riconosce `MATERIA (PROFESSORE):`;
- conserva descrizioni multilinea, accenti, virgolette e righe vuote interne;
- non unisce compiti distinti con la stessa materia e lo stesso professore;
- supporta UTF-8 con BOM e file LF o CRLF;
- interpreta settembre–dicembre nell'anno iniziale e gennaio–agosto nell'anno successivo;
- verifica il giorno della settimana dichiarato;
- rifiuta date inesistenti, giorni incoerenti e consegne precedenti all'assegnazione;
- rifiuta l'intero file se una parte non è valida.

### 7.2 Diario

Esempio:

```text
Attività svolta il 13 NOVEMBRE

1° ORA
INGLESE (E. NIFOSI)
Virtual classmates

2° ORA
ITALIANO (G. FAZIO)
Laboratorio sui miti greci: creazione di un elaborato su cartoncino con testo e immagini.
```

Per ogni ora MIDA estrae:

- data;
- numero dell'ora (`1° ORA`, `2° ORA`, …);
- materia e professore;
- descrizione integrale, anche multilinea.

La descrizione può essere vuota: nell'app viene mostrata come “Nessuna attività descritta”.

### 7.3 Identità di compiti e attività

DidUP non fornisce sempre un identificativo stabile per il singolo compito. MIDA usa alcune impronte SHA-256 sui campi normalizzati:

- `contentHash`: identifica il contenuto esatto;
- `slotKey`: raggruppa i compiti dello stesso giorno, materia, professore e consegna;
- `baseKey`: riconosce una scadenza spostata;
- `descriptionHash`: impronta della sola descrizione.

Comportamento:

| Situazione | Comportamento |
| --- | --- |
| Contenuto già presente | Nessun duplicato. |
| Descrizione estesa o corretta | Aggiorna lo stesso compito, `revision` aumenta, `completed` resta invariato. |
| Scadenza spostata con un solo candidato | Aggiorna lo stesso compito. |
| Descrizioni diverse con numeri diversi | Crea due compiti distinti. |
| Stessa data e ora del diario | Aggiorna la riga esistente. |
| Stesso codice con contenuto diverso | Anomalia `DUPLICATE_CONFLICT`, file conservato. |

Le notifiche dei nuovi compiti vengono inviate solo per i compiti realmente nuovi, non per gli aggiornamenti.

## 8. Uso dell'app

### Compiti

- La voce **Compiti** apre il calendario.
- Viste disponibili: **Mese**, **Settimana**, **Giorno**, **Agenda**.
- In **Agenda** puoi scegliere l'intervallo **Dal/Al** e ordinare per data crescente o decrescente.
- Il filtro data predefinito è **Consegna**; puoi passare a **Assegnazione**.
- Filtri: ricerca testuale, materia, professore, stato (tutti, da fare, completati).
- Ogni materia ha un colore stabile.
- Cliccando un compito si apre il dettaglio con materia, professore, date, descrizione e origine.
- Il pulsante di completamento aggiorna il compito e viene riflesso in tempo reale sugli altri dispositivi.

### Diario

La voce **Diario** apre il registro delle attività svolte:

- giorno selezionato con navigazione precedente/successiva e pulsante **Oggi**;
- elenco dei giorni disponibili con il numero di attività;
- attività ordinate per ora, con materia, professore e descrizione;
- filtri per ricerca, materia e professore;
- aggiornamento automatico via SSE.

### Dashboard

La dashboard riepiloga:

- compiti da fare;
- compiti in scadenza oggi;
- compiti scaduti non completati;
- compiti completati;
- prossime consegne;
- stato del monitor;
- importazioni recenti e anomalie aperte.

### Importazioni

La pagina **Importazioni** mostra:

- stato del monitor, ultima scansione e ultimo import;
- numero di importazioni, compiti, attività del diario, anomalie e dispositivi push;
- cronologia dei file di compiti e di diario;
- anomalie con tipo, file, descrizione, stato, tentativi e ultima osservazione.

### Posta

La pagina **Posta** contiene:

- **Email**: messaggi Gmail sincronizzati, ricerca, dettaglio, allegati scaricabili.
- **Classroom**: corsi, compiti, materiali e annunci, con descrizione e allegati.

La sezione è disponibile solo dopo aver configurato Google.

### Impostazioni

Da **Impostazioni** puoi:

- attivare e disattivare le notifiche push;
- configurare anticipo e orario dei promemoria;
- importare, sostituire o rimuovere il client OAuth Google;
- collegare o scollegare l'account Google;
- impostare la ricerca Gmail e i toggle Gmail/Classroom;
- vedere lo stato del monitor e delle importazioni;
- aprire la **Guida Google** integrata.

## 9. Notifiche push

Le notifiche sono **opt-in**: MIDA non chiede il permesso all'avvio. Apri **Impostazioni** e premi **Attiva notifiche push**.

### Tipi di notifica

- **Nuovi compiti**: dopo un'importazione, con il numero di compiti per materia.
- **Promemoria di consegna**: elenco dei compiti non completati in scadenza, con materia e anteprima della descrizione (fino a 6; poi “e altri N”).
- **Notifica di prova**: dal pannello Impostazioni.

### Comportamento

- Le notifiche restano sullo schermo finché non vengono chiuse (`requireInteraction`).
- Il click apre l'**Agenda** sulla data di consegna e, se il compito è uno solo, anche il dettaglio.
- Le notifiche arrivano anche con la scheda di MIDA chiusa, nei limiti del browser e del sistema operativo.
- Su iPhone/iPad MIDA deve essere installata nella schermata Home e aperta da lì.
- Il browser o il sistema operativo devono poter eseguire il servizio push. In risparmio energetico estremo o con il browser terminato la consegna non è garantita.
- Le finestre in incognito non supportano Push API.
- Disattivando le notifiche dal dispositivo, la sottoscrizione viene rimossa anche dal backend.
- Le sottoscrizioni scadute (`404`/`410`) vengono disattivate automaticamente.

### Notifiche su macOS

Per ricevere i banner:

1. **Impostazioni di Sistema → Notifiche → Google Chrome**.
2. Attiva **Consenti notifiche**.
3. Imposta **Stile notifiche** su **Banner** o **Avvisi**. Se è **Nessuno**, la notifica finisce solo nel Centro Notifiche.
4. Abilita anche **Suoni** e **Badge** se li vuoi.
5. Controlla eventuali righe Chrome duplicate e abilitale entrambe.
6. Verifica che Focus/Non disturbare non sia attivo.

Se la notifica compare nel Centro Notifiche ma non come banner, il problema è lo stile di avviso, non MIDA.

### Chiavi VAPID

Al primo avvio MIDA genera le chiavi VAPID e le salva in MongoDB (`appSettings`). Se elimini il volume del database, le chiavi cambiano e i dispositivi devono essere riattivati.

Per usare chiavi tue, impostale in `.env`:

```dotenv
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...
```

## 10. Gmail e Google Classroom

L'integrazione Google è opzionale. MIDA usa OAuth 2.0 e permessi di sola lettura. La password Google non viene mai richiesta né salvata.

### 10.1 Cosa può fare

- leggere le email Gmail che corrispondono a una ricerca;
- conservare corpo testuale e allegati;
- archiviare gli allegati in GridFS e renderli scaricabili;
- importare automaticamente i TXT DidUP trovati come allegati;
- leggere corsi, compiti, materiali e annunci Classroom;
- scaricare i file Drive allegati ai materiali Classroom.

### 10.2 Preparare Google Cloud

1. Apri <https://console.cloud.google.com/> e crea o seleziona un progetto.
2. Vai su **API e servizi → Libreria** e abilita:
   - **Gmail API**;
   - **Google Classroom API**;
   - **Google Drive API**.
3. Vai su **API e servizi → Schermata di consenso OAuth**:
   - scegli **Internal** se usi un account Google Workspace scolastico;
   - scegli **External** solo se necessario. In modalità test i refresh token possono scadere dopo 7 giorni;
   - aggiungi il tuo account tra gli **utenti di test** se il progetto è in testing.
4. Vai su **API e servizi → Credenziali → Crea credenziali → ID client OAuth**.
5. Scegli **Applicazione web**.
6. In **URI di reindirizzamento autorizzati** aggiungi l'indirizzo di MIDA:
   - `http://localhost:8200/api/google/oauth/callback`
   - se usi il proxy HTTPS, aggiungi anche `https://<your-domain.com>:4443/api/google/oauth/callback`
7. Crea il client e scarica il file **JSON**.

### 10.3 Importare il JSON in MIDA

1. Apri MIDA → **Impostazioni → Account Google**.
2. Premi **Importa JSON** e seleziona il file scaricato da Google.
3. MIDA valida `client_id`, `client_secret` e il redirect URI.
4. Genera una chiave UUID, cifra il JSON completo con AES-256-GCM e lo salva nella collection `googleClientCredentials`.
5. Premi **Collega account Google** e autorizza i permessi.

Non servono credenziali Google nel file `.env`.

Se importi un nuovo JSON, la connessione precedente viene scollegata: dovrai autorizzare di nuovo l'account. **Rimuovi client** elimina le credenziali cifrate e scollega l'account.

### 10.4 Permessi richiesti

| Scope | Uso |
| --- | --- |
| `openid`, `email`, `profile` | Identificare l'account collegato. |
| `gmail.readonly` | Leggere messaggi, corpo e allegati. |
| `classroom.courses.readonly` | Elencare i corsi attivi. |
| `classroom.coursework.me.readonly` | Leggere i compiti dello studente. |
| `classroom.courseworkmaterials.readonly` | Leggere i materiali dei corsi. |
| `classroom.announcements.readonly` | Leggere gli annunci. |
| `drive.readonly` | Scaricare i file allegati ai materiali Classroom. |

Sono tutti permessi di lettura: MIDA non modifica email, corsi o file.

### 10.5 Gmail

La ricerca predefinita è:

```text
from:didup
```

Al primo collegamento di un account Workspace, se non esiste già una configurazione, MIDA imposta automaticamente:

```text
from:<dominio-della-scuola> OR from:didup
```

Esempi di ricerca:

```text
from:didup
from:(didup OR "DidUP Family")
subject:compiti
has:attachment from:didup
```

La ricerca si modifica da **Impostazioni → Account Google → Ricerca Gmail**.

La sincronizzazione Gmail:

1. elenca i messaggi che corrispondono alla ricerca;
2. salta quelli già completi;
3. scarica il messaggio in formato `full`;
4. estrae corpo testuale e allegati;
5. archivia gli allegati in GridFS;
6. se un allegato è un TXT DidUP valido, lo importa nei compiti o nel diario.

Gli allegati non DidUP restano disponibili nella pagina **Posta** e scaricabili.

MIDA non conserva né renderizza l'HTML originale delle email: lo converte in testo.

### 10.6 Google Classroom

La scheda **Classroom** nella pagina **Posta** mostra:

- corsi, compiti, materiali e annunci;
- titolo e anteprima della descrizione;
- materia e corso;
- materiali allegati.

I file Drive vengono scaricati in MongoDB GridFS e sono disponibili con **Scarica** nel dettaglio. I link e i video YouTube restano riferimenti apribili. Il limite predefinito per singolo file è **100 MB** (`GOOGLE_MAX_ATTACHMENT_SIZE_BYTES`).

La sincronizzazione Classroom non ha un polling proprio: parte dopo il collegamento OAuth, quando Gmail trova almeno una nuova email, oppure manualmente dal pulsante **Sincronizza Classroom**.

### 10.7 Quote API

L'uso di MIDA è molto sotto le soglie gratuite:

- **Gmail API**: 6.000 unità/minuto per utente, 80.000.000 unità/giorno per progetto;
- **Classroom API**: 4.000.000 richieste/giorno per client, 3.000/minuto;
- **Drive API**: 1.000.000 unità/minuto per progetto, 400.000.000 unità/giorno gratuite.

### 10.8 Problemi comuni

- **`redirect_uri_mismatch`**: il redirect URI nel JSON non coincide con quello registrato su Google Cloud. Devono essere identici, compresi schema, porta e path.
- **JSON rifiutato**: manca `client_id`, `client_secret` o un redirect URI che termini con `/api/google/oauth/callback`.
- **Accesso bloccato**: se il progetto è in testing, aggiungi il tuo account tra gli utenti di test.
- **`invalid_grant`**: il refresh token è scaduto o revocato. Premi **Collega account Google** per autorizzare di nuovo.
- **403 su Gmail, Classroom o Drive**: manca uno scope, l'amministratore del dominio li ha bloccati, oppure l'account non ha accesso alla risorsa.
- **Nessuna email**: controlla la ricerca Gmail, che Gmail sia abilitato e premi **Sincronizza email**.
- **Classroom non si aggiorna**: la sincronizzazione parte solo con nuove email o manualmente; premi **Sincronizza Classroom**.

La guida passo passo è disponibile anche nell'app alla pagina **Guida Google** (`/guida-google`). La documentazione completa è in [`docs/google.md`](docs/google.md).

## 11. API REST, OpenAPI e SSE

Base URL locale: `http://localhost:8200`.

Base URL HTTPS, se il proxy è attivo: `https://maximilianclassroom.com:4443`.

### Endpoint principali

| Metodo | Endpoint | Descrizione |
| --- | --- | --- |
| `GET` | `/api/homework` | Compiti paginati con filtri e ordinamento. |
| `GET` | `/api/homework/calendar` | Compiti di un intervallo, senza paginazione. |
| `GET` | `/api/homework/subjects` | Materie presenti. |
| `GET` | `/api/homework/teachers` | Professori presenti. |
| `GET` | `/api/homework/{id}` | Dettaglio di un compito. |
| `PATCH` | `/api/homework/{id}/completion` | Aggiorna lo stato completato. |
| `GET` | `/api/diary` | Attività del diario paginate e filtrabili. |
| `GET` | `/api/diary/day` | Attività di un giorno. |
| `GET` | `/api/diary/dates` | Giorni disponibili. |
| `GET` | `/api/imports` | Storico importazioni compiti. |
| `GET` | `/api/imports/diary` | Storico importazioni diario. |
| `GET` | `/api/imports/issues` | Anomalie. |
| `GET` | `/api/imports/status` | Stato del monitor. |
| `GET` | `/api/notifications/public-key` | Chiave pubblica VAPID. |
| `GET` | `/api/notifications/preferences` | Preferenze di notifica. |
| `PUT` | `/api/notifications/preferences` | Aggiorna le preferenze. |
| `POST` | `/api/notifications/subscriptions` | Registra una sottoscrizione push. |
| `DELETE` | `/api/notifications/subscriptions` | Disattiva una sottoscrizione push. |
| `POST` | `/api/notifications/test` | Invia una notifica di prova. |
| `GET` | `/api/events` | Stream SSE degli aggiornamenti. |
| `GET` | `/api/google/client` | Stato del client OAuth importato. |
| `POST` | `/api/google/client` | Importa il JSON del client OAuth. |
| `DELETE` | `/api/google/client` | Rimuove le credenziali e scollega l'account. |
| `GET` | `/api/google/status` | Stato della connessione Google. |
| `GET` | `/api/google/connect` | Avvia il consenso OAuth. |
| `GET` | `/api/google/oauth/callback` | Callback OAuth. |
| `POST` | `/api/google/disconnect` | Scollega l'account. |
| `POST` | `/api/google/sync` | Avvia la sincronizzazione Classroom. |
| `PUT` | `/api/google/settings` | Ricerca Gmail e toggle Gmail/Classroom. |
| `GET` | `/api/google/classroom` | Elementi Classroom paginati. |
| `GET` | `/api/google/classroom/{id}` | Dettaglio e materiali Classroom. |
| `GET` | `/api/google/gmail/status` | Stato della sincronizzazione Gmail. |
| `POST` | `/api/google/gmail/sync` | Avvia la sincronizzazione Gmail. |
| `GET` | `/api/mail/emails` | Email sincronizzate. |
| `GET` | `/api/mail/emails/{id}` | Dettaglio email e allegati. |
| `GET` | `/api/attachments/{id}` | Scarica un allegato da GridFS. |

La documentazione completa dei parametri è in [`docs/api.md`](docs/api.md).

### OpenAPI e Swagger

- OpenAPI JSON: <http://localhost:8200/v3/api-docs>
- Swagger UI: <http://localhost:8200/swagger-ui.html>

Con il proxy HTTPS usano lo stesso host e la porta 4443.

### SSE

Lo stream `/api/events` invia:

- `connected` alla connessione;
- `import.completed` dopo un'importazione di compiti;
- `diary.imported` dopo un'importazione di diario;
- `homework.updated` dopo un cambio di completamento;
- `import.issue` quando compare o si risolve un'anomalia;
- `import.status` quando cambia lo stato del monitor;
- commenti `heartbeat` periodici.

Se SSE non è disponibile, importazione e persistenza continuano a funzionare; il frontend si riconnette e riallinea i dati.

Le risposte di errore usano **Problem Details** (RFC 9457).

## 12. Database, backup e ripristino

Database: `mida`.

Collezioni principali:

| Collezione | Contenuto |
| --- | --- |
| `homeworkAssignments` | Un documento per compito, con impronte, revisione e completamento. |
| `diaryEntries` | Una riga per ogni ora del diario. |
| `homeworkImports` / `diaryImports` | Storico dei file importati. |
| `importIssues` | Anomalie di importazione. |
| `pushSubscriptions` | Sottoscrizioni Web Push. |
| `notificationPreferences` | Preferenze di notifica. |
| `notificationDeliveries` | Tentativi di consegna e deduplicazione. |
| `appSettings` | Chiavi VAPID generate. |
| `googleClientCredentials` | JSON del client OAuth cifrato e chiave UUID. |
| `googleConnection` | Account Google, token cifrati e stato. |
| `googleSettings` / `googleOauthStates` | Impostazioni Google e stati OAuth. |
| `emailMessages` / `emailAttachments` | Email Gmail e metadati degli allegati. |
| `classroomItems` / `classroomMaterials` | Compiti, materiali e annunci Classroom. |
| `fs.files` / `fs.chunks` | Contenuto binario in GridFS. |

I dati sono nel volume Docker nominato `mida-mongodb-data`. MongoDB non pubblica porte sull'host e non richiede credenziali perché raggiungibile solo dalla rete interna.

Per ispezionare il database:

```bash
docker exec -it mida-mongodb mongosh mida
```

### Backup con mongodump

```bash
docker exec mida-mongodb mongodump --db mida --archive=/tmp/mida.archive --gzip
docker cp mida-mongodb:/tmp/mida.archive "./mida-backup-$(date +%F).archive"
```

### Ripristino

Ferma il backend per evitare scritture concorrenti:

```bash
docker compose stop mida-backend
docker cp ./mida-backup-2026-11-18.archive mida-mongodb:/tmp/mida.archive
docker exec mida-mongodb mongorestore --drop --db mida --archive=/tmp/mida.archive --gzip
docker compose start mida-backend
```

### Backup del volume

In alternativa puoi salvare il volume Docker:

```bash
docker compose stop
docker run --rm -v mida-mongodb-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/mida-mongodb-data.tar.gz -C /data .
docker compose start
```

**Non usare** `docker compose down -v` se vuoi conservare i dati: elimina il volume.

## 13. Proxy HTTPS su maximilianclassroom.com

Il servizio `mida-proxy` espone MIDA su:

```text
https://maximilianclassroom.com:4443
```

È un nginx separato dal frontend. Instrada:

- `/api/` verso `mida-backend:8080`, incluse le SSE di `/api/events`;
- `/v3/api-docs`, `/swagger-ui/` e `/swagger-ui.html` verso il backend;
- tutto il resto verso `mida-frontend:8080`.

La configurazione è in [`proxy/nginx.conf`](proxy/nginx.conf). I nomi dei servizi Docker vengono risolti dinamicamente, quindi il proxy continua a funzionare anche quando backend o frontend vengono ricreati.

### Certificato TLS

Genera un certificato self-signed:

```bash
./scripts/generate-proxy-cert.sh
docker compose up -d mida-proxy
```

I file vengono creati in `proxy/certs/` (`fullchain.pem`, `privkey.pem`) e non sono versionati.

Per l'uso quotidiano sostituiscili con un certificato reale (per esempio Let's Encrypt) e riavvia `mida-proxy`.

Un certificato self-signed permette di aprire l'app dopo aver accettato l'avviso, ma Chrome blocca service worker e Web Push su un certificato non attendibile. Per installare la PWA e ricevere notifiche da `maximilianclassroom.com` serve un certificato considerato valido dal sistema.

### Hostname

Il nome `maximilianclassroom.com` deve risolvere all'indirizzo della macchina che esegue Docker. Nel setup attuale risolve in `192.168.1.100`.

Per gli altri dispositivi configura il DNS locale o `/etc/hosts`:

```text
192.168.1.100 maximilianclassroom.com
```

### Google OAuth via HTTPS

Se colleghi Gmail o Classroom aprendo MIDA dall'hostname HTTPS, aggiungi al client OAuth su Google Cloud anche:

```text
https://maximilianclassroom.com:4443/api/google/oauth/callback
```

Poi riscarica il JSON e reimportalo da **Impostazioni → Account Google**.

Se il JSON contiene sia il redirect di localhost sia quello HTTPS, MIDA usa il primo che termina con `/api/google/oauth/callback`. Assicurati che sia quello dell'indirizzo da cui colleghi l'account.

## 14. Configurazione

Tutte le variabili si trovano in `.env`. Il file è ignorato da Git e non deve essere committato.

| Variabile | Obbligatoria | Default | Descrizione |
| --- | --- | --- | --- |
| `DOWNLOADS_PATH` | Sì | — | Cartella assoluta montata in `/imports`. |
| `SCHOOL_YEAR_START` | No | `2026` | Primo anno scolastico: settembre–dicembre nell'anno indicato, gennaio–agosto nel successivo. |
| `IMPORT_MONITORING_ENABLED` | No | `true` | Abilita il monitoraggio della cartella. |
| `IMPORT_POLL_INTERVAL_MS` | No | `5000` | Intervallo di polling in millisecondi. |
| `IMPORT_STABILITY_SCANS` | No | `2` | Scansioni stabili prima dell'importazione. |
| `IMPORT_MAX_FILE_SIZE_BYTES` | No | `10485760` | Dimensione massima di un TXT (10 MiB). |
| `IMPORT_DELETE_AFTER_IMPORT` | No | `true` | Elimina il TXT dopo un salvataggio confermato. |
| `NOTIFICATIONS_ENABLED` | No | `true` | Interruttore generale delle notifiche. |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | No | vuote | Se impostate entrambe, MIDA usa queste chiavi; altrimenti le genera e le conserva in MongoDB. |
| `VAPID_SUBJECT` | No | `mailto:mida@localhost` | Contatto VAPID. |
| `GOOGLE_ENABLED` | No | `true` | Abilita l'integrazione Google. Il client OAuth si importa dall'app, non da `.env`. |
| `GOOGLE_GMAIL_ENABLED` | No | `true` | Abilita Gmail. |
| `GOOGLE_GMAIL_QUERY` | No | `from:didup` | Ricerca Gmail iniziale, modificabile dalle Impostazioni. |
| `GOOGLE_GMAIL_MAX_RESULTS` | No | `50` | Messaggi massimi per sincronizzazione. |
| `GOOGLE_GMAIL_SYNC_INTERVAL_MS` | No | `300000` | Intervallo di polling Gmail. |
| `GOOGLE_GMAIL_INITIAL_DELAY_MS` | No | `30000` | Attesa prima del primo sync Gmail. |
| `GOOGLE_CLASSROOM_ENABLED` | No | `true` | Abilita Classroom. |
| `GOOGLE_CLASSROOM_ANNOUNCEMENTS` | No | `true` | Include gli annunci. |
| `GOOGLE_MAX_ATTACHMENT_SIZE_BYTES` | No | `104857600` | Limite per allegato/file Drive (100 MB). |
| `MIDA_UID` / `MIDA_GID` | Consigliata | `10001` | UID/GID dell'utente host con cui gira il backend. |

MongoDB non richiede credenziali: è raggiungibile solo dalla rete interna Docker e non pubblica porte sull'host. Se in futuro esponi il database, aggiungi autenticazione e non pubblicarlo su Internet.

Per cambiare la porta locale del frontend modifica il mapping in `docker-compose.yml`:

```yaml
ports:
  - "127.0.0.1:8200:8080"
```

Se cambi hostname o porta HTTPS, aggiorna anche `proxy/nginx.conf`, il certificato e i redirect URI Google.

## 15. Sicurezza e limiti

Misure adottate:

- MongoDB e backend non pubblicano porte sull'host;
- il frontend locale ascolta solo su `127.0.0.1:8200`;
- il proxy HTTPS pubblica `4443`;
- nginx applica CSP, `X-Content-Type-Options`, `X-Frame-Options` e `Referrer-Policy`;
- il backend legge solo la directory configurata, ignora link simbolici e applica un limite di dimensione;
- le chiavi VAPID private non vengono mai scritte nei log né committate;
- il JSON del client OAuth, il refresh token Google e l'indirizzo dell'account sono cifrati AES-256-GCM;
- l'HTML delle email non viene conservato né renderizzato;
- `.env` è escluso dal repository;
- gli endpoint di scrittura validano input, date, dimensioni e stato.

Limiti noti:

- **non c'è login né multiutente**: MIDA è pensata per un solo nucleo familiare e un computer fidato;
- **non esporre MIDA direttamente su Internet** senza VPN, autenticazione o un reverse proxy protetto;
- il certificato self-signed del proxy non è adatto alla PWA e alle notifiche push;
- i dati restano nel volume MongoDB locale;
- Gmail e Classroom richiedono un client OAuth e il rispetto delle policy del dominio Workspace;
- il parser supporta il formato DidUP Family documentato; formati diversi producono un'anomalia e il file resta intatto;
- eliminare un TXT dalla cartella non elimina i compiti già importati;
- non è possibile modificare manualmente materia, professore o descrizione;
- non esiste una schermata per cancellare singole importazioni o anomalie;
- le notifiche push dipendono dal browser, dal sistema operativo e dal servizio push del produttore;
- su iOS le notifiche richiedono l'installazione della PWA nella schermata Home;
- con browser o sistema operativo completamente arrestati la consegna non è garantita;
- non è incluso un sistema di backup automatico: usa `mongodump` o salva il volume.

## 16. Manutenzione e aggiornamento

### Arresto e riavvio

```bash
docker compose stop
docker compose start
```

Rimozione dei container mantenendo i dati:

```bash
docker compose down
docker compose up -d
```

**Non usare** `docker compose down -v` se vuoi conservare compiti, diario, email, preferenze e chiavi VAPID.

### Aggiornamento del codice

```bash
git pull
docker compose up -d --build
```

I dati nel volume restano. Le migrazioni del backend sono idempotenti.

### Log

```bash
docker compose logs -f mida-backend
docker compose logs -f mida-frontend
docker compose logs -f mida-proxy
```

### Ricostruzione completa

```bash
docker compose build --no-cache
docker compose up -d
```

### Certificato HTTPS

Sostituisci `proxy/certs/fullchain.pem` e `proxy/certs/privkey.pem` con il certificato reale, poi:

```bash
docker compose restart mida-proxy
```

### Reset totale

Solo se accetti di perdere tutti i dati:

```bash
docker compose down -v
docker compose up -d --build
```

Le chiavi VAPID vengono rigenerate e i dispositivi devono essere riattivati.

## 17. Test

I test backend usano JUnit 5, Mockito, Spring Boot Test e Testcontainers con MongoDB 8. Testcontainers richiede Docker in esecuzione.

```bash
cd backend
./mvnw test
```

I test frontend usano Angular CLI e Vitest:

```bash
cd frontend
npm ci --legacy-peer-deps
npm test -- --watch=false
```

Build di produzione del frontend:

```bash
cd frontend
npm run build
```

La build genera `ngsw-worker.js` e aggiunge gli handler `push` e `notificationclick`.

Smoke test dello stack già avviato:

```bash
./scripts/smoke-test.sh
```

Per un ambiente HTTPS con certificato self-signed:

```bash
INSECURE=1 BASE_URL=https://maximilianclassroom.com:4443 ./scripts/smoke-test.sh
```

Con un certificato reale ometti `INSECURE=1`.

## 18. Risoluzione dei problemi

### La pagina non si apre

```bash
docker compose ps
docker compose logs mida-backend --tail=100
docker compose logs mida-frontend --tail=100
```

Tutti i servizi devono essere `healthy`. Controlla che le porte 8200 e 4443 siano libere.

### Il backend non è healthy

Controlla i log e che `mida-mongodb` sia healthy. Il backend usa `mida-mongodb:27017`, non `localhost`.

### La cartella Downloads è vuota nel container

- verifica `DOWNLOADS_PATH`;
- controlla i permessi di Docker Desktop su macOS;
- verifica `MIDA_UID` e `MIDA_GID`;
- crea un file nella cartella host e controlla che compaia nella pagina Importazioni.

### Un file non viene importato

- il nome deve essere `text-XXXXXXXXXXXX.txt` (12 caratteri esadecimali);
- il file deve essere UTF-8 e con struttura DidUP valida;
- per i compiti deve iniziare con `ASSEGNATI IL ...`;
- per il diario deve iniziare con `Attività svolta il ...`;
- un file che contiene entrambi i formati viene rifiutato;
- controlla le anomalie nella pagina Importazioni;
- un file con errore non viene ritentato finché il contenuto non cambia.

### Il diario non compare

- verifica l'intestazione `Attività svolta il ...`;
- ogni ora deve avere materia e professore;
- la descrizione può essere vuota;
- controlla i filtri materia/professore;
- se il codice è già usato da un file di compiti, controlla le anomalie.

### Le notifiche non arrivano

- il permesso deve essere concesso dal browser;
- su iPhone/iPad MIDA deve essere installata nella schermata Home;
- controlla che il dispositivo risulti attivo nelle Impostazioni;
- verifica che Focus/Non disturbare non sia attivo;
- su macOS imposta Chrome su **Banner** o **Avvisi**, non **Nessuno**;
- controlla eventuali righe Chrome duplicate nelle impostazioni di sistema;
- assicurati che il browser o il sistema operativo non siano in risparmio energetico estremo;
- usa **Invia notifica di prova** e controlla i log del backend;
- se la notifica è nel Centro Notifiche ma non come banner, è lo stile di avviso di macOS;
- se hai appena aggiornato il frontend, chiudi completamente la PWA e riaprila per aggiornare il service worker.

### Google non si collega

- controlla che il JSON contenga `client_id`, `client_secret` e il redirect URI giusto;
- verifica che il redirect URI su Google Cloud coincida esattamente con quello di MIDA;
- se il progetto è in testing, aggiungi l'account tra gli utenti di test;
- controlla gli scope autorizzati e le policy del dominio Workspace;
- se compare `invalid_grant`, ricollega l'account;
- per Classroom, ricorda che la sincronizzazione parte con nuove email o manualmente.

### Il proxy HTTPS non parte

```bash
docker compose logs mida-proxy
docker compose exec mida-proxy nginx -t
```

Verifica che `proxy/certs/fullchain.pem` e `privkey.pem` esistano e siano leggibili. Se mancano, esegui `./scripts/generate-proxy-cert.sh`.

### Il browser blocca service worker o notifiche sul dominio HTTPS

Il certificato self-signed non è attendibile per Chrome. Sostituiscilo con un certificato reale (Let's Encrypt o certificato aziendale) e riavvia `mida-proxy`. In alternativa usa `http://localhost:8200`, che è considerato un contesto sicuro.

### Voglio azzerare tutto

Solo se accetti di perdere i dati:

```bash
docker compose down -v
docker compose up -d --build
```

## 19. Struttura del progetto

```text
mida/
├── backend/                  Spring Boot 4.1.2, Java 25
│   ├── src/main/java/it/mida/
│   │   ├── imports/          monitoraggio cartella e importazione
│   │   ├── parser/           parser DidUP compiti e diario
│   │   ├── homework/         compiti e API
│   │   ├── diary/            diario e API
│   │   ├── notifications/    Web Push, promemoria, VAPID
│   │   ├── google/           OAuth, Gmail, Classroom, GridFS
│   │   ├── events/           SSE
│   │   └── common/           errori, cifratura, paginazione
│   └── src/test/             test JUnit e Testcontainers
├── frontend/                 Angular 21 PWA
│   ├── src/app/core/         API, store, modelli, push, SSE
│   ├── src/app/pages/        compiti, diario, posta, dashboard, impostazioni, guida
│   └── scripts/              append-push-handler.mjs
├── proxy/                    nginx HTTPS e certificati (certs ignorati)
├── scripts/                  cert helper e smoke test
├── docs/                     API, database, architettura, Google
├── docker-compose.yml
├── .env.example
└── README.md
```

## 20. Documentazione di dettaglio

- [`docs/api.md`](docs/api.md): endpoint, parametri, esempi e SSE.
- [`docs/database.md`](docs/database.md): collezioni, indici e documenti.
- [`docs/architecture.md`](docs/architecture.md): scelte tecniche e flussi.
- [`docs/google.md`](docs/google.md): OAuth, scope, quote e risoluzione problemi Google.
- **Guida Google in-app**: `http://localhost:8200/guida-google`.
- **Swagger UI**: `http://localhost:8200/swagger-ui.html`.

Per qualunque dubbio sul flusso, la regola è sempre la stessa: **DidUP Family esporta, MIDA importa dalla cartella Downloads, MongoDB conserva e l'interfaccia mostra**. Google è un'aggiunta opzionale e non sostituisce l'importazione dei file DidUP.
