import { Component, HostListener, OnDestroy, AfterViewInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FullCalendarComponent, FullCalendarModule } from '@fullcalendar/angular';
import type { CalendarOptions, EventInput } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/angular/daygrid';
import listPlugin from '@fullcalendar/angular/list';
import classicThemePlugin from '@fullcalendar/angular/themes/classic';
import timeGridPlugin from '@fullcalendar/angular/timegrid';
import itLocale from 'fullcalendar/locales/it';
import { firstValueFrom } from 'rxjs';
import { MidaApiService } from '../../core/api.service';
import { HomeworkStore, apiErrorMessage } from '../../core/homework-store';
import { HomeworkView } from '../../core/models';
import { dueLabel, formatDate, formatLongDate, toIsoDate, todayIso, truncate } from '../../core/format';
import { subjectPalette } from '../../core/subject-colors';

type CalendarView = 'dayGridMonth' | 'timeGridWeek' | 'timeGridDay' | 'listMonth';
type AgendaSort = 'date-asc' | 'date-desc';

const CALENDAR_VIEWS: ReadonlyArray<{ id: CalendarView; label: string }> = [
  { id: 'dayGridMonth', label: 'Mese' },
  { id: 'timeGridWeek', label: 'Settimana' },
  { id: 'timeGridDay', label: 'Giorno' },
  { id: 'listMonth', label: 'Agenda' },
];

const VIEW_STORAGE_KEY = 'mida.calendar.view';

function isCalendarView(value: string | null): value is CalendarView {
  return CALENDAR_VIEWS.some((view) => view.id === value);
}

function readStoredView(): CalendarView {
  if (typeof window === 'undefined') {
    return 'dayGridMonth';
  }
  try {
    const stored = window.localStorage.getItem(VIEW_STORAGE_KEY);
    return isCalendarView(stored) ? stored : 'dayGridMonth';
  } catch {
    return 'dayGridMonth';
  }
}

function currentMonthFrom(): string {
  const now = new Date();
  return toIsoDate(new Date(now.getFullYear(), now.getMonth(), 1));
}

function currentMonthTo(): string {
  const now = new Date();
  return toIsoDate(new Date(now.getFullYear(), now.getMonth() + 1, 0));
}


@Component({
  selector: 'app-calendar-page',
  imports: [FullCalendarModule, RouterLink],
  templateUrl: './calendar-page.html',
  styleUrl: './calendar-page.scss',
})
export class CalendarPage implements OnDestroy, AfterViewInit {
  readonly store = inject(HomeworkStore);
  private readonly api = inject(MidaApiService);
  readonly formatDate = formatDate;
  readonly agendaToday = todayIso;
  readonly views = CALENDAR_VIEWS;
  readonly activeView = signal<CalendarView>(readStoredView());

  readonly agendaFrom = signal(currentMonthFrom());
  readonly agendaTo = signal(currentMonthTo());
  readonly agendaSort = signal<AgendaSort>('date-asc');
  readonly agendaAssignments = signal<HomeworkView[]>([]);
  readonly agendaLoading = signal(false);
  readonly agendaError = signal<string | null>(null);

  private agendaRequest = 0;

  private readonly calendar = viewChild(FullCalendarComponent);

  constructor() {
    effect(() => {
      if (this.activeView() !== 'listMonth') {
        return;
      }
      // Letture sincrone tracciate dall'effect: cambiando filtri o date
      // l'agenda viene ricaricata automaticamente.
      this.agendaFrom();
      this.agendaTo();
      this.store.filters();
      void this.loadAgenda();
    });
  }

  readonly calendarOptions: CalendarOptions = {
    plugins: [classicThemePlugin, dayGridPlugin, timeGridPlugin, listPlugin],
    initialView: this.activeView(),
    locale: itLocale,
    firstDay: 1,
    height: 'auto',
    expandRows: true,
    dayMaxEvents: 3,
    nowIndicator: true,
    allDaySlot: false,
    slotMinTime: '07:00:00',
    slotMaxTime: '22:00:00',
    eventDisplay: 'block',
    noEventsText: 'Nessun compito in questo periodo',
    headerToolbar: {
      left: 'prev,next today',
      center: 'title',
      right: '',
    },
    datesSet: (info) => {
      const view = info.view.type;
      if (isCalendarView(view)) {
        this.activeView.set(view);
        if (typeof window !== 'undefined') {
          try {
            window.localStorage.setItem(VIEW_STORAGE_KEY, view);
          } catch {
            // La vista resta attiva anche se localStorage non è disponibile.
          }
        }
      }
      const from = toIsoDate(info.start);
      const end = new Date(info.end);
      end.setDate(end.getDate() - 1);
      this.store.setRange(from, toIsoDate(end));
    },
    eventClick: (info) => {
      info.jsEvent.preventDefault();
      this.store.select(info.event.id);
    },
    eventDidMount: (info) => {
      const description = String(info.event.extendedProps['description'] ?? '');
      if (description) {
        info.el.setAttribute('title', description);
      }
    },
    eventClass: (info) =>
      info.event.extendedProps['completed'] ? 'is-completed' : '',
  };

  readonly events = computed<EventInput[]>(() =>
    this.store.assignments().map((item) => this.toEvent(item)),
  );

  readonly subjectLegend = computed(() => {
    const subjects = new Map<string, ReturnType<typeof subjectPalette>>();
    for (const item of this.store.assignments()) {
      if (item.subject && !subjects.has(item.subject)) {
        subjects.set(item.subject, subjectPalette(item.subject));
      }
    }
    return [...subjects.entries()]
      .sort(([first], [second]) => first.localeCompare(second, 'it'))
      .slice(0, 8)
      .map(([subject, palette]) => ({ subject, palette }));
  });

  readonly showEmptyDatabase = computed(
    () => this.store.status() !== null && this.store.totalAssignments() === 0,
  );

  readonly rangeLabel = computed(() => {
    const range = this.store.range();
    if (!range) {
      return 'Periodo in caricamento';
    }
    return `${formatDate(range.from)} – ${formatDate(range.to)}`;
  });

  readonly agendaRangeLabel = computed(
    () => `${formatDate(this.agendaFrom())} – ${formatDate(this.agendaTo())}`,
  );

  readonly agendaGroups = computed(() => {
    const field = this.store.filters().dateField;
    const direction = this.agendaSort() === 'date-asc' ? 1 : -1;
    const sorted = [...this.agendaAssignments()].sort((first, second) => {
      const firstDate = field === 'DUE' ? first.dueDate : first.assignedDate;
      const secondDate = field === 'DUE' ? second.dueDate : second.assignedDate;
      const byDate = (firstDate ?? '').localeCompare(secondDate ?? '') * direction;
      if (byDate !== 0) {
        return byDate;
      }
      const bySubject = first.subject.localeCompare(second.subject, 'it');
      if (bySubject !== 0) {
        return bySubject;
      }
      return first.teacher.localeCompare(second.teacher, 'it');
    });

    const groups = new Map<string, HomeworkView[]>();
    for (const item of sorted) {
      const date = field === 'DUE' ? item.dueDate : item.assignedDate;
      const key = date || '';
      const items = groups.get(key) ?? [];
      items.push(item);
      groups.set(key, items);
    }
    return [...groups.entries()].map(([date, items]) => ({
      date,
      label: date ? formatLongDate(date) : 'Senza data',
      items,
    }));
  });

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  ngAfterViewInit(): void {
    this.openNotificationTarget();
  }

  /**
   * Handles the URL written by the service worker when a push notification is
   * clicked: moves FullCalendar to the relevant day and opens the assignment
   * detail. The query string is removed afterwards so a reload does not
   * reopen the panel.
   */
  private openNotificationTarget(): void {
    if (typeof window === 'undefined') {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const date = params.get('date');
    const assignmentId = params.get('assignment');
    const view = params.get('view');
    if (!date && !assignmentId) {
      return;
    }

    const target = date && /^\d{4}-\d{2}-\d{2}$/.test(date) ? date : null;
    // Le notifiche dei promemoria chiedono esplicitamente l'elenco: senza
    // questo parametro la PWA potrebbe riaprire l'ultima vista del calendario.
    if (view === 'listMonth' || view === 'agenda' || view === 'list') {
      this.changeView('listMonth');
    }
    if (target) {
      this.calendar()?.getApi().gotoDate(target);
    }
    if (assignmentId) {
      void this.store.focusAssignment(assignmentId).then((item) => {
        if (!target && item) {
          this.calendar()?.getApi().gotoDate(item.dueDate);
        }
      });
    }

    const cleanUrl = new URL(window.location.href);
    cleanUrl.searchParams.delete('date');
    cleanUrl.searchParams.delete('assignment');
    cleanUrl.searchParams.delete('view');
    window.history.replaceState({}, '', cleanUrl.pathname + cleanUrl.search + cleanUrl.hash);
  }

  @HostListener('document:keydown.escape')
  closeDetail(): void {
    this.store.select(null);
  }

  onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => this.store.setFilters({ q: value }), 320);
  }

  onSubject(event: Event): void {
    this.store.setFilters({ subject: (event.target as HTMLSelectElement).value });
  }

  onTeacher(event: Event): void {
    this.store.setFilters({ teacher: (event.target as HTMLSelectElement).value });
  }

  onCompletion(event: Event): void {
    this.store.setFilters({
      completion: (event.target as HTMLSelectElement).value as 'all' | 'open' | 'completed',
    });
  }

  setDateField(dateField: 'DUE' | 'ASSIGNED'): void {
    this.store.setFilters({ dateField });
  }

  changeView(view: CalendarView): void {
    this.calendar()?.getApi().changeView(view);
  }

  onAgendaFrom(event: Event): void {
    this.agendaFrom.set((event.target as HTMLInputElement).value);
  }

  onAgendaTo(event: Event): void {
    this.agendaTo.set((event.target as HTMLInputElement).value);
  }

  onAgendaSort(event: Event): void {
    this.agendaSort.set((event.target as HTMLSelectElement).value as AgendaSort);
  }

  openAgendaItem(item: HomeworkView): void {
    void this.store.focusAssignment(item.id);
  }

  agendaSecondary(item: HomeworkView): string {
    return this.store.filters().dateField === 'DUE'
      ? `Assegnato ${formatDate(item.assignedDate)}`
      : `Consegna ${formatDate(item.dueDate)}`;
  }

  private async loadAgenda(): Promise<void> {
    const from = this.agendaFrom();
    const to = this.agendaTo();
    if (!from || !to) {
      this.agendaError.set('Indica una data iniziale e una data finale.');
      this.agendaAssignments.set([]);
      return;
    }
    if (from > to) {
      this.agendaError.set('La data iniziale non può essere successiva alla data finale.');
      this.agendaAssignments.set([]);
      return;
    }
    const request = ++this.agendaRequest;
    this.agendaLoading.set(true);
    this.agendaError.set(null);
    const filters = this.store.filters();
    try {
      const items = await firstValueFrom(
        this.api.calendarHomework({
          from,
          to,
          dateField: filters.dateField,
          subject: filters.subject,
          teacher: filters.teacher,
          completion: filters.completion,
          q: filters.q,
        }),
      );
      if (request !== this.agendaRequest) {
        return;
      }
      this.agendaAssignments.set(items);
    } catch (error) {
      if (request === this.agendaRequest) {
        this.agendaError.set(apiErrorMessage(error));
        this.agendaAssignments.set([]);
      }
    } finally {
      if (request === this.agendaRequest) {
        this.agendaLoading.set(false);
      }
    }
  }

  resetFilters(): void {
    this.store.resetFilters();
  }

  toggleSelected(): void {
    const item = this.store.selected();
    if (item) {
      void this.store.toggleCompletion(item);
    }
  }

  dueText(item: HomeworkView): string {
    return dueLabel(item.dueDate);
  }

  longDate(item: HomeworkView): string {
    return formatLongDate(item.dueDate);
  }

  descriptionPreview(item: HomeworkView): string {
    return truncate(item.description, 90);
  }

  paletteFor(item: HomeworkView) {
    return subjectPalette(item.subject);
  }

  retry(): void {
    void this.store.loadCalendar();
  }

  private toEvent(item: HomeworkView): EventInput {
    const palette = subjectPalette(item.subject);
    const date = this.store.filters().dateField === 'ASSIGNED' ? item.assignedDate : item.dueDate;
    return {
      id: item.id,
      title: `${item.subject}: ${truncate(item.description, 70)}`,
      start: date,
      allDay: true,
      // FullCalendar 7 replaced backgroundColor/borderColor/textColor with
      // the single `color` plus `contrastColor` pair.
      color: item.completed ? '#e8edf2' : palette.background,
      contrastColor: item.completed ? '#475569' : palette.text,
      classNames: item.completed ? ['is-completed'] : [],
      extendedProps: {
        subject: item.subject,
        teacher: item.teacher,
        description: item.description,
        completed: item.completed,
        dueDate: item.dueDate,
      },
    };
  }
}
