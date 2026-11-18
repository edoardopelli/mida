import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MidaApiService } from '../../core/api.service';
import { apiErrorMessage } from '../../core/homework-store';
import {
  DiaryImportSummary,
  ImportIssue,
  ImportIssueStatus,
  ImportIssueType,
  ImportStatus,
  ImportSummary,
  PageResponse,
} from '../../core/models';
import { formatDateTime, formatTime } from '../../core/format';
import { ISSUE_TYPE_LABELS, MONITOR_STATE_LABELS, MONITOR_STATE_TONE } from '../../core/labels';
import { LiveUpdatesService } from '../../core/live-updates.service';

type IssueFilter = ImportIssueStatus | 'ALL';

@Component({
  selector: 'app-imports-page',
  imports: [RouterLink],
  templateUrl: './imports-page.html',
  styleUrl: './imports-page.scss',
})
export class ImportsPage implements OnInit {
  private readonly api = inject(MidaApiService);
  private readonly live = inject(LiveUpdatesService);

  readonly imports = signal<PageResponse<ImportSummary> | null>(null);
  readonly diaryImports = signal<PageResponse<DiaryImportSummary> | null>(null);
  readonly issues = signal<PageResponse<ImportIssue> | null>(null);
  readonly status = signal<ImportStatus | null>(null);
  readonly loading = signal(true);
  readonly diaryLoading = signal(true);
  readonly issuesLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly issueFilter = signal<IssueFilter>('ALL');
  readonly importPage = signal(0);
  readonly diaryImportPage = signal(0);
  readonly issuePage = signal(0);

  readonly canPreviousImport = computed(() => this.importPage() > 0);
  readonly canNextImport = computed(() => {
    const page = this.imports();
    return page ? page.page + 1 < page.totalPages : false;
  });
  readonly canPreviousDiaryImport = computed(() => this.diaryImportPage() > 0);
  readonly canNextDiaryImport = computed(() => {
    const page = this.diaryImports();
    return page ? page.page + 1 < page.totalPages : false;
  });
  readonly canPreviousIssue = computed(() => this.issuePage() > 0);
  readonly canNextIssue = computed(() => {
    const page = this.issues();
    return page ? page.page + 1 < page.totalPages : false;
  });

  private lastEvent: unknown = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const event = this.live.lastEvent();
      if (event && event !== this.lastEvent) {
        this.lastEvent = event;
        this.scheduleRefresh();
      }
    });

    effect(() => {
      if (this.live.connectionEpoch() > 0) {
        this.scheduleRefresh();
      }
    });
  }

  ngOnInit(): void {
    void this.loadAll();
  }

  async loadAll(): Promise<void> {
    await Promise.all([
      this.loadImports(false),
      this.loadDiaryImports(false),
      this.loadIssues(false),
      this.loadStatus(),
    ]);
  }

  async loadImports(showLoading = true): Promise<void> {
    if (showLoading) {
      this.loading.set(true);
    }
    try {
      this.imports.set(await firstValueFrom(this.api.imports(this.importPage(), 10)));
      this.error.set(null);
    } catch (error) {
      this.error.set(apiErrorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  async loadDiaryImports(showLoading = true): Promise<void> {
    if (showLoading) {
      this.diaryLoading.set(true);
    }
    try {
      this.diaryImports.set(
        await firstValueFrom(this.api.diaryImports(this.diaryImportPage(), 10)),
      );
      this.error.set(null);
    } catch (error) {
      this.error.set(apiErrorMessage(error));
    } finally {
      this.diaryLoading.set(false);
    }
  }

  async loadIssues(showLoading = true): Promise<void> {
    if (showLoading) {
      this.issuesLoading.set(true);
    }
    try {
      this.issues.set(
        await firstValueFrom(this.api.issues(this.issueFilter(), this.issuePage(), 10)),
      );
    } catch (error) {
      this.error.set(apiErrorMessage(error));
    } finally {
      this.issuesLoading.set(false);
    }
  }

  async loadStatus(): Promise<void> {
    try {
      this.status.set(await firstValueFrom(this.api.importStatus()));
    } catch (error) {
      this.error.set(apiErrorMessage(error));
    }
  }

  setIssueFilter(filter: IssueFilter): void {
    if (this.issueFilter() === filter) {
      return;
    }
    this.issueFilter.set(filter);
    this.issuePage.set(0);
    void this.loadIssues();
  }

  previousImports(): void {
    if (!this.canPreviousImport()) {
      return;
    }
    this.importPage.update((page) => page - 1);
    void this.loadImports();
  }

  nextImports(): void {
    if (!this.canNextImport()) {
      return;
    }
    this.importPage.update((page) => page + 1);
    void this.loadImports();
  }

  previousDiaryImports(): void {
    if (!this.canPreviousDiaryImport()) {
      return;
    }
    this.diaryImportPage.update((page) => page - 1);
    void this.loadDiaryImports();
  }

  nextDiaryImports(): void {
    if (!this.canNextDiaryImport()) {
      return;
    }
    this.diaryImportPage.update((page) => page + 1);
    void this.loadDiaryImports();
  }

  previousIssues(): void {
    if (!this.canPreviousIssue()) {
      return;
    }
    this.issuePage.update((page) => page - 1);
    void this.loadIssues();
  }

  nextIssues(): void {
    if (!this.canNextIssue()) {
      return;
    }
    this.issuePage.update((page) => page + 1);
    void this.loadIssues();
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

  dateTime(value: string | null | undefined): string {
    return formatDateTime(value);
  }

  time(value: string | null | undefined): string {
    return formatTime(value);
  }

  shortSha(value: string | null | undefined): string {
    return value ? `${value.slice(0, 10)}…` : '—';
  }

  private scheduleRefresh(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }
    this.refreshTimer = setTimeout(() => {
      this.refreshTimer = null;
      void this.loadAll();
    }, 220);
  }
}
