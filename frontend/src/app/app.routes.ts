import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'calendario',
  },
  {
    path: 'calendario',
    title: 'Calendario · MIDA',
    loadComponent: () =>
      import('./pages/calendar/calendar-page').then((module) => module.CalendarPage),
  },
  {
    path: 'diario',
    title: 'Diario · MIDA',
    loadComponent: () =>
      import('./pages/diary/diary-page').then((module) => module.DiaryPage),
  },
  {
    path: 'posta',
    title: 'Posta · MIDA',
    loadComponent: () =>
      import('./pages/mail/mail-page').then((module) => module.MailPage),
  },
  {
    path: 'google',
    pathMatch: 'full',
    redirectTo: 'posta',
  },
  {
    path: 'guida-google',
    title: 'Guida Google · MIDA',
    loadComponent: () =>
      import('./pages/google-guide/google-guide-page').then(
        (module) => module.GoogleGuidePage,
      ),
  },
  {
    path: 'dashboard',
    title: 'Dashboard · MIDA',
    loadComponent: () =>
      import('./pages/dashboard/dashboard-page').then((module) => module.DashboardPage),
  },
  {
    path: 'importazioni',
    title: 'Importazioni · MIDA',
    loadComponent: () =>
      import('./pages/imports/imports-page').then((module) => module.ImportsPage),
  },
  {
    path: 'impostazioni',
    title: 'Impostazioni · MIDA',
    loadComponent: () =>
      import('./pages/settings/settings-page').then((module) => module.SettingsPage),
  },
  {
    path: '**',
    redirectTo: 'calendario',
  },
];
