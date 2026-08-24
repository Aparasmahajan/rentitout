'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useRequireAuth } from '@/lib/auth';
import { initials } from '@/lib/format';

const RELATIONS = [
  { key: 'has', label: 'I can do' },
  { key: 'teaches', label: 'I can teach' },
  { key: 'needs', label: 'I need' },
  { key: 'enjoys', label: 'I enjoy' },
];

export default function MePage() {
  const { me, loading, refresh, signOut } = useRequireAuth();
  const queryClient = useQueryClient();

  const [displayName, setDisplayName] = useState('');
  const [areaLabel, setAreaLabel] = useState('');
  const [bio, setBio] = useState('');
  const [radiusKm, setRadiusKm] = useState(5);
  const [openToRequests, setOpenToRequests] = useState(true);
  const [relation, setRelation] = useState('has');
  const [locating, setLocating] = useState(false);
  const [point, setPoint] = useState<{ lat: number; lon: number } | null>(null);

  useEffect(() => {
    if (!me) return;
    setDisplayName(me.displayName);
    setAreaLabel(me.areaLabel ?? '');
    setBio(me.bio ?? '');
    setRadiusKm(me.searchRadiusKm);
    setOpenToRequests(me.openToRequests);
  }, [me]);

  const vocabulary = useQuery({ queryKey: ['tags'], queryFn: () => api.tags.all() });

  const save = useMutation({
    mutationFn: () =>
      api.me.patch({
        displayName,
        areaLabel,
        bio,
        searchRadiusKm: radiusKm,
        openToRequests,
        ...(point ?? {}),
      }),
    onSuccess: async () => {
      await refresh();
      await queryClient.invalidateQueries({ queryKey: ['feed'] });
    },
  });

  const toggleTag = useMutation({
    mutationFn: ({ slug, on }: { slug: string; on: boolean }) =>
      on ? api.me.removeTag(slug, relation) : api.me.addTag(slug, relation),
    onSuccess: () => refresh(),
  });

  if (loading || !me) return <div className="skeleton" style={{ marginTop: 24 }} />;

  const mine = new Set(me.tags.filter((t) => t.relation === relation).map((t) => t.slug));

  return (
    <section className="stack" style={{ paddingTop: 'var(--space-6)' }}>
      <div className="row">
        <div className="thumb-placeholder" style={{ width: 64, aspectRatio: '1', borderRadius: 999 }}>
          {initials(me.displayName)}
        </div>
        <div className="grow">
          <h1 style={{ margin: 0 }}>{me.displayName}</h1>
          <span className="muted">
            {me.areaLabel ?? 'No area set'} · {me.completedCount} completed
          </span>
          <div className="wrap" style={{ marginTop: 6 }}>
            {me.idChecked && <span className="badge" data-tone="checked">ID checked</span>}
            {me.professional && <span className="badge" data-tone="pro">Professional</span>}
            {me.role === 'ADMIN' && <span className="badge">Administrator</span>}
          </div>
        </div>
      </div>

      <div className="field">
        <label htmlFor="name">Name</label>
        <input id="name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
      </div>

      <div className="field">
        <label htmlFor="area">Neighbourhood</label>
        <input id="area" value={areaLabel} onChange={(e) => setAreaLabel(e.target.value)} placeholder="Kreuzberg" />
      </div>

      <div className="field">
        <label htmlFor="bio">About you</label>
        <textarea id="bio" rows={3} value={bio} onChange={(e) => setBio(e.target.value)} />
      </div>

      <div className="stack">
        <span className="tiny">Home point</span>
        <p className="muted">
          {point
            ? `${point.lat.toFixed(3)}, ${point.lon.toFixed(3)} — saved rounded to about 100 m`
            : me.lat
              ? `${me.lat.toFixed(3)}, ${me.lon?.toFixed(3)} — rounded to about 100 m`
              : 'Not set — the feed needs this'}
        </p>
        <button
          className="btn ghost"
          disabled={locating}
          onClick={() => {
            setLocating(true);
            navigator.geolocation.getCurrentPosition(
              (pos) => {
                setPoint({ lat: pos.coords.latitude, lon: pos.coords.longitude });
                setLocating(false);
              },
              () => setLocating(false),
              { enableHighAccuracy: false, timeout: 8000 },
            );
          }}
        >
          {locating ? 'Locating…' : 'Use my current location'}
        </button>
      </div>

      <label className="field">
        <span>Search radius: {radiusKm} km</span>
        <input type="range" min={1} max={25} value={radiusKm} onChange={(e) => setRadiusKm(Number(e.target.value))} />
      </label>

      <label className="row">
        <input type="checkbox" checked={openToRequests} onChange={(e) => setOpenToRequests(e.target.checked)} />
        <span>Open to requests</span>
      </label>

      <button className="btn block" onClick={() => save.mutate()} disabled={save.isPending}>
        {save.isPending ? 'Saving…' : 'Save profile'}
      </button>

      <div className="stack">
        <h2>Tags</h2>
        <div className="wrap">
          {RELATIONS.map((r) => (
            <button key={r.key} className="chip" data-active={relation === r.key} onClick={() => setRelation(r.key)}>
              {r.label}
            </button>
          ))}
        </div>
        <div className="wrap">
          {(vocabulary.data ?? []).map((tag) => (
            <button
              key={tag.slug}
              className="chip"
              data-active={mine.has(tag.slug)}
              onClick={() => toggleTag.mutate({ slug: tag.slug, on: mine.has(tag.slug) })}
            >
              {tag.label}
            </button>
          ))}
        </div>
      </div>

      <div className="card stack" style={{ padding: 'var(--s5)' }}>
        <div className="between">
          <div className="stack" style={{ gap: 4 }}>
            <strong>Checks</strong>
            <span className="muted">
              {me.idChecked
                ? 'Your ID is checked.'
                : 'Get your ID checked to offer services that visit homes.'}
            </span>
          </div>
          <span className="badge" data-tone={me.idChecked ? 'checked' : 'pending'}>
            {me.idChecked ? 'ID checked' : 'Not checked'}
          </span>
        </div>
        <Link href="/me/verification" className="btn ghost" style={{ alignSelf: 'flex-start' }}>
          {me.idChecked ? 'View your checks' : 'Get checked'}
        </Link>
      </div>

      {me.idChecked && (
        <Link href="/me/professional" className="btn ghost block">
          {me.professional ? 'Edit trade details' : 'Offer a professional service'}
        </Link>
      )}

      {me.role === 'ADMIN' && (
        <Link href="/admin" className="btn ghost block">
          Review queue
        </Link>
      )}

      <Link href="/dashboard" className="btn ghost block">
        My listings
      </Link>
      <button className="btn danger block" onClick={() => void signOut()}>
        Sign out
      </button>
    </section>
  );
}
