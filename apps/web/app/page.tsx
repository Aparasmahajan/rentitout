'use client';

import { useInfiniteQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { ListingTile } from '@/components/ListingTile';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { KINDS, KIND_LABEL } from '@/lib/format';
import { useViewerPoint } from '@/lib/useViewerPoint';

const STEPS = [1, 2, 5, 10, 25, 50];

/**
 * The nearby feed. Open to everyone — a first-time visitor sees real listings
 * before being asked for anything.
 */
export default function FeedPage() {
  const { signedIn } = useAuth();
  const { point, locate, locating } = useViewerPoint();

  const [kind, setKind] = useState<string | null>(null);
  const [radiusKm, setRadiusKm] = useState(5);

  const feed = useInfiniteQuery({
    queryKey: ['feed', kind, radiusKm, point.lat, point.lon],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.listings.feed({
        kind: kind ?? undefined,
        cursor: pageParam,
        radiusKm,
        lat: point.lat,
        lon: point.lon,
      }),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

  const items = feed.data?.pages.flatMap((page) => page.items) ?? [];
  const [lead, ...rest] = items;
  const empty = !feed.isPending && !feed.isError && items.length === 0;

  return (
    <section className="stack" style={{ paddingTop: 'var(--s6)', gap: 'var(--s5)' }}>
      <div className="between" style={{ alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <div className="stack" style={{ gap: 6 }}>
          <span className="eyebrow">{point.label} · {items.length} nearby</span>
          <h1>
            Within <em>{radiusKm} kilometres</em>
          </h1>
        </div>
        <button className="btn quiet" onClick={locate} disabled={locating} style={{ minHeight: 38 }}>
          {locating ? 'Locating…' : point.assumed ? 'Use my location' : 'Update location'}
        </button>
      </div>

      <div className="wrap" role="group" aria-label="Filter by kind">
        <button className="chip" data-active={kind === null} onClick={() => setKind(null)}>
          Everything
        </button>
        {KINDS.map((k) => (
          <button key={k} className="chip" data-active={kind === k} onClick={() => setKind(k)}>
            {KIND_LABEL[k]}
          </button>
        ))}
      </div>

      <div className="wrap" role="group" aria-label="Distance">
        {STEPS.map((km) => (
          <button key={km} className="chip" data-active={radiusKm === km} onClick={() => setRadiusKm(km)}>
            {km} km
          </button>
        ))}
      </div>

      {feed.isPending && <div className="skeleton" />}

      {feed.isError && <div className="error">{(feed.error as Error).message}</div>}

      {empty && (
        <div className="notice">
          <strong>Nothing is listed within {radiusKm} km yet.</strong>
          <div className="muted" style={{ marginTop: 6 }}>
            Widen the distance, or be the first neighbour to put something up.
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

      {items.length > 0 && (
        <div className="mosaic">
          {lead && (
            <div className="lead">
              <ListingTile listing={lead} size="lead" />
            </div>
          )}
          {rest.map((listing, index) => (
            <ListingTile
              key={listing.id}
              listing={listing}
              size={index % 3 === 0 ? 'tall' : 'wide'}
            />
          ))}
        </div>
      )}

      {feed.hasNextPage && (
        <button
          className="btn ghost block"
          onClick={() => feed.fetchNextPage()}
          disabled={feed.isFetchingNextPage}
        >
          {feed.isFetchingNextPage ? 'Loading…' : 'Show more'}
        </button>
      )}

      {!signedIn && items.length > 0 && (
        <p className="muted" style={{ textAlign: 'center' }}>
          Have a look around. You only need an account when you ask someone for something.
        </p>
      )}
    </section>
  );
}
