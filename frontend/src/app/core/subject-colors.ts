export interface SubjectPalette {
  background: string;
  border: string;
  text: string;
  strong: string;
}

const PALETTES: SubjectPalette[] = [
  { background: '#dbeafe', border: '#93c5fd', text: '#1e3a8a', strong: '#2563eb' },
  { background: '#dcfce7', border: '#86efac', text: '#14532d', strong: '#16a34a' },
  { background: '#fef3c7', border: '#fcd34d', text: '#78350f', strong: '#d97706' },
  { background: '#fce7f3', border: '#f9a8d4', text: '#831843', strong: '#db2777' },
  { background: '#ede9fe', border: '#c4b5fd', text: '#4c1d95', strong: '#7c3aed' },
  { background: '#cffafe', border: '#67e8f9', text: '#164e63', strong: '#0891b2' },
  { background: '#ffedd5', border: '#fdba74', text: '#7c2d12', strong: '#ea580c' },
  { background: '#e0f2fe', border: '#7dd3fc', text: '#0c4a6e', strong: '#0284c7' },
  { background: '#f3e8ff', border: '#d8b4fe', text: '#581c87', strong: '#9333ea' },
  { background: '#ccfbf1', border: '#5eead4', text: '#134e4a', strong: '#0d9488' },
  { background: '#fee2e2', border: '#fca5a5', text: '#7f1d1d', strong: '#dc2626' },
  { background: '#ecfccb', border: '#bef264', text: '#365314', strong: '#65a30d' },
];

const cache = new Map<string, SubjectPalette>();

export function subjectPalette(subject: string | null | undefined): SubjectPalette {
  const key = (subject ?? '').trim().toLocaleLowerCase('it-IT');
  const existing = cache.get(key);
  if (existing) {
    return existing;
  }
  let hash = 0;
  for (let index = 0; index < key.length; index += 1) {
    hash = (hash * 31 + key.charCodeAt(index)) >>> 0;
  }
  const palette = PALETTES[hash % PALETTES.length];
  cache.set(key, palette);
  return palette;
}

export const completedPalette: SubjectPalette = {
  background: '#e8edf2',
  border: '#94a3b8',
  text: '#475569',
  strong: '#64748b',
};
