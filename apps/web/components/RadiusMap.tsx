'use client';

import Link from 'next/link';
import { useMemo } from 'react';
import { money } from '@/lib/format';
import type { SearchListingHit } from '@/lib/types';

/**
 * A tile-free map.
 *
 * Deliberately not Leaflet: a tile provider is a third-party dependency, a key,
 * a rate limit and a per-view cost, and none of that is needed to answer the
 * only question this screen asks — what is near me, and how far. Over a few
 * kilometres an equirectangular projection is accurate to well under a pixel.
 *
 * Swap in MapLibre when streets under the pins genuinely help; the props and
 * the endpoint behind them do not change.
 */

export interface RadiusMapProps {
  centre: { lat: number; lon: number; label: string };
  radiusKm: number;
  hits: SearchListingHit[];
  loading?: boolean;
}

export function RadiusMap({ centre, radiusKm, hits, loading = false }: RadiusMapProps) {
  // The ring is 78% of the box, so a listing exactly at the radius edge still
  // lands inside the frame instead of being clipped off.
  const RING_FRACTION = 0.78;

  const project = useMemo(() => {
    const latSpan = radiusKm / 111.32;
    const lonSpan = latSpan / Math.max(Math.cos((centre.lat * Math.PI) / 180), 0.01);

    return (lat: number, lon: number) => {
      const dx = (lon - centre.lon) / lonSpan;
      const dy = (lat - centre.lat) / latSpan;
      return {
        x: 50 + dx * 50 * RING_FRACTION,
        y: 50 - dy * 50 * RING_FRACTION,
        // Anything beyond the ring is outside the searched radius.
        outside: Math.hypot(dx, dy) > 1.02,
      };
    };
  }, [centre.lat, centre.lon, radiusKm]);

  const plotted = hits
    .map((hit) => ({ hit, at: project(hit.lat, hit.lon) }))
    .filter((p) => !p.at.outside);

  return (
    <div className="map">
      {/* Distance rings: the full radius, and a halfway mark for a sense of scale. */}
      <span className="map-ring" style={{ width: `${RING_FRACTION * 100}%`, height: `${RING_FRACTION * 100}%` }} aria-hidden />
      <span className="map-ring" style={{ width: `${RING_FRACTION * 50}%`, height: `${RING_FRACTION * 50}%`, opacity: 0.6 }} aria-hidden />
      <span className="map-you" aria-hidden />

      <span
        style={{
          position: 'absolute',
          left: '50%',
          top: 'calc(50% + 14px)',
          transform: 'translateX(-50%)',
          fontSize: '0.66rem',
          letterSpacing: '0.14em',
          textTransform: 'uppercase',
          color: 'var(--faint)',
          whiteSpace: 'nowrap',
        }}
      >
        {centre.label}
      </span>

      {plotted.map(({ hit, at }) => (
        <Link
          key={hit.id}
          href={`/l/${hit.id}`}
          className="map-pin"
          style={{ left: `${at.x}%`, top: `${at.y}%` }}
          title={`${hit.title} — ${hit.distanceKm} km`}
        >
          {hit.priceMinor !== null
            ? money(hit.priceMinor, hit.currency)
            : hit.title.length > 14
              ? `${hit.title.slice(0, 14)}…`
              : hit.title}
        </Link>
      ))}

      <span
        style={{
          position: 'absolute',
          right: 'var(--s4)',
          bottom: 'var(--s4)',
          padding: '5px 11px',
          borderRadius: 'var(--r-pill)',
          border: '1px solid var(--rule)',
          background: 'var(--surface)',
          fontSize: '0.7rem',
          color: 'var(--muted)',
        }}
      >
        {loading ? 'Looking…' : `${plotted.length} within ${radiusKm} km`}
      </span>
    </div>
  );
}
