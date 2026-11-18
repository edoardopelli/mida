import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MidaApiService } from './api.service';
import { apiErrorMessage } from './homework-store';
import { LiveUpdatesService } from './live-updates.service';
import { ToastService } from './toast.service';
import {
  ClassroomCourse,
  ClassroomFilters,
  ClassroomItem,
  ClassroomItemSummary,
  EmailFilters,
  EmailMessage,
  EmailSummary,
  GoogleStatus,
  GoogleSyncPayload,
  GmailStatus,
  MailSyncPayload,
  MidaEvent,
} from './models';

export type MailTab = 'email' | 'classroom';

const DEFAULT_EMAIL_FILTERS: EmailFilters = { from: '', q: '' };
const DEFAULT_CLASSROOM_FILTERS: ClassroomFilters = { courseId: '', type: '', q: '' };

@Injectable({ providedIn: 'root' })
export class MailStore {
  private readonly api = inject(MidaApiService);
  private readonly live = inject(LiveUpdatesService);
  private readonly toast = inject(ToastService);

  readonly gmailStatus = signal<GmailStatus | null>(null);
  readonly classroomStatus = signal<GoogleStatus | null>(null);
  readonly tab = signal<MailTab>('email');

  readonly emails = signal<EmailSummary[]>([]);
  readonly emailFilters = signal<EmailFilters>({ ...DEFAULT_EMAIL_FILTERS });
  readonly emailPage = signal(0);
  readonly emailTotal = signal(0);
  readonly emailTotalPages = signal(0);
  readonly selectedEmail = signal<EmailMessage | null>(null);

  readonly classroomItems = signal<ClassroomItemSummary[]>([]);
  readonly classroomCourses = signal<ClassroomCourse[]>([]);
  readonly classroomFilters = signal<ClassroomFilters>({ ...DEFAULT_CLASSROOM_FILTERS });
  readonly classroomPage = signal(0);
  readonly classroomTotal = signal(0);
  readonly classroomTotalPages = signal(0);
  readonly selectedClassroomItem = signal<ClassroomItem | null>(null);

  readonly loading = signal(false);
  readonly detailLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly ready = signal(false);

  readonly mailConfigured = computed(() => this.gmailStatus()?.connected === true);
  readonly classroomConfigured = computed(() => this.classroomStatus()?.configured === true);
  readonly classroomConnected = computed(() => this.classroomStatus()?.connected === true);
  readonly mailSyncing = computed(() => this.gmailStatus()?.syncInProgress === true);
  readonly classroomSyncing = computed(() => this.classroomStatus()?.syncInProgress === true);
  readonly emailFiltersActive = computed(() => {
    const filters = this.emailFilters();
    return !!filters.from.trim() || !!filters.q.trim();
  });
  readonly classroomFiltersActive = computed(() => {
    const filters = this.classroomFilters();
    return !!filters.courseId || !!filters.type || !!filters.q.trim();
  });

  private emailRequest = 0;
  private classroomRequest = 0;
  private loadedEvent: MidaEvent | null = null;

  constructor() {
    this.loadedEvent = this.live.lastEvent();
    effect(() => {
      const event = this.live.lastEvent();
      if (!event || event === this.loadedEvent) {
        return;
      }
      this.loadedEvent = event;
      if (event.type === 'mail.sync') {
        const payload = event.data as MailSyncPayload;
        if ((payload?.newEmails ?? 0) > 0 || (payload?.newAttachments ?? 0) > 0) {
          this.toast.success(
            `Posta sincronizzata: ${payload?.newEmails ?? 0} nuove email, ` +
              `${payload?.newAttachments ?? 0} nuovi allegati.`,
          );
        } else if ((payload?.errors ?? 0) > 0) {
          this.toast.error('Sincronizzazione della posta completata con errori.');
        }
        void this.reloadAfterMailSync();
      } else if (event.type === 'google.sync') {
        const payload = event.data as GoogleSyncPayload;
        if ((payload?.newClassroomItems ?? 0) > 0) {
          this.toast.success(
            `Classroom sincronizzato: ${payload?.newClassroomItems ?? 0} nuovi elementi.`,
          );
        } else if ((payload?.errors ?? 0) > 0) {
          this.toast.error('Sincronizzazione Classroom completata con errori.');
        }
        void this.reloadAfterClassroomSync();
      }
    });

    effect(() => {
      if (this.live.connectionEpoch() > 0) {
        void this.reloadAll();
      }
    });
  }

  async initialize(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      await Promise.all([
        this.loadGmailStatus(),
        this.loadClassroomStatus(),
        this.loadEmails(),
        this.loadClassroom(),
        this.loadCourses(),
      ]);
    } finally {
      this.loading.set(false);
      this.ready.set(true);
    }
  }

  setTab(tab: MailTab): void {
    this.tab.set(tab);
    if (tab === 'email' && this.emails().length === 0) {
      void this.loadEmails();
    }
    if (tab === 'classroom' && this.classroomItems().length === 0) {
      void this.loadClassroom();
      if (this.classroomCourses().length === 0) {
        void this.loadCourses();
      }
    }
  }

  async loadGmailStatus(): Promise<void> {
    try {
      this.gmailStatus.set(await firstValueFrom(this.api.gmailStatus()));
    } catch (error) {
      this.error.set(apiErrorMessage(error));
    }
  }

  async loadClassroomStatus(): Promise<void> {
    try {
      this.classroomStatus.set(await firstValueFrom(this.api.googleStatus()));
    } catch (error) {
      this.error.set(apiErrorMessage(error));
    }
  }

  async loadEmails(page = this.emailPage()): Promise<void> {
    const request = ++this.emailRequest;
    this.loading.set(true);
    this.error.set(null);
    try {
      const response = await firstValueFrom(
        this.api.googleEmails(this.emailFilters(), page, 20),
      );
      if (request !== this.emailRequest) {
        return;
      }
      this.emails.set(response.content);
      this.emailPage.set(response.page);
      this.emailTotal.set(response.totalElements);
      this.emailTotalPages.set(response.totalPages);
    } catch (error) {
      if (request === this.emailRequest) {
        this.error.set(apiErrorMessage(error));
      }
    } finally {
      if (request === this.emailRequest) {
        this.loading.set(false);
      }
    }
  }

  async loadClassroom(page = this.classroomPage()): Promise<void> {
    const request = ++this.classroomRequest;
    this.loading.set(true);
    this.error.set(null);
    try {
      const response = await firstValueFrom(
        this.api.googleClassroom(this.classroomFilters(), page, 20),
      );
      if (request !== this.classroomRequest) {
        return;
      }
      this.classroomItems.set(response.content);
      this.classroomPage.set(response.page);
      this.classroomTotal.set(response.totalElements);
      this.classroomTotalPages.set(response.totalPages);
    } catch (error) {
      if (request === this.classroomRequest) {
        this.error.set(apiErrorMessage(error));
      }
    } finally {
      if (request === this.classroomRequest) {
        this.loading.set(false);
      }
    }
  }

  async loadCourses(): Promise<void> {
    try {
      this.classroomCourses.set(
        await firstValueFrom(this.api.googleClassroomCourses()),
      );
    } catch {
      // I corsi sono solo un filtro: l'elenco principale resta utilizzabile.
    }
  }

  setEmailFilters(partial: Partial<EmailFilters>): void {
    this.emailFilters.update((filters) => ({ ...filters, ...partial }));
    void this.loadEmails(0);
  }

  resetEmailFilters(): void {
    this.emailFilters.set({ ...DEFAULT_EMAIL_FILTERS });
    void this.loadEmails(0);
  }

  setClassroomFilters(partial: Partial<ClassroomFilters>): void {
    this.classroomFilters.update((filters) => ({ ...filters, ...partial }));
    void this.loadClassroom(0);
  }

  resetClassroomFilters(): void {
    this.classroomFilters.set({ ...DEFAULT_CLASSROOM_FILTERS });
    void this.loadClassroom(0);
  }

  async openEmail(id: string): Promise<void> {
    this.selectedClassroomItem.set(null);
    this.detailLoading.set(true);
    try {
      this.selectedEmail.set(await firstValueFrom(this.api.googleEmail(id)));
    } catch (error) {
      this.toast.error(apiErrorMessage(error));
    } finally {
      this.detailLoading.set(false);
    }
  }

  closeEmail(): void {
    this.selectedEmail.set(null);
  }

  async openClassroomItem(id: string): Promise<void> {
    this.selectedEmail.set(null);
    this.detailLoading.set(true);
    try {
      this.selectedClassroomItem.set(
        await firstValueFrom(this.api.googleClassroomItem(id)),
      );
    } catch (error) {
      this.toast.error(apiErrorMessage(error));
    } finally {
      this.detailLoading.set(false);
    }
  }

  closeClassroomItem(): void {
    this.selectedClassroomItem.set(null);
  }

  async syncMail(): Promise<void> {
    if (this.mailSyncing()) {
      return;
    }
    try {
      const response = await firstValueFrom(this.api.syncGmail());
      this.gmailStatus.update((status) =>
        status ? { ...status, syncInProgress: true } : status,
      );
      this.toast.info(
        response.started
          ? 'Sincronizzazione della posta avviata.'
          : 'Sincronizzazione della posta già in corso.',
      );
      this.pollMail();
    } catch (error) {
      this.toast.error(apiErrorMessage(error));
    }
  }

  async syncClassroom(): Promise<void> {
    if (this.classroomSyncing()) {
      return;
    }
    try {
      const response = await firstValueFrom(this.api.googleSync());
      this.classroomStatus.update((status) =>
        status ? { ...status, syncInProgress: true } : status,
      );
      this.toast.info(
        response.started
          ? 'Sincronizzazione Classroom avviata.'
          : 'Sincronizzazione Classroom già in corso.',
      );
      this.pollClassroom();
    } catch (error) {
      this.toast.error(apiErrorMessage(error));
    }
  }

  async updateClassroomSettings(enabled: boolean): Promise<void> {
    try {
      this.classroomStatus.set(
        await firstValueFrom(this.api.updateGoogleSettings({ classroomEnabled: enabled })),
      );
      this.toast.success('Impostazioni Classroom aggiornate.');
    } catch (error) {
      this.toast.error(apiErrorMessage(error));
    }
  }

  async disconnectClassroom(): Promise<void> {
    try {
      await firstValueFrom(this.api.googleDisconnect());
      this.classroomItems.set([]);
      this.classroomCourses.set([]);
      this.selectedClassroomItem.set(null);
      await this.loadClassroomStatus();
      this.toast.success('Account Google Classroom scollegato.');
    } catch (error) {
      this.toast.error(apiErrorMessage(error));
    }
  }

  attachmentUrl(id: string | null): string | null {
    return id ? this.api.attachmentUrl(id) : null;
  }

  private async reloadAll(): Promise<void> {
    await Promise.all([
      this.loadGmailStatus(),
      this.loadClassroomStatus(),
      this.loadEmails(),
      this.loadClassroom(),
      this.loadCourses(),
    ]);
  }

  private async reloadAfterMailSync(): Promise<void> {
    await Promise.all([this.loadGmailStatus(), this.loadEmails()]);
    if (this.selectedEmail()) {
      await this.openEmail(this.selectedEmail()!.id);
    }
  }

  private async reloadAfterClassroomSync(): Promise<void> {
    await Promise.all([this.loadClassroomStatus(), this.loadClassroom(), this.loadCourses()]);
    if (this.selectedClassroomItem()) {
      await this.openClassroomItem(this.selectedClassroomItem()!.id);
    }
  }

  private pollMail(attempt = 0): void {
    if (attempt > 60) {
      return;
    }
    setTimeout(async () => {
      await this.loadGmailStatus();
      if (this.mailSyncing()) {
        this.pollMail(attempt + 1);
      } else {
        await this.reloadAfterMailSync();
      }
    }, 1500);
  }

  private pollClassroom(attempt = 0): void {
    if (attempt > 60) {
      return;
    }
    setTimeout(async () => {
      await this.loadClassroomStatus();
      if (this.classroomSyncing()) {
        this.pollClassroom(attempt + 1);
      } else {
        await this.reloadAfterClassroomSync();
      }
    }, 1500);
  }
}
