import { addDays, daysFromToday, formatDate, initials, parseDate, toIsoDate, truncate } from './format';

describe('format helpers', () => {
  it('formatta e interpreta le date ISO locali senza slittamenti di fuso', () => {
    expect(parseDate('2026-11-20')?.getDate()).toBe(20);
    expect(toIsoDate(new Date(2026, 10, 20))).toBe('2026-11-20');
    expect(formatDate('2026-11-20')).toContain('2026');
    expect(formatDate('non-data')).toBe('—');
    expect(formatDate(null)).toBe('—');
  });

  it('aggiunge giorni e calcola la distanza da oggi', () => {
    expect(addDays('2026-11-20', 5)).toBe('2026-11-25');
    expect(addDays('2026-11-20', -20)).toBe('2026-10-31');
    expect(daysFromToday(toIsoDate(new Date()))).toBe(0);
    expect(daysFromToday('non-data')).toBeNull();
  });

  it('tronca i testi conservando parole leggibili', () => {
    expect(truncate('  Esercizi   sulle frazioni  ', 30)).toBe('Esercizi sulle frazioni');
    expect(truncate('Esercizi sulle frazioni', 10)).toBe('Esercizi…');
  });

  it('calcola le iniziali dei nomi', () => {
    expect(initials('Maria Rossi')).toBe('MR');
    expect(initials('  ')).toBe('');
  });
});
