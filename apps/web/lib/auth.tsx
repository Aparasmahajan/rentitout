'use client';

import { usePathname, useRouter } from 'next/navigation';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, tokenStore } from './api';
import type { Me } from './types';

interface AuthState {
  me: Me | null;
  loading: boolean;
  signedIn: boolean;
  isAdmin: boolean;
  setMe: (me: Me) => void;
  signOut: () => Promise<void>;
  refresh: () => Promise<void>;
  /**
   * Send someone to sign in and bring them back to where they were. Called at
   * the moment of a real action — never on page load.
   */
  requireSignIn: (returnTo?: string) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();
  const pathname = usePathname();

  const refresh = useCallback(async () => {
    if (!tokenStore.access()) {
      setMe(null);
      setLoading(false);
      return;
    }
    try {
      setMe(await api.me.get());
    } catch {
      tokenStore.clear();
      setMe(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const signOut = useCallback(async () => {
    try {
      await api.auth.logout();
    } catch {
      // Already gone as far as we are concerned.
    }
    tokenStore.clear();
    setMe(null);
    router.push('/');
  }, [router]);

  const requireSignIn = useCallback(
    (returnTo?: string) => {
      const target = returnTo ?? pathname ?? '/';
      router.push(`/sign-in?next=${encodeURIComponent(target)}`);
    },
    [pathname, router],
  );

  const value = useMemo<AuthState>(
    () => ({
      me,
      loading,
      signedIn: me !== null,
      isAdmin: me?.role === 'ADMIN',
      setMe,
      signOut,
      refresh,
      requireSignIn,
    }),
    [me, loading, signOut, refresh, requireSignIn],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

/**
 * For the handful of pages that genuinely cannot render for a guest — your own
 * requests, your dashboard, the review queue. Browsing pages must NOT use this:
 * the whole point is that the feed, search, the map and a listing all read
 * without an account.
 */
export function useRequireAuth(): AuthState {
  const auth = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!auth.loading && !auth.signedIn) {
      router.replace(`/sign-in?next=${encodeURIComponent(pathname ?? '/')}`);
    }
  }, [auth.loading, auth.signedIn, pathname, router]);

  return auth;
}
