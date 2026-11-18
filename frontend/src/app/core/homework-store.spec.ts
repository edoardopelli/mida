import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { HomeworkStore } from './homework-store';
import { LiveUpdatesService } from './live-updates.service';
import { MidaEvent, HomeworkView, ImportStatus } from './models';
import { ToastService } from './toast.service';

const homework: HomeworkView = {
  id: 'assignment-1',
  importId: 'import-1',
  sourceCode: 'ABC123',
  originalFilename: 'compiti.txt',
  importedAt: '2026-11-13T10:00:00Z',
  assignedDate: '2026-11-12',
  assignedDateRaw: '12 novembre 2026',
  dueDate: '2026-11-20',
  dueDateRaw: '20 novembre 2026',
  subject: 'Matematica',
  teacher: 'Prof.ssa Rossi',
  description: 'Esercizi sulle frazioni.',
  completed: false,
  completedAt: null,
  revision: 1,
  updatedAt: '2026-11-13T10:00:00Z',
};

const status: ImportStatus = {
  monitoringEnabled: true,
  state: 'ACTIVE',
  lastScanAt: '2026-11-13T10:00:00Z',
  lastSuccessfulImportAt: '2026-11-13T09:59:00Z',
  totalImports: 1,
  totalAssignments: 1,
  totalDiaryImports: 0,
  totalDiaryEntries: 0,
  openIssues: 0,
  activePushSubscriptions: 0,
  directoryReadable: true,
  lastError: null,
  generatedAt: '2026-11-13T10:00:01Z',
};

describe('HomeworkStore', () => {
  let store: HomeworkStore;
  let http: HttpTestingController;
  let live: {
    lastEvent: ReturnType<typeof signal<MidaEvent | null>>;
    connectionEpoch: ReturnType<typeof signal<number>>;
  };
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    live = {
      lastEvent: signal<MidaEvent | null>(null),
      connectionEpoch: signal(0),
    };
    toast = { success: vi.fn(), error: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LiveUpdatesService, useValue: live },
        { provide: ToastService, useValue: toast },
      ],
    });
    store = TestBed.inject(HomeworkStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carica i compiti del periodo e aggiorna lo stato', async () => {
    store.range.set({ from: '2026-11-01', to: '2026-11-30' });
    const loading = store.loadCalendar();
    http
      .expectOne((request) => request.url === '/api/homework/calendar')
      .flush([homework]);
    await loading;

    expect(store.assignments()).toEqual([homework]);
    expect(store.loading()).toBe(false);
    expect(store.lastLoadedAt()).toBeTruthy();
  });

  it('imposta il periodo e richiede il calendario', async () => {
    store.setRange('2026-11-01', '2026-11-30');

    const request = http.expectOne((candidate) => candidate.url === '/api/homework/calendar');
    expect(request.request.params.get('from')).toBe('2026-11-01');
    expect(request.request.params.get('to')).toBe('2026-11-30');
    request.flush([homework]);
    await Promise.resolve();

    expect(store.range()).toEqual({ from: '2026-11-01', to: '2026-11-30' });
    expect(store.assignments()).toEqual([homework]);
  });

  it('segna subito il compito come completato e conferma la risposta', async () => {
    store.assignments.set([homework]);

    const toggling = store.toggleCompletion(homework);
    expect(store.assignments()[0].completed).toBe(true);

    http.expectOne('/api/homework/assignment-1/completion').flush({
      ...homework,
      completed: true,
      completedAt: '2026-11-13T12:00:00Z',
    });
    await toggling;

    expect(store.assignments()[0].completed).toBe(true);
    expect(toast.success).toHaveBeenCalledWith('Compito segnato come completato.');
  });

  it('ripristina il compito se il salvataggio fallisce', async () => {
    store.assignments.set([homework]);

    const toggling = store.toggleCompletion(homework);
    http
      .expectOne('/api/homework/assignment-1/completion')
      .flush({ detail: 'MongoDB non disponibile' }, { status: 503, statusText: 'Service Unavailable' });
    await toggling;

    expect(store.assignments()[0].completed).toBe(false);
    expect(toast.error).toHaveBeenCalledWith('MongoDB non disponibile');
  });

  it('applica gli eventi SSE di aggiornamento compito', () => {
    store.assignments.set([homework]);
    live.lastEvent.set({
      type: 'homework.updated',
      at: '2026-11-13T12:00:00Z',
      data: { assignmentId: 'assignment-1', completed: true, subject: 'Matematica', dueDate: '2026-11-20' },
    });

    TestBed.tick();

    expect(store.assignments()[0].completed).toBe(true);
  });

  it('apre il dettaglio di un compito da un link di notifica', async () => {
    const loading = store.focusAssignment('assignment-1');
    http.expectOne('/api/homework/assignment-1').flush(homework);
    const item = await loading;

    expect(item?.id).toBe('assignment-1');
    expect(store.selected()?.id).toBe('assignment-1');
    expect(store.assignments().some((entry) => entry.id === 'assignment-1')).toBe(true);
  });

  it('ricarica i dati quando la connessione SSE si riapre', async () => {
    live.connectionEpoch.set(1);
    TestBed.tick();

    http
      .expectOne((request) => request.url === '/api/homework/subjects')
      .flush(['Matematica']);
    http
      .expectOne((request) => request.url === '/api/homework/teachers')
      .flush(['Prof.ssa Rossi']);
    http.expectOne((request) => request.url === '/api/imports/status').flush(status);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(store.subjects()).toEqual(['Matematica']);
    expect(store.status()).toEqual(status);
  });
});
