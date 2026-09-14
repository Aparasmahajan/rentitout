'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { api } from '@/lib/api';
import { useRequireAuth } from '@/lib/auth';
import { STATUS_TONE, day, money, when } from '@/lib/format';

export default function RequestsPage() {
  const { loading } = useRequireAuth();
  const [direction, setDirection] = useState<'in' | 'out'>('in');

  const requests = useQuery({
    queryKey: ['requests', direction],
    queryFn: () => api.requests.list(direction),
    refetchInterval: 30_000,
  });

  if (loading) return <div className="skeleton" style={{ marginTop: 24 }} />;

  return (
    <section className="stack" style={{ paddingTop: 'var(--space-6)' }}>
      <h1 style={{ margin: 0 }}>Requests</h1>

      <div className="wrap">
        <button className="chip" data-active={direction === 'in'} onClick={() => setDirection('in')}>
          Incoming
        </button>
        <button className="chip" data-active={direction === 'out'} onClick={() => setDirection('out')}>
          Outgoing
        </button>
      </div>

      {requests.isPending && <div className="skeleton" />}

      {requests.data?.length === 0 && (
        <p className="muted">
          {direction === 'in'
            ? 'Nobody has asked for anything of yours yet.'
            : 'You have not asked for anything yet.'}
        </p>
      )}

      <div className="stack">
        {requests.data?.map((request) => (
          <Link key={request.id} href={`/requests/${request.id}`} className="card stack" style={{ padding: 'var(--space-4)' }}>
            <div className="between">
              <strong>{request.listingTitle}</strong>
              <span className="badge" data-tone={STATUS_TONE[request.status]}>
                {request.status.replace('_', ' ')}
              </span>
            </div>
            <div className="between">
              <span className="muted">
                {day(request.startDate)} · {request.units} {request.unit.toLowerCase()}
                {request.units > 1 ? 's' : ''}
              </span>
              <span className="price">{money(request.breakdown.totalMinor, request.breakdown.currency)}</span>
            </div>
            <div className="between">
              <span className="muted">{when(request.createdAt)}</span>
              {request.unreadCount > 0 && <span className="badge" data-tone="warn">{request.unreadCount} new</span>}
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
