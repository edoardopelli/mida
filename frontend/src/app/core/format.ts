const TIME_ZONE = 'Europe/Rome';

const dateFormatter = new Intl.DateTimeFormat('it-IT', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

const longDateFormatter = new Intl.DateTimeFormat('it-IT', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

const dateTimeFormatter = new Intl.DateTimeFormat('it-IT', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: TIME_ZONE,
});

const timeFormatter = new Intl.DateTimeFormat('it-IT', {
  hour: '2-digit',
  minute: '2-digit',
  timeZone: TIME_ZONE,
});

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = parseDate(value);
  return date ? dateFormatter.format(date) : '—';
}

export function formatLongDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = parseDate(value);
  return date ? longDateFormatter.format(date) : '—';
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormatter.format(date);
}

export function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '—'
    : timeFormatter.format(date);
}

export function parseDate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    return null;
  }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(date.getTime()) ? null : date;
}

export function todayIso(): string {
  return toIsoDate(new Date());
}

export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function addDays(isoDate: string, days: number): string {
  const date = parseDate(isoDate) ?? new Date();
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}

export function daysFromToday(isoDate: string): number | null {
  const date = parseDate(isoDate);
  if (!date) {
    return null;
  }
  const today = parseDate(todayIso());
  if (!today) {
    return null;
  }
  return Math.round((date.getTime() - today.getTime()) / 86_400_000);
}

export function dueLabel(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return 'Scadenza non indicata';
  }
  const days = daysFromToday(isoDate);
  if (days === 0) {
    return 'Scade oggi';
  }
  if (days === 1) {
    return 'Scade domani';
  }
  if (days === -1) {
    return 'Scaduto ieri';
  }
  if (days != null && days < 0) {
    return `Scaduto da ${Math.abs(days)} giorni`;
  }
  if (days != null && days > 1 && days <= 7) {
    return `Scade tra ${days} giorni`;
  }
  return `Scade il ${formatDate(isoDate)}`;
}

export function truncate(value: string, length: number): string {
  const clean = value.replace(/\s+/g, ' ').trim();
  return clean.length <= length ? clean : `${clean.slice(0, length - 1).trimEnd()}…`;
}

export function initials(value: string): string {
  return value
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}
