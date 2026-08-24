'use client';

import { useMutation, useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { ApiError, api } from '@/lib/api';
import { money } from '@/lib/format';
import type { Listing } from '@/lib/types';

/**
 * The booking sheet. Every number on it comes from the server: the client sends
 * a day and a count, and renders whatever breakdown comes back. That is what
 * lets Phase 05 switch the fee on without touching this component.
 */
export function BookingSheet({ listing, onClose }: { listing: Listing; onClose: () => void }) {
  const router = useRouter();
  const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);

  const [startDate, setStartDate] = useState(tomorrow);
  const [units, setUnits] = useState(1);
  const [message, setMessage] = useState('');

  const availability = useQuery({
    queryKey: ['availability', listing.id],
    queryFn: () => api.listings.availability(listing.id),
  });

  const quote = useQuery({
    queryKey: ['quote', listing.id, startDate, units],
    queryFn: () => api.requests.quote(listing.id, startDate, units),
    retry: false,
  });

  const send = useMutation({
    mutationFn: () => api.requests.create({ listingId: listing.id, startDate, units, message }),
    onSuccess: (request) => router.push(`/requests/${request.id}`),
  });

  const blocked = availability.data?.blocked ?? [];
  const clashes = blocked.some((range) => startDate >= range.from && startDate <= range.to);

  return (
    <>
      <div className="scrim" onClick={onClose} role="presentation" />
      <div className="sheet stack" role="dialog" aria-label={`Request ${listing.title}`}>
        <div className="between">
          <h2 style={{ margin: 0 }}>{listing.title}</h2>
          <button className="chip" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>

        <div className="field">
          <label htmlFor="start">Day</label>
          <input id="start" type="date" min={tomorrow} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </div>

        {clashes && <div className="error">Those days are already taken. Pick another.</div>}

        {blocked.length > 0 && (
          <p className="muted">
            Taken: {blocked.map((r) => (r.from === r.to ? r.from : `${r.from} → ${r.to}`)).join(', ')}
          </p>
        )}

        <div className="field">
          <label htmlFor="units">How many {listing.unit?.toLowerCase() ?? 'unit'}s</label>
          <div className="row">
            <button className="chip" onClick={() => setUnits((u) => Math.max(1, u - 1))} aria-label="One fewer">
              −
            </button>
            <input
              id="units"
              type="number"
              min={1}
              max={365}
              value={units}
              onChange={(e) => setUnits(Math.max(1, Number(e.target.value)))}
              style={{ width: 80, textAlign: 'center' }}
            />
            <button className="chip" onClick={() => setUnits((u) => u + 1)} aria-label="One more">
              +
            </button>
          </div>
        </div>

        <div className="field">
          <label htmlFor="message">Message</label>
          <textarea
            id="message"
            rows={3}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="Hi — I only need it for the Saturday morning."
          />
        </div>

        {quote.data && (
          <div className="card stack" style={{ padding: 'var(--space-4)' }}>
            <div className="between">
              <span>
                {money(quote.data.rateMinor, quote.data.currency)} × {quote.data.units}
              </span>
              <span>{money(quote.data.amountMinor, quote.data.currency)}</span>
            </div>
            <div className="between">
              <span className="muted">Refundable deposit</span>
              <span>{money(quote.data.depositMinor, quote.data.currency)}</span>
            </div>
            <div className="between">
              <span className="muted">{quote.data.feeLabel}</span>
              <span>{money(quote.data.feeMinor, quote.data.currency)}</span>
            </div>
            <div className="between" style={{ borderTop: '1px solid var(--color-divider)', paddingTop: 8 }}>
              <strong>Total</strong>
              <strong className="price">{money(quote.data.totalMinor, quote.data.currency)}</strong>
            </div>
            <span className="muted">{quote.data.settlementNote}</span>
          </div>
        )}

        {quote.isError && <div className="error">{(quote.error as ApiError).message}</div>}
        {send.isError && <div className="error">{(send.error as ApiError).message}</div>}

        <button className="btn block" onClick={() => send.mutate()} disabled={send.isPending || clashes || quote.isError}>
          {send.isPending ? 'Sending…' : 'Send request'}
        </button>
        <p className="muted">The owner has 48 hours to answer.</p>
      </div>
    </>
  );
}
