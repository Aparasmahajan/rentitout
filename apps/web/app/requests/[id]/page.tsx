'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { ApiError, api } from '@/lib/api';
import { useRequireAuth } from '@/lib/auth';
import { STATUS_TONE, day, money, when } from '@/lib/format';
import { useThread } from '@/lib/useThread';

export default function RequestThreadPage() {
  const { id } = useParams<{ id: string }>();
  const { me, loading } = useRequireAuth();
  const queryClient = useQueryClient();
  const { messages, live, send } = useThread(id);
  const [draft, setDraft] = useState('');
  const bottom = useRef<HTMLDivElement>(null);

  const request = useQuery({
    queryKey: ['request', id],
    queryFn: () => api.requests.get(id),
  });

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  const act = useMutation({
    mutationFn: (action: 'accept' | 'decline' | 'start' | 'complete' | 'cancel') => {
      if (action === 'decline') return api.requests.decline(id);
      return api.requests[action](id);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['request', id] }),
  });

  if (loading || request.isPending) return <div className="skeleton" style={{ marginTop: 24 }} />;
  if (request.isError) return <div className="error">{(request.error as Error).message}</div>;

  const r = request.data;
  const b = r.breakdown;

  return (
    <section className="stack" style={{ paddingTop: 'var(--space-6)' }}>
      <div className="between">
        <Link href={`/l/${r.listingId}`}>
          <h1 style={{ margin: 0 }}>{r.listingTitle}</h1>
        </Link>
        <span className="badge" data-tone={STATUS_TONE[r.status]}>
          {r.status.replace('_', ' ')}
        </span>
      </div>

      <p className="muted">
        {day(r.startDate)}
        {r.endDate !== r.startDate ? ` → ${day(r.endDate)}` : ''} · {r.units} {r.unit.toLowerCase()}
        {r.units > 1 ? 's' : ''} · {r.iAmOwner ? 'you are lending' : 'you are borrowing'}
      </p>

      <div className="card stack" style={{ padding: 'var(--space-4)' }}>
        <div className="between">
          <span>
            {money(b.rateMinor, b.currency)} × {b.units}
          </span>
          <span>{money(b.amountMinor, b.currency)}</span>
        </div>
        <div className="between">
          <span className="muted">Refundable deposit</span>
          <span>{money(b.depositMinor, b.currency)}</span>
        </div>
        <div className="between">
          <span className="muted">{b.feeLabel}</span>
          <span>{money(b.feeMinor, b.currency)}</span>
        </div>
        <div className="between" style={{ borderTop: '1px solid var(--color-divider)', paddingTop: 8 }}>
          <strong>Total</strong>
          <strong className="price">{money(b.totalMinor, b.currency)}</strong>
        </div>
        <span className="muted">{b.settlementNote}</span>
      </div>

      {act.isError && <div className="error">{(act.error as ApiError).message}</div>}

      <div className="row">
        {r.iAmOwner && r.status === 'SENT' && (
          <>
            <button className="btn grow" onClick={() => act.mutate('accept')} disabled={act.isPending}>
              Accept
            </button>
            <button className="btn ghost grow" onClick={() => act.mutate('decline')} disabled={act.isPending}>
              Decline
            </button>
          </>
        )}
        {r.status === 'ACCEPTED' && (
          <button className="btn grow" onClick={() => act.mutate('start')} disabled={act.isPending}>
            Handed over
          </button>
        )}
        {(r.status === 'IN_PROGRESS' || r.status === 'ACCEPTED') && (
          <button className="btn ghost grow" onClick={() => act.mutate('complete')} disabled={act.isPending}>
            Mark complete
          </button>
        )}
        {(r.status === 'SENT' || r.status === 'ACCEPTED') && !r.iAmOwner && (
          <button className="btn danger grow" onClick={() => act.mutate('cancel')} disabled={act.isPending}>
            Cancel
          </button>
        )}
      </div>

      <div className="between">
        <h2 style={{ margin: 0 }}>Thread</h2>
        <span className="muted">{live ? 'live' : 'reconnecting…'}</span>
      </div>

      <div className="stack" style={{ minHeight: 160 }}>
        {r.message && (
          <div className="bubble" data-mine={!r.iAmOwner}>
            {r.message}
          </div>
        )}
        {messages.map((message) => (
          <div key={message.id} className="bubble" data-mine={message.senderId === me?.id}>
            {message.body}
            <div className="tiny" style={{ opacity: 0.7 }}>
              {when(message.sentAt)}
            </div>
          </div>
        ))}
        <div ref={bottom} />
      </div>

      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          const body = draft.trim();
          if (!body) return;
          setDraft('');
          void send(body);
        }}
      >
        <input
          className="grow"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Message your neighbour"
          style={{ padding: 11, border: '1px solid var(--color-divider)', borderRadius: 'var(--radius-md)' }}
        />
        <button className="btn" type="submit" disabled={!draft.trim()}>
          Send
        </button>
      </form>

      <details>
        <summary className="muted">History</summary>
        <ul className="muted">
          {r.history.map((t, i) => (
            <li key={i}>
              {t.from ? `${t.from} → ` : ''}
              {t.to} · {when(t.at)}
              {t.reason ? ` · ${t.reason}` : ''}
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}
