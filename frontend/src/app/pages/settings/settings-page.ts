import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MidaApiService } from '../../core/api.service';
import { formatDateTime } from '../../core/format';
import { apiErrorMessage } from '../../core/homework-store';
import { MONITOR_STATE_LABELS, MONITOR_STATE_TONE } from '../../core/labels';
import { LiveUpdatesService } from '../../core/live-updates.service';
import { ImportStatus, NotificationPreferences, GoogleClientStatus, GoogleStatus } from '../../core/models';
import { PushService } from '../../core/push.service';
import { ToastService } from '../../core/toast.service';

interface PreferencesForm {
  enabled: boolean;
  newAssignmentsEnabled: boolean;
  remindersEnabled: boolean;
  reminderDaysBefore: number;
  reminderTime: string;
  timezone: string;
}

type FlagKey = 'enabled' | 'newAssignmentsEnabled' | 'remindersEnabled';

const REMINDER_TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

@Component({
  selector: 'app-settings-page',
  imports: [RouterLink],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
})
export class SettingsPage implements OnInit {
  private readonly api = inject(MidaApiService);
  private readonly live = inject(LiveUpdatesService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  readonly push = inject(PushService);

  readonly preferences = signal<NotificationPreferences | null>(null);
  readonly form = signal<PreferencesForm>(emptyForm());
  readonly status = signal<ImportStatus | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly saveError = signal<string | null>(null);
  readonly statusError = signal<string | null>(null);
  readonly savedNow = signal(false);
  readonly googleStatus = signal<GoogleStatus | null>(null);
  readonly googleClient = signal<GoogleClientStatus | null>(null);
  readonly importingClient = signal(false);
  readonly gmailQuery = signal('from:didup');
  readonly gmailEnabled = signal(true);
  readonly classroomEnabled = signal(true);
  readonly savingGoogle = signal(false);
  readonly googleError = signal<string | null>(null);

  readonly dirty = computed(() => {
    const saved = this.preferences();
    return saved ? !samePreferences(this.form(), saved) : false;
  });

  readonly canSave = computed(
    () => this.dirty() && !this.saving() && REMINDER_TIME_PATTERN.test(this.form().reminderTime),
  );

  readonly pushSupported = computed(() => this.push.browserSupported());
  readonly pushStatusLabel = computed(() => {
    if (!this.push.browserSupported()) {
      return 'Non supportate';
    }
    if (this.push.subscribed()) {
      return 'Attive su questo dispositivo';
    }
    if (this.push.permission() === 'denied') {
      return 'Bloccate dal browser';
    }
    if (this.push.permission() === 'granted') {
      return 'Pronte da attivare';
    }
    return 'Non attive';
  });

  readonly pushStatusTone = computed(() => {
    if (!this.push.browserSupported() || this.push.permission() === 'denied') {
      return 'red';
    }
    return this.push.subscribed() ? 'teal' : 'gray';
  });

  readonly permissionLabel = computed(() => {
    switch (this.push.permission()) {
      case 'granted':
        return 'Permesso concesso';
      case 'denied':
        return 'Permesso bloccato';
      case 'default':
        return 'Permesso non ancora richiesto';
      default:
        return 'Browser non supportato';
    }
  });

  readonly monitorTone = computed(() => {
    const state = this.status()?.state;
    return state ? MONITOR_STATE_TONE[state] : 'gray';
  });

  readonly monitorLabel = computed(() => {
    const state = this.status()?.state;
    return state ? MONITOR_STATE_LABELS[state] : 'Sconosciuto';
  });

  private lastEvent: unknown = null;
  private statusTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const event = this.live.lastEvent();
      if (event && event !== this.lastEvent) {
        this.lastEvent = event;
        this.scheduleStatusRefresh();
      }
    });

    effect(() => {
      if (this.live.connectionEpoch() > 0) {
        this.scheduleStatusRefresh();
      }
    });
  }

  ngOnInit(): void {
    void this.initialize().then(() => this.handleGoogleCallback());
  }

  async initialize(): Promise<void> {
    this.loading.set(true);
    await Promise.all([
      this.loadPreferences(),
      this.loadStatus(),
      this.loadGoogleStatus(),
    ]);
    this.loading.set(false);
    void this.push.initialize();
  }

  async loadPreferences(): Promise<void> {
    try {
      const preferences = await firstValueFrom(this.api.notificationPreferences());
      this.preferences.set(preferences);
      this.form.set(toForm(preferences));
      this.loadError.set(null);
    } catch (error) {
      this.loadError.set(apiErrorMessage(error));
    }
  }

  async loadStatus(): Promise<void> {
    try {
      this.status.set(await firstValueFrom(this.api.importStatus()));
      this.statusError.set(null);
    } catch (error) {
      this.statusError.set(apiErrorMessage(error));
    }
  }

  async loadGoogleStatus(): Promise<void> {
    try {
      const [client, status] = await Promise.all([
        firstValueFrom(this.api.googleClientStatus()),
        firstValueFrom(this.api.googleStatus()),
      ]);
      this.googleClient.set(client);
      this.googleStatus.set(status);
      const gmail = await firstValueFrom(this.api.gmailStatus());
      this.gmailQuery.set(gmail.gmailQuery);
      this.gmailEnabled.set(gmail.gmailEnabled);
      this.classroomEnabled.set(status.classroomEnabled);
      this.googleError.set(null);
    } catch (error) {
      this.googleError.set(apiErrorMessage(error));
    }
  }

  async importClientFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > 1024 * 1024) {
      this.googleError.set('Il file JSON del client OAuth è troppo grande.');
      return;
    }
    this.importingClient.set(true);
    this.googleError.set(null);
    try {
      const json = await file.text();
      const client = await firstValueFrom(this.api.importGoogleClient(json));
      this.googleClient.set(client);
      await this.loadGoogleStatus();
      this.toast.success('Client OAuth importato. Ora puoi collegare l’account Google.');
    } catch (error) {
      this.googleError.set(apiErrorMessage(error));
      this.toast.error('Importazione del JSON non riuscita.');
    } finally {
      this.importingClient.set(false);
    }
  }

  async removeGoogleClient(): Promise<void> {
    if (
      !window.confirm(
        'Rimuovere il client OAuth? L’account Google verrà scollegato e le credenziali eliminate dal database. Email e materiali già scaricati restano in MIDA.',
      )
    ) {
      return;
    }
    this.importingClient.set(true);
    try {
      await firstValueFrom(this.api.deleteGoogleClient());
      await this.loadGoogleStatus();
      this.toast.success('Client OAuth rimosso.');
    } catch (error) {
      this.googleError.set(apiErrorMessage(error));
      this.toast.error('Rimozione del client OAuth non riuscita.');
    } finally {
      this.importingClient.set(false);
    }
  }

  setGmailQuery(event: Event): void {
    this.gmailQuery.set((event.target as HTMLInputElement).value);
  }

  setGmailEnabled(event: Event): void {
    this.gmailEnabled.set((event.target as HTMLInputElement).checked);
  }

  setClassroomEnabled(event: Event): void {
    this.classroomEnabled.set((event.target as HTMLInputElement).checked);
  }

  async saveGoogle(): Promise<void> {
    this.savingGoogle.set(true);
    this.googleError.set(null);
    try {
      const status = await firstValueFrom(
        this.api.updateGoogleSettings({
          gmailQuery: this.gmailQuery(),
          gmailEnabled: this.gmailEnabled(),
          classroomEnabled: this.classroomEnabled(),
        }),
      );
      this.googleStatus.set(status);
      this.toast.success('Impostazioni Google salvate.');
    } catch (error) {
      this.googleError.set(apiErrorMessage(error));
      this.toast.error('Salvataggio delle impostazioni Google non riuscito.');
    } finally {
      this.savingGoogle.set(false);
    }
  }

  async disconnectGoogle(): Promise<void> {
    if (!window.confirm('Scollegare l’account Google? Le email e i materiali già scaricati restano in MIDA.')) {
      return;
    }
    try {
      await firstValueFrom(this.api.googleDisconnect());
      await this.loadGoogleStatus();
      this.toast.success('Account Google scollegato.');
    } catch (error) {
      this.googleError.set(apiErrorMessage(error));
      this.toast.error('Scollegamento di Google non riuscito.');
    }
  }

  googleStatusLabel(): string {
    const status = this.googleStatus();
    if (!status) {
      return 'Caricamento';
    }
    if (!status.configured) {
      return 'Da configurare';
    }
    if (!status.connected) {
      return 'Da collegare';
    }
    if (status.syncInProgress) {
      return 'Sincronizzazione';
    }
    switch (status.lastSyncStatus) {
      case 'OK':
        return 'Collegato';
      case 'REAUTH_REQUIRED':
        return 'Riconnessione';
      case 'ERROR':
        return 'Errore';
      case 'NEVER':
        return 'Da sincronizzare';
      default:
        return status.lastSyncStatus;
    }
  }

  googleStatusTone(): string {
    const status = this.googleStatus();
    if (!status?.connected) {
      return 'gray';
    }
    return status.lastSyncStatus === 'OK' && !status.syncInProgress ? 'teal' : 'amber';
  }

  setFlag(key: FlagKey, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.form.update((form) => {
      if (key === 'enabled') {
        return { ...form, enabled: checked };
      }
      if (key === 'newAssignmentsEnabled') {
        return { ...form, newAssignmentsEnabled: checked };
      }
      return { ...form, remindersEnabled: checked };
    });
    this.savedNow.set(false);
  }

  setDays(event: Event): void {
    const raw = Number((event.target as HTMLInputElement).value);
    const days = Number.isFinite(raw) ? Math.min(14, Math.max(0, Math.trunc(raw))) : 1;
    this.form.update((form) => ({ ...form, reminderDaysBefore: days }));
    this.savedNow.set(false);
  }

  setTime(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.form.update((form) => ({ ...form, reminderTime: value }));
    this.saveError.set(null);
    this.savedNow.set(false);
  }

  async save(): Promise<void> {
    const form = this.form();
    this.saveError.set(null);
    if (!REMINDER_TIME_PATTERN.test(form.reminderTime)) {
      this.saveError.set("L'orario del promemoria deve essere nel formato HH:mm.");
      return;
    }
    if (
      !Number.isInteger(form.reminderDaysBefore) ||
      form.reminderDaysBefore < 0 ||
      form.reminderDaysBefore > 14
    ) {
      this.saveError.set('I giorni di anticipo devono essere un numero intero tra 0 e 14.');
      return;
    }

    this.saving.set(true);
    try {
      const saved = await firstValueFrom(
        this.api.updateNotificationPreferences({
          enabled: form.enabled,
          newAssignmentsEnabled: form.newAssignmentsEnabled,
          remindersEnabled: form.remindersEnabled,
          reminderDaysBefore: form.reminderDaysBefore,
          reminderTime: form.reminderTime,
          timezone: form.timezone || 'Europe/Rome',
        }),
      );
      this.preferences.set(saved);
      this.form.set(toForm(saved));
      this.savedNow.set(true);
      this.toast.success('Preferenze di notifica salvate.');
    } catch (error) {
      this.saveError.set(apiErrorMessage(error));
      this.toast.error('Salvataggio delle preferenze non riuscito.');
    } finally {
      this.saving.set(false);
    }
  }

  reset(): void {
    const saved = this.preferences();
    if (saved) {
      this.form.set(toForm(saved));
      this.saveError.set(null);
      this.savedNow.set(false);
    }
  }

  async enablePush(): Promise<void> {
    await this.push.enable();
    await this.loadStatus();
  }

  async disablePush(): Promise<void> {
    await this.push.disable();
    await this.loadStatus();
  }

  async sendTest(): Promise<void> {
    await this.push.sendTest();
  }

  dateTime(value: string | null | undefined): string {
    return formatDateTime(value);
  }

  private handleGoogleCallback(): void {
    const params = this.route.snapshot.queryParamMap;
    const result = params.get('google');
    if (!result) {
      return;
    }
    if (result === 'connected') {
      this.toast.success('Account Google collegato. La sincronizzazione è stata avviata.');
      void this.loadGoogleStatus();
    } else if (result === 'not-configured') {
      const message =
        'Importa il file JSON del client OAuth da Impostazioni → Account Google e riprova.';
      this.toast.info(message);
      this.googleError.set(message);
    } else {
      const message = params.get('message') || 'Collegamento Google non riuscito.';
      this.toast.error(message);
      this.googleError.set(message);
    }
    const url = new URL(window.location.href);
    url.searchParams.delete('google');
    url.searchParams.delete('message');
    window.history.replaceState({}, '', url.pathname + url.search + url.hash);
  }

  private scheduleStatusRefresh(): void {
    if (this.statusTimer) {
      clearTimeout(this.statusTimer);
    }
    this.statusTimer = setTimeout(() => {
      this.statusTimer = null;
      void this.loadStatus();
    }, 220);
  }
}

function emptyForm(): PreferencesForm {
  return {
    enabled: true,
    newAssignmentsEnabled: true,
    remindersEnabled: true,
    reminderDaysBefore: 1,
    reminderTime: '18:00',
    timezone: 'Europe/Rome',
  };
}

function toForm(preferences: NotificationPreferences): PreferencesForm {
  return {
    enabled: preferences.enabled,
    newAssignmentsEnabled: preferences.newAssignmentsEnabled,
    remindersEnabled: preferences.remindersEnabled,
    reminderDaysBefore: preferences.reminderDaysBefore,
    reminderTime: preferences.reminderTime,
    timezone: preferences.timezone,
  };
}

function samePreferences(form: PreferencesForm, preferences: NotificationPreferences): boolean {
  return (
    form.enabled === preferences.enabled &&
    form.newAssignmentsEnabled === preferences.newAssignmentsEnabled &&
    form.remindersEnabled === preferences.remindersEnabled &&
    form.reminderDaysBefore === preferences.reminderDaysBefore &&
    form.reminderTime === preferences.reminderTime &&
    form.timezone === preferences.timezone
  );
}
