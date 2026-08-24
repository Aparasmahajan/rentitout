import Constants from 'expo-constants';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import type {
  AppNotification,
  BookingRequest,
  Breakdown,
  ChatMessage,
  FeedPage,
  Listing,
  ListingCard,
  MapResponse,
  Me,
  PublicProfile,
  SearchResponse,
  TagRef,
  Tokens,
} from './types';

/**
 * The gateway is not on localhost from a device's point of view:
 *   Android emulator  -> 10.0.2.2
 *   iOS simulator     -> localhost
 *   real phone        -> your machine's LAN address, set EXPO_PUBLIC_API_URL
 */
function resolveBaseUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_URL;
  if (fromEnv) return fromEnv;

  const fromConfig = (Constants.expoConfig?.extra as { apiUrl?: string } | undefined)?.apiUrl;
  if (Platform.OS === 'android') return fromConfig ?? 'http://10.0.2.2:8080';
  return 'http://localhost:8080';
}

export const BASE_URL = resolveBaseUrl();

const ACCESS_KEY = 'radius.access';
const REFRESH_KEY = 'radius.refresh';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

/** Tokens live in the keychain / keystore, never in AsyncStorage. */
export const tokenStore = {
  _access: null as string | null,

  async load() {
    this._access = await SecureStore.getItemAsync(ACCESS_KEY);
    return this._access;
  },
  access() {
    return this._access;
  },
  async refreshToken() {
    return SecureStore.getItemAsync(REFRESH_KEY);
  },
  async set(tokens: { accessToken: string; refreshToken: string }) {
    this._access = tokens.accessToken;
    await SecureStore.setItemAsync(ACCESS_KEY, tokens.accessToken);
    await SecureStore.setItemAsync(REFRESH_KEY, tokens.refreshToken);
  },
  async clear() {
    this._access = null;
    await SecureStore.deleteItemAsync(ACCESS_KEY);
    await SecureStore.deleteItemAsync(REFRESH_KEY);
  },
};

let refreshing: Promise<boolean> | null = null;

async function refreshTokens(): Promise<boolean> {
  const refreshToken = await tokenStore.refreshToken();
  if (!refreshToken) return false;

  // One rotation at a time: parallel refreshes would present a revoked token
  // and the server would treat that as a leak and end every session.
  refreshing ??= (async () => {
    try {
      const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) {
        await tokenStore.clear();
        return false;
      }
      await tokenStore.set((await res.json()) as Tokens);
      return true;
    } finally {
      setTimeout(() => {
        refreshing = null;
      }, 0);
    }
  })();

  return refreshing;
}

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const access = tokenStore.access();
  if (access) headers.set('Authorization', `Bearer ${access}`);

  const res = await fetch(`${BASE_URL}${path}`, { ...init, headers });

  if (res.status === 401 && retry && (await refreshTokens())) {
    return request<T>(path, init, false);
  }

  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as {
      code?: string;
      message?: string;
      fields?: Record<string, string>;
    };
    throw new ApiError(res.status, body.code ?? 'error', body.message ?? `Request failed (${res.status})`, body.fields ?? {});
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

const get = <T,>(path: string) => request<T>(path);
const post = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
const patch = <T,>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body: JSON.stringify(body) });
const del = <T,>(path: string) => request<T>(path, { method: 'DELETE' });

export const api = {
  baseUrl: BASE_URL,

  auth: {
    startOtp: (phone: string) =>
      post<{ phone: string; expiresInSeconds: number; devCode: string | null }>('/api/auth/otp', { phone }),
    verify: (phone: string, code: string, displayName?: string) =>
      post<Tokens>('/api/auth/verify', { phone, code, displayName }),
    logout: () => post<void>('/api/auth/logout'),
  },

  me: {
    get: () => get<Me>('/api/me'),
    patch: (body: Record<string, unknown>) => patch<Me>('/api/me/profile', body),
    addTag: (slug: string, relation: string) => post<TagRef[]>('/api/me/tags', { slug, relation }),
    removeTag: (slug: string, relation: string) => del<TagRef[]>(`/api/me/tags/${slug}?relation=${relation}`),
  },

  users: { profile: (id: string) => get<PublicProfile>(`/api/users/${id}`) },

  tags: { all: (kind?: string) => get<TagRef[]>(`/api/tags${kind ? `?kind=${kind}` : ''}`) },

  listings: {
    feed: (params: { radiusKm?: number; kind?: string; cursor?: string; lat?: number; lon?: number }) => {
      const q = new URLSearchParams();
      Object.entries(params).forEach(([k, v]) => v !== undefined && v !== null && q.set(k, String(v)));
      return get<FeedPage>(`/api/feed?${q.toString()}`);
    },
    mine: () => get<ListingCard[]>('/api/listings/mine'),
    detail: (id: string) => get<Listing>(`/api/listings/${id}`),
    create: (body: unknown) => post<Listing>('/api/listings', body),
    pause: (id: string) => post<Listing>(`/api/listings/${id}/pause`),
    resume: (id: string) => post<Listing>(`/api/listings/${id}/resume`),
    availability: (id: string) =>
      get<{ listingId: string; blocked: { from: string; to: string; reason: string }[] }>(
        `/api/listings/${id}/availability`,
      ),
    presign: (contentType: string, sizeBytes: number) =>
      post<{ uploadUrl: string; objectKey: string; publicUrl: string }>('/api/photos/presign', {
        contentType,
        sizeBytes,
      }),
    attachPhoto: (id: string, objectKey: string) => post<unknown>(`/api/listings/${id}/photos`, { objectKey }),
  },

  search: {
    run: (body: unknown) => post<SearchResponse>('/api/search', body),
    map: (bbox: string) => get<MapResponse>(`/api/search/map?bbox=${bbox}`),
  },

  requests: {
    quote: (listingId: string, startDate: string, units: number) =>
      post<Breakdown>('/api/requests/quote', { listingId, startDate, units }),
    create: (body: { listingId: string; startDate: string; units: number; message?: string }) =>
      post<BookingRequest>('/api/requests', body),
    list: (direction: 'in' | 'out') => get<BookingRequest[]>(`/api/requests?direction=${direction}`),
    get: (id: string) => get<BookingRequest>(`/api/requests/${id}`),
    accept: (id: string) => post<BookingRequest>(`/api/requests/${id}/accept`),
    decline: (id: string) => post<BookingRequest>(`/api/requests/${id}/decline`, {}),
    start: (id: string) => post<BookingRequest>(`/api/requests/${id}/start`),
    complete: (id: string) => post<BookingRequest>(`/api/requests/${id}/complete`),
    cancel: (id: string) => post<BookingRequest>(`/api/requests/${id}/cancel`),
  },

  threads: {
    history: (requestId: string) => get<ChatMessage[]>(`/api/threads/${requestId}`),
    post: (requestId: string, body: string) => post<ChatMessage>(`/api/threads/${requestId}`, { body }),
    markRead: (requestId: string) => post<{ marked: number }>(`/api/threads/${requestId}/read`),
  },

  notifications: {
    inbox: () => get<AppNotification[]>('/api/notifications'),
    unread: () => get<{ count: number }>('/api/notifications/unread'),
  },
};

/** Photos go straight to the object store, same as on the web. */
export async function uploadPhoto(uri: string, mimeType: string): Promise<string> {
  const blob = await (await fetch(uri)).blob();
  const { uploadUrl, objectKey } = await api.listings.presign(mimeType, blob.size || 1);
  const res = await fetch(uploadUrl, { method: 'PUT', headers: { 'Content-Type': mimeType }, body: blob });
  if (!res.ok) throw new ApiError(res.status, 'upload_failed', 'That photo would not upload');
  return objectKey;
}
