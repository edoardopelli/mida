import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MidaApiService } from './api.service';
import { HomeworkView } from './models';

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

describe('MidaApiService', () => {
  let service: MidaApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MidaApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('invia i filtri e la paginazione alla lista compiti', () => {
    service
      .listHomework({
        dateField: 'DUE',
        subject: 'Matematica',
        teacher: 'Rossi',
        completion: 'open',
        q: 'frazioni',
        page: 2,
        size: 10,
      })
      .subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/api/homework');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('dateField')).toBe('DUE');
    expect(request.request.params.get('subject')).toBe('Matematica');
    expect(request.request.params.get('teacher')).toBe('Rossi');
    expect(request.request.params.get('completed')).toBe('false');
    expect(request.request.params.get('q')).toBe('frazioni');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');

    request.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('carica il calendario con l’intervallo richiesto', () => {
    service
      .calendarHomework({
        from: '2026-11-01',
        to: '2026-11-30',
        dateField: 'ASSIGNED',
        subject: '',
        teacher: '',
        completion: 'all',
        q: '',
      })
      .subscribe((items) => expect(items).toEqual([homework]));

    const request = http.expectOne((candidate) => candidate.url === '/api/homework/calendar');
    expect(request.request.params.get('from')).toBe('2026-11-01');
    expect(request.request.params.get('to')).toBe('2026-11-30');
    expect(request.request.params.get('dateField')).toBe('ASSIGNED');
    expect(request.request.params.has('completed')).toBe(false);

    request.flush([homework]);
  });

  it('aggiorna il completamento con PATCH', () => {
    service.setCompletion('assignment-1', true).subscribe();

    const request = http.expectOne('/api/homework/assignment-1/completion');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ completed: true });
    request.flush({ ...homework, completed: true });
  });

  it('salva le preferenze di notifica', () => {
    const preferences = {
      enabled: true,
      newAssignmentsEnabled: true,
      remindersEnabled: true,
      reminderDaysBefore: 1,
      reminderTime: '18:00',
      timezone: 'Europe/Rome',
    };

    service.updateNotificationPreferences(preferences).subscribe();

    const request = http.expectOne('/api/notifications/preferences');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(preferences);
    request.flush({ ...preferences, updatedAt: '2026-11-13T10:00:00Z' });
  });

  it('rimuove una sottoscrizione push passando l’endpoint nel body', () => {
    service.unsubscribePush('https://fcm.googleapis.com/push/123').subscribe();

    const request = http.expectOne('/api/notifications/subscriptions');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual({ endpoint: 'https://fcm.googleapis.com/push/123' });
    request.flush(null);
  });

  it('carica le attività di un giorno del diario con i filtri', () => {
    service.diaryDay('2026-11-13', { subject: 'INGLESE', teacher: 'E. NIFOSI', q: 'classmates' }).subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/api/diary/day');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('date')).toBe('2026-11-13');
    expect(request.request.params.get('subject')).toBe('INGLESE');
    expect(request.request.params.get('teacher')).toBe('E. NIFOSI');
    expect(request.request.params.get('q')).toBe('classmates');
    request.flush([]);
  });

  it('carica i giorni disponibili e lo storico del diario', () => {
    service.diaryDates().subscribe();
    http.expectOne('/api/diary/dates').flush([{ date: '2026-11-13', entryCount: 5, subjects: ['INGLESE'] }]);

    service.diaryImports(1, 10).subscribe();
    const request = http.expectOne((candidate) => candidate.url === '/api/imports/diary');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
    request.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
  });
});
