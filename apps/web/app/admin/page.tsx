'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError, api } from '@/lib/api';
import { useRequireAuth } from '@/lib/auth';
import { when } from '@/lib/format';
import type { ReviewItem } from '@/lib/types';

const FILTERS = [
  { key: '', label: 'Waiting' },
  { key: 'APPROVED', label: 'Approved' },
  { key: 'REJECTED', label: 'Rejected' },
];

/** The review queue. Only reachable with an ADMIN token; the API checks again. */
export default function AdminPage() {
  const { loading, isAdmin } = useRequireAuth();
  const queryClient = useQueryClient();

  const [filter, setFilter] = useState('');
  const [openId, setOpenId] = useState<string | null>(null);
  const [note, setNote] = useState('');

  const queue = useQuery({
    queryKey: ['admin-queue', filter],
    queryFn: () => api.admin.queue(filter || undefined),
    enabled: isAdmin,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin-queue'] });
    setOpenId(null);
    setNote('');
  };

  const approve = useMutation({
    mutationFn: (id: string) => api.admin.approve(id, note.trim() || undefined),
    onSuccess: refresh,
  });
  const reject = useMutation({
    mutationFn: (id: string) => api.admin.reject(id, note.trim()),
    onSuccess: refresh,
  });
  const claim = useMutation({
    mutationFn: (id: string) => api.admin.claim(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-queue'] }),
  });

  if (loading) return <div className="skeleton" style={{ marginTop: 'var(--s6)' }} />;

  if (!isAdmin) {
    return (
      <section className="stack" style={{ paddingTop: 'var(--s6)' }}>
        <h1>Not for you</h1>
        <p className="muted">This is the review queue. Your account is not an administrator.</p>
      </section>
    );
  }

  const items = queue.data ?? [];

  return (
    <section className="stack" style={{ paddingTop: 'var(--s6)', gap: 'var(--s5)' }}>
      <div className="stack" style={{ gap: 6 }}>
        <span className="eyebrow">Administration</span>
        <h1>
          Checks <em>waiting on you</em>
        </h1>
      </div>

      <div className="wrap">
        {FILTERS.map((f) => (
          <button key={f.key} className="chip" data-active={filter === f.key} onClick={() => setFilter(f.key)}>
            {f.label}
          </button>
        ))}
      </div>

      {queue.isPending && <div className="skeleton" />}
      {queue.isError && <div className="error">{(queue.error as ApiError).message}</div>}

      {!queue.isPending && items.length === 0 && (
        <p className="muted">Nothing here. Everything is dealt with.</p>
      )}

      <div className="stack" style={{ gap: 'var(--s4)' }}>
        {items.map((item) => (
          <ReviewCard
            key={item.id}
            item={item}
            open={openId === item.id}
            note={note}
            onNote={setNote}
            onOpen={() => {
              setOpenId(openId === item.id ? null : item.id);
              setNote('');
              if (item.state === 'SUBMITTED') claim.mutate(item.id);
            }}
            onApprove={() => approve.mutate(item.id)}
            onReject={() => reject.mutate(item.id)}
            busy={approve.isPending || reject.isPending}
            error={(approve.error ?? reject.error) as ApiError | null}
          />
        ))}
      </div>
    </section>
  );
}

function ReviewCard({
  item,
  open,
  note,
  onNote,
  onOpen,
  onApprove,
  onReject,
  busy,
  error,
}: {
  item: ReviewItem;
  open: boolean;
  note: string;
  onNote: (v: string) => void;
  onOpen: () => void;
  onApprove: () => void;
  onReject: () => void;
  busy: boolean;
  error: ApiError | null;
}) {
  const decided = item.state === 'APPROVED' || item.state === 'REJECTED';

  return (
    <div className="card stack" style={{ padding: 'var(--s5)' }}>
      <div className="between" style={{ flexWrap: 'wrap' }}>
        <div className="stack" style={{ gap: 4 }}>
          <strong>{item.displayName ?? 'Unnamed member'}</strong>
          <span className="muted">
            {item.phone} · {item.kind === 'IDENTITY' ? 'ID check' : 'Trade check'} · sent{' '}
            {when(item.submittedAt)}
          </span>
        </div>
        <span className="badge" data-tone={item.state === 'APPROVED' ? 'checked' : item.state === 'REJECTED' ? 'warn' : 'pending'}>
          {item.state.replace('_', ' ').toLowerCase()}
        </span>
      </div>

      {item.memberNote && <p style={{ margin: 0 }}>“{item.memberNote}”</p>}

      {item.professional && (
        <div className="stack" style={{ gap: 4, paddingTop: 'var(--s2)', borderTop: '1px solid var(--rule)' }}>
          <span className="eyebrow">Trade details</span>
          <span>
            <strong>{item.professional.trade}</strong>
            {item.professional.businessName ? ` · ${item.professional.businessName}` : ''}
          </span>
          {item.professional.shopAddress && <span className="muted">{item.professional.shopAddress}</span>}
          <span className="muted">
            {item.professional.yearsExperience ?? '—'} years · licence{' '}
            {item.professional.licenceRef ?? 'not given'} · insurance{' '}
            {item.professional.insuranceRef ?? 'not given'}
          </span>
          <span className="muted">Travels up to {item.professional.serviceRadiusKm} km</span>
        </div>
      )}

      {item.evidenceKeys.length > 0 && (
        <div className="stack" style={{ gap: 4 }}>
          <span className="eyebrow">Evidence ({item.evidenceKeys.length})</span>
          <div className="wrap">
            {item.evidenceKeys.map((key) => (
              <a
                key={key}
                className="chip"
                href={`${api.baseUrl.replace(':8080', ':9010')}/radius-photos/${key}`}
                target="_blank"
                rel="noreferrer"
              >
                Open
              </a>
            ))}
          </div>
          {item.purgeAfter && (
            <span className="muted">Images to be destroyed after {item.purgeAfter}.</span>
          )}
        </div>
      )}

      {!decided && (
        <>
          <button className="btn ghost" style={{ alignSelf: 'flex-start' }} onClick={onOpen}>
            {open ? 'Close' : 'Decide'}
          </button>

          {open && (
            <div className="stack">
              <div className="field">
                <label htmlFor={`note-${item.id}`}>
                  Note — required to refuse, so they can fix it
                </label>
                <textarea id={`note-${item.id}`} value={note} onChange={(e) => onNote(e.target.value)} />
              </div>

              {error && <div className="error">{error.message}</div>}

              <div className="row">
                <button className="btn" onClick={onApprove} disabled={busy}>
                  Approve
                </button>
                <button className="btn danger" onClick={onReject} disabled={busy || note.trim() === ''}>
                  Refuse
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
