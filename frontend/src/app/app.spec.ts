import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { App } from './app';
import { HomeworkStore } from './core/homework-store';
import { LiveUpdatesService } from './core/live-updates.service';
import { ToastService } from './core/toast.service';

describe('App', () => {
  const live = {
    connect: vi.fn(),
    disconnect: vi.fn(),
    connection: signal('connected'),
    connectionEpoch: signal(0),
  };
  const store = { initialize: vi.fn() };
  const toast = {
    toasts: signal<Array<{ id: number; message: string; kind: 'success' | 'error' | 'info' }>>([]),
    dismiss: vi.fn(),
  };

  beforeEach(async () => {
    live.connect.mockClear();
    live.disconnect.mockClear();
    store.initialize.mockClear();

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: LiveUpdatesService, useValue: live },
        { provide: HomeworkStore, useValue: store },
        { provide: ToastService, useValue: toast },
      ],
    }).compileComponents();
  });

  it('crea l’applicazione e avvia la connessione in tempo reale', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    expect(store.initialize).toHaveBeenCalled();
    expect(live.connect).toHaveBeenCalled();
  });

  it('mostra il nome esteso e le sei sezioni di navigazione', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('MIDA');
    expect(text).toContain('Monitoraggio Integrato');
    expect(text).toContain('Compiti');
    expect(text).toContain('Diario');
    expect(text).toContain('Posta');
    expect(text).toContain('Dashboard');
    expect(text).toContain('Importazioni');
    expect(text).toContain('Impostazioni');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.nav-link')).toHaveLength(6);
  });

  it('chiude la connessione quando l’app viene distrutta', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    fixture.destroy();

    expect(live.disconnect).toHaveBeenCalled();
  });

  it('mostra i toast del servizio', () => {
    toast.toasts.set([{ id: 1, message: 'Import completato', kind: 'success' }]);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.toast')?.textContent).toContain(
      'Import completato',
    );
  });
});
