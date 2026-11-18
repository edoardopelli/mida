import {
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  inject,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MailStore } from '../../core/mail-store';
import { formatDate, formatDateTime } from '../../core/format';

@Component({
  selector: 'app-mail-page',
  imports: [RouterLink],
  templateUrl: './mail-page.html',
  styleUrl: './mail-page.scss',
})
export class MailPage implements OnInit, OnDestroy {
  readonly store = inject(MailStore);
  readonly formatDate = formatDate;
  readonly formatDateTime = formatDateTime;

  private emailTimer: ReturnType<typeof setTimeout> | null = null;
  private classroomTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    void this.store.initialize();
  }

  ngOnDestroy(): void {
    this.clearTimers();
  }

  @HostListener('document:keydown.escape')
  closeDetails(): void {
    this.store.closeEmail();
    this.store.closeClassroomItem();
  }

  onEmailSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (this.emailTimer) {
      clearTimeout(this.emailTimer);
    }
    this.emailTimer = setTimeout(() => this.store.setEmailFilters({ q: value }), 320);
  }

  onEmailFrom(event: Event): void {
    this.store.setEmailFilters({ from: (event.target as HTMLInputElement).value });
  }

  onClassroomSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (this.classroomTimer) {
      clearTimeout(this.classroomTimer);
    }
    this.classroomTimer = setTimeout(
      () => this.store.setClassroomFilters({ q: value }),
      320,
    );
  }

  onCourse(event: Event): void {
    this.store.setClassroomFilters({ courseId: (event.target as HTMLSelectElement).value });
  }

  onType(event: Event): void {
    this.store.setClassroomFilters({ type: (event.target as HTMLSelectElement).value });
  }

  syncMail(): void {
    void this.store.syncMail();
  }

  syncClassroom(): void {
    void this.store.syncClassroom();
  }

  retry(): void {
    if (this.store.tab() === 'email') {
      void this.store.loadEmails();
    } else {
      void this.store.loadClassroom();
    }
  }

  mailStatusText(): string {
    const status = this.store.gmailStatus();
    if (!status) {
      return 'Caricamento';
    }
    if (status.syncInProgress) {
      return 'Sincronizzazione in corso';
    }
    if (!status.configured) {
      return 'Da configurare';
    }
    if (!status.connected) {
      return 'Da collegare';
    }
    if (!status.gmailEnabled) {
      return 'Disattivata';
    }
    return switchStatus(status.lastSyncStatus);
  }

  mailStatusClass(): string {
    const status = this.store.gmailStatus();
    if (!status?.connected) {
      return 'badge badge-gray';
    }
    if (status.syncInProgress) {
      return 'badge badge-blue';
    }
    return status.lastSyncStatus === 'OK' ? 'badge badge-teal' : 'badge badge-amber';
  }

  classroomStatusText(): string {
    const status = this.store.classroomStatus();
    if (!status) {
      return 'Caricamento';
    }
    if (status.syncInProgress) {
      return 'Sincronizzazione in corso';
    }
    if (!status.configured) {
      return 'Da configurare';
    }
    if (!status.connected) {
      return 'Da collegare';
    }
    return switchStatus(status.lastSyncStatus);
  }

  classroomStatusClass(): string {
    const status = this.store.classroomStatus();
    if (!status?.connected) {
      return 'badge badge-gray';
    }
    if (status.syncInProgress) {
      return 'badge badge-blue';
    }
    return status.lastSyncStatus === 'OK' ? 'badge badge-teal' : 'badge badge-amber';
  }

  typeLabel(type: string): string {
    return type === 'COURSEWORK'
      ? 'Compito'
      : type === 'MATERIAL'
        ? 'Materiale'
        : type === 'ANNOUNCEMENT'
          ? 'Annuncio'
          : type;
  }

  typeClass(type: string): string {
    return type === 'COURSEWORK'
      ? 'badge badge-blue'
      : type === 'MATERIAL'
        ? 'badge badge-teal'
        : 'badge badge-amber';
  }

  materialTypeLabel(type: string): string {
    return type === 'DRIVE_FILE'
      ? 'File'
      : type === 'YOUTUBE_VIDEO'
        ? 'Video'
        : type === 'FORM'
          ? 'Modulo'
          : 'Link';
  }

  attachmentStatusClass(status: string | null): string {
    if (status === 'IMPORTED' || status === 'ALREADY_IMPORTED') {
      return 'badge badge-teal';
    }
    if (status === 'FAILED') {
      return 'badge badge-red';
    }
    if (status === 'TOO_LARGE') {
      return 'badge badge-amber';
    }
    return 'badge badge-gray';
  }

  attachmentStatusLabel(status: string | null): string {
    switch (status) {
      case 'IMPORTED':
        return 'Importato nei compiti';
      case 'ALREADY_IMPORTED':
        return 'Già presente';
      case 'NOT_DIDUP':
        return 'Non DidUP';
      case 'FAILED':
        return 'Import fallito';
      case 'TOO_LARGE':
        return 'Troppo grande';
      case 'STORED':
        return 'Archiviato';
      default:
        return 'Allegato';
    }
  }

  materialStatusLabel(status: string): string {
    switch (status) {
      case 'DOWNLOADED':
        return 'Disponibile';
      case 'LINK_ONLY':
        return 'Link';
      case 'ERROR':
        return 'Errore';
      case 'TOO_LARGE':
        return 'Troppo grande';
      default:
        return 'Non scaricato';
    }
  }

  materialStatusClass(status: string): string {
    if (status === 'DOWNLOADED') {
      return 'badge badge-teal';
    }
    if (status === 'LINK_ONLY') {
      return 'badge badge-gray';
    }
    if (status === 'ERROR' || status === 'TOO_LARGE') {
      return 'badge badge-red';
    }
    return 'badge badge-amber';
  }

  formatSize(size: number): string {
    if (!size) {
      return '';
    }
    if (size < 1024) {
      return `${size} B`;
    }
    if (size < 1024 * 1024) {
      return `${(size / 1024).toFixed(1)} KB`;
    }
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }

  emailCountLabel(count: number): string {
    return count === 1 ? '1 email' : `${count} email`;
  }

  classroomCountLabel(count: number): string {
    return count === 1 ? '1 elemento' : `${count} elementi`;
  }

  materialCountLabel(count: number): string {
    return count === 1 ? '1 materiale' : `${count} materiali`;
  }

  private clearTimers(): void {
    if (this.emailTimer) {
      clearTimeout(this.emailTimer);
      this.emailTimer = null;
    }
    if (this.classroomTimer) {
      clearTimeout(this.classroomTimer);
      this.classroomTimer = null;
    }
  }
}

function switchStatus(status: string): string {
  switch (status) {
    case 'OK':
      return 'Sincronizzato';
    case 'SYNCING':
      return 'Sincronizzazione in corso';
    case 'REAUTH_REQUIRED':
      return 'Riconnessione necessaria';
    case 'ERROR':
      return 'Errore di sincronizzazione';
    case 'NEVER':
      return 'Mai sincronizzato';
    default:
      return status;
  }
}
