import { ImportIssueType, ImportStatus } from './models';

export const ISSUE_TYPE_LABELS: Record<ImportIssueType, string> = {
  PARSE_ERROR: 'Formato non riconosciuto',
  VALIDATION_ERROR: 'Dati non validi',
  READ_ERROR: 'File non leggibile',
  FILE_TOO_LARGE: 'File troppo grande',
  DUPLICATE_CONFLICT: 'Conflitto sullo stesso codice',
  FILE_DELETION_FAILED: 'Cancellazione non riuscita',
  FILE_CHANGED_DURING_IMPORT: 'File cambiato durante l’importazione',
  DIRECTORY_UNAVAILABLE: 'Cartella non disponibile',
  PERSISTENCE_ERROR: 'Errore di salvataggio',
  UNEXPECTED_ERROR: 'Errore imprevisto',
};

export const MONITOR_STATE_LABELS: Record<ImportStatus['state'], string> = {
  STARTING: 'Avvio',
  ACTIVE: 'Attivo',
  ERROR: 'Errore',
  DISABLED: 'Disattivato',
};

export const MONITOR_STATE_TONE: Record<ImportStatus['state'], string> = {
  STARTING: 'blue',
  ACTIVE: 'teal',
  ERROR: 'red',
  DISABLED: 'gray',
};
