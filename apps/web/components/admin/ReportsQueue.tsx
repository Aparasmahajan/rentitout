'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { ApiError, api } from '@/lib/api';
import { when } from '@/lib/format';
import type { ReportView } from '@/lib/types';

const FILTERS = [
  { key: 'OPEN', label: 'Waiting' },
  { key: 'REVIEWING', label: 'Being looked at' },
  { key: 'ACTIONED', label: 'Actioned' },
  { key: 'DISMISSED', label: 'Dismissed' },
  { key: '', label: 'Everything' },
];

const REASON_LABEL: Record<string, string> = {
  SPAM: 'Spam',
  SCAM: 'Scam',
  OFFENSIVE: 'Offensive',
  HARASSMENT: 'Harassment',
  MISLEADING: 'Misleading',
  WRONG_CATEGORY: 'Wrong category',
  PROHIBITED_ITEM: 'Prohibited',
  OTHER: 'Other',
};

/**
 * The reports queue across listings, comments and ratings.
 *
 * The reported content is rendered inline, because a moderator who has to open
 * a second tab decides slower — and by then the author may have edited it.
 */
export function ReportsQueue() {
  const queryClient = useQueryClient();
  const [state, setState] = useState('OPEN');

  const reports = useQuery({
    queryKey: ['moderation-reports', state],
    queryFn: () => api.moderation.reports(state || undefined),
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['moderation-reports'] });
  const items = reports.data ?? [];

  return (
    <div className="stack" style={{ gap: 'var(--s5)' }}>
      <div className="wrap">
        {FILTERS.map((f) => (
          <button key={f.key} className="chip" data-active={state === f.key} onClick={() => setState(f.key)}>
            {f.label}
          </button>
        ))}
      </div>

      {reports.isPending && <div className="skeleton" />}
      {reports.isError && <div className="error">{(reports.error as ApiError).message}</div>}

      {!reports.isPending && items.length === 0 && (
        <p className="muted">Nothing reported here. Quiet neighbourhood.</p>
      )}

      <div className="stack" style={{ gap: 'var(--s4)' }}>
        {items.map((report) => (
          <ReportCard key={report.id} report={report} onChanged={refresh} />
        ))}
      </div>
    </div>
  );
}

function ReportCard({ report, onChanged }: { report: ReportView; onChanged: () => void }) {
  const [note, setNote] = useState('');
  const [banDays, setBanDays] = useState(7);
  const open = report.state === 'OPEN' || report.state === 'REVIEWING';

  const done = () => {
    setNote('');
    onChanged();
  };

  const claim = useMutation({ mutationFn: () => api.moderation.claim(report.id), onSuccess: done });
  const uphold = useMutation({
    mutationFn: () => api.moderation.action(report.id, note.trim() || undefined),
    onSuccess: done,
  });
  const dismiss = useMutation({
    mutationFn: () => api.moderation.dismiss(report.id, note.trim()),
    onSuccess: done,
  });

  /** Removal closes every open report about the same thing, server-side. */
  const removeContent = useMutation({
    mutationFn: () => {
      const reason = note.trim() || undefined;
      if (report.targetType === 'COMMENT') return api.moderation.removeComment(report.targetId, reason);
      if (report.targetType === 'RATING') return api.moderation.removeRating(report.targetId, reason);
      return api.moderation.unlist(report.targetId, reason);
    },
    onSuccess: done,
  });

  const ban = useMutation({
    mutationFn: () =>
      api.moderation.ban(
        report.targetAuthorId as string,
        banDays,
        note.trim() || `${REASON_LABEL[report.reason] ?? report.reason} — reported content`,
      ),
    onSuccess: done,
  });

  const error = (claim.error ?? uphold.error ?? dismiss.error ?? removeContent.error ?? ban.error) as
    | ApiError
    | null;
  const busy =
    claim.isPending || uphold.isPending || dismiss.isPending || removeContent.isPending || ban.isPending;

  return (
    <div className="card stack" style={{ padding: 'var(--s5)' }}>
      <div className="between" style={{ flexWrap: 'wrap' }}>
        <div className="stack" style={{ gap: 4 }}>
          <strong>
            {report.targetType.toLowerCase()} · {REASON_LABEL[report.reason] ?? report.reason}
          </strong>
          <span className="muted">Reported {when(report.createdAt)}</span>
        </div>
        <span
          className="badge"
          data-tone={
            report.state === 'ACTIONED' ? 'checked' : report.state === 'DISMISSED' ? 'warn' : 'pending'
          }
        >
          {report.state.toLowerCase()}
        </span>
      </div>

      {report.detail && <p style={{ margin: 0 }}>“{report.detail}”</p>}

      <div className="stack" style={{ gap: 4, paddingTop: 'var(--s2)', borderTop: '1px solid var(--rule)' }}>
        <span className="eyebrow">What was reported</span>
        <p style={{ margin: 0, maxWidth: '62ch' }}>{report.targetSummary ?? '(no longer there)'}</p>
        {report.targetType === 'LISTING' && (
          <Link className="linklike muted" style={{ fontSize: '0.85rem' }} href={`/l/${report.targetId}`}>
            Open the listing
          </Link>
        )}
        {report.targetAuthorId && (
          <Link className="linklike muted" style={{ fontSize: '0.85rem' }} href={`/u/${report.targetAuthorId}`}>
            Who wrote it
          </Link>
        )}
      </div>

      {report.note && (
        <p className="muted">
          Decision: {report.note} · {report.decidedAt ? when(report.decidedAt) : ''}
        </p>
      )}

      {error && <div className="error">{error.message}</div>}

      {open && (
        <div className="stack">
          <div className="field">
            <label htmlFor={`note-${report.id}`}>Note — required to dismiss</label>
            <textarea id={`note-${report.id}`} rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
          </div>

          <div className="wrap">
            {report.state === 'OPEN' && (
              <button className="btn quiet" onClick={() => claim.mutate()} disabled={busy}>
                Take it
              </button>
            )}
            <button className="btn danger" onClick={() => removeContent.mutate()} disabled={busy}>
              {report.targetType === 'LISTING' ? 'Unlist it' : 'Remove it'}
            </button>
            <button className="btn ghost" onClick={() => uphold.mutate()} disabled={busy}>
              Uphold, leave it up
            </button>
            <button
              className="btn quiet"
              onClick={() => dismiss.mutate()}
              disabled={busy || note.trim() === ''}
            >
              Dismiss
            </button>
          </div>

          {report.targetAuthorId && report.targetType !== 'LISTING' && (
            <div className="row" style={{ flexWrap: 'wrap' }}>
              <span className="muted">Stop them commenting for</span>
              <input
                type="number"
                min={1}
                max={365}
                value={banDays}
                onChange={(e) => setBanDays(Math.max(1, Number(e.target.value)))}
                style={{ width: 80 }}
              />
              <span className="muted">days</span>
              <button className="btn quiet" onClick={() => ban.mutate()} disabled={busy}>
                Restrict
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
