import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MidaApiService } from '../../core/api.service';
import { apiErrorMessage } from '../../core/homework-store';
import {
  DashboardStats,
  HomeworkFilters,
  ImportIssueType,
  ImportStatus,
} from '../../core/models';
import {
  addDays,
  dueLabel,
  formatDate,
  formatDateTime,
  formatTime,
  todayIso,
  truncate,
} from '../../core/format';
import { ISSUE_TYPE_LABELS, MONITOR_STATE_LABELS, MONITOR_STATE_TONE } from '../../core/labels';
import { LiveUpdatesService } from '../../core/live-updates.service';
import { subjectPalette } from '../../core/subject-colors';

@Component({
  selector: 'app-dashboard-page',
  imports: [RouterLink],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage implements OnInit {
  private readonly api = inject(MidaApiService);
  private readonly live = inject(LiveUpdatesService);

  readonly stats = signal<DashboardStats | null>(null);
  readonly monitor = signal<ImportStatus | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly emptyDatabase = computed(() => {
    const stats = this.stats();
    return stats !== null && stats.total === 0;
  });

  private requestId = 0;
  private lastEvent: unknown = null;

  constructor() {
    effect(() => {
      const event = this.live.lastEvent();
      if (event && event !== this.lastEvent) {
        this.lastEvent = event;
        void this.load(true);
      }
    });

    effect(() => {
      if (this.live.connectionEpoch() > 0) {
        void this.load(true);
      }
    });
  }

  ngOnInit(): void {
    void this.load();
  }

  async load(silent = false): Promise<void> {
    const requestId = ++this.requestId;
    if (!silent) {
      this.loading.set(true);
    }
    this.error.set(null);
    const today = todayIso();
    const yesterday = addDays(today, -1);
    const tomorrow = addDays(today, 1);
    const nextWeek = addDays(today, 6);
    const nextTwoWeeks = addDays(today, 14);
    const base: HomeworkFilters = {
      dateField: 'DUE',
      subject: '',
      teacher: '',
      completion: 'all',
      q: '',
    };

    try {
      const [
        total,
        open,
        completed,
        overdue,
        dueToday,
        dueTomorrow,
        nextSevenDays,
        upcoming,
        recentImports,
        openIssues,
        monitor,
      ] = await Promise.all([
        firstValueFrom(this.api.listHomework({ ...base, page: 0, size: 1 })),
        firstValueFrom(
          this.api.listHomework({ ...base, completion: 'open', page: 0, size: 1 }),
        ),
        firstValueFrom(
          this.api.listHomework({ ...base, completion: 'completed', page: 0, size: 1 }),
        ),
        firstValueFrom(
          this.api.listHomework({
            ...base,
            to: yesterday,
            completion: 'open',
            page: 0,
            size: 1,
          }),
        ),
        firstValueFrom(
          this.api.listHomework({
            ...base,
            from: today,
            to: today,
            completion: 'open',
            page: 0,
            size: 1,
          }),
        ),
        firstValueFrom(
          this.api.listHomework({
            ...base,
            from: tomorrow,
            to: tomorrow,
            completion: 'open',
            page: 0,
            size: 1,
          }),
        ),
        firstValueFrom(
          this.api.listHomework({
            ...base,
            from: today,
            to: nextWeek,
            completion: 'open',
            page: 0,
            size: 1,
          }),
        ),
        firstValueFrom(
          this.api.calendarHomework({
            ...base,
            from: today,
            to: nextTwoWeeks,
            completion: 'open',
          }),
        ),
        firstValueFrom(this.api.imports(0, 5)),
        firstValueFrom(this.api.issues('OPEN', 0, 5)),
        firstValueFrom(this.api.importStatus()),
      ]);

      if (requestId !== this.requestId) {
        return;
      }
      this.stats.set({
        total: total.totalElements,
        open: open.totalElements,
        completed: completed.totalElements,
        overdue: overdue.totalElements,
        today: dueToday.totalElements,
        tomorrow: dueTomorrow.totalElements,
        nextSevenDays: nextSevenDays.totalElements,
        upcoming: upcoming.slice(0, 9),
        recentImports: recentImports.content,
        openIssues: openIssues.content,
      });
      this.monitor.set(monitor);
    } catch (error) {
      if (requestId === this.requestId) {
        this.error.set(apiErrorMessage(error));
      }
    } finally {
      if (requestId === this.requestId) {
        this.loading.set(false);
      }
    }
  }

  palette(subject: string) {
    return subjectPalette(subject);
  }

  issueLabel(type: ImportIssueType): string {
    return ISSUE_TYPE_LABELS[type] ?? type;
  }

  stateLabel(state: ImportStatus['state']): string {
    return MONITOR_STATE_LABELS[state] ?? state;
  }

  stateTone(state: ImportStatus['state']): string {
    return MONITOR_STATE_TONE[state] ?? 'gray';
  }

  dueText(date: string | null | undefined): string {
    return dueLabel(date);
  }

  shortDescription(value: string): string {
    return truncate(value, 100);
  }

  date(value: string): string {
    return formatDate(value);
  }

  dateTime(value: string | null): string {
    return formatDateTime(value);
  }

  time(value: string | null): string {
    return formatTime(value);
  }
}
