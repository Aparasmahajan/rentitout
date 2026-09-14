import { useRouter } from 'expo-router';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, tokenStore } from './api';
import type { Me } from './types';

interface AuthState {
  me: Me | null;
  loading: boolean;
  signedIn: boolean;
  isAdmin: boolean;
  setMe: (me: Me) => void;
  refresh: () => Promise<void>;
  signOut: () => Promise<void>;
  /** Send someone to sign in, and bring them back here afterwards. */
  requireSignIn: (returnTo?: string) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  const refresh = useCallback(async () => {
    try {
      const token = await tokenStore.load();
      if (!token) {
        setMe(null);
        return;
      }
      setMe(await api.me.get());
    } catch {
      await tokenStore.clear();
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
      // nothing to do - the local tokens go either way
    }
    await tokenStore.clear();
    setMe(null);
  }, []);

  const requireSignIn = useCallback(
    (returnTo?: string) => {
      router.push({ pathname: '/sign-in', params: returnTo ? { next: returnTo } : {} });
    },
    [router],
  );

  const value = useMemo<AuthState>(
    () => ({
      me,
      loading,
      signedIn: me !== null,
      isAdmin: me?.role === 'ADMIN',
      setMe,
      refresh,
      signOut,
      requireSignIn,
    }),
    [me, loading, refresh, signOut, requireSignIn],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
