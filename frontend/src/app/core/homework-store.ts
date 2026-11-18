import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MidaApiService } from './api.service';
import { LiveUpdatesService } from './live-updates.service';
import {
  HomeworkFilters,
  HomeworkUpdatedPayload,
  HomeworkView,
  ImportCompletedPayload,
  ImportStatus,
  MidaEvent,
} from './models';
import { ToastService } from './toast.service';

const DEFAULT_FILTERS: HomeworkFilters = {
  dateField: 'DUE',
  subject: '',
  teacher: '',
  completion: 'all',
  q: '',
};

@Injectable({ providedIn: 'root' })
export class HomeworkStore {
  private readonly api = inject(MidaApiService);
  private readonly live = inject(LiveUpdatesService);
  private readonly toast = inject(ToastService);

  readonly filters = signal<HomeworkFilters>({ ...DEFAULT_FILTERS });
  readonly range = signal<{ from: string; to: string } | null>(null);
  readonly assignments = signal<HomeworkView[]>([]);
  readonly subjects = signal<string[]>([]);
  readonly teachers = signal<string[]>([]);
  readonly status = signal<ImportStatus | null>(null);
  readonly loading = signal(false);
  readonly refreshing = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastLoadedAt = signal<Date | null>(null);
  readonly selectedId = signal<string | null>(null);

  readonly selected = computed(() => {
    const id = this.selectedId();
    return id ? this.assignments().find((item) => item.id === id) ?? null : null;
  });
  readonly totalAssignments = computed(() => this.status()?.totalAssignments ?? 0);
  readonly openVisible = computed(() => this.assignments().filter((item) => !item.completed).length);
  readonly completedVisible = computed(() => this.assignments().filter((item) => item.completed).length);
  readonly filtersActive = computed(() => {
    const filters = this.filters();
    return (
      filters.dateField !== DEFAULT_FILTERS.dateField ||
      filters.subject !== DEFAULT_FILTERS.subject ||
      filters.teacher !== DEFAULT_FILTERS.teacher ||
      filters.completion !== DEFAULT_FILTERS.completion ||
      filters.q.trim() !== DEFAULT_FILTERS.q
    );
  });

  private initialized = false;
  private calendarRequest = 0;
  private readonly loadedEvent = signal<MidaEvent | null>(null);

  constructor() {
    effect(() => {
      const event = this.live.lastEvent();
      if (!event || event === this.loadedEvent()) {
        return;
      }
      this.loadedEvent.set(event);
      this.handleEvent(event);
    });

    effect(() => {
      const epoch = this.live.connectionEpoch();
      if (epoch > 0) {
        void this.reloadFromServer(true);
      }
    });
  }

  initialize(): void {
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    void this.loadReferenceData();
  }

  setRange(from: string, to: string): void {
    const current = this.range();
    if (current?.from === from && current.to === to) {
      return;
    }
    this.range.set({ from, to });
    void this.loadCalendar();
  }

  setFilters(partial: Partial<HomeworkFilters>): void {
    this.filters.update((filters) => ({ ...filters, ...partial }));
    void this.loadCalendar();
  }

  resetFilters(): void {
    this.filters.set({ ...DEFAULT_FILTERS });
    void this.loadCalendar();
  }

  select(id: string | null): void {
    this.selectedId.set(id);
  }

  /**
   * Opens a specific assignment from a notification deep link. The assignment
   * may not be part of the currently loaded calendar range yet, so it is
   * fetched directly and temporarily added to the visible collection.
   */
  async focusAssignment(id: string): Promise<HomeworkView | null> {
    try {
      const item = await firstValueFrom(this.api.homework(id));
      this.assignments.update((items) =>
        items.some((existing) => existing.id === item.id) ? items : [...items, item],
      );
      this.select(item.id);
      return item;
    } catch (error) {
      this.toast.error(apiErrorMessage(error));
      return null;
    }
  }

  async reloadAll(): Promise<void> {
    await this.reloadFromServer(false);
  }

  async loadCalendar(options: { silent?: boolean } = {}): Promise<void> {
    const range = this.range();
    if (!range) {
      return;
    }
    const requestId = ++this.calendarRequest;
    if (options.silent) {
      this.refreshing.set(true);
    } else {
      this.loading.set(true);
    }
    this.error.set(null);
    const filters = this.filters();
    try {
      const assignments = await firstValueFrom(
        this.api.calendarHomework({
          from: range.from,
          to: range.to,
          ...filters,
        }),
      );
      if (requestId !== this.calendarRequest) {
        return;
      }
      this.assignments.set(assignments);
      this.lastLoadedAt.set(new Date());
    } catch (error) {
      if (requestId === this.calendarRequest) {
        this.error.set(apiErrorMessage(error));
      }
    } finally {
      if (requestId === this.calendarRequest) {
        this.loading.set(false);
        this.refreshing.set(false);
      }
    }
  }

  async toggleCompletion(item: HomeworkView): Promise<void> {
    const previous = item;
    this.patchAssignment(item.id, {
      ...item,
      completed: !item.completed,
      completedAt: !item.completed ? new Date().toISOString() : null,
    });
    try {
      const updated = await firstValueFrom(this.api.setCompletion(item.id, !item.completed));
      this.replaceAssignment(updated);
      this.toast.success(updated.completed ? 'Compito segnato come completato.' : 'Compito riaperto.');
    } catch (error) {
      this.replaceAssignment(previous);
      this.toast.error(apiErrorMessage(error));
    }
  }

  async loadStatus(): Promise<void> {
    try {
      this.status.set(await firstValueFrom(this.api.importStatus()));
    } catch {
      // The main error is surfaced by the page that owns the request.
    }
  }

  private async loadReferenceData(): Promise<void> {
    const [subjects, teachers, status] = await Promise.allSettled([
      firstValueFrom(this.api.subjects()),
      firstValueFrom(this.api.teachers()),
      firstValueFrom(this.api.importStatus()),
    ]);
    if (subjects.status === 'fulfilled') {
      this.subjects.set(subjects.value);
    }
    if (teachers.status === 'fulfilled') {
      this.teachers.set(teachers.value);
    }
    if (status.status === 'fulfilled') {
      this.status.set(status.value);
    }
  }

  private async reloadFromServer(silent: boolean): Promise<void> {
    await Promise.allSettled([this.loadReferenceData(), this.loadCalendar({ silent })]);
  }

  private handleEvent(event: MidaEvent): void {
    switch (event.type) {
      case 'import.completed': {
        const data = event.data as ImportCompletedPayload;
        const counts = Object.entries(data?.subjectCounts ?? {})
          .map(([subject, count]) => `${subject} (${count})`)
          .join(', ');
        const total = data?.assignmentCount ?? 0;
        const summary = total === 1 ? 'Importato 1 compito' : `Importati ${total} compiti`;
        this.toast.success(`${summary}${counts ? `: ${counts}` : ''}.`);
        void this.reloadFromServer(true);
        break;
      }
      case 'homework.updated': {
        const data = event.data as HomeworkUpdatedPayload;
        if (data?.assignmentId) {
          const existing = this.assignments().find((item) => item.id === data.assignmentId);
          if (existing) {
            this.patchAssignment(data.assignmentId, {
              ...existing,
              completed: data.completed,
              completedAt: data.completed ? new Date().toISOString() : null,
            });
          }
        }
        break;
      }
      case 'import.issue': {
        const data = event.data as { description?: string } | null;
        if (data?.description) {
          this.toast.error(`Anomalia di importazione: ${data.description}`);
        }
        void this.loadStatus();
        break;
      }
      case 'import.status': {
        const status = event.data as ImportStatus;
        if (status?.state) {
          this.status.set(status);
        }
        break;
      }
      default:
        break;
    }
  }

  private patchAssignment(id: string, value: HomeworkView): void {
    this.assignments.update((items) => items.map((item) => (item.id === id ? value : item)));
  }

  private replaceAssignment(value: HomeworkView): void {
    this.assignments.update((items) => items.map((item) => (item.id === value.id ? value : item)));
  }
}

export function apiErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const detail = error.error?.detail;
    const title = error.error?.title;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
    if (typeof title === 'string' && title.trim()) {
      return title;
    }
    if (error.status === 0) {
      return 'Impossibile raggiungere il backend MIDA. Verifica che Docker Compose sia avviato.';
    }
    return `Errore ${error.status}: richiesta non riuscita.`;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return 'Si è verificato un errore inatteso.';
}
