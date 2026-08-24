import AsyncStorageFallback from 'expo-secure-store';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { DARK, LIGHT, makeStyles, type Palette, type Styles } from './theme';

/**
 * Light by default, dark on request, remembered between launches.
 *
 * Deliberately ignores the OS colour scheme: the brief is that a first launch
 * always lands on light.
 */

type Mode = 'light' | 'dark';

const STORE_KEY = 'radius.theme';

interface ThemeState {
  mode: Mode;
  colors: Palette;
  s: Styles;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeState | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<Mode>('light');

  useEffect(() => {
    AsyncStorageFallback.getItemAsync(STORE_KEY)
      .then((stored) => {
        if (stored === 'dark' || stored === 'light') setMode(stored);
      })
      .catch(() => undefined);
  }, []);

  const toggle = useCallback(() => {
    setMode((current) => {
      const next: Mode = current === 'light' ? 'dark' : 'light';
      void AsyncStorageFallback.setItemAsync(STORE_KEY, next).catch(() => undefined);
      return next;
    });
  }, []);

  const value = useMemo<ThemeState>(() => {
    const colors = mode === 'dark' ? DARK : LIGHT;
    return { mode, colors, s: makeStyles(colors), toggle };
  }, [mode, toggle]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeState {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider');
  return ctx;
}
