'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { when } from '@/lib/format';
import type { RatingView } from '@/lib/types';
import { ReportControl } from './ReportControl';

/** Five glyphs, filled to the score. Rounds to the nearest whole star. */
export function Stars({ value, size }: { value: number; size?: 'lg' }) {
  const filled = Math.round(value);
  return (
    <span className="stars" data-size={size} aria-label={`${value.toFixed(1)} out of 5`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= filled ? undefined : 'off'} aria-hidden>
          ★
        </span>
      ))}
    </span>
  );
}

/**
 * Ratings on a listing.
 *
 * Anyone may rate; a rating from someone who actually completed a booking
 * carries a badge and sorts first. Requiring a booking would leave every new
 * listing blank, and requiring nothing would make the number worthless — this
 * is the middle path, and the badge is what carries the weight.
 */
export function Ratings({ listingId, mine }: { listingId: string; mine: boolean }) {
  const { signedIn, requireSignIn } = useAuth();
  const queryClient = useQueryClient();

  const [open, setOpen] = useState(false);
  const [stars, setStars] = useState(5);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');

  const summary = useQuery({
    queryKey: ['ratings', listingId],
    queryFn: () => api.ratings.summary(listingId),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['ratings', listingId] });
    // The average is denormalised onto the listing, so the header is stale too.
    void queryClient.invalidateQueries({ queryKey: ['listing', listingId] });
  };

  const send = useMutation({
    mutationFn: () => api.ratings.rate(listingId, { stars, title: title.trim(), body: body.trim() }),
    onSuccess: () => {
      setOpen(false);
      invalidate();
    },
  });

  const remove = useMutation({
    mutationFn: (id: string) => api.ratings.remove(id),
    onSuccess: invalidate,
  });

  const data = summary.data;
  const ratings = data?.ratings ?? [];
  const highest = Math.max(1, ...Object.values(data?.histogram ?? {}));

  function startRating() {
    if (!signedIn) {
      requireSignIn();
      return;
    }
    const existing = data?.mine;
    setStars(existing?.stars ?? 5);
    setTitle(existing?.title ?? '');
    setBody(existing?.body ?? '');
    setOpen(true);
  }

  return (
    <section className="stack" style={{ gap: 'var(--s4)' }}>
      <span className="eyebrow">Ratings</span>

      {summary.isPending && <div className="skeleton" style={{ height: 90 }} />}
      {summary.isError && <div className="error">{(summary.error as ApiError).message}</div>}

      {data && (
        <>
          <div className="row" style={{ gap: 'var(--s5)', flexWrap: 'wrap', alignItems: 'flex-start' }}>
            <div className="stack" style={{ gap: 4 }}>
              <span className="price">{data.average === null ? '—' : data.average.toFixed(1)}</span>
              <Stars value={data.average ?? 0} size="lg" />
              <span className="muted">
                {data.count === 0 ? 'Not rated yet' : `${data.count} rating${data.count === 1 ? '' : 's'}`}
              </span>
            </div>

            {data.count > 0 && (
              <div className="histogram grow" style={{ minWidth: 200 }}>
                {[5, 4, 3, 2, 1].map((star) => {
                  const count = data.histogram[String(star)] ?? 0;
                  return (
                    <HistogramRow key={star} star={star} count={count} width={(count / highest) * 100} />
                  );
                })}
              </div>
            )}
          </div>

          {/* Rating your own listing is refused server-side; not offering the
              button is the polite half of the same rule. */}
          {!mine && (
            <button className="btn ghost" style={{ alignSelf: 'flex-start' }} onClick={startRating}>
              {!signedIn ? 'Sign in to rate' : data.mine ? 'Change your rating' : 'Rate this'}
            </button>
          )}

          <div className="stack" style={{ gap: 'var(--s4)' }}>
            {ratings.map((rating) => (
              <RatingRow
                key={rating.id}
                rating={rating}
                onRemove={() => remove.mutate(rating.id)}
                removing={remove.isPending}
              />
            ))}
          </div>
          {remove.isError && <div className="error">{(remove.error as ApiError).message}</div>}
        </>
      )}

      {open && (
        <>
          <div className="scrim-bg" onClick={() => setOpen(false)} role="presentation" />
          <div className="sheet stack" role="dialog" aria-label="Rate this listing">
            <div className="between">
              <h2 style={{ margin: 0 }}>How was it?</h2>
              <button className="chip" onClick={() => setOpen(false)} aria-label="Close">
                ×
              </button>
            </div>

            <div className="field">
              <label>Stars</label>
              <div className="starpick">
                {[1, 2, 3, 4, 5].map((n) => (
                  <button
                    key={n}
                    type="button"
                    data-on={n <= stars}
                    aria-label={`${n} star${n === 1 ? '' : 's'}`}
                    onClick={() => setStars(n)}
                  >
                    ★
                  </button>
                ))}
              </div>
            </div>

            <div className="field">
              <label htmlFor="rating-title">Headline (optional)</label>
              <input
                id="rating-title"
                value={title}
                maxLength={120}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Sturdy, and he threw in the harness"
              />
            </div>

            <div className="field">
              <label htmlFor="rating-body">What should a neighbour know? (optional)</label>
              <textarea id="rating-body" rows={4} value={body} onChange={(e) => setBody(e.target.value)} />
            </div>

            {send.isError && <div className="error">{(send.error as ApiError).message}</div>}

            <button className="btn block" onClick={() => send.mutate()} disabled={send.isPending}>
              {send.isPending ? 'Saving…' : 'Post rating'}
            </button>
            <p className="muted">
              If you booked this through Radius, your rating is marked as a verified booking.
            </p>
          </div>
        </>
      )}
    </section>
  );
}

function HistogramRow({ star, count, width }: { star: number; count: number; width: number }) {
  return (
    <>
      <span className="muted" style={{ fontSize: '0.8rem' }}>
        {star}★
      </span>
      <span className="bar">
        <span style={{ width: `${width}%` }} />
      </span>
      <span className="count">{count}</span>
    </>
  );
}

function RatingRow({
  rating,
  onRemove,
  removing,
}: {
  rating: RatingView;
  onRemove: () => void;
  removing: boolean;
}) {
  return (
    <article className="stack" style={{ gap: 6, paddingTop: 'var(--s3)', borderTop: '1px solid var(--rule)' }}>
      <div className="between" style={{ flexWrap: 'wrap', gap: 'var(--s2)' }}>
        <div className="row" style={{ gap: 'var(--s3)' }}>
          <Stars value={rating.stars} />
          <strong>{rating.authorName ?? 'A neighbour'}</strong>
          {rating.verifiedBooking && (
            <span className="badge" data-tone="checked">
              Verified booking
            </span>
          )}
        </div>
        <span className="muted" style={{ fontSize: '0.8rem' }}>
          {when(rating.createdAt)}
          {rating.editedAt ? ' · edited' : ''}
        </span>
      </div>

      {rating.title && <strong>{rating.title}</strong>}
      {rating.body && <p style={{ margin: 0, maxWidth: '62ch' }}>{rating.body}</p>}

      <div className="wrap" style={{ gap: 'var(--s4)', alignItems: 'center' }}>
        {rating.canDelete && (
          <button className="linklike muted" style={{ fontSize: '0.85rem' }} onClick={onRemove} disabled={removing}>
            Remove
          </button>
        )}
        {!rating.canEdit && <ReportControl targetType="RATING" targetId={rating.id} />}
      </div>
    </article>
  );
}
