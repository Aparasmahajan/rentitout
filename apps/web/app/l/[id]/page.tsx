'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { BookingSheet } from '@/components/BookingSheet';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { KIND_LABEL, distance, money, rate } from '@/lib/format';

const WEEKDAYS = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

const TRADE_LABEL: Record<string, string> = {
  ELECTRICIAN: 'Electrician',
  PLUMBER: 'Plumber',
  AC_SERVICE: 'AC service',
  APPLIANCE_REPAIR: 'Appliance repair',
  CARPENTER: 'Carpenter',
  PAINTER: 'Painter',
  PEST_CONTROL: 'Pest control',
  CLEANING: 'Cleaning',
  HOUSE_HELP: 'House help',
  COOK: 'Cook',
  DRIVER: 'Driver',
  MOVER: 'Mover',
  GARDENER: 'Gardener',
  BEAUTICIAN: 'Beautician',
  TUTOR: 'Tutor',
  IT_SUPPORT: 'IT support',
  OTHER: 'Service',
};

/**
 * A listing reads without an account. The wall goes up at the moment of asking
 * for something — not before.
 */
export default function ListingPage() {
  const { id } = useParams<{ id: string }>();
  const { signedIn, requireSignIn } = useAuth();
  const [booking, setBooking] = useState(false);

  const listing = useQuery({
    queryKey: ['listing', id],
    queryFn: () => api.listings.detail(id),
  });

  if (listing.isPending) return <div className="skeleton" style={{ marginTop: 'var(--s6)' }} />;
  if (listing.isError) {
    return (
      <div className="error" style={{ marginTop: 'var(--s6)' }}>
        {(listing.error as Error).message}
      </div>
    );
  }

  const l = listing.data;
  const isSale = l.kind === 'SELL_ITEM' && l.buyPriceMinor !== null;
  const bookable = !l.mine && l.status === 'LIVE' && l.priceMinor !== null && l.kind !== 'OPEN_NEED';

  function askToBook() {
    if (!signedIn) {
      // The only place a guest is stopped, and they come straight back here.
      requireSignIn(`/l/${id}`);
      return;
    }
    setBooking(true);
  }

  return (
    <section className="stack" style={{ paddingTop: 'var(--s6)', gap: 'var(--s5)' }}>
      <div className="tile" style={{ aspectRatio: '16 / 9' }}>
        {l.photos.length > 0 ? (
          <img src={l.photos[0].url} alt={l.title} />
        ) : (
          <span
            className="tile-wash"
            style={{ background: 'linear-gradient(150deg, #C4B79E 0%, #9E8F76 46%, #6E6252 100%)' }}
            aria-hidden
          />
        )}
        <span className="tile-body">
          <span className="eyebrow">
            {KIND_LABEL[l.kind]}
            {l.distanceKm !== null ? ` · ${distance(l.distanceKm)}` : ''}
          </span>
          <h1 style={{ color: '#fff' }}>{l.title}</h1>
        </span>
      </div>

      <div className="between" style={{ flexWrap: 'wrap', gap: 'var(--s4)' }}>
        <span className="price" style={{ fontSize: '2rem' }}>
          {isSale ? money(l.buyPriceMinor, l.currency) : rate(l.priceMinor, l.unit, l.currency)}
        </span>
        <div className="wrap">
          {l.homeVisit && <span className="badge" data-tone="pro">Comes to you</span>}
          {l.owner.idChecked && <span className="badge" data-tone="checked">ID checked</span>}
          {l.owner.professional && l.owner.trade && (
            <span className="badge">{TRADE_LABEL[l.owner.trade] ?? l.owner.trade}</span>
          )}
        </div>
      </div>

      {l.depositMinor > 0 && (
        <p className="muted">Refundable deposit {money(l.depositMinor, l.currency)}</p>
      )}

      {l.description && <p style={{ maxWidth: '62ch' }}>{l.description}</p>}

      {l.homeVisit && (
        <div className="notice">
          This service includes a visit to your home. We have seen this person&apos;s ID and their
          trade details — that is a check on who they say they are, not a recommendation of their
          work. Read their reviews and agree the job before they arrive.
        </div>
      )}

      {l.tags.length > 0 && (
        <div className="wrap">
          {l.tags.map((tag) => (
            <span key={tag} className="chip static">
              {tag}
            </span>
          ))}
        </div>
      )}

      {l.availability.length > 0 && (
        <div className="stack">
          <span className="eyebrow">Usually available</span>
          <div className="wrap">
            {l.availability.map((rule) => (
              <span key={`${rule.weekday}-${rule.from}`} className="chip static">
                {WEEKDAYS[rule.weekday]} {rule.from.slice(0, 5)}–{rule.to.slice(0, 5)}
              </span>
            ))}
          </div>
        </div>
      )}

      <Link
        href={l.owner.professional ? `/pro/${l.owner.id}` : `/u/${l.owner.id}`}
        className="card between"
        style={{ padding: 'var(--s4)' }}
      >
        <span className="stack" style={{ gap: 4 }}>
          <strong>{l.owner.displayName ?? 'A neighbour'}</strong>
          <span className="muted">{l.owner.areaLabel}</span>
        </span>
        <span className="muted">View →</span>
      </Link>

      {l.mine ? (
        <Link href="/dashboard" className="btn ghost block">
          Manage this listing
        </Link>
      ) : bookable ? (
        <>
          <button className="btn block" onClick={askToBook}>
            {signedIn ? 'Request this' : 'Sign in to request'}
          </button>
          {!signedIn && (
            <p className="muted" style={{ textAlign: 'center' }}>
              Browsing is free. We only ask who you are when a neighbour needs to know.
            </p>
          )}
        </>
      ) : (
        <p className="muted">
          {l.kind === 'OPEN_NEED'
            ? 'This is a request from a neighbour — message them if you can help.'
            : 'Not available right now.'}
        </p>
      )}

      {booking && <BookingSheet listing={l} onClose={() => setBooking(false)} />}
    </section>
  );
}
