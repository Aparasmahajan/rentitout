'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
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

const MIN_ZOOM = 1;
const MAX_ZOOM = 16;

/** One step is ~1.5x, so five presses cover the full range without feeling twitchy. */
const STEP = 1.5;

export function RadiusMap({ centre, radiusKm, hits, loading = false }: RadiusMapProps) {
  // The ring is 78% of the box, so a listing exactly at the radius edge still
  // lands inside the frame instead of being clipped off.
  const RING_FRACTION = 0.78;

  // Zoom 1 frames the whole search radius. Higher divides the span, showing less
  // ground in more detail. The radius filter still sets the starting frame — ask
  // for 2 km and you open close in, ask for 50 km and you open pulled back —
  // which is the coupling the map had before, now adjustable afterwards.
  const [zoom, setZoom] = useState(1);
  const frameRef = useRef<HTMLDivElement>(null);

  // A new radius means a new question, so the view resets to frame it.
  useEffect(() => { setZoom(1); }, [radiusKm]);

  const clamp = (z: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, z));
  const zoomBy = useCallback((factor: number) => setZoom((z) => clamp(z * factor)), []);

  // Non-passive so preventDefault actually stops the page scrolling underneath.
  // React's onWheel attaches passively, which is why this is a manual listener.
  useEffect(() => {
    const el = frameRef.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      zoomBy(e.deltaY < 0 ? STEP : 1 / STEP);
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [zoomBy]);

  // Pinch, for the phone layout this app is designed at first.
  useEffect(() => {
    const el = frameRef.current;
    if (!el) return;
    let startGap = 0;
    let startZoom = 1;
    const gap = (t: TouchList) => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);

    const onStart = (e: TouchEvent) => {
      if (e.touches.length !== 2) return;
      startGap = gap(e.touches);
      startZoom = zoom;
    };
    const onMove = (e: TouchEvent) => {
      if (e.touches.length !== 2 || startGap === 0) return;
      e.preventDefault();
      setZoom(clamp(startZoom * (gap(e.touches) / startGap)));
    };
    const onEnd = () => { startGap = 0; };

    el.addEventListener('touchstart', onStart, { passive: true });
    el.addEventListener('touchmove', onMove, { passive: false });
    el.addEventListener('touchend', onEnd, { passive: true });
    return () => {
      el.removeEventListener('touchstart', onStart);
      el.removeEventListener('touchmove', onMove);
      el.removeEventListener('touchend', onEnd);
    };
  }, [zoom]);

  // What the frame actually spans now — the number the legend has to report,
  // because "13 within 5 km" is a lie once you have zoomed into 800 m of it.
  const visibleKm = radiusKm / zoom;

  const project = useMemo(() => {
    const latSpan = radiusKm / 111.32 / zoom;
    const lonSpan = latSpan / Math.max(Math.cos((centre.lat * Math.PI) / 180), 0.01);

    return (lat: number, lon: number) => {
      const dx = (lon - centre.lon) / lonSpan;
      const dy = (lat - centre.lat) / latSpan;
      return {
        x: 50 + dx * 50 * RING_FRACTION,
        y: 50 - dy * 50 * RING_FRACTION,
        // Clip to the frame, not to the radius: zoomed in, most of the radius is
        // off-screen and those pins genuinely cannot be drawn.
        outside: Math.abs(dx) > 1.05 || Math.abs(dy) > 1.05,
      };
    };
  }, [centre.lat, centre.lon, radiusKm, zoom]);

  const plotted = hits
    .map((hit) => ({ hit, at: project(hit.lat, hit.lon) }))
    .filter((p) => !p.at.outside);

  const hidden = hits.length - plotted.length;

  return (
    <div className="map" ref={frameRef} style={{ touchAction: 'pan-y' }}>
      {/* Distance rings: the full radius, and a halfway mark for a sense of scale.
          They scale with the zoom, so the ring stays a truthful "this is 5 km"
          marker rather than a decoration pinned to the frame. */}
      <span
        className="map-ring"
        style={{ width: `${RING_FRACTION * 100 * zoom}%`, height: `${RING_FRACTION * 100 * zoom}%` }}
        aria-hidden
      />
      <span
        className="map-ring"
        style={{ width: `${RING_FRACTION * 50 * zoom}%`, height: `${RING_FRACTION * 50 * zoom}%`, opacity: 0.6 }}
        aria-hidden
      />
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

      {/* Zoom controls. Buttons rather than a slider: they are reachable with a
          thumb, they work from the keyboard, and each press is a known step. */}
      <div
        style={{
          position: 'absolute',
          right: 'var(--s4)',
          top: 'var(--s4)',
          display: 'flex',
          flexDirection: 'column',
          gap: '1px',
          borderRadius: 'var(--r-pill)',
          overflow: 'hidden',
          border: '1px solid var(--rule)',
          background: 'var(--rule)',
        }}
      >
        <button
          type="button"
          onClick={() => zoomBy(STEP)}
          disabled={zoom >= MAX_ZOOM}
          aria-label="Zoom in"
          title="Zoom in"
          style={zoomButton(zoom >= MAX_ZOOM)}
        >
          +
        </button>
        <button
          type="button"
          onClick={() => zoomBy(1 / STEP)}
          disabled={zoom <= MIN_ZOOM}
          aria-label="Zoom out"
          title="Zoom out"
          style={zoomButton(zoom <= MIN_ZOOM)}
        >
          −
        </button>
        <button
          type="button"
          onClick={() => setZoom(1)}
          disabled={zoom === 1}
          aria-label={`Reset zoom to the full ${radiusKm} km`}
          title="Fit the whole radius"
          style={{ ...zoomButton(zoom === 1), fontSize: '0.8rem' }}
        >
          ⤢
        </button>
      </div>

      <span
        style={{
          position: 'absolute',
          right: 'var(--s4)',
          bottom: 'var(--s4)',
          padding: '6px 12px',
          borderRadius: 'var(--r-pill)',
          border: '1px solid var(--rule)',
          background: 'var(--surface)',
          fontSize: '0.8rem',
          color: 'var(--muted)',
        }}
      >
        {loading
          ? 'Looking…'
          : `${plotted.length} in view${hidden > 0 ? ` · ${hidden} outside` : ''} · ${formatSpan(visibleKm)} across`}
      </span>
    </div>
  );
}

/** 800 m reads better than 0.8 km, and 12 km better than 12.0 km. */
function formatSpan(km: number): string {
  if (km < 1) return `${Math.round((km * 1000) / 50) * 50} m`;
  return `${km < 10 ? km.toFixed(1) : Math.round(km)} km`;
}

function zoomButton(disabled: boolean): CSSProperties {
  return {
    width: 34,
    height: 34,
    border: 'none',
    background: 'var(--surface)',
    color: disabled ? 'var(--faint)' : 'var(--ink)',
    fontSize: '1.05rem',
    lineHeight: 1,
    cursor: disabled ? 'default' : 'pointer',
    display: 'grid',
    placeItems: 'center',
    padding: 0,
  };
}
