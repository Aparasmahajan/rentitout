'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { ApiError, api, tokenStore } from '@/lib/api';
import { useAuth } from '@/lib/auth';

/**
 * Two steps, one number. There is no password to forget, and the POC hands the
 * code back in the response because there is no SMS provider behind it.
 */
export default function SignInPage() {
  const router = useRouter();
  const params = useSearchParams();
  const { setMe } = useAuth();
  // Where the member was when we stopped them. Only ever an in-app path.
  const rawNext = params.get('next') ?? '/';
  const next = rawNext.startsWith('/') && !rawNext.startsWith('//') ? rawNext : '/';

  const [phone, setPhone] = useState('+491700000001');
  const [displayName, setDisplayName] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState(false);
  const [hint, setHint] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function sendCode() {
    setBusy(true);
    setError(null);
    try {
      const res = await api.auth.startOtp(phone.trim());
      setSent(true);
      setHint(res.devCode);
      if (res.devCode) setCode(res.devCode);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not send a code');
    } finally {
      setBusy(false);
    }
  }

  async function verify() {
    setBusy(true);
    setError(null);
    try {
      const tokens = await api.auth.verify(phone.trim(), code.trim(), displayName.trim() || undefined);
      tokenStore.set(tokens);
      setMe(tokens.me);
      router.push(next);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'That did not work');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="stack" style={{ maxWidth: 420, margin: '10vh auto 0' }}>
      <h1 className="brand" style={{ fontSize: '2.6rem' }}>
        Radius
      </h1>
      <p className="muted">
        {next === '/'
          ? 'A neighbourhood index of people, skills and things. One account: you rent, and you lend.'
          : 'One step before you can ask a neighbour for something. We will take you straight back.'}
      </p>

      {error && <div className="error">{error}</div>}

      <div className="field">
        <label htmlFor="phone">Phone number</label>
        <input
          id="phone"
          inputMode="tel"
          autoComplete="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          disabled={sent}
        />
      </div>

      {!sent && (
        <>
          <div className="field">
            <label htmlFor="name">Your name (new members only)</label>
            <input
              id="name"
              autoComplete="name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="Amara Okafor"
            />
          </div>
          <button className="btn block" onClick={sendCode} disabled={busy || phone.trim().length < 8}>
            {busy ? 'Sending…' : 'Send me a code'}
          </button>
        </>
      )}

      {sent && (
        <>
          <div className="field">
            <label htmlFor="code">Six-digit code</label>
            <input
              id="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
          </div>
          {hint && (
            <p className="muted">
              No SMS provider in the POC — the code is <strong>{hint}</strong>, and it is also in the
              user-service log.
            </p>
          )}
          <button className="btn block" onClick={verify} disabled={busy || code.trim().length !== 6}>
            {busy ? 'Checking…' : 'Sign in'}
          </button>
          <button className="btn ghost block" onClick={() => setSent(false)} disabled={busy}>
            Use a different number
          </button>
        </>
      )}
    </section>
  );
}
