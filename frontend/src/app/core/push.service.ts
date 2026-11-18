import { Injectable, isDevMode, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MidaApiService } from './api.service';
import { TestNotificationResponse, VapidPublicKey } from './models';
import { ToastService } from './toast.service';

@Injectable({ providedIn: 'root' })
export class PushService {
  readonly browserSupported = signal(this.detectSupport());
  readonly devMode = signal(isDevMode());
  readonly permission = signal<NotificationPermission | 'unsupported'>(this.currentPermission());
  readonly subscribed = signal(false);
  readonly endpoint = signal<string | null>(null);
  readonly vapid = signal<VapidPublicKey | null>(null);
  readonly busy = signal(false);
  readonly initialized = signal(false);
  readonly error = signal<string | null>(null);

  constructor(
    private readonly api: MidaApiService,
    private readonly toast: ToastService,
  ) {}

  async initialize(force = false): Promise<void> {
    if (this.initialized() && !force) {
      return;
    }
    this.initialized.set(true);
    this.permission.set(this.currentPermission());
    this.error.set(null);
    if (!this.browserSupported()) {
      return;
    }
    try {
      const vapid = await firstValueFrom(this.api.vapidPublicKey());
      this.vapid.set(vapid);
      if (!this.devMode()) {
        const registration = await navigator.serviceWorker.getRegistration();
        const subscription = await registration?.pushManager.getSubscription();
        this.setSubscription(subscription);
      } else {
        this.subscribed.set(false);
      }
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  async enable(): Promise<boolean> {
    this.error.set(null);
    if (!this.browserSupported()) {
      this.error.set('Questo browser non supporta le notifiche Web Push.');
      return false;
    }
    if (this.devMode()) {
      this.error.set(
        'Le notifiche push si attivano dalla build di produzione servita da Docker su localhost:8080 (o via HTTPS).',
      );
      return false;
    }
    if (this.permission() === 'denied') {
      this.error.set(
        'Le notifiche sono bloccate per questo sito. Riabilita il permesso dalle impostazioni del browser.',
      );
      return false;
    }

    this.busy.set(true);
    let createdSubscription: PushSubscription | null = null;
    try {
      const permission = await window.Notification.requestPermission();
      this.permission.set(permission);
      if (permission !== 'granted') {
        this.error.set('Permesso notifiche non concesso. MIDA non invierà notifiche.');
        return false;
      }

      const vapid = this.vapid() ?? (await firstValueFrom(this.api.vapidPublicKey()));
      this.vapid.set(vapid);
      const registration = await withTimeout(navigator.serviceWorker.ready, 30_000);
      let subscription = await withTimeout(registration.pushManager.getSubscription(), 15_000);
      if (!subscription) {
        subscription = await withTimeout(
          registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: urlBase64ToUint8Array(vapid.publicKey),
          }),
          60_000,
        );
        createdSubscription = subscription;
      }

      const json = subscription.toJSON();
      if (!json.endpoint || !json.keys?.['p256dh'] || !json.keys?.['auth']) {
        throw new Error('Il browser non ha restituito chiavi push valide.');
      }
      await firstValueFrom(
        this.api.subscribePush({
          endpoint: json.endpoint,
          p256dh: json.keys['p256dh'],
          auth: json.keys['auth'],
        }),
      );
      this.setSubscription(subscription);
      this.toast.success('Notifiche push attivate su questo dispositivo.');
      return true;
    } catch (error) {
      if (createdSubscription) {
        await createdSubscription.unsubscribe().catch(() => undefined);
      }
      this.error.set(errorMessage(error));
      this.toast.error('Attivazione delle notifiche non riuscita.');
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  async disable(): Promise<boolean> {
    this.error.set(null);
    if (!this.browserSupported()) {
      return false;
    }
    this.busy.set(true);
    try {
      const registration = await navigator.serviceWorker.getRegistration();
      const subscription = await registration?.pushManager.getSubscription();
      if (subscription) {
        await firstValueFrom(this.api.unsubscribePush(subscription.endpoint));
        await subscription.unsubscribe();
      } else if (this.endpoint()) {
        await firstValueFrom(this.api.unsubscribePush(this.endpoint()!));
      }
      this.setSubscription(null);
      this.toast.success('Notifiche push disattivate su questo dispositivo.');
      return true;
    } catch (error) {
      this.error.set(errorMessage(error));
      this.toast.error('Disattivazione non riuscita.');
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  async sendTest(): Promise<TestNotificationResponse | null> {
    this.busy.set(true);
    try {
      const result = await firstValueFrom(this.api.sendTestNotification());
      if (result.sent > 0) {
        this.toast.success(`Notifica di prova inviata a ${result.sent} dispositivo/i.`);
      } else if (result.expired > 0) {
        this.toast.error('La sottoscrizione è scaduta. Disattiva e riattiva le notifiche.');
      } else if (result.subscriptions === 0) {
        this.toast.info('Nessun dispositivo sottoscritto: attiva prima le notifiche push.');
      } else {
        this.toast.error('Invio non riuscito. Controlla i log del backend.');
      }
      return result;
    } catch (error) {
      this.toast.error(errorMessage(error));
      return null;
    } finally {
      this.busy.set(false);
    }
  }

  private setSubscription(subscription: PushSubscription | null | undefined): void {
    const endpoint = subscription?.endpoint ?? null;
    this.subscribed.set(!!subscription);
    this.endpoint.set(endpoint);
    try {
      if (endpoint) {
        localStorage.setItem('mida.push.endpoint', endpoint);
      } else {
        localStorage.removeItem('mida.push.endpoint');
      }
    } catch {
      // Storage can be unavailable in private browsing; the UI still works.
    }
  }

  private detectSupport(): boolean {
    return (
      typeof window !== 'undefined' &&
      'serviceWorker' in navigator &&
      'PushManager' in window &&
      'Notification' in window
    );
  }

  private currentPermission(): NotificationPermission | 'unsupported' {
    return this.detectSupport() ? window.Notification.permission : 'unsupported';
  }
}

function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    if (error.message.includes('timed out')) {
      return 'Il browser non ha completato l’operazione in tempo. Riprova tra qualche istante.';
    }
    return error.message;
  }
  return 'Operazione sulle notifiche non riuscita.';
}

function withTimeout<T>(promise: Promise<T>, milliseconds: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) =>
      window.setTimeout(() => reject(new Error('Service worker timed out')), milliseconds),
    ),
  ]);
}

export function urlBase64ToUint8Array(value: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (value.length % 4)) % 4);
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let index = 0; index < raw.length; index += 1) {
    bytes[index] = raw.charCodeAt(index);
  }
  return bytes;
}
