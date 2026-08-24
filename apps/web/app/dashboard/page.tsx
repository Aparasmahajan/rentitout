'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { ListingTile } from '@/components/ListingTile';
import { api } from '@/lib/api';
import { useRequireAuth } from '@/lib/auth';
import { money } from '@/lib/format';

export default function DashboardPage() {
  const { loading } = useRequireAuth();
  const queryClient = useQueryClient();

  const listings = useQuery({ queryKey: ['my-listings'], queryFn: () => api.listings.mine() });
  const incoming = useQuery({ queryKey: ['requests', 'in'], queryFn: () => api.requests.list('in') });

  const toggle = useMutation({
    mutationFn: ({ id, live }: { id: string; live: boolean }) =>
      live ? api.listings.pause(id) : api.listings.resume(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-listings'] }),
  });

  if (loading || listings.isPending) return <div className="skeleton" style={{ marginTop: 24 }} />;

  const pending = incoming.data?.filter((r) => r.status === 'SENT') ?? [];
  const earned = (incoming.data ?? [])
    .filter((r) => r.status === 'COMPLETED')
    .reduce((sum, r) => sum + r.breakdown.amountMinor, 0);

  return (
    <section className="stack" style={{ paddingTop: 'var(--space-6)' }}>
      <h1 style={{ margin: 0 }}>Dashboard</h1>

      <div className="grid">
        <div className="card stack" style={{ padding: 'var(--space-4)' }}>
          <span className="tiny">Live listings</span>
          <span className="price">{listings.data?.filter((l) => l.status === 'LIVE').length ?? 0}</span>
        </div>
        <div className="card stack" style={{ padding: 'var(--space-4)' }}>
          <span className="tiny">Waiting on you</span>
          <span className="price">{pending.length}</span>
        </div>
        <div className="card stack" style={{ padding: 'var(--space-4)' }}>
          <span className="tiny">Earned (completed)</span>
          <span className="price">{money(earned)}</span>
        </div>
      </div>

      {pending.length > 0 && (
        <div className="stack">
          <h2>Waiting on you</h2>
          {pending.map((request) => (
            <Link key={request.id} href={`/requests/${request.id}`} className="card between" style={{ padding: 'var(--space-4)' }}>
              <span>{request.listingTitle}</span>
              <span className="muted">{request.startDate}</span>
            </Link>
          ))}
        </div>
      )}

      <div className="between">
        <h2 style={{ margin: 0 }}>My listings</h2>
        <Link href="/new" className="chip static">
          + New
        </Link>
      </div>

      {listings.data?.length === 0 && <p className="muted">Nothing listed yet.</p>}

      <div className="grid">
        {listings.data?.map((listing) => (
          <div key={listing.id} className="stack">
            <ListingTile listing={listing} showStatus />
            <button
              className="btn ghost"
              onClick={() => toggle.mutate({ id: listing.id, live: listing.status === 'LIVE' })}
              disabled={toggle.isPending}
            >
              {listing.status === 'LIVE' ? 'Pause' : 'Resume'}
            </button>
          </div>
        ))}
      </div>
    </section>
  );
}
