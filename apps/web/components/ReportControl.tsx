'use client';

import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { ReportTargetType } from '@/lib/types';

/**
 * The reasons the server accepts, spelled for a human. The list is fixed on the
 * server — {@code GET /api/reports/reasons} — and duplicated here only for the
 * wording; an unknown reason is refused there, not hidden here.
 */
const REASON_LABEL: Record<string, string> = {
  SPAM: 'Spam or an advert',
  SCAM: 'Looks like a scam',
  OFFENSIVE: 'Offensive content',
  HARASSMENT: 'Harassment',
  MISLEADING: 'Misleading or untrue',
  WRONG_CATEGORY: 'In the wrong category',
  PROHIBITED_ITEM: 'Something that should not be listed',
  OTHER: 'Something else',
};

const REASONS = Object.keys(REASON_LABEL);

/**
 * Report a listing, a comment or a rating. Deliberately quiet — a small text
 * control rather than a button, because the loud version gets pressed for
 * disagreement rather than abuse.
 */
export function ReportControl({
  targetType,
  targetId,
  label = 'Report',
}: {
  targetType: ReportTargetType;
  targetId: string;
  label?: string;
}) {
  const { signedIn, requireSignIn } = useAuth();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState(REASONS[0]);
  const [detail, setDetail] = useState('');
  const [done, setDone] = useState(false);

  const send = useMutation({
    mutationFn: () => api.reports.create({ targetType, targetId, reason, detail: detail.trim() }),
    onSuccess: () => {
      setDone(true);
      setOpen(false);
      setDetail('');
    },
  });

  if (done) {
    return (
      <span className="muted" style={{ fontSize: '0.85rem' }}>
        Reported — thank you.
      </span>
    );
  }

  return (
    <>
      <button
        className="linklike muted"
        style={{ fontSize: '0.85rem' }}
        onClick={() => (signedIn ? setOpen(true) : requireSignIn())}
      >
        {label}
      </button>

      {open && (
        <>
          <div className="scrim-bg" onClick={() => setOpen(false)} role="presentation" />
          <div className="sheet stack" role="dialog" aria-label="Report this">
            <div className="between">
              <h2 style={{ margin: 0 }}>What is wrong with it?</h2>
              <button className="chip" onClick={() => setOpen(false)} aria-label="Close">
                ×
              </button>
            </div>

            <div className="field">
              <label htmlFor="report-reason">Reason</label>
              <select id="report-reason" value={reason} onChange={(e) => setReason(e.target.value)}>
                {REASONS.map((r) => (
                  <option key={r} value={r}>
                    {REASON_LABEL[r]}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="report-detail">Anything else? (optional)</label>
              <textarea
                id="report-detail"
                rows={3}
                value={detail}
                onChange={(e) => setDetail(e.target.value)}
                placeholder="What a moderator should look at."
              />
            </div>

            {send.isError && <div className="error">{(send.error as ApiError).message}</div>}

            <button className="btn block" onClick={() => send.mutate()} disabled={send.isPending}>
              {send.isPending ? 'Sending…' : 'Send report'}
            </button>
            <p className="muted">
              A moderator reads every report. We do not tell the other person who sent it.
            </p>
          </div>
        </>
      )}
    </>
  );
}
