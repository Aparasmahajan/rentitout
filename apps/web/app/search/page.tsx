'use client';

import { useMutation } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { ListingTile } from '@/components/ListingTile';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useViewerPoint } from '@/lib/useViewerPoint';
import { KINDS, KIND_LABEL, distance, initials } from '@/lib/format';
import type { SearchResponse } from '@/lib/types';

const EXAMPLES = [
  'I need someone who knows video editing and lives within 5 km',
  'ladder this weekend',
  'who can teach guitar nearby',
  'want to buy a printer',
];

/**
 * Two modes onto one endpoint. Ask parses a sentence and shows what it
 * understood as removable chips; Filter is the same query, set by hand.
 */
export default function SearchPage() {
  const { loading } = useAuth();
  const { point } = useViewerPoint();
  const [mode, setMode] = useState<'ask' | 'filter'>('ask');
  const [q, setQ] = useState('');
  const [keyword, setKeyword] = useState('');
  const [kinds, setKinds] = useState<string[]>([]);
  const [radiusKm, setRadiusKm] = useState(5);
  const [sort, setSort] = useState('relevance');
  const [dropped, setDropped] = useState<string[]>([]);
  const [result, setResult] = useState<SearchResponse | null>(null);

  const run = useMutation({
    mutationFn: () =>
      api.search.run({
        lat: point.lat,
        lon: point.lon,
        q: mode === 'ask' ? q : undefined,
        keyword: mode === 'filter' ? keyword || undefined : undefined,
        kinds: kinds.length ? kinds : undefined,
        tags: dropped.length && result ? result.parsed.tags.filter((t) => !dropped.includes(t)) : undefined,
        radiusKm,
        sort,
      }),
    onSuccess: setResult,
  });

  if (loading) return <div className="skeleton" style={{ marginTop: 24 }} />;

  const parsed = result?.parsed;

  return (
    <section className="stack" style={{ paddingTop: 'var(--space-6)' }}>
      <h1 style={{ margin: 0 }}>Search</h1>

      <div className="wrap" role="tablist">
        <button className="chip" data-active={mode === 'ask'} onClick={() => setMode('ask')} role="tab">
          Ask
        </button>
        <button className="chip" data-active={mode === 'filter'} onClick={() => setMode('filter')} role="tab">
          Filter
        </button>
      </div>

      {mode === 'ask' ? (
        <div className="stack">
          <div className="field">
            <label htmlFor="q">What do you need?</label>
            <textarea
              id="q"
              rows={2}
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="I need someone who knows video editing and lives within 5 km"
            />
          </div>
          <div className="wrap">
            {EXAMPLES.map((example) => (
              <button key={example} className="chip" onClick={() => setQ(example)}>
                {example}
              </button>
            ))}
          </div>
        </div>
      ) : (
        <div className="stack">
          <div className="field">
            <label htmlFor="keyword">Keyword</label>
            <input id="keyword" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
          </div>
          <div className="wrap">
            {KINDS.map((k) => (
              <button
                key={k}
                className="chip"
                data-active={kinds.includes(k)}
                onClick={() => setKinds((prev) => (prev.includes(k) ? prev.filter((x) => x !== k) : [...prev, k]))}
              >
                {KIND_LABEL[k]}
              </button>
            ))}
          </div>
        </div>
      )}

      <label className="field">
        <span>Within {radiusKm} km</span>
        <input type="range" min={1} max={25} value={radiusKm} onChange={(e) => setRadiusKm(Number(e.target.value))} />
      </label>

      <div className="wrap">
        {['relevance', 'distance', 'price', 'rating'].map((option) => (
          <button key={option} className="chip" data-active={sort === option} onClick={() => setSort(option)}>
            {option}
          </button>
        ))}
      </div>

      <button className="btn block" onClick={() => run.mutate()} disabled={run.isPending}>
        {run.isPending ? 'Looking…' : 'Search'}
      </button>

      {run.isError && <div className="error">{(run.error as Error).message}</div>}

      {parsed && (
        <div className="stack">
          <span className="tiny">What we understood ({parsed.source})</span>
          <div className="wrap">
            {parsed.tags
              .filter((tag) => !dropped.includes(tag))
              .map((tag) => (
                <button
                  key={tag}
                  className="chip"
                  data-active
                  onClick={() => {
                    setDropped((prev) => [...prev, tag]);
                    run.mutate();
                  }}
                  title="Remove this"
                >
                  {tag} ×
                </button>
              ))}
            {parsed.kinds.map((kind) => (
              <span key={kind} className="chip static">
                {KIND_LABEL[kind as keyof typeof KIND_LABEL] ?? kind}
              </span>
            ))}
            {parsed.radiusKm && <span className="chip static">within {parsed.radiusKm} km</span>}
            {parsed.day && <span className="chip static">{parsed.day}</span>}
            {parsed.tags.length === 0 && parsed.terms.length === 0 && (
              <span className="muted">Nothing specific — showing everything nearby.</span>
            )}
          </div>
        </div>
      )}

      {result && result.members.length > 0 && (
        <div className="stack">
          <h2>Neighbours</h2>
          <div className="grid">
            {result.members.map((member) => (
              <Link key={member.id} href={`/u/${member.id}`} className="card" style={{ padding: 'var(--space-4)' }}>
                <div className="row">
                  <div className="thumb-placeholder" style={{ width: 48, aspectRatio: '1', borderRadius: 999 }}>
                    {initials(member.displayName)}
                  </div>
                  <div>
                    <strong>{member.displayName}</strong>
                    <div className="muted">
                      {member.areaLabel} · {distance(member.distanceKm)}
                    </div>
                  </div>
                </div>
                <div className="wrap" style={{ marginTop: 'var(--space-3)' }}>
                  {member.tags.slice(0, 4).map((tag) => (
                    <span key={tag} className="chip static">
                      {tag}
                    </span>
                  ))}
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}

      {result && (
        <div className="stack">
          <h2>{result.listings.length > 0 ? 'Listings' : 'Nothing matched'}</h2>
          <div className="grid">
            {result.listings.map((hit) => (
              <ListingTile
                key={hit.id}
                listing={{
                  ...hit,
                  owner: { displayName: hit.ownerName, idChecked: false, professional: false },
                }}
              />
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
