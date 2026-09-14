'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { ApiError, api, uploadPhoto } from '@/lib/api';
import { useRequireAuth } from '@/lib/auth';
import { when } from '@/lib/format';
import type { VerificationState } from '@/lib/types';

const STATE_COPY: Record<VerificationState, { label: string; tone: string }> = {
  NONE: { label: 'Not started', tone: 'pending' },
  SUBMITTED: { label: 'With us', tone: 'pending' },
  IN_REVIEW: { label: 'Being looked at', tone: 'pending' },
  APPROVED: { label: 'Done', tone: 'checked' },
  REJECTED: { label: 'Not accepted', tone: 'warn' },
  WITHDRAWN: { label: 'Withdrawn', tone: 'pending' },
};

/** Anyone can open this and see exactly where their checks stand. */
export default function VerificationPage() {
  const { loading } = useRequireAuth();
  const queryClient = useQueryClient();

  const [note, setNote] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);

  const status = useQuery({ queryKey: ['verification'], queryFn: () => api.verification.mine() });

  const submit = useMutation({
    mutationFn: async (kind: 'IDENTITY' | 'PROFESSIONAL') => {
      setUploading(true);
      try {
        // The images go straight to the object store; only the keys reach the API.
        const evidenceKeys = await Promise.all(files.map((file) => uploadPhoto(file)));
        return await api.verification.submit({ kind, evidenceKeys, note: note.trim() || undefined });
      } finally {
        setUploading(false);
      }
    },
    onSuccess: () => {
      setNote('');
      setFiles([]);
      void queryClient.invalidateQueries({ queryKey: ['verification'] });
    },
  });

  const withdraw = useMutation({
    mutationFn: (id: string) => api.verification.withdraw(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['verification'] }),
  });

  if (loading || status.isPending) return <div className="skeleton" style={{ marginTop: 'var(--s6)' }} />;
  if (status.isError) {
    return <div className="error" style={{ marginTop: 'var(--s6)' }}>{(status.error as Error).message}</div>;
  }

  const v = status.data;
  const identityOpen = v.identityState === 'SUBMITTED' || v.identityState === 'IN_REVIEW';
  const professionalOpen = v.professionalState === 'SUBMITTED' || v.professionalState === 'IN_REVIEW';
  const busy = submit.isPending || uploading;

  return (
    <section className="stack" style={{ paddingTop: 'var(--s6)', gap: 'var(--s5)' }}>
      <div className="stack" style={{ gap: 6 }}>
        <span className="eyebrow">Your account</span>
        <h1>
          Getting <em>checked</em>
        </h1>
      </div>

      <div className="card stack" style={{ padding: 'var(--s5)' }}>
        <div className="between">
          <div className="stack" style={{ gap: 4 }}>
            <strong>Step one · ID check</strong>
            <span className="muted">
              Needed before you can offer anything that involves coming to someone&apos;s home.
            </span>
          </div>
          <span className="badge" data-tone={STATE_COPY[v.identityState].tone}>
            {STATE_COPY[v.identityState].label}
          </span>
        </div>

        {v.idChecked && (
          <p className="muted">ID checked {v.idCheckedAt ? when(v.idCheckedAt) : ''}.</p>
        )}
      </div>

      <div className="card stack" style={{ padding: 'var(--s5)' }}>
        <div className="between">
          <div className="stack" style={{ gap: 4 }}>
            <strong>Step two · Trade details</strong>
            <span className="muted">
              Your licence, insurance and shop address, so neighbours know who is arriving.
            </span>
          </div>
          <span className="badge" data-tone={STATE_COPY[v.professionalState].tone}>
            {STATE_COPY[v.professionalState].label}
          </span>
        </div>

        {!v.canGoProfessional && (
          <p className="muted">Available once your ID check is done.</p>
        )}
        {v.canGoProfessional && (
          <Link href="/me/professional" className="btn ghost" style={{ alignSelf: 'flex-start' }}>
            {v.isProfessional ? 'Edit trade details' : 'Fill in trade details'}
          </Link>
        )}
      </div>

      {/* Submitting */}
      {(!v.idChecked || (v.canGoProfessional && !v.isProfessional)) && (
        <div className="card stack" style={{ padding: 'var(--s5)' }}>
          <h2>Send something in</h2>
          <p className="muted" style={{ margin: 0 }}>
            A photo of your government ID for step one; your licence or insurance paperwork for step
            two. We record that we looked and when — the images themselves are deleted 30 days after
            a decision.
          </p>

          <div className="field">
            <label htmlFor="evidence">Photos</label>
            <input
              id="evidence"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
            />
            {files.length > 0 && <span className="muted">{files.length} selected</span>}
          </div>

          <div className="field">
            <label htmlFor="note">Anything we should know</label>
            <textarea id="note" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>

          {submit.isError && <div className="error">{(submit.error as ApiError).message}</div>}

          <div className="row" style={{ flexWrap: 'wrap' }}>
            {!v.idChecked && (
              <button
                className="btn"
                onClick={() => submit.mutate('IDENTITY')}
                disabled={busy || identityOpen || files.length === 0}
              >
                {identityOpen ? 'ID check already with us' : busy ? 'Sending…' : 'Send ID for checking'}
              </button>
            )}
            {v.canGoProfessional && !v.isProfessional && (
              <button
                className="btn ghost"
                onClick={() => submit.mutate('PROFESSIONAL')}
                disabled={busy || professionalOpen}
              >
                {professionalOpen ? 'Trade check already with us' : 'Apply as a professional'}
              </button>
            )}
          </div>
        </div>
      )}

      {v.history.length > 0 && (
        <div className="stack">
          <h2>History</h2>
          <div className="stack" style={{ gap: 0 }}>
            {v.history.map((item) => (
              <div
                key={item.id}
                className="stack"
                style={{ padding: 'var(--s4) 0', borderTop: '1px solid var(--rule)', gap: 6 }}
              >
                <div className="between">
                  <strong>{item.kind === 'IDENTITY' ? 'ID check' : 'Trade check'}</strong>
                  <span className="badge" data-tone={STATE_COPY[item.state].tone}>
                    {STATE_COPY[item.state].label}
                  </span>
                </div>
                <span className="muted">Sent {when(item.submittedAt)}</span>
                {item.decisionNote && (
                  <p style={{ margin: 0 }}>
                    <strong>{item.state === 'REJECTED' ? 'Why not: ' : 'Note: '}</strong>
                    {item.decisionNote}
                  </p>
                )}
                {item.open && (
                  <button
                    className="btn quiet"
                    style={{ alignSelf: 'flex-start', minHeight: 36 }}
                    onClick={() => withdraw.mutate(item.id)}
                    disabled={withdraw.isPending}
                  >
                    Withdraw
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
