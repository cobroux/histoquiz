import { Period } from './models';

/** Each period has its own colour; text on top of it is always dark ink. */
export const PERIOD_COLORS: Record<Period, string> = {
  PREMIERE_GUERRE: 'var(--p-premiere)',
  ENTRE_DEUX_GUERRES: 'var(--p-entre)',
  SECONDE_GUERRE: 'var(--p-seconde)',
  GUERRE_FROIDE: 'var(--p-froide)',
};

export const PERIOD_SHORT: Record<Period, string> = {
  PREMIERE_GUERRE: '14–18',
  ENTRE_DEUX_GUERRES: '19–39',
  SECONDE_GUERRE: '39–45',
  GUERRE_FROIDE: '47–91',
};

const AVATAR_COLORS = ['#FF5C8A', '#FFC43D', '#22C58B', '#8FA8FF', '#FF8A5B', '#C9A7FF', '#5FD3F3'];

/** Stable colour per player name. */
export function avatarColor(name: string): string {
  let hash = 0;
  for (const char of name) {
    hash = (hash * 31 + char.charCodeAt(0)) | 0;
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

export function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase();
}

export function formatPoints(points: number): string {
  return points.toLocaleString('fr-FR');
}
