import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MidaApiService } from './api.service';
import { ToastService } from './toast.service';
import { LiveUpdatesService } from './live-updates.service';
import { todayIso } from './format';
import { DiaryDateSummary, DiaryEntry, DiaryFilters, DiaryImportedPayload, MidaEvent } from './models';
import { apiErrorMessage } from './homework-store';

const DEFAULT_FILTERS: DiaryFilters = {
  subject: '',
  teacher: '',
  q: '',
};

@Injectable({ providedIn: 'root' })
export class DiaryStore {
  private readonly api = inject(MidaApiService);
  private readonly live = inject(LiveUpdatesService);
  private readonly toast = inject(ToastService);

  readonly dates = signal<DiaryDateSummary[]>([]);
  readonly entries = signal<DiaryEntry[]>([]);
  readonly selectedDate = signal<string | null>(null);
  readonly subjects = signal<string[]>([]);
  readonly teachers = signal<string[]>([]);
  readonly filters = signal<DiaryFilters>({ ...DEFAULT_FILTERS });
  readonly loading = signal(false);
  readonly ready = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastLoadedAt = signal<Date | null>(null);

  readonly hasEntries = computed(() => this.dates().length > 0);
  readonly selectedSummary = computed(() => {
    const date = this.selectedDate();
    return date ? this.dates().find((item) => item.date === date) ?? null : null;
  });
  readonly filtersActive = computed(() => {
    const filters = this.filters();
    return (
      filters.subject !== DEFAULT_FILTERS.subject ||
      filters.teacher !== DEFAULT_FILTERS.teacher ||
      filters.q.trim() !== DEFAULT_FILTERS.q
    );
  });
  readonly hasOlder = computed(() => {
    const date = this.selectedDate();
    return !!date && this.dates().some((item) => item.date < date);
  });
  readonly hasNewer = computed(() => {
    const date = this.selectedDate();
    return !!date && this.dates().some((item) => item.date > date);
  });
  readonly isToday = computed(() => this.selectedDate() === todayIso());

  private requestId = 0;
  private loadedEvent: MidaEvent | null = null;

  constructor() {
    // The store may be created when the user opens the diary page after a
    // previous diary event: do not replay an old toast or refresh.
    this.loadedEvent = this.live.lastEvent();
    effect(() => {
      const event = this.live.lastEvent();
      if (!event || event === this.loadedEvent) {
        return;
      }
      this.loadedEvent = event;
      if (event.type === 'diary.imported') {
        const payload = event.data as DiaryImportedPayload;
        const count = payload?.entryCount ?? 0;
        this.toast.success(
          count === 1 ? 'Importata 1 attività nel diario.' : `Importate ${count} attività nel diario.`,
        );
        const preferred = payload?.activityDates?.length
          ? [...payload.activityDates].sort().at(-1)
          : undefined;
        const previousLatest = this.dates()[0]?.date;
        const followNewDate =
          this.dates().length === 0 ||
          !this.selectedDate() ||
          this.selectedDate() === previousLatest;
        void this.reloadFromServer(preferred, followNewDate);
      }
    });

    effect(() => {
      if (this.live.connectionEpoch() > 0) {
        void this.reloadFromServer();
      }
    });
  }

  async initialize(): Promise<void> {
    try {
      await Promise.all([this.loadReferenceData(), this.loadDates()]);
      await this.loadDay();
    } finally {
      this.ready.set(true);
    }
  }

  selectDate(date: string | null): void {
    if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      return;
    }
    this.selectedDate.set(date);
    void this.loadDay();
  }

  goOlder(): void {
    const date = this.selectedDate();
    if (!date) {
      return;
    }
    const older = this.dates().find((item) => item.date < date);
    if (older) {
      this.selectDate(older.date);
    }
  }

  goNewer(): void {
    const date = this.selectedDate();
    if (!date) {
      return;
    }
    const newer = [...this.dates()].reverse().find((item) => item.date > date);
    if (newer) {
      this.selectDate(newer.date);
    }
  }

  goToday(): void {
    this.selectDate(todayIso());
  }

  setFilters(partial: Partial<DiaryFilters>): void {
    this.filters.update((filters) => ({ ...filters, ...partial }));
    void this.loadDay();
  }

  resetFilters(): void {
    this.filters.set({ ...DEFAULT_FILTERS });
    void this.loadDay();
  }

  async reloadAll(): Promise<void> {
    await this.reloadFromServer();
  }

  async loadDay(): Promise<void> {
    const date = this.selectedDate();
    if (!date) {
      this.entries.set([]);
      return;
    }
    const request = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    try {
      const entries = await firstValueFrom(this.api.diaryDay(date, this.filters()));
      if (request !== this.requestId) {
        return;
      }
      this.entries.set(entries);
      this.lastLoadedAt.set(new Date());
    } catch (error) {
      if (request === this.requestId) {
        this.error.set(apiErrorMessage(error));
      }
    } finally {
      if (request === this.requestId) {
        this.loading.set(false);
      }
    }
  }

  private async loadDates(): Promise<void> {
    try {
      const dates = await firstValueFrom(this.api.diaryDates());
      this.dates.set(dates);
      if (!this.selectedDate()) {
        this.selectedDate.set(dates[0]?.date ?? todayIso());
      }
    } catch (error) {
      this.error.set(apiErrorMessage(error));
    }
  }

  private async loadReferenceData(): Promise<void> {
    const [subjects, teachers] = await Promise.allSettled([
      firstValueFrom(this.api.diarySubjects()),
      firstValueFrom(this.api.diaryTeachers()),
    ]);
    if (subjects.status === 'fulfilled') {
      this.subjects.set(subjects.value);
    }
    if (teachers.status === 'fulfilled') {
      this.teachers.set(teachers.value);
    }
  }

  private async reloadFromServer(preferredDate?: string, followPreferred = false): Promise<void> {
    await Promise.all([this.loadDates(), this.loadReferenceData()]);
    if (
      preferredDate &&
      (followPreferred || !this.dates().some((item) => item.date === this.selectedDate()))
    ) {
      this.selectedDate.set(preferredDate);
    }
    await this.loadDay();
  }
}
