import { Component, OnDestroy, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HomeworkStore } from './core/homework-store';
import { LiveUpdatesService } from './core/live-updates.service';
import { ToastService } from './core/toast.service';

interface NavigationItem {
  path: string;
  label: string;
  shortLabel: string;
  icon: string;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnDestroy {
  private readonly store = inject(HomeworkStore);
  private readonly live = inject(LiveUpdatesService);
  readonly toasts = inject(ToastService);

  readonly navigation: NavigationItem[] = [
    {
      path: '/calendario',
      label: 'Compiti',
      shortLabel: 'Compiti',
      icon: 'M7 3v3m10-3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm3 8h2m3 0h2m-7 4h2m3 0h2',
    },
    {
      path: '/diario',
      label: 'Diario',
      shortLabel: 'Diario',
      icon: 'M5 4h11a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2V4Zm3 0v14m3-10h4m-4 3h4m-4 3h2',
    },
    {
      path: '/posta',
      label: 'Posta',
      shortLabel: 'Posta',
      icon: 'M4 5h16v14H4V5Zm0 1 8 6 8-6',
    },
    {
      path: '/dashboard',
      label: 'Dashboard',
      shortLabel: 'Riepilogo',
      icon: 'M4 13h6V4H4v9Zm0 7h6v-4H4v4Zm10 0h6v-9h-6v9Zm0-16v4h6V4h-6Z',
    },
    {
      path: '/importazioni',
      label: 'Importazioni',
      shortLabel: 'Import',
      icon: 'M12 3v11m0 0 4-4m-4 4-4-4M4 17v2a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-2',
    },
    {
      path: '/impostazioni',
      label: 'Impostazioni',
      shortLabel: 'Impostazioni',
      icon: 'M12 15.5A3.5 3.5 0 1 0 12 8.5a3.5 3.5 0 0 0 0 7Zm8-3.5a8.2 8.2 0 0 0-.1-1.2l2-1.5-2-3.4-2.3 1a8 8 0 0 0-2-1.2L15.2 3h-4l-.4 2.7a8 8 0 0 0-2 1.2l-2.3-1-2 3.4 2 1.5a8.2 8.2 0 0 0 0 2.4l-2 1.5 2 3.4 2.3-1a8 8 0 0 0 2 1.2l.4 2.7h4l.4-2.7a8 8 0 0 0 2-1.2l2.3 1 2-3.4-2-1.5c.1-.4.1-.8.1-1.2Z',
    },
  ];

  readonly connectionLabel = computed(() => {
    switch (this.live.connection()) {
      case 'connected':
        return 'In tempo reale';
      case 'connecting':
        return 'Connessione…';
      case 'reconnecting':
        return 'Riconnessione…';
      case 'offline':
        return 'Offline';
    }
  });

  readonly connectionClass = computed(() => `is-${this.live.connection()}`);

  constructor() {
    this.store.initialize();
    this.live.connect();
  }

  ngOnDestroy(): void {
    this.live.disconnect();
  }

  dismissToast(id: number): void {
    this.toasts.dismiss(id);
  }
}
