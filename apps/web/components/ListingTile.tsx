'use client';

import Link from 'next/link';
import { KIND_LABEL, distance, money, rate } from '@/lib/format';
import type { ListingKind, Owner } from '@/lib/types';

/**
 * The signature element of this design: a photo tile with the words laid over
 * it. Where there is no photo we paint a deterministic wash rather than a grey
 * box — the layout is the same either way, so a listing without a picture does
 * not punch a hole in the page.
 */

export interface TileListing {
  id: string;
  kind: ListingKind | string;
  title: string;
  priceMinor: number | null;
  unit: string | null;
  buyPriceMinor: number | null;
  currency: string;
  photoUrl: string | null;
  distanceKm: number;
  owner?: Pick<Owner, 'displayName' | 'idChecked' | 'professional'> | null;
  status?: string;
  homeVisit?: boolean;
}

/** Same title always gets the same wash, so the page is stable across reloads. */
const WASHES = [
  'linear-gradient(150deg, #C4B79E 0%, #9E8F76 46%, #6E6252 100%)',
  'linear-gradient(150deg, #8E9BA8 0%, #5E6C7C 58%, #3D4854 100%)',
  'linear-gradient(150deg, #C0A98C 0%, #8C7455 60%, #5C4A33 100%)',
  'linear-gradient(150deg, #A3A79C 0%, #6F7469 62%, #4A4E45 100%)',
  'linear-gradient(150deg, #99A891 0%, #66735F 62%, #434C3E 100%)',
  'linear-gradient(150deg, #B3A5A0 0%, #7E706B 62%, #544A46 100%)',
];

function washFor(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
  return WASHES[hash % WASHES.length];
}

export function ListingTile({
  listing,
  size = 'wide',
  showStatus = false,
}: {
  listing: TileListing;
  size?: 'lead' | 'tall' | 'wide';
  showStatus?: boolean;
}) {
  const isSale = listing.kind === 'SELL_ITEM' && listing.buyPriceMinor !== null;
  const priceText = isSale
    ? money(listing.buyPriceMinor, listing.currency)
    : rate(listing.priceMinor, listing.unit, listing.currency);

  return (
    <Link href={`/l/${listing.id}`} className={`tile ${size}`}>
      {listing.photoUrl ? (
        <img src={listing.photoUrl} alt="" loading="lazy" />
      ) : (
        <span className="tile-wash" style={{ background: washFor(listing.title) }} aria-hidden />
      )}

      <span className="tile-body">
        <span className="eyebrow">
          {KIND_LABEL[listing.kind as ListingKind] ?? listing.kind}
          {listing.distanceKm > 0 ? ` · ${distance(listing.distanceKm)}` : ''}
        </span>

        {size === 'wide' ? <h3>{listing.title}</h3> : <h2>{listing.title}</h2>}

        <span className="row" style={{ gap: 'var(--s2)', flexWrap: 'wrap' }}>
          <span className="price" style={{ color: '#fff' }}>
            {priceText}
          </span>
          {listing.owner?.displayName && (
            <span style={{ fontSize: '0.8rem', color: 'rgba(255,255,255,0.8)' }}>
              {listing.owner.displayName}
            </span>
          )}
        </span>

        <span className="wrap" style={{ gap: 6 }}>
          {listing.homeVisit && (
            <span className="badge" style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.55)' }}>
              Home visit
            </span>
          )}
          {listing.owner?.idChecked && (
            <span className="badge" style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.55)' }}>
              ID checked
            </span>
          )}
          {listing.owner?.professional && (
            <span className="badge" style={{ background: '#fff', borderColor: '#fff', color: '#131313' }}>
              Professional
            </span>
          )}
          {showStatus && listing.status && listing.status !== 'LIVE' && (
            <span className="badge" style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.55)' }}>
              {listing.status}
            </span>
          )}
        </span>
      </span>
    </Link>
  );
}
