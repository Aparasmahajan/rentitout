import * as Location from 'expo-location';
import * as SecureStore from 'expo-secure-store';
import { useCallback, useEffect, useState } from 'react';
import { useAuth } from './auth';

/**
 * Where to centre "nearby".
 *
 * A signed-in member has a saved home point. A guest has none, and the API will
 * not guess for them — so we ask the device on an explicit tap, remember the
 * answer, and fall back to a default area so the first launch is never empty.
 */

const STORE_KEY = 'radius.point';

/** Kreuzberg. Replace with your launch neighbourhood. */
const FALLBACK = { lat: 52.4996, lon: 13.418, label: 'Kreuzberg' };

export interface ViewerPoint {
  lat: number;
  lon: number;
  label: string;
  assumed: boolean;
}

export function useViewerPoint() {
  const { me } = useAuth();
  const [guestPoint, setGuestPoint] = useState<ViewerPoint | null>(null);
  const [locating, setLocating] = useState(false);

  useEffect(() => {
    SecureStore.getItemAsync(STORE_KEY)
      .then((raw) => {
        if (!raw) return;
        const parsed = JSON.parse(raw) as ViewerPoint;
        if (typeof parsed.lat === 'number' && typeof parsed.lon === 'number') setGuestPoint(parsed);
      })
      .catch(() => undefined);
  }, []);

  const point: ViewerPoint =
    me?.lat != null && me?.lon != null
      ? { lat: me.lat, lon: me.lon, label: me.areaLabel ?? 'Your area', assumed: false }
      : (guestPoint ?? { ...FALLBACK, assumed: true });

  const locate = useCallback(async () => {
    setLocating(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return;
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Low });
      const next: ViewerPoint = {
        lat: position.coords.latitude,
        lon: position.coords.longitude,
        label: 'Where you are',
        assumed: false,
      };
      await SecureStore.setItemAsync(STORE_KEY, JSON.stringify(next)).catch(() => undefined);
      setGuestPoint(next);
    } finally {
      setLocating(false);
    }
  }, []);

  return { point, locate, locating };
}
