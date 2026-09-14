'use client';

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
  MyVerification,
  ProfessionalProfile,
  PublicProfile,
  ReviewItem,
  SearchResponse,
  TagRef,
  Tokens,
  VerificationStatus,
} from './types';

const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

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

export const tokenStore = {
  access: () => (typeof window === 'undefined' ? null : localStorage.getItem(ACCESS_KEY)),
  refresh: () => (typeof window === 'undefined' ? null : localStorage.getItem(REFRESH_KEY)),
  set(tokens: { accessToken: string; refreshToken: string }) {
    localStorage.setItem(ACCESS_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

/**
 * One in-flight refresh at a time. Without this, five parallel queries hitting
 * a just-expired token would each rotate the refresh token, and four of them
 * would present a revoked one — which the server correctly treats as a replay
 * and logs the member out of everything.
 */
let refreshing: Promise<boolean> | null = null;

async function refreshTokens(): Promise<boolean> {
  const refreshToken = tokenStore.refresh();
  if (!refreshToken) return false;

  refreshing ??= (async () => {
    try {
      const res = await fetch(`${BASE}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) {
        tokenStore.clear();
        return false;
      }
      tokenStore.set((await res.json()) as Tokens);
      return true;
    } finally {
      // Let the next 401 start a fresh attempt.
      setTimeout(() => {
        refreshing = null;
      }, 0);
    }
  })();

  return refreshing;
}

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const access = tokenStore.access();
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (access) headers.set('Authorization', `Bearer ${access}`);

  const res = await fetch(`${BASE}${path}`, { ...init, headers });

  if (res.status === 401 && retry && (await refreshTokens())) {
    return request<T>(path, init, false);
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(
      res.status,
      body.code ?? 'error',
      body.message ?? `Request failed (${res.status})`,
      body.fields ?? {},
    );
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

const get = <T,>(path: string) => request<T>(path);
const post = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
const patch = <T,>(path: string, body: unknown) =>
  request<T>(path, { method: 'PATCH', body: JSON.stringify(body) });
const del = <T,>(path: string) => request<T>(path, { method: 'DELETE' });

export const api = {
  baseUrl: BASE,

  auth: {
    startOtp: (phone: string) =>
      post<{ phone: string; expiresInSeconds: number; devCode: string | null }>('/api/auth/otp', { phone }),
    verify: (phone: string, code: string, displayName?: string) =>
      post<Tokens>('/api/auth/verify', { phone, code, displayName }),
    logout: () => post<void>('/api/auth/logout'),
  },

  me: {
    get: () => get<Me>('/api/me'),
    patch: (body: Partial<Me>) => patch<Me>('/api/me/profile', body),
    addTag: (slug: string, relation: string) => post<TagRef[]>('/api/me/tags', { slug, relation }),
    removeTag: (slug: string, relation: string) =>
      del<TagRef[]>(`/api/me/tags/${slug}?relation=${relation}`),
  },

  users: {
    profile: (id: string) => get<PublicProfile>(`/api/users/${id}`),
  },

  tags: {
    all: (kind?: string) => get<TagRef[]>(`/api/tags${kind ? `?kind=${kind}` : ''}`),
  },

  listings: {
    feed: (params: { radiusKm?: number; kind?: string; cursor?: string; lat?: number; lon?: number }) => {
      const q = new URLSearchParams();
      Object.entries(params).forEach(([k, v]) => v !== undefined && v !== null && q.set(k, String(v)));
      return get<FeedPage>(`/api/feed?${q.toString()}`);
    },
    mine: () => get<ListingCard[]>('/api/listings/mine'),
    detail: (id: string) => get<Listing>(`/api/listings/${id}`),
    create: (body: unknown) => post<Listing>('/api/listings', body),
    update: (id: string, body: unknown) => patch<Listing>(`/api/listings/${id}`, body),
    pause: (id: string) => post<Listing>(`/api/listings/${id}/pause`),
    resume: (id: string) => post<Listing>(`/api/listings/${id}/resume`),
    unlist: (id: string) => del<Listing>(`/api/listings/${id}`),
    availability: (id: string) =>
      get<{ listingId: string; weekly: unknown[]; blocked: { from: string; to: string; reason: string }[] }>(
        `/api/listings/${id}/availability`,
      ),
    presign: (contentType: string, sizeBytes: number) =>
      post<{ uploadUrl: string; objectKey: string; publicUrl: string; expiresInSeconds: number }>(
        '/api/photos/presign',
        { contentType, sizeBytes },
      ),
    attachPhoto: (id: string, objectKey: string) =>
      post<{ id: string; url: string }[]>(`/api/listings/${id}/photos`, { objectKey }),
  },

  search: {
    run: (body: unknown) => post<SearchResponse>('/api/search', body),
    map: (bbox: string) => get<MapResponse>(`/api/search/map?bbox=${bbox}`),
    saved: () => get<{ id: string; label: string; query: string; radiusKm: number }[]>('/api/saved-searches'),
    save: (body: unknown) => post<{ id: string }>('/api/saved-searches', body),
    unsave: (id: string) => del<void>(`/api/saved-searches/${id}`),
  },

  requests: {
    quote: (listingId: string, startDate: string, units: number) =>
      post<Breakdown>('/api/requests/quote', { listingId, startDate, units }),
    create: (body: { listingId: string; startDate: string; units: number; message?: string }) =>
      post<BookingRequest>('/api/requests', body),
    list: (direction: 'in' | 'out') => get<BookingRequest[]>(`/api/requests?direction=${direction}`),
    get: (id: string) => get<BookingRequest>(`/api/requests/${id}`),
    accept: (id: string) => post<BookingRequest>(`/api/requests/${id}/accept`),
    decline: (id: string, reason?: string) => post<BookingRequest>(`/api/requests/${id}/decline`, { reason }),
    start: (id: string) => post<BookingRequest>(`/api/requests/${id}/start`),
    complete: (id: string) => post<BookingRequest>(`/api/requests/${id}/complete`),
    cancel: (id: string) => post<BookingRequest>(`/api/requests/${id}/cancel`),
  },

  threads: {
    history: (requestId: string) => get<ChatMessage[]>(`/api/threads/${requestId}`),
    post: (requestId: string, body: string) => post<ChatMessage>(`/api/threads/${requestId}`, { body }),
    markRead: (requestId: string) => post<{ marked: number }>(`/api/threads/${requestId}/read`),
  },

  verification: {
    mine: () => get<MyVerification>('/api/me/verification'),
    submit: (body: { kind: string; evidenceKeys?: string[]; note?: string }) =>
      post<VerificationStatus>('/api/me/verification', body),
    withdraw: (id: string) => del<void>(`/api/me/verification/${id}`),
  },

  professionals: {
    mine: () => get<ProfessionalProfile>('/api/me/professional'),
    save: (body: Record<string, unknown>) =>
      request<ProfessionalProfile>('/api/me/professional', {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
    get: (id: string) => get<ProfessionalProfile>(`/api/professionals/${id}`),
  },

  admin: {
    queue: (state?: string) =>
      get<ReviewItem[]>(`/api/admin/verifications${state ? `?state=${state}` : ''}`),
    claim: (id: string) => post<ReviewItem>(`/api/admin/verifications/${id}/claim`),
    approve: (id: string, note?: string) =>
      post<ReviewItem>(`/api/admin/verifications/${id}/approve`, { note }),
    reject: (id: string, note: string) =>
      post<ReviewItem>(`/api/admin/verifications/${id}/reject`, { note }),
    suspend: (userId: string, note?: string) =>
      post<ProfessionalProfile>(`/api/admin/professionals/${userId}/suspend`, { note }),
  },

  notifications: {
    inbox: () => get<AppNotification[]>('/api/notifications'),
    unread: () => get<{ count: number }>('/api/notifications/unread'),
    markRead: () => post<{ marked: number }>('/api/notifications/read'),
  },
};

/** Photos go straight to the object store; the API only ever sees the key. */
export async function uploadPhoto(file: File): Promise<string> {
  const { uploadUrl, objectKey } = await api.listings.presign(file.type, file.size);
  const res = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file,
  });
  if (!res.ok) throw new ApiError(res.status, 'upload_failed', 'That photo would not upload');
  return objectKey;
}
