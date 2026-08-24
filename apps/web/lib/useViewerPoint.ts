'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from './auth';

/**
 * Where to centre "nearby".
 *
 * A signed-in member has a saved home point. A guest has nothing, and the API
 * refuses to guess for them — so we ask the browser, remember the answer, and
 * fall back to a default area so the feed is never empty on a first visit.
 */

const STORE_KEY = 'radius.point';

/** Kreuzberg. Replace with your launch neighbourhood. */
const FALLBACK = { lat: 52.4996, lon: 13.418, label: 'Kreuzberg' };

export interface ViewerPoint {
  lat: number;
  lon: number;
  label: string;
  /** True when this is the fallback rather than the member's or the browser's. */
  assumed: boolean;
}

function readStored(): ViewerPoint | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ViewerPoint;
    if (typeof parsed.lat !== 'number' || typeof parsed.lon !== 'number') return null;
    return parsed;
  } catch {
    return null;
  }
}

export function useViewerPoint() {
  const { me } = useAuth();
  const [guestPoint, setGuestPoint] = useState<ViewerPoint | null>(null);
  const [locating, setLocating] = useState(false);

  useEffect(() => {
    setGuestPoint(readStored());
  }, []);

  const point: ViewerPoint =
    me?.lat != null && me?.lon != null
      ? { lat: me.lat, lon: me.lon, label: me.areaLabel ?? 'Your area', assumed: false }
      : (guestPoint ?? { ...FALLBACK, assumed: true });

  /** Asks the browser once, on an explicit tap — never on load. */
  const locate = useCallback(() => {
    if (!('geolocation' in navigator)) return;
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const next: ViewerPoint = {
          lat: pos.coords.latitude,
          lon: pos.coords.longitude,
          label: 'Where you are',
          assumed: false,
        };
        localStorage.setItem(STORE_KEY, JSON.stringify(next));
        setGuestPoint(next);
        setLocating(false);
      },
      () => setLocating(false),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 600_000 },
    );
  }, []);

  return { point, locate, locating };
}
