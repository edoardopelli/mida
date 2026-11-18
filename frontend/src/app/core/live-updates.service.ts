import { Injectable, OnDestroy, signal } from '@angular/core';
import { MidaEvent } from './models';

export type ConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'offline';

const EVENT_TYPES = [
  'connected',
  'import.completed',
  'diary.imported',
  'homework.updated',
  'import.issue',
  'import.status',
  'google.sync',
  'mail.sync',
] as const;

@Injectable({ providedIn: 'root' })
export class LiveUpdatesService implements OnDestroy {
  readonly connection = signal<ConnectionState>('connecting');
  readonly lastEvent = signal<MidaEvent | null>(null);
  readonly connectionEpoch = signal(0);
  readonly lastSyncAt = signal<Date | null>(null);

  private source: EventSource | null = null;
  private reconnectTimer: number | null = null;
  private attempts = 0;
  private disposed = false;
  private started = false;

  private readonly onlineHandler = () => {
    this.attempts = 0;
    this.connect();
  };

  private readonly offlineHandler = () => {
    this.connection.set('offline');
  };

  connect(): void {
    if (this.disposed || typeof EventSource === 'undefined') {
      return;
    }
    if (this.source) {
      return;
    }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      this.connection.set('offline');
      this.scheduleReconnect();
      return;
    }
    this.started = true;
    this.connection.set(this.attempts === 0 ? 'connecting' : 'reconnecting');
    this.source = new EventSource('/api/events');

    for (const type of EVENT_TYPES) {
      this.source.addEventListener(type, (event) => this.handleEvent(type, event as MessageEvent<string>));
    }

    this.source.onopen = () => {
      this.attempts = 0;
      this.connection.set('connected');
    };

    this.source.onerror = () => {
      this.connection.set('reconnecting');
      this.closeSource();
      this.scheduleReconnect();
    };
  }

  disconnect(): void {
    this.started = false;
    this.closeSource();
    if (this.reconnectTimer != null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  ngOnDestroy(): void {
    this.disposed = true;
    this.disconnect();
    window.removeEventListener('online', this.onlineHandler);
    window.removeEventListener('offline', this.offlineHandler);
  }

  private readonly handleEvent = (type: string, event: MessageEvent<string>): void => {
    this.lastSyncAt.set(new Date());
    if (type === 'connected') {
      this.connection.set('connected');
      this.connectionEpoch.update((epoch) => epoch + 1);
      return;
    }
    if (!event.data) {
      return;
    }
    try {
      const payload = JSON.parse(event.data) as MidaEvent;
      this.lastEvent.set({
        ...payload,
        type: payload.type || type,
      });
    } catch {
      // A malformed event must not break the stream or the UI.
    }
  };

  private scheduleReconnect(): void {
    if (this.disposed || this.reconnectTimer != null) {
      return;
    }
    if (!this.started) {
      return;
    }
    this.attempts += 1;
    const delay = Math.min(30_000, 1_000 * 2 ** Math.min(this.attempts - 1, 5));
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  private closeSource(): void {
    if (this.source) {
      this.source.close();
      this.source = null;
    }
  }

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', this.onlineHandler);
      window.addEventListener('offline', this.offlineHandler);
      if (navigator.onLine === false) {
        this.connection.set('offline');
      }
    }
  }
}
