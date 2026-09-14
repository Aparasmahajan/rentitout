import type { ListingKind, RequestStatus, Unit } from './types';

/** Money arrives as integer minor units and is only ever divided for display. */
export function money(minor: number | null | undefined, currency = 'EUR'): string {
  if (minor === null || minor === undefined) return '—';
  return new Intl.NumberFormat('en-IE', { style: 'currency', currency }).format(minor / 100);
}

export function rate(minor: number | null, unit: Unit | string | null, currency = 'EUR'): string {
  if (minor === null) return '—';
  const per: Record<string, string> = {
    HOUR: '/hour',
    DAY: '/day',
    WEEK: '/week',
    SESSION: '/session',
    ITEM: '',
  };
  return `${money(minor, currency)}${unit ? (per[unit] ?? '') : ''}`;
}

export function distance(km: number | null | undefined): string {
  if (km === null || km === undefined) return '';
  return km < 1 ? `${Math.round(km * 1000)} m away` : `${km.toFixed(1)} km away`;
}

export const KIND_LABEL: Record<ListingKind, string> = {
  RENT_ITEM: 'To rent',
  SELL_ITEM: 'For sale',
  TRADE_SERVICE: 'Trade',
  SKILL_FOR_HIRE: 'For hire',
  TEACHING: 'Teaching',
  SPACE_OR_VEHICLE: 'Space',
  OPEN_NEED: 'Wanted',
};

export const KINDS = Object.keys(KIND_LABEL) as ListingKind[];

export const UNITS: Unit[] = ['HOUR', 'DAY', 'WEEK', 'SESSION', 'ITEM'];

export const STATUS_TONE: Record<RequestStatus, 'live' | 'paused' | 'warn'> = {
  SENT: 'paused',
  ACCEPTED: 'live',
  IN_PROGRESS: 'live',
  COMPLETED: 'live',
  DECLINED: 'warn',
  CANCELLED: 'warn',
  EXPIRED: 'warn',
};

export function when(iso: string): string {
  const date = new Date(iso);
  const minutes = Math.round((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 60 * 24) return `${Math.round(minutes / 60)} h ago`;
  return date.toLocaleDateString();
}

export function day(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
}

export function initials(name: string | null | undefined): string {
  if (!name) return '··';
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}
