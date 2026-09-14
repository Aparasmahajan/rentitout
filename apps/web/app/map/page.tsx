'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { RadiusMap } from '@/components/RadiusMap';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { KIND_LABEL, distance, money, rate } from '@/lib/format';
import { useViewerPoint } from '@/lib/useViewerPoint';
import type { ListingKind, SearchResponse } from '@/lib/types';

const STEPS = [1, 2, 5, 10, 25, 50];

/**
 * The map screen. No account needed.
 *
 * With a query it maps what matches; with an empty query it maps everything
 * inside the radius — which is the honest default for "what is near me".
 */
export default function MapPage() {
  const { signedIn } = useAuth();
  const { point, locate, locating } = useViewerPoint();

  const [radiusKm, setRadiusKm] = useState(5);
  const [draft, setDraft] = useState('');
  const [query, setQuery] = useState('');

  const results = useQuery<SearchResponse>({
    queryKey: ['map-search', query, radiusKm, point.lat, point.lon],
    // An empty query is a real search here: it means "everything nearby".
    queryFn: () =>
      api.search.run({
        q: query.trim() === '' ? undefined : query.trim(),
        radiusKm,
        lat: point.lat,
        lon: point.lon,
        sort: 'distance',
      }),
  });

  const hits = results.data?.listings ?? [];
  const nothingListed = !results.isPending && !results.isError && hits.length === 0;

  return (
    <section className="stack" style={{ paddingTop: 'var(--s6)', gap: 'var(--s5)' }}>
      <div className="stack">
        <span className="eyebrow">Around {point.label}</span>
        <h1>
          What is <em>within reach</em>
        </h1>
      </div>

      <form
        className="row"
        style={{ gap: 'var(--s2)', flexWrap: 'wrap' }}
        onSubmit={(e) => {
          e.preventDefault();
          setQuery(draft);
        }}
      >
        <input
          className="grow"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Electrician, ladder, someone who teaches guitar…"
          aria-label="Search services near you"
          style={{
            minHeight: 46,
            padding: '11px 14px',
            border: '1px solid var(--rule)',
            borderRadius: 'var(--r-pill)',
            background: 'var(--surface)',
            minWidth: 220,
          }}
        />
        <button className="btn" type="submit">
          Search
        </button>
        {query && (
          <button
            className="btn quiet"
            type="button"
            onClick={() => {
              setDraft('');
              setQuery('');
            }}
          >
            Show everything
          </button>
        )}
      </form>

      <div className="stack">
        <div className="between" style={{ flexWrap: 'wrap' }}>
          <span className="eyebrow">Distance</span>
          <button className="btn quiet" onClick={locate} disabled={locating} style={{ minHeight: 36 }}>
            {locating ? 'Locating…' : point.assumed ? 'Use my location' : 'Update my location'}
          </button>
        </div>
        <div className="wrap">
          {STEPS.map((km) => (
            <button
              key={km}
              className="chip"
              data-active={radiusKm === km}
              onClick={() => setRadiusKm(km)}
            >
              {km} km
            </button>
          ))}
        </div>
      </div>

      {point.assumed && (
        <p className="muted">
          Showing {point.label} until you share your location — nothing is sent anywhere, it just
          centres the map.
        </p>
      )}

      <RadiusMap centre={point} radiusKm={radiusKm} hits={hits} loading={results.isPending} />

      {results.isError && (
        <div className="error">{(results.error as Error).message}</div>
      )}

      {nothingListed && (
        <div className="notice">
          <strong>Nothing is listed here yet.</strong>
          <div className="muted" style={{ marginTop: 6 }}>
            {query
              ? `No neighbour within ${radiusKm} km is offering “${query}”. Try a wider distance, or clear the search to see everything.`
              : `Nobody within ${radiusKm} km has listed anything. Widen the distance, or be the first.`}
          </div>
          <div className="row" style={{ marginTop: 'var(--s3)', flexWrap: 'wrap' }}>
            {radiusKm < 50 && (
              <button
                className="btn ghost"
                onClick={() => setRadiusKm(STEPS[Math.min(STEPS.indexOf(radiusKm) + 1, STEPS.length - 1)])}
              >
                Widen the search
              </button>
            )}
            <Link href="/new" className="btn">
              List something
            </Link>
          </div>
        </div>
      )}

      {hits.length > 0 && (
        <div className="stack">
          <div className="between">
            <h2>{query ? `Matching “${query}”` : 'Everything nearby'}</h2>
            <span className="muted">{hits.length} found</span>
          </div>

          <div className="stack" style={{ gap: 0 }}>
            {hits.map((hit) => (
              <Link
                key={hit.id}
                href={`/l/${hit.id}`}
                className="between"
                style={{ padding: 'var(--s4) 0', borderTop: '1px solid var(--rule)', gap: 'var(--s4)' }}
              >
                <span className="stack" style={{ gap: 4 }}>
                  <span className="eyebrow">
                    {KIND_LABEL[hit.kind as ListingKind] ?? hit.kind} · {distance(hit.distanceKm)}
                  </span>
                  <h3 style={{ margin: 0 }}>{hit.title}</h3>
                  <span className="muted">{hit.ownerName ?? 'A neighbour'}</span>
                </span>
                <span className="price" style={{ whiteSpace: 'nowrap' }}>
                  {hit.kind === 'SELL_ITEM' && hit.buyPriceMinor !== null
                    ? money(hit.buyPriceMinor, hit.currency)
                    : rate(hit.priceMinor, hit.unit, hit.currency)}
                </span>
              </Link>
            ))}
          </div>
        </div>
      )}

      {!signedIn && hits.length > 0 && (
        <p className="muted">
          Browsing is open. You only need an account when you ask someone for something.
        </p>
      )}
    </section>
  );
}
